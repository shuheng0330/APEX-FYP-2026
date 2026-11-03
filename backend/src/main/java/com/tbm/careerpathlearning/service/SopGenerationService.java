package com.tbm.careerpathlearning.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tbm.careerpathlearning.dto.GeneratedSopContent;
import com.tbm.careerpathlearning.dto.GeneratedSopPlan;
import com.tbm.careerpathlearning.enums.MaterialReviewAction;
import com.tbm.careerpathlearning.enums.QuizDifficulty;
import com.tbm.careerpathlearning.enums.QuizQuestionType;
import com.tbm.careerpathlearning.enums.ReviewStatus;
import com.tbm.careerpathlearning.enums.SopGenerationStatus;
import com.tbm.careerpathlearning.exception.InternalServerException;
import com.tbm.careerpathlearning.model.SopDocument;
import com.tbm.careerpathlearning.model.SopMaterialReviewEvent;
import com.tbm.careerpathlearning.model.SopModule;
import com.tbm.careerpathlearning.model.SopQuizQuestion;
import com.tbm.careerpathlearning.repository.SopDocumentRepository;
import com.tbm.careerpathlearning.repository.SopMaterialReviewEventRepository;
import com.tbm.careerpathlearning.repository.SopModuleRepository;
import com.tbm.careerpathlearning.repository.SopQuizQuestionRepository;
import com.tbm.careerpathlearning.repository.SopQuizReviewEventRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.file.Path;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Orchestrates AI generation for an SOP: parse -> prompt Gemini -> persist
 * modules + quiz, tracking {@link SopGenerationStatus} for the UI (FR-08-04/05).
 * When the AI fails, the remaining modules are built offline by {@link LocalSopGenerator}.
 */
@Service
public class SopGenerationService {

    private static final Logger log = LoggerFactory.getLogger(SopGenerationService.class);

    /** Safety bound on how many modules a single SOP can expand into. */
    private static final int MAX_MODULES = 25;

    /** The question formats generated for staff (FR-08-01): multiple choice and true/false. */
    /** The most earlier questions listed in a prompt, so a long course doesn't crowd out the SOP text. */
    private static final int MAX_ASKED_IN_PROMPT = 40;

    private static final Set<QuizQuestionType> GENERATED_TYPES = EnumSet.of(QuizQuestionType.MULTIPLE_CHOICE, QuizQuestionType.TRUE_FALSE);

    /** Marks a module's content as built offline, so the review page can flag it. */
    private static final String OFFLINE_SOURCE = "offline";

    @Autowired private SopDocumentRepository sopDocumentRepository;
    @Autowired private SopModuleRepository sopModuleRepository;
    @Autowired private SopQuizQuestionRepository sopQuizQuestionRepository;
    @Autowired private SopQuizReviewEventRepository sopQuizReviewEventRepository;
    @Autowired private SopMaterialReviewEventRepository sopMaterialReviewEventRepository;
    @Autowired private DocumentParserService documentParserService;
    @Autowired private FileService fileService;
    @Autowired private GeminiClient geminiClient;
    @Autowired private LocalSopGenerator localSopGenerator;
    @Autowired private Clock clock;

    /** Self-reference so @Transactional helper methods go through the Spring proxy (not self-invocation). */
    @Autowired @Lazy private SopGenerationService self;

    /**
     * Delay between Gemini calls to respect the free-tier rate limit
     * (gemini-2.5-flash free tier = 5 requests/minute). ~14s keeps us under it.
     */
    @Value("${gemini.call-delay-ms:20000}")
    private long callDelayMs;

    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * Runs the full generation pipeline (FR-08-05). {@link SopGenerationQueue} calls it on its own thread, one
     * document at a time (FR-06-11), so the upload request returns fast.
     */
    public void generateForDocument(Long sopDocumentId) {
        SopDocument doc = sopDocumentRepository.findById(sopDocumentId).orElse(null);
        if (doc == null) {
            log.warn("SOP generation skipped: document {} not found", sopDocumentId);
            return;
        }
        try {
            updateStatus(doc, SopGenerationStatus.PARSING, null);

            String text = doc.getExtractedText();
            if (text == null || text.isBlank()) {
                Path path = fileService.getFilePath(doc.getFilePath());
                text = documentParserService.extractText(path, doc.getOriginalFilename());
                doc.setExtractedText(text);
                sopDocumentRepository.save(doc);
            }
            if (text.isBlank()) {
                throw new RuntimeException("The document appears to be empty.");
            }

            updateStatus(doc, SopGenerationStatus.GENERATING, null);

            // Phase A — plan: break the SOP into modules that together cover every section.
            List<GeneratedSopPlan.PlannedModule> planned = planWithAi(doc, text);

            // Replace any previous generation, then expand each planned module on its own.
            self.clearGenerated(doc.getId());

            if (planned.isEmpty()) {
                // The AI could not plan the SOP at all: build every module from its text instead.
                List<GeneratedSopContent.GeneratedModule> offline = localSopGenerator.generate(doc.getTitle(), text);
                if (offline.isEmpty()) {
                    throw new RuntimeException("The AI is unavailable and no modules could be built from this document's text.");
                }
                List<String> asked = new ArrayList<>();
                for (int i = 0; i < offline.size(); i++) {
                    GeneratedSopContent.GeneratedModule gm = offline.get(i);
                    gm.setQuiz(fullQuiz(gm.getSections(), gm.getQuiz(), gm.getTitle(), asked));
                    self.saveModuleWithQuiz(doc.getId(), i + 1, gm, true);
                    asked.addAll(questionTexts(gm.getQuiz()));
                }
                updateStatus(doc, SopGenerationStatus.COMPLETED,
                        "The AI was unavailable, so all modules were generated offline from the SOP text. Review them carefully before approving.");
                log.info("SOP generation completed offline for document {} ({} modules)", sopDocumentId, offline.size());
                return;
            }

            int total = Math.min(planned.size(), MAX_MODULES);
            List<GeneratedSopContent.GeneratedModule> offline = null; // built once the AI fails part-way
            int saved = 0;
            int firstOffline = 0;
            List<String> asked = new ArrayList<>(); // what the modules so far already ask (test note AR-03)
            for (int i = 0; i < total; i++) {
                GeneratedSopPlan.PlannedModule pm = planned.get(i);
                GeneratedSopContent.GeneratedModule gm = null;
                if (offline == null) {
                    pace(); // stay under the free-tier rate limit between calls
                    try {
                        // Phase B — expand: full-fidelity content for THIS module only.
                        gm = callGeminiForModule(
                                buildModuleExpandPrompt(doc.getTitle(), text, pm.getTitle(), pm.getCovers(), i + 1, total, asked));
                    } catch (Exception e) {
                        // Stop calling the AI for this document; follow its plan offline from here on.
                        log.warn("AI failed on module {}/{} of document {}, finishing offline: {}",
                                i + 1, total, sopDocumentId, e.getMessage());
                        offline = localSopGenerator.generateForPlan(text, planned.subList(0, total));
                        firstOffline = saved + 1;
                    }
                }
                boolean fromOffline = gm == null;
                if (fromOffline) {
                    gm = offline.get(i);
                    if (gm == null) {
                        continue; // no SOP text matched this planned module
                    }
                }
                gm.setQuiz(fullQuiz(gm.getSections(), gm.getQuiz(), gm.getTitle(), asked));
                self.saveModuleWithQuiz(doc.getId(), ++saved, gm, fromOffline);
                asked.addAll(questionTexts(gm.getQuiz()));
            }

            updateStatus(doc, SopGenerationStatus.COMPLETED, offlineNote(firstOffline, saved));
            log.info("SOP generation completed for document {} ({} modules, 2-phase{})", sopDocumentId, saved,
                    firstOffline > 0 ? ", offline from module " + firstOffline : "");
        } catch (Exception e) {
            log.error("SOP generation failed for document {}", sopDocumentId, e);
            updateStatus(doc, SopGenerationStatus.FAILED, e.getMessage());
        }
    }

    /** Remove any previous generation for this document (supports re-runs). */
    @Transactional
    public void clearGenerated(Long sopDocumentId) {
        List<SopModule> existing = sopModuleRepository.findAllBySopDocumentIdOrderByModuleOrderAsc(sopDocumentId);
        if (!existing.isEmpty()) {
            List<Long> moduleIds = existing.stream().map(SopModule::getId).toList();
            sopQuizQuestionRepository.deleteAllBySopModuleIdIn(moduleIds);
            sopQuizReviewEventRepository.deleteAllBySopModuleIdIn(moduleIds);
            sopMaterialReviewEventRepository.deleteAllBySopModuleIdIn(moduleIds);
            sopModuleRepository.deleteAllBySopDocumentId(sopDocumentId);
        }
    }

    /** Persist one expanded module and its quiz (incremental, so progress is visible live). */
    @Transactional
    public void saveModuleWithQuiz(Long sopDocumentId, int order, GeneratedSopContent.GeneratedModule gm, boolean offline) {
        OffsetDateTime now = OffsetDateTime.now(clock);
        SopModule module = new SopModule();
        module.setSopDocumentId(sopDocumentId);
        module.setModuleOrder(order);
        module.setTitle(stripModulePrefix(safe(gm.getTitle(), "Module " + order)));
        module.setContent(serializeRichContent(gm, offline));
        module.setReviewStatus(ReviewStatus.PENDING);
        module.setCreatedAt(now);
        module.setUpdatedAt(now);
        SopModule savedModule = sopModuleRepository.save(module);
        // The first entry in the module's material history (FR-07-14).
        sopMaterialReviewEventRepository.save(SopMaterialReviewEvent.of(savedModule.getId(), MaterialReviewAction.GENERATED,
                offline ? "Built from the SOP text because the AI was unavailable. Review it carefully."
                        : "Written by the AI from the uploaded SOP.", null, now));

        saveQuestions(savedModule.getId(), gm.getQuiz(), now, ReviewStatus.PENDING);
    }

    /** Serialize the structured content fields to JSON for storage in the content column. */
    private String serializeRichContent(GeneratedSopContent.GeneratedModule gm, boolean offline) {
        try {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("summary", gm.getSummary() != null ? gm.getSummary() : "");
            map.put("learningObjectives", gm.getLearningObjectives() != null ? gm.getLearningObjectives() : List.of());
            map.put("tools", gm.getTools() != null ? gm.getTools() : List.of());
            map.put("sections", gm.getSections() != null ? gm.getSections() : List.of());
            map.put("keyTerms", gm.getKeyTerms() != null ? gm.getKeyTerms() : List.of());
            if (offline) {
                map.put("source", OFFLINE_SOURCE);
            }
            return objectMapper.writeValueAsString(map);
        } catch (Exception e) {
            return gm.getSummary() != null ? gm.getSummary() : "";
        }
    }

    /** Regenerates a single module's content from a trainer's rejection reason (FR-09-05, UC-18). */
    @Transactional
    public void regenerateModule(SopModule module, String reason) {
        SopDocument doc = sopDocumentRepository.findById(module.getSopDocumentId()).orElseThrow();
        List<String> asked = otherModulesQuestions(module);
        GeneratedSopContent.GeneratedModule gm = callGeminiForRegeneration(
                buildModuleRegenerationPrompt(doc.getTitle(), doc.getExtractedText(), module.getTitle(), reason, asked));
        gm.setQuiz(fullQuiz(gm.getSections(), gm.getQuiz(), safe(gm.getTitle(), module.getTitle()), asked));

        OffsetDateTime now = OffsetDateTime.now(clock);
        module.setTitle(safe(gm.getTitle(), module.getTitle()));
        module.setContent(serializeRichContent(gm, false));
        module.setReviewStatus(ReviewStatus.REGENERATED);
        module.setRejectionReason(reason);
        module.setUpdatedAt(now);
        sopModuleRepository.save(module);

        // Refresh the quiz to match the regenerated content.
        sopQuizQuestionRepository.deleteAllBySopModuleIdIn(List.of(module.getId()));
        saveQuestions(module.getId(), gm.getQuiz(), now, ReviewStatus.REGENERATED);
    }

    /** Regenerates only the quiz for a module from a trainer's rejection reason (FR-10-03). */
    @Transactional
    public void regenerateQuiz(SopModule module, String reason) {
        SopDocument doc = sopDocumentRepository.findById(module.getSopDocumentId()).orElseThrow();
        List<String> asked = otherModulesQuestions(module);
        GeneratedSopContent.GeneratedModule gm = callGeminiForRegeneration(
                buildQuizRegenerationPrompt(doc.getTitle(), module.getTitle(), module.getContent(), reason, asked));
        gm.setQuiz(fullQuiz(sectionsOf(module.getContent()), gm.getQuiz(), module.getTitle(), asked));

        OffsetDateTime now = OffsetDateTime.now(clock);
        sopQuizQuestionRepository.deleteAllBySopModuleIdIn(List.of(module.getId()));
        saveQuestions(module.getId(), gm.getQuiz(), now, ReviewStatus.REGENERATED);
    }

    /**
     * The quiz in the formats staff take, multiple choice and true/false (FR-08-01), without any question another
     * module of the course already asks (test note AR-03), topped up from the module's own text when fewer than
     * QUIZ_SIZE questions are left. A top-up question that repeats another module's is left out too.
     */
    private List<GeneratedSopContent.GeneratedQuestion> fullQuiz(List<GeneratedSopContent.ContentSection> sections,
                                                               List<GeneratedSopContent.GeneratedQuestion> quiz,
                                                               String title, Collection<String> asked) {
        List<GeneratedSopContent.GeneratedQuestion> kept = quiz == null ? List.of()
                : quiz.stream().filter(q -> GENERATED_TYPES.contains(parseType(q.getType()))).toList();
        kept = QuestionOverlap.withoutRepeats(kept, asked);
        return QuestionOverlap.withoutRepeats(
                localSopGenerator.topUpQuiz(sections, kept, title, LocalSopGenerator.QUIZ_SIZE, asked), asked);
    }

    private static List<String> questionTexts(List<GeneratedSopContent.GeneratedQuestion> quiz) {
        return quiz == null ? List.of() : quiz.stream().map(GeneratedSopContent.GeneratedQuestion::getQuestion).toList();
    }

    /** The questions the course's other modules ask, so a rewrite of this one doesn't repeat them. */
    private List<String> otherModulesQuestions(SopModule module) {
        List<Long> others = sopModuleRepository.findAllBySopDocumentIdOrderByModuleOrderAsc(module.getSopDocumentId()).stream()
                .map(SopModule::getId).filter(id -> !id.equals(module.getId())).toList();
        return others.isEmpty() ? List.of() : sopQuizQuestionRepository.findAllBySopModuleIdInOrderByIdAsc(others).stream()
                .map(SopQuizQuestion::getQuestionText).toList();
    }

    /** Tells the AI which questions other modules already ask; empty when none do. */
    private static String askedBlock(List<String> asked) {
        if (asked == null || asked.isEmpty()) {
            return "";
        }
        StringBuilder out = new StringBuilder("Other modules of this course already ask these questions. Do not repeat them,"
                + " reword them or test the same fact again:\n");
        for (String q : asked.subList(Math.max(0, asked.size() - MAX_ASKED_IN_PROMPT), asked.size())) {
            out.append("- ").append(q.length() > 200 ? q.substring(0, 200) + "..." : q).append("\n");
        }
        return out.toString();
    }

    /** The content sections stored for a module, or none when its content is not structured JSON. */
    private List<GeneratedSopContent.ContentSection> sectionsOf(String content) {
        try {
            return objectMapper.readValue(content, GeneratedSopContent.GeneratedModule.class).getSections();
        } catch (Exception e) {
            return List.of();
        }
    }

    private void saveQuestions(Long moduleId, List<GeneratedSopContent.GeneratedQuestion> questions,
                               OffsetDateTime now, ReviewStatus status) {
        if (questions == null) {
            return;
        }
        for (GeneratedSopContent.GeneratedQuestion gq : questions) {
            SopQuizQuestion q = new SopQuizQuestion();
            q.setSopModuleId(moduleId);
            q.setQuestionType(parseType(gq.getType()));
            q.setQuestionText(safe(gq.getQuestion(), ""));
            q.setOptions(writeOptions(gq.getOptions()));
            q.setCorrectAnswer(gq.getAnswer());
            q.setExplanation(gq.getExplanation());
            q.setDifficulty(parseDifficulty(gq.getDifficulty()));
            q.setReviewStatus(status);
            q.setCreatedAt(now);
            q.setUpdatedAt(now);
            sopQuizQuestionRepository.save(q);
        }
    }

    // --- Gemini calls + JSON parsing -----------------------------------------

    /** Phase A via the AI. An empty list means the AI could not plan the SOP, so it is built offline. */
    private List<GeneratedSopPlan.PlannedModule> planWithAi(SopDocument doc, String text) {
        try {
            List<GeneratedSopPlan.PlannedModule> planned = callGeminiPlan(buildOutlinePrompt(doc.getTitle(), text)).getModules();
            return planned == null ? List.of() : planned;
        } catch (Exception e) {
            log.warn("AI planning failed for document {}, generating offline: {}", doc.getId(), e.getMessage());
            return List.of();
        }
    }

    /** AI call for a trainer's regenerate request. There is no offline fallback: it must follow the feedback. */
    private GeneratedSopContent.GeneratedModule callGeminiForRegeneration(String prompt) {
        try {
            return callGeminiForModule(prompt);
        } catch (Exception e) {
            log.warn("AI regeneration failed: {}", e.getMessage());
            throw new InternalServerException("AI unavailable",
                    "The AI service is unavailable right now, so nothing was regenerated. Please try again later.", e);
        }
    }

    private GeneratedSopPlan callGeminiPlan(String prompt) {
        String raw = geminiClient.generateJson(prompt); // throws with a clear message on HTTP errors
        try {
            return objectMapper.readValue(stripFences(raw), GeneratedSopPlan.class);
        } catch (Exception e) {
            throw new RuntimeException("Could not parse AI module plan: " + e.getMessage(), e);
        }
    }

    private GeneratedSopContent.GeneratedModule callGeminiForModule(String prompt) {
        String raw = geminiClient.generateJson(prompt);
        try {
            return objectMapper.readValue(stripFences(raw), GeneratedSopContent.GeneratedModule.class);
        } catch (Exception e) {
            throw new RuntimeException("Could not parse AI response for module: " + e.getMessage(), e);
        }
    }

    private String stripFences(String raw) {
        if (raw == null) {
            return "{}";
        }
        String t = raw.trim();
        if (t.startsWith("```")) {
            t = t.replaceAll("^```[a-zA-Z]*\\s*", "").replaceAll("```\\s*$", "").trim();
        }
        return t;
    }

    // --- Prompts -------------------------------------------------------------

    /** Phase A: ask only for a module plan that covers EVERY section of the SOP. */
    private String buildOutlinePrompt(String title, String sopText) {
        return """
                You are an instructional designer. Read the ENTIRE SOP below and break it into a
                sequence of training modules that, TOGETHER, COVER EVERY SECTION, STEP, RULE AND
                DETAIL of the SOP. Do not skip or omit any section. Create as many modules as needed
                for full coverage (typically one per major numbered section or sub-section), but each
                module needs enough content for a quiz of at least %d questions: fold any section too short
                for that into a neighbouring module, and name it in that module's "covers". Keep the
                modules in the same logical order as the SOP.

                For each module return:
                - "title": a short module title.
                - "covers": exactly which SOP sections / headings / steps / rules this module must
                  fully include (be specific, e.g. "Section 4.1 Handling Cash: cash drawer setup,
                  etiquette, receiving payments, security measures incl. counterfeit bills").

                Respond with ONLY a JSON object of this exact shape:
                {"modules":[{"title":"string","covers":"string"}]}

                SOP title: %s

                SOP content:
                %s
                """.formatted(LocalSopGenerator.QUIZ_SIZE, safe(title, "Untitled SOP"), truncate(sopText));
    }

    /** Phase B: expand ONE planned module into structured rich content + quiz. */
    private String buildModuleExpandPrompt(String sopTitle, String sopText, String moduleTitle,
                                           String covers, int order, int total, List<String> asked) {
        return """
                You are writing ONE training module (module %d of %d) from the SOP below.
                Produce COMPLETE, detailed content that fully preserves EVERY step, rule, figure,
                threshold and consequence in this module's scope. DO NOT summarize or omit anything.

                Return ONLY a JSON object with this EXACT shape — no markdown fences, no extra keys:
                {
                  "title": "module title",
                  "summary": "2-3 sentence overview of what this module covers and why it matters",
                  "learningObjectives": ["After completing this module learners will be able to ..."],
                  "tools": ["tool or material name"],
                  "sections": [
                    {"type":"paragraph","heading":"Section Title","body":"Full text..."},
                    {"type":"steps","heading":"Procedure Title","items":["Step 1: ...","Step 2: ..."]},
                    {"type":"table","heading":"Table Title","headers":["Col1","Col2"],"rows":[["v1","v2"]]},
                    {"type":"warnings","heading":"Critical Safety Rules","items":["Warning text..."]}
                  ],
                  "keyTerms": [{"term":"Term","definition":"Definition"}],
                  "quiz": [
                    {"type":"MULTIPLE_CHOICE","question":"...","options":["a","b","c","d"],"answer":"exact option text","explanation":"...","difficulty":"MEDIUM"},
                    {"type":"TRUE_FALSE","question":"...","options":["True","False"],"answer":"True","explanation":"...","difficulty":"EASY"}
                  ]
                }

                Section rules:
                - Use "steps" for numbered procedures, "warnings" for safety/critical rules,
                  "table" for reference tables, "paragraph" for explanatory text.
                - Create as many section objects as needed — one per logical block of content.
                - learningObjectives: 2-4 items each starting with an action verb.
                - tools: only if there are physical tools or materials; otherwise empty [].
                - keyTerms: key technical terms with definitions; otherwise [].
                - quiz: at least %d questions, more where this module's content supports them, of types
                  MULTIPLE_CHOICE or TRUE_FALSE only, with at least one of each type.
                - difficulty of each question: EASY (recall one stated fact), MEDIUM (apply a step
                  or rule), HARD (combine several rules or judge a scenario).
                %s
                Module title: %s
                This module must completely cover: %s

                SOP title: %s
                Full SOP content:
                %s
                """.formatted(order, total, LocalSopGenerator.QUIZ_SIZE, askedBlock(asked), safe(moduleTitle, "Module " + order),
                safe(covers, "the relevant section"), safe(sopTitle, ""), truncate(sopText));
    }

    private String buildModuleRegenerationPrompt(String sopTitle, String sopText, String moduleTitle, String reason,
                                                 List<String> asked) {
        return """
                Regenerate ONE training module from the SOP below. The trainer rejected the
                previous version for this reason: "%s". Address that feedback fully.
                If the feedback asks to drop something, such as key terms, tools or learning
                objectives, return that field as an empty list []. Any list below may be empty.

                Return ONLY a JSON object with this EXACT shape — no markdown fences:
                {
                  "title": "module title",
                  "summary": "2-3 sentence overview",
                  "learningObjectives": ["..."],
                  "tools": ["..."],
                  "sections": [
                    {"type":"paragraph","heading":"string","body":"string"},
                    {"type":"steps","heading":"string","items":["string"]},
                    {"type":"table","heading":"string","headers":["string"],"rows":[["string"]]},
                    {"type":"warnings","heading":"string","items":["string"]}
                  ],
                  "keyTerms": [{"term":"string","definition":"string"}],
                  "quiz": [
                    {"type":"MULTIPLE_CHOICE","question":"...","options":["a","b","c","d"],"answer":"...","explanation":"...","difficulty":"MEDIUM"},
                    {"type":"TRUE_FALSE","question":"...","options":["True","False"],"answer":"True","explanation":"...","difficulty":"EASY"}
                  ]
                }
                The quiz must have at least %d questions, more where the content supports them, of types
                MULTIPLE_CHOICE or TRUE_FALSE only. Give every quiz question a "difficulty": EASY
                (recall one stated fact), MEDIUM (apply a step or rule) or HARD (combine several rules
                or judge a scenario).
                %s
                SOP title: %s
                Module to regenerate: %s
                SOP content:
                %s
                """.formatted(safe(reason, "no reason given"), LocalSopGenerator.QUIZ_SIZE, askedBlock(asked), safe(sopTitle, ""),
                safe(moduleTitle, ""), truncate(sopText));
    }

    private String buildQuizRegenerationPrompt(String sopTitle, String moduleTitle, String moduleContent, String reason,
                                               List<String> asked) {
        return """
                Regenerate ONLY the quiz for the training module below. The trainer rejected the
                previous quiz for this reason: "%s". Address that feedback. Keep the same "title"
                and "content" exactly as given, and produce a new quiz of at least %d questions, more where
                the content supports them (types: MULTIPLE_CHOICE with 4 options, or TRUE_FALSE with
                ["True","False"]; no other types) with exact "answer", one-sentence "explanation" and a "difficulty" of
                EASY (recall one stated fact), MEDIUM (apply a step or rule) or HARD (combine several
                rules or judge a scenario).
                %s
                Respond with ONLY a JSON object of this exact shape:
                {"title":"string","content":"string","quiz":[{"type":"MULTIPLE_CHOICE","question":"string","options":["a","b","c","d"],"answer":"string","explanation":"string","difficulty":"MEDIUM"}]}

                SOP title: %s
                Module title: %s
                Module content:
                %s
                """.formatted(safe(reason, "no reason given"), LocalSopGenerator.QUIZ_SIZE, askedBlock(asked), safe(sopTitle, ""),
                safe(moduleTitle, ""), truncate(moduleContent));
    }

    // --- helpers -------------------------------------------------------------

    /** Status note when the AI dropped out part-way; null when every module came from the AI. */
    private String offlineNote(int firstOffline, int saved) {
        if (firstOffline == 0 || firstOffline > saved) {
            return null;
        }
        String modules = firstOffline == saved ? "module " + saved + " was" : "modules " + firstOffline + "-" + saved + " were";
        return "The AI became unavailable, so " + modules
                + " generated offline from the SOP text. Review them carefully before approving.";
    }

    /** Sleep between Gemini calls so we stay within the free-tier requests-per-minute limit. */
    private void pace() {
        if (callDelayMs <= 0) {
            return;
        }
        try {
            Thread.sleep(callDelayMs);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private void updateStatus(SopDocument doc, SopGenerationStatus status, String message) {
        doc.setGenerationStatus(status);
        doc.setStatusMessage(message);
        doc.setUpdatedAt(OffsetDateTime.now(clock));
        sopDocumentRepository.save(doc);
    }

    /** The AI's difficulty label, or null when it gave none (FR-08-07). */
    private QuizDifficulty parseDifficulty(String difficulty) {
        if (difficulty == null) {
            return null;
        }
        try {
            return QuizDifficulty.valueOf(difficulty.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private QuizQuestionType parseType(String type) {
        if (type == null) {
            return QuizQuestionType.MULTIPLE_CHOICE;
        }
        try {
            return QuizQuestionType.valueOf(type.trim().toUpperCase().replace('-', '_').replace(' ', '_'));
        } catch (IllegalArgumentException e) {
            return QuizQuestionType.MULTIPLE_CHOICE;
        }
    }

    private String writeOptions(List<String> options) {
        if (options == null || options.isEmpty()) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(options);
        } catch (Exception e) {
            return null;
        }
    }

    private String safe(String value, String fallback) {
        return (value == null || value.isBlank()) ? fallback : value;
    }

    /** Remove a leading "Module N:" the model sometimes adds (the UI already numbers modules). */
    private String stripModulePrefix(String title) {
        return title.replaceFirst("(?i)^\\s*module\\s*\\d+\\s*[:\\-.]?\\s*", "").trim();
    }

    /**
     * Safety bound on prompt size. Set high so normal SOPs are never truncated
     * (the previous 24k cap risked dropping content from longer documents).
     */
    private String truncate(String text) {
        int max = 200000;
        if (text == null) {
            return "";
        }
        return text.length() <= max ? text : text.substring(0, max);
    }
}
