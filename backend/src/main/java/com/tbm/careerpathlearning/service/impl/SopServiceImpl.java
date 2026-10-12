package com.tbm.careerpathlearning.service.impl;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tbm.careerpathlearning.dto.SopDetailDto;
import com.tbm.careerpathlearning.dto.SopDocumentDto;
import com.tbm.careerpathlearning.dto.SopModuleDto;
import com.tbm.careerpathlearning.dto.SopQuizQuestionDto;
import com.tbm.careerpathlearning.dto.SopQuizReviewEventDto;
import com.tbm.careerpathlearning.enums.QuizQuestionType;
import com.tbm.careerpathlearning.enums.QuizReviewAction;
import com.tbm.careerpathlearning.enums.ReviewStatus;
import com.tbm.careerpathlearning.enums.SopGenerationStatus;
import com.tbm.careerpathlearning.exception.BadRequestException;
import com.tbm.careerpathlearning.model.SopDocument;
import com.tbm.careerpathlearning.model.SopEnrollment;
import com.tbm.careerpathlearning.model.SopModule;
import com.tbm.careerpathlearning.model.SopQuizQuestion;
import com.tbm.careerpathlearning.model.SopQuizReviewEvent;
import com.tbm.careerpathlearning.repository.SopAssignmentRepository;
import com.tbm.careerpathlearning.repository.SopCourseFeedbackRepository;
import com.tbm.careerpathlearning.repository.SopDocumentRepository;
import com.tbm.careerpathlearning.repository.SopEnrollmentRepository;
import com.tbm.careerpathlearning.repository.SopQuizAttemptRepository;
import com.tbm.careerpathlearning.repository.SopModuleRepository;
import com.tbm.careerpathlearning.repository.SopQuizQuestionRepository;
import com.tbm.careerpathlearning.repository.SopQuizReviewEventRepository;
import com.tbm.careerpathlearning.repository.StaffRepository;
import com.tbm.careerpathlearning.service.FileService;
import com.tbm.careerpathlearning.service.SopGenerationService;
import com.tbm.careerpathlearning.service.SopService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class SopServiceImpl implements SopService {

    @Autowired private SopDocumentRepository sopDocumentRepository;
    @Autowired private SopModuleRepository sopModuleRepository;
    @Autowired private SopQuizQuestionRepository sopQuizQuestionRepository;
    @Autowired private SopQuizReviewEventRepository sopQuizReviewEventRepository;
    @Autowired private SopAssignmentRepository sopAssignmentRepository;
    @Autowired private SopEnrollmentRepository sopEnrollmentRepository;
    @Autowired private SopQuizAttemptRepository sopQuizAttemptRepository;
    @Autowired private SopCourseFeedbackRepository sopCourseFeedbackRepository;
    @Autowired private FileService fileService;
    @Autowired private SopGenerationService sopGenerationService;
    @Autowired private StaffRepository staffRepository;
    @Autowired private Clock clock;

    private final ObjectMapper objectMapper = new ObjectMapper();

    // === Module 1: upload + generation ======================================

    @Override
    @Transactional
    public SopDocumentDto upload(MultipartFile file, String title, String version, String departmentTag, UUID userId) {
        if (file == null || file.isEmpty()) {
            throw new BadRequestException("Please select a document to upload.");
        }
        String filename = file.getOriginalFilename();
        String lower = filename == null ? "" : filename.toLowerCase();
        if (!lower.endsWith(".doc") && !lower.endsWith(".docx") && !lower.endsWith(".pdf")) {
            throw new BadRequestException("Only Word (.doc, .docx) or PDF (.pdf) documents are supported.");
        }

        String storedPath = fileService.uploadFile(file, "sop");

        OffsetDateTime now = OffsetDateTime.now(clock);
        SopDocument doc = new SopDocument();
        doc.setTitle(resolveTitle(title, filename)); // FR-08-03: filename as title when blank
        doc.setVersion(version);
        doc.setOriginalFilename(filename);
        doc.setFilePath(storedPath);
        doc.setDepartmentTag(departmentTag);
        doc.setUploadedBy(userId);
        doc.setGenerationStatus(SopGenerationStatus.PENDING);
        doc.setCreatedAt(now);
        doc.setUpdatedAt(now);
        SopDocument saved = sopDocumentRepository.save(doc);

        // Kick off parse + AI generation in the background (FR-08-04/05).
        startGenerationAfterCommit(saved.getId());

        return toDocDto(saved, 0);
    }

    @Override
    public List<SopDocumentDto> listDocuments() {
        return sopDocumentRepository.findAllByIsDeletedFalseOrderByCreatedAtDesc().stream()
                .map(d -> toDocDto(d, (int) sopModuleRepository
                        .findAllBySopDocumentIdOrderByModuleOrderAsc(d.getId()).size()))
                .toList();
    }

    @Override
    public SopDetailDto getDetail(Long sopDocumentId) {
        SopDocument doc = getDocOrThrow(sopDocumentId);
        List<SopModule> modules = sopModuleRepository.findAllBySopDocumentIdOrderByModuleOrderAsc(sopDocumentId);

        SopDetailDto detail = new SopDetailDto();
        detail.setDocument(toDocDto(doc, modules.size()));
        detail.setModules(modules.stream().map(this::toModuleDto).toList());
        return detail;
    }

    @Override
    public SopDocumentDto getStatus(Long sopDocumentId) {
        SopDocument doc = getDocOrThrow(sopDocumentId);
        int moduleCount = sopModuleRepository.findAllBySopDocumentIdOrderByModuleOrderAsc(sopDocumentId).size();
        return toDocDto(doc, moduleCount);
    }

    @Override
    @Transactional
    public SopDocumentDto regenerateDocument(Long sopDocumentId) {
        SopDocument doc = getDocOrThrow(sopDocumentId);
        doc.setGenerationStatus(SopGenerationStatus.PENDING);
        doc.setStatusMessage(null);
        doc.setUpdatedAt(OffsetDateTime.now(clock));
        sopDocumentRepository.save(doc);
        startGenerationAfterCommit(sopDocumentId);
        return toDocDto(doc, 0);
    }

    /**
     * Starts background generation only once the current transaction has committed.
     * Starting it earlier races the commit: the async thread can look the document up
     * before the row is visible, find nothing, and leave it stuck at PENDING.
     */
    private void startGenerationAfterCommit(Long sopDocumentId) {
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                sopGenerationService.generateForDocument(sopDocumentId);
            }
        });
    }

    // === Module 2: material review (FR-09) ==================================

    @Override
    public SopModuleDto getModule(Long moduleId) {
        return toModuleDto(getModuleOrThrow(moduleId));
    }

    @Override
    @Transactional
    public SopModuleDto updateModule(Long moduleId, String title, String content) {
        SopModule module = getModuleOrThrow(moduleId);
        if (title != null && !title.isBlank()) {
            module.setTitle(title);
        }
        module.setContent(content);
        module.setUpdatedAt(OffsetDateTime.now(clock));
        return toModuleDto(sopModuleRepository.save(module));
    }

    @Override
    @Transactional
    public SopModuleDto approveModule(Long moduleId) {
        SopModule module = getModuleOrThrow(moduleId);
        module.setReviewStatus(ReviewStatus.APPROVED);
        module.setUpdatedAt(OffsetDateTime.now(clock));
        return toModuleDto(sopModuleRepository.save(module));
    }

    @Override
    public SopModuleDto rejectModule(Long moduleId, String reason) {
        if (reason == null || reason.isBlank()) {
            throw new BadRequestException("A reason is required to reject and regenerate the module."); // FR-09-05
        }
        SopModule module = getModuleOrThrow(moduleId);
        sopGenerationService.regenerateModule(module, reason); // sets status REGENERATED + refreshes quiz
        recordQuizEvent(moduleId, QuizReviewAction.REGENERATED, "Regenerated together with the rejected module material.", null);
        return toModuleDto(getModuleOrThrow(moduleId));
    }

    // === Module 2: quiz review (FR-10) ======================================

    @Override
    @Transactional
    public SopQuizQuestionDto updateQuestion(Long questionId, SopQuizQuestionDto dto, UUID userId) {
        SopQuizQuestion q = sopQuizQuestionRepository.findById(questionId)
                .orElseThrow(() -> new BadRequestException("Quiz question not found."));
        validateQuestion(dto);
        if (dto.getQuestionType() != null) {
            q.setQuestionType(dto.getQuestionType());
        }
        if (dto.getQuestionText() != null) {
            q.setQuestionText(dto.getQuestionText());
        }
        if (dto.getDifficulty() != null) {
            q.setDifficulty(dto.getDifficulty());
        }
        q.setOptions(writeOptions(dto.getOptions()));
        q.setCorrectAnswer(dto.getCorrectAnswer());
        q.setExplanation(dto.getExplanation());
        q.setUpdatedAt(OffsetDateTime.now(clock));
        SopQuizQuestion saved = sopQuizQuestionRepository.save(q);
        recordQuizEvent(q.getSopModuleId(), QuizReviewAction.EDITED, "Edited: " + q.getQuestionText(), userId);
        return toQuizDto(saved);
    }

    @Override
    @Transactional
    public SopQuizQuestionDto addQuestion(Long moduleId, SopQuizQuestionDto dto, UUID userId) {
        getModuleOrThrow(moduleId);
        validateQuestion(dto);
        OffsetDateTime now = OffsetDateTime.now(clock);
        SopQuizQuestion q = new SopQuizQuestion();
        q.setSopModuleId(moduleId);
        q.setQuestionType(dto.getQuestionType());
        q.setQuestionText(dto.getQuestionText().trim());
        q.setOptions(writeOptions(dto.getOptions()));
        q.setCorrectAnswer(dto.getCorrectAnswer().trim());
        q.setExplanation(dto.getExplanation());
        q.setDifficulty(dto.getDifficulty());
        q.setReviewStatus(ReviewStatus.PENDING); // new content goes back through the review gate
        q.setCreatedAt(now);
        q.setUpdatedAt(now);
        SopQuizQuestion saved = sopQuizQuestionRepository.save(q);
        // Logged as EDITED: the quiz changed, and the action column only accepts the existing values.
        recordQuizEvent(moduleId, QuizReviewAction.EDITED, "Added: " + saved.getQuestionText(), userId);
        return toQuizDto(saved);
    }

    /** A question staff can answer and the system can mark: text, and an answer among its options. */
    private void validateQuestion(SopQuizQuestionDto dto) {
        if (dto.getQuestionType() == null || dto.getQuestionText() == null || dto.getQuestionText().isBlank()) {
            throw new BadRequestException("A question needs a type and some text.");
        }
        if (dto.getCorrectAnswer() == null || dto.getCorrectAnswer().isBlank()) {
            throw new BadRequestException("A question needs a correct answer.");
        }
        if (dto.getQuestionType() != QuizQuestionType.FILL_IN_THE_BLANK) {
            List<String> options = dto.getOptions() == null ? List.of() : dto.getOptions();
            if (options.size() < 2 || options.stream().anyMatch(o -> o == null || o.isBlank())) {
                throw new BadRequestException("A choice question needs at least two filled-in options.");
            }
            if (!options.contains(dto.getCorrectAnswer())) {
                throw new BadRequestException("The correct answer must be one of the options.");
            }
        }
    }

    @Override
    @Transactional
    public SopModuleDto approveModuleQuiz(Long moduleId, UUID userId) {
        SopModule module = getModuleOrThrow(moduleId);
        OffsetDateTime now = OffsetDateTime.now(clock);
        List<SopQuizQuestion> questions = sopQuizQuestionRepository.findAllBySopModuleIdOrderByIdAsc(moduleId);
        for (SopQuizQuestion q : questions) {
            q.setReviewStatus(ReviewStatus.APPROVED);
            q.setUpdatedAt(now);
        }
        sopQuizQuestionRepository.saveAll(questions);
        recordQuizEvent(moduleId, QuizReviewAction.APPROVED, null, userId);
        return toModuleDto(module);
    }

    @Override
    public SopModuleDto rejectModuleQuiz(Long moduleId, String reason, UUID userId) {
        if (reason == null || reason.isBlank()) {
            throw new BadRequestException("A reason is required to reject and regenerate the quiz."); // FR-10-03
        }
        SopModule module = getModuleOrThrow(moduleId);
        sopGenerationService.regenerateQuiz(module, reason);
        // Recorded only once the regeneration succeeded; a failed attempt leaves the quiz untouched.
        recordQuizEvent(moduleId, QuizReviewAction.REJECTED, reason, userId);
        recordQuizEvent(moduleId, QuizReviewAction.REGENERATED, "Regenerated from the trainer's feedback.", null);
        return toModuleDto(getModuleOrThrow(moduleId));
    }

    @Override
    public List<SopQuizReviewEventDto> getQuizHistory(Long moduleId) {
        getModuleOrThrow(moduleId);
        Map<UUID, String> names = new HashMap<>();
        return sopQuizReviewEventRepository.findAllBySopModuleIdOrderByCreatedAtDescIdDesc(moduleId).stream()
                .map(e -> {
                    SopQuizReviewEventDto dto = new SopQuizReviewEventDto();
                    dto.setId(e.getId());
                    dto.setAction(e.getAction());
                    dto.setDetail(e.getDetail());
                    dto.setActorName(e.getActorId() == null ? null : names.computeIfAbsent(e.getActorId(),
                            id -> staffRepository.findById(id).map(s -> s.getName()).orElse("Unknown user")));
                    dto.setCreatedAt(e.getCreatedAt());
                    return dto;
                })
                .toList();
    }

    @Override
    @Transactional
    public void deleteDocument(Long sopDocumentId) {
        SopDocument doc = getDocOrThrow(sopDocumentId);
        List<SopModule> modules = sopModuleRepository.findAllBySopDocumentIdOrderByModuleOrderAsc(sopDocumentId);
        if (!modules.isEmpty()) {
            List<Long> moduleIds = modules.stream().map(SopModule::getId).toList();
            sopQuizQuestionRepository.deleteAllBySopModuleIdIn(moduleIds);
            sopQuizReviewEventRepository.deleteAllBySopModuleIdIn(moduleIds);
            sopModuleRepository.deleteAllBySopDocumentId(sopDocumentId);
        }
        List<Long> enrollmentIds = sopEnrollmentRepository.findAllBySopDocumentId(sopDocumentId).stream()
                .map(SopEnrollment::getId).toList();
        if (!enrollmentIds.isEmpty()) {
            sopQuizAttemptRepository.deleteAllByEnrollmentIdIn(enrollmentIds);
        }
        sopCourseFeedbackRepository.deleteAllBySopDocumentId(sopDocumentId);
        sopEnrollmentRepository.deleteAllBySopDocumentId(sopDocumentId);
        sopAssignmentRepository.deleteAllBySopDocumentId(sopDocumentId);
        sopDocumentRepository.delete(doc);
    }

    // === helpers ============================================================

    private SopDocument getDocOrThrow(Long id) {
        SopDocument doc = sopDocumentRepository.findById(id).orElse(null);
        if (doc == null || doc.isDeleted()) {
            throw new BadRequestException("SOP document not found.");
        }
        return doc;
    }

    private SopModule getModuleOrThrow(Long id) {
        return sopModuleRepository.findById(id)
                .orElseThrow(() -> new BadRequestException("Module not found."));
    }

    /** Adds an entry to a module quiz's review history (FR-08-09, NFR-007); actorId null means the AI. */
    private void recordQuizEvent(Long moduleId, QuizReviewAction action, String detail, UUID actorId) {
        SopQuizReviewEvent event = new SopQuizReviewEvent();
        event.setSopModuleId(moduleId);
        event.setAction(action);
        event.setDetail(detail);
        event.setActorId(actorId);
        event.setCreatedAt(OffsetDateTime.now(clock));
        sopQuizReviewEventRepository.save(event);
    }

    private String resolveTitle(String title, String filename) {
        if (title != null && !title.isBlank()) {
            return title.trim();
        }
        if (filename == null) {
            return "Untitled SOP";
        }
        int dot = filename.lastIndexOf('.');
        return dot > 0 ? filename.substring(0, dot) : filename;
    }

    private SopDocumentDto toDocDto(SopDocument d, int moduleCount) {
        SopDocumentDto dto = new SopDocumentDto();
        dto.setId(d.getId());
        dto.setTitle(d.getTitle());
        dto.setVersion(d.getVersion());
        dto.setOriginalFilename(d.getOriginalFilename());
        dto.setDepartmentTag(d.getDepartmentTag());
        dto.setGenerationStatus(d.getGenerationStatus());
        dto.setStatusMessage(d.getStatusMessage());
        dto.setModuleCount(moduleCount);
        dto.setCreatedAt(d.getCreatedAt());
        dto.setPreparedByName(preparerName(d));
        return dto;
    }

    /** Name of the trainer who uploaded the SOP, shown on each material (FR-07-07). */
    private String preparerName(SopDocument d) {
        if (d.getUploadedBy() == null) {
            return null;
        }
        return staffRepository.findById(d.getUploadedBy())
                .map(s -> s.getName())
                .orElse(null);
    }

    private SopModuleDto toModuleDto(SopModule m) {
        SopModuleDto dto = new SopModuleDto();
        dto.setId(m.getId());
        dto.setSopDocumentId(m.getSopDocumentId());
        dto.setModuleOrder(m.getModuleOrder());
        dto.setTitle(m.getTitle());
        dto.setContent(m.getContent());
        dto.setReviewStatus(m.getReviewStatus());
        dto.setRejectionReason(m.getRejectionReason());
        dto.setQuiz(sopQuizQuestionRepository.findAllBySopModuleIdOrderByIdAsc(m.getId())
                .stream().map(this::toQuizDto).toList());
        return dto;
    }

    private SopQuizQuestionDto toQuizDto(SopQuizQuestion q) {
        SopQuizQuestionDto dto = new SopQuizQuestionDto();
        dto.setId(q.getId());
        dto.setSopModuleId(q.getSopModuleId());
        dto.setQuestionType(q.getQuestionType());
        dto.setQuestionText(q.getQuestionText());
        dto.setOptions(readOptions(q.getOptions()));
        dto.setCorrectAnswer(q.getCorrectAnswer());
        dto.setExplanation(q.getExplanation());
        dto.setDifficulty(q.getDifficulty());
        dto.setReviewStatus(q.getReviewStatus());
        dto.setRejectionReason(q.getRejectionReason());
        return dto;
    }

    private List<String> readOptions(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<List<String>>() {});
        } catch (Exception e) {
            return List.of();
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
}
