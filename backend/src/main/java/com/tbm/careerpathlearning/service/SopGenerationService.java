package com.tbm.careerpathlearning.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tbm.careerpathlearning.dto.GeneratedSopContent;
import com.tbm.careerpathlearning.dto.GeneratedSopPlan;
import com.tbm.careerpathlearning.enums.QuizQuestionType;
import com.tbm.careerpathlearning.enums.ReviewStatus;
import com.tbm.careerpathlearning.enums.SopGenerationStatus;
import com.tbm.careerpathlearning.model.SopDocument;
import com.tbm.careerpathlearning.model.SopModule;
import com.tbm.careerpathlearning.model.SopQuizQuestion;
import com.tbm.careerpathlearning.repository.SopDocumentRepository;
import com.tbm.careerpathlearning.repository.SopModuleRepository;
import com.tbm.careerpathlearning.repository.SopQuizQuestionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.file.Path;
import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Orchestrates AI generation for an SOP: parse -> prompt Gemini -> persist
 * modules + quiz, tracking {@link SopGenerationStatus} for the UI (FR-08-04/05).
 */
@Service
public class SopGenerationService {

    private static final Logger log = LoggerFactory.getLogger(SopGenerationService.class);

    /** Safety bound on how many modules a single SOP can expand into. */
    private static final int MAX_MODULES = 25;

    @Autowired private SopDocumentRepository sopDocumentRepository;
    @Autowired private SopModuleRepository sopModuleRepository;
    @Autowired private SopQuizQuestionRepository sopQuizQuestionRepository;
    @Autowired private DocumentParserService documentParserService;
    @Autowired private FileService fileService;
    @Autowired private GeminiClient geminiClient;

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
     * Runs the full generation pipeline on a background thread (FR-08-05).
     * Called after the document row is created so the upload request returns fast.
     */
    @Async
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
            GeneratedSopPlan plan = callGeminiPlan(buildOutlinePrompt(doc.getTitle(), text));
            List<GeneratedSopPlan.PlannedModule> planned = plan.getModules();
            if (planned == null || planned.isEmpty()) {
                throw new RuntimeException("The AI did not return any modules for this document.");
            }

            // Replace any previous generation, then expand each planned module on its own.
            self.clearGenerated(doc.getId());

            int total = Math.min(planned.size(), MAX_MODULES);
            for (int i = 0; i < total; i++) {
                pace(); // stay under the free-tier rate limit between calls
                GeneratedSopPlan.PlannedModule pm = planned.get(i);
                // Phase B — expand: full-fidelity content + quiz for THIS module only.
                GeneratedSopContent.GeneratedModule gm = callGeminiForModule(
                        buildModuleExpandPrompt(doc.getTitle(), text, pm.getTitle(), pm.getCovers(), i + 1, total));
                self.saveModuleWithQuiz(doc.getId(), i + 1, gm);
            }

            updateStatus(doc, SopGenerationStatus.COMPLETED, null);
            log.info("SOP generation completed for document {} ({} modules, 2-phase)", sopDocumentId, total);
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
            sopQuizQuestionRepository.deleteAllBySopModuleIdIn(existing.stream().map(SopModule::getId).toList());
            sopModuleRepository.deleteAllBySopDocumentId(sopDocumentId);
        }
    }

    /** Persist one expanded module and its quiz (incremental, so progress is visible live). */
    @Transactional
    public void saveModuleWithQuiz(Long sopDocumentId, int order, GeneratedSopContent.GeneratedModule gm) {
        OffsetDateTime now = OffsetDateTime.now();
        SopModule module = new SopModule();
        module.setSopDocumentId(sopDocumentId);
        module.setModuleOrder(order);
        module.setTitle(stripModulePrefix(safe(gm.getTitle(), "Module " + order)));
        module.setContent(serializeRichContent(gm));
        module.setReviewStatus(ReviewStatus.PENDING);
        module.setCreatedAt(now);
        module.setUpdatedAt(now);
        SopModule savedModule = sopModuleRepository.save(module);

        saveQuestions(savedModule.getId(), gm.getQuiz(), now);
    }

    /** Serialize the structured content fields to JSON for storage in the content column. */
    private String serializeRichContent(GeneratedSopContent.GeneratedModule gm) {
        try {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("summary", gm.getSummary() != null ? gm.getSummary() : "");
            map.put("learningObjectives", gm.getLearningObjectives() != null ? gm.getLearningObjectives() : List.of());
            map.put("tools", gm.getTools() != null ? gm.getTools() : List.of());
            map.put("sections", gm.getSections() != null ? gm.getSections() : List.of());
            map.put("keyTerms", gm.getKeyTerms() != null ? gm.getKeyTerms() : List.of());
            return objectMapper.writeValueAsString(map);
        } catch (Exception e) {
            return gm.getSummary() != null ? gm.getSummary() : "";
        }
    }

    /** Regenerates a single module's content from a trainer's rejection reason (FR-09-05, UC-18). */
    @Transactional
    public void regenerateModule(SopModule module, String reason) {
        SopDocument doc = sopDocumentRepository.findById(module.getSopDocumentId()).orElseThrow();
        GeneratedSopContent.GeneratedModule gm = callGeminiForModule(
                buildModuleRegenerationPrompt(doc.getTitle(), doc.getExtractedText(), module.getTitle(), reason));

        OffsetDateTime now = OffsetDateTime.now();
        module.setTitle(safe(gm.getTitle(), module.getTitle()));
        module.setContent(serializeRichContent(gm));
        module.setReviewStatus(ReviewStatus.REGENERATED);
        module.setRejectionReason(reason);
        module.setUpdatedAt(now);
        sopModuleRepository.save(module);

        // Refresh the quiz to match the regenerated content.
        sopQuizQuestionRepository.deleteAllBySopModuleIdIn(List.of(module.getId()));
        saveQuestions(module.getId(), gm.getQuiz(), now);
    }

    /** Regenerates only the quiz for a module from a trainer's rejection reason (FR-10-03). */
    @Transactional
    public void regenerateQuiz(SopModule module, String reason) {
        SopDocument doc = sopDocumentRepository.findById(module.getSopDocumentId()).orElseThrow();
        GeneratedSopContent.GeneratedModule gm = callGeminiForModule(
                buildQuizRegenerationPrompt(doc.getTitle(), module.getTitle(), module.getContent(), reason));

        OffsetDateTime now = OffsetDateTime.now();
        sopQuizQuestionRepository.deleteAllBySopModuleIdIn(List.of(module.getId()));
        saveQuestions(module.getId(), gm.getQuiz(), now);
    }

    private void saveQuestions(Long moduleId, List<GeneratedSopContent.GeneratedQuestion> questions, OffsetDateTime now) {
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
            q.setReviewStatus(ReviewStatus.PENDING);
            q.setCreatedAt(now);
            q.setUpdatedAt(now);
            sopQuizQuestionRepository.save(q);
        }
    }

    // --- Gemini calls + JSON parsing -----------------------------------------

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
                DETAIL of the SOP. Do not skip, merge away, or omit any section. Create as many
                modules as needed for full coverage (typically one per major numbered section or
                sub-section). Keep the modules in the same logical order as the SOP.

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
                """.formatted(safe(title, "Untitled SOP"), truncate(sopText));
    }

    /** Phase B: expand ONE planned module into structured rich content + quiz. */
    private String buildModuleExpandPrompt(String sopTitle, String sopText, String moduleTitle,
                                           String covers, int order, int total) {
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
                    {"type":"MULTIPLE_CHOICE","question":"...","options":["a","b","c","d"],"answer":"exact option text","explanation":"..."},
                    {"type":"TRUE_FALSE","question":"...","options":["True","False"],"answer":"True","explanation":"..."}
                  ]
                }

                Section rules:
                - Use "steps" for numbered procedures, "warnings" for safety/critical rules,
                  "table" for reference tables, "paragraph" for explanatory text.
                - Create as many section objects as needed — one per logical block of content.
                - learningObjectives: 2-4 items each starting with an action verb.
                - tools: only if there are physical tools or materials; otherwise empty [].
                - keyTerms: key technical terms with definitions; otherwise [].
                - quiz: 2-3 questions, types MULTIPLE_CHOICE or TRUE_FALSE only.

                Module title: %s
                This module must completely cover: %s

                SOP title: %s
                Full SOP content:
                %s
                """.formatted(order, total, safe(moduleTitle, "Module " + order),
                safe(covers, "the relevant section"), safe(sopTitle, ""), truncate(sopText));
    }

    private String buildModuleRegenerationPrompt(String sopTitle, String sopText, String moduleTitle, String reason) {
        return """
                Regenerate ONE training module from the SOP below. The trainer rejected the
                previous version for this reason: "%s". Address that feedback fully.

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
                    {"type":"MULTIPLE_CHOICE","question":"...","options":["a","b","c","d"],"answer":"...","explanation":"..."},
                    {"type":"TRUE_FALSE","question":"...","options":["True","False"],"answer":"True","explanation":"..."}
                  ]
                }

                SOP title: %s
                Module to regenerate: %s
                SOP content:
                %s
                """.formatted(safe(reason, "no reason given"), safe(sopTitle, ""), safe(moduleTitle, ""), truncate(sopText));
    }

    private String buildQuizRegenerationPrompt(String sopTitle, String moduleTitle, String moduleContent, String reason) {
        return """
                Regenerate ONLY the quiz for the training module below. The trainer rejected the
                previous quiz for this reason: "%s". Address that feedback. Keep the same "title"
                and "content" exactly as given, and produce a new 2-4 question quiz (types:
                MULTIPLE_CHOICE with 4 options, TRUE_FALSE with ["True","False"], FILL_IN_THE_BLANK
                using ___) with exact "answer" and one-sentence "explanation".

                Respond with ONLY a JSON object of this exact shape:
                {"title":"string","content":"string","quiz":[{"type":"MULTIPLE_CHOICE","question":"string","options":["a","b","c","d"],"answer":"string","explanation":"string"}]}

                SOP title: %s
                Module title: %s
                Module content:
                %s
                """.formatted(safe(reason, "no reason given"), safe(sopTitle, ""), safe(moduleTitle, ""), truncate(moduleContent));
    }

    // --- helpers -------------------------------------------------------------

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
        doc.setUpdatedAt(OffsetDateTime.now());
        sopDocumentRepository.save(doc);
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
