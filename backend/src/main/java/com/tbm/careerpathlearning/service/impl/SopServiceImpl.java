package com.tbm.careerpathlearning.service.impl;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tbm.careerpathlearning.dto.SopCourseSettingsRequest;
import com.tbm.careerpathlearning.dto.SopDetailDto;
import com.tbm.careerpathlearning.dto.SopDocumentDto;
import com.tbm.careerpathlearning.dto.SopModuleDto;
import com.tbm.careerpathlearning.dto.SopQuizQuestionDto;
import com.tbm.careerpathlearning.dto.SopQuizReviewEventDto;
import com.tbm.careerpathlearning.enums.NotificationType;
import com.tbm.careerpathlearning.enums.QuizQuestionType;
import com.tbm.careerpathlearning.enums.QuizReviewAction;
import com.tbm.careerpathlearning.enums.ReviewStatus;
import com.tbm.careerpathlearning.enums.SopCourseStatus;
import com.tbm.careerpathlearning.enums.SopEnrollmentStatus;
import com.tbm.careerpathlearning.enums.SopGenerationStatus;
import com.tbm.careerpathlearning.enums.TrainingAuditAction;
import com.tbm.careerpathlearning.enums.TrainingAuditTarget;
import com.tbm.careerpathlearning.exception.BadRequestException;
import com.tbm.careerpathlearning.exception.ForbiddenRequestException;
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
import com.tbm.careerpathlearning.service.NotificationService;
import com.tbm.careerpathlearning.service.SopAccess;
import com.tbm.careerpathlearning.service.SopGenerationService;
import com.tbm.careerpathlearning.service.SopService;
import com.tbm.careerpathlearning.service.SopVersionService;
import com.tbm.careerpathlearning.service.SopViewer;
import com.tbm.careerpathlearning.service.TrainingAuditService;
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
import java.util.stream.Collectors;

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
    @Autowired private NotificationService notificationService;
    @Autowired private TrainingAuditService trainingAuditService;
    @Autowired private SopVersionService sopVersionService;
    @Autowired private Clock clock;

    private final ObjectMapper objectMapper = new ObjectMapper();

    /** A course's estimated duration is between 30 minutes and 24 hours (FR-07-15). */
    static final int MIN_DURATION_MINUTES = 30;
    static final int MAX_DURATION_MINUTES = 24 * 60;

    // === Module 1: upload + generation ======================================

    @Override
    @Transactional
    public SopDocumentDto upload(MultipartFile file, String title, String version, String departmentTag, UUID userId,
                                 Long newVersionOf) {
        if (file == null || file.isEmpty()) {
            throw new BadRequestException("Please select a document to upload.");
        }
        String filename = file.getOriginalFilename();
        String lower = filename == null ? "" : filename.toLowerCase();
        if (!lower.endsWith(".doc") && !lower.endsWith(".docx") && !lower.endsWith(".pdf")) {
            throw new BadRequestException("Only Word (.doc, .docx) or PDF (.pdf) documents are supported.");
        }

        // FR-06-06: a revised SOP becomes the draft of the course's next version; checked before the file is stored.
        SopViewer owner = new SopViewer(userId, false);
        SopDocument doc = newVersionOf == null ? new SopDocument()
                : sopVersionService.newDraftOf(editableDoc(newVersionOf, owner));

        String storedPath = fileService.uploadFile(file, "sop");

        OffsetDateTime now = OffsetDateTime.now(clock);
        if (newVersionOf == null || (title != null && !title.isBlank())) {
            doc.setTitle(resolveTitle(title, filename)); // FR-08-03: filename as title when blank
        }
        doc.setVersion(version);
        doc.setOriginalFilename(filename);
        doc.setFilePath(storedPath);
        if (newVersionOf == null || (departmentTag != null && !departmentTag.isBlank())) {
            doc.setDepartmentTag(departmentTag);
        }
        doc.setUploadedBy(userId);
        doc.setGenerationStatus(SopGenerationStatus.PENDING);
        doc.setCreatedAt(now);
        doc.setUpdatedAt(now);
        SopDocument saved = sopDocumentRepository.save(doc);
        if (saved.getCourseId() == null) {
            saved.setCourseId(saved.getId()); // a new course is its own first version
            saved = sopDocumentRepository.save(saved);
        } else {
            trainingAuditService.record(userId, TrainingAuditAction.COURSE_DRAFT_CREATED, TrainingAuditTarget.COURSE,
                    saved.courseKey().toString(), null,
                    "Draft v" + saved.getVersionNo() + " started from a revised SOP, " + filename + ".");
        }

        // Kick off parse + AI generation in the background (FR-08-04/05).
        startGenerationAfterCommit(saved.getId());

        return toDocDto(saved, 0, owner, List.of(), versionsOf(saved));
    }

    @Override
    public List<SopDocumentDto> listDocuments(SopViewer viewer, boolean mineOnly, boolean archived) {
        List<SopDocument> all = sopDocumentRepository.findAllByIsDeletedFalseOrderByCreatedAtDesc();
        // Current courses are the live versions plus the owner's own drafts (FR-07-16); replaced versions never show.
        List<SopDocument> docs = all.stream()
                .filter(d -> archived ? d.isArchived() : d.isLive() || (d.isDraft() && SopAccess.isOwner(d, viewer)))
                .filter(d -> mineOnly ? SopAccess.isOwner(d, viewer) : SopAccess.canView(d, viewer))
                .toList();
        Map<Long, List<SopDocument>> versions = all.stream().collect(Collectors.groupingBy(SopDocument::courseKey));
        Map<Long, List<SopEnrollment>> enrollments = docs.isEmpty() ? Map.of()
                : sopEnrollmentRepository.findAllBySopDocumentIdIn(docs.stream().map(SopDocument::getId).toList()).stream()
                .collect(Collectors.groupingBy(SopEnrollment::getSopDocumentId));
        return docs.stream()
                .map(d -> toDocDto(d, sopModuleRepository.findAllBySopDocumentIdOrderByModuleOrderAsc(d.getId()).size(),
                        viewer, enrollments.getOrDefault(d.getId(), List.of()), versions.getOrDefault(d.courseKey(), List.of(d))))
                .toList();
    }

    @Override
    public SopDetailDto getDetail(Long sopDocumentId, SopViewer viewer) {
        SopDocument doc = viewableDoc(sopDocumentId, viewer);
        List<SopModule> modules = sopModuleRepository.findAllBySopDocumentIdOrderByModuleOrderAsc(sopDocumentId);

        SopDetailDto detail = new SopDetailDto();
        detail.setDocument(toDocDto(doc, modules.size(), viewer, sopEnrollmentRepository.findAllBySopDocumentId(sopDocumentId),
                versionsOf(doc)));
        detail.setModules(modules.stream().map(this::toModuleDto).toList());
        return detail;
    }

    @Override
    public SopDocumentDto getStatus(Long sopDocumentId, SopViewer viewer) {
        SopDocument doc = viewableDoc(sopDocumentId, viewer);
        int moduleCount = sopModuleRepository.findAllBySopDocumentIdOrderByModuleOrderAsc(sopDocumentId).size();
        return toDocDto(doc, moduleCount, viewer, sopEnrollmentRepository.findAllBySopDocumentId(sopDocumentId), versionsOf(doc));
    }

    @Override
    @Transactional
    public SopDocumentDto regenerateDocument(Long sopDocumentId, SopViewer viewer) {
        SopDocument doc = editableDoc(sopDocumentId, viewer);
        if (sopVersionService.hasLearners(doc)) {
            throw new BadRequestException("Staff have enrolled in this course, so it can't be generated again from scratch. "
                    + "Upload the revised SOP as a new version instead.");
        }
        doc.setGenerationStatus(SopGenerationStatus.PENDING);
        doc.setStatusMessage(null);
        doc.setUpdatedAt(OffsetDateTime.now(clock));
        sopDocumentRepository.save(doc);
        startGenerationAfterCommit(sopDocumentId);
        return toDocDto(doc, 0, viewer, List.of(), versionsOf(doc));
    }

    @Override
    @Transactional
    public SopDocumentDto updateSettings(Long sopDocumentId, SopCourseSettingsRequest request, SopViewer viewer) {
        SopDocument doc = editableDoc(sopDocumentId, viewer);
        if (request == null || request.getPassMark() == null
                || request.getPassMark() < 1 || request.getPassMark() > 100) {
            throw new BadRequestException("The pass mark must be between 1 and 100.");
        }
        if (request.getDurationMinutes() == null
                || request.getDurationMinutes() < MIN_DURATION_MINUTES || request.getDurationMinutes() > MAX_DURATION_MINUTES) {
            throw new BadRequestException("The estimated duration must be between 30 minutes and 24 hours.");
        }
        doc.setPassMark(request.getPassMark());
        doc.setDurationMinutes(request.getDurationMinutes());
        doc.setUpdatedAt(OffsetDateTime.now(clock));
        sopDocumentRepository.save(doc);
        return toDocDto(doc, sopModuleRepository.findAllBySopDocumentIdOrderByModuleOrderAsc(sopDocumentId).size(), viewer,
                sopEnrollmentRepository.findAllBySopDocumentId(sopDocumentId), versionsOf(doc));
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
    public SopModuleDto getModule(Long moduleId, SopViewer viewer) {
        return toModuleDto(viewableModule(moduleId, viewer));
    }

    @Override
    @Transactional
    public SopModuleDto updateModule(Long moduleId, String title, String content, SopViewer viewer) {
        SopModule module = editableModule(moduleId, viewer);
        if (title != null && !title.isBlank()) {
            module.setTitle(title);
        }
        module.setContent(content);
        module.setUpdatedAt(OffsetDateTime.now(clock));
        return toModuleDto(sopModuleRepository.save(module));
    }

    @Override
    @Transactional
    public SopModuleDto approveModule(Long moduleId, SopViewer viewer) {
        SopModule module = editableModule(moduleId, viewer);
        module.setReviewStatus(ReviewStatus.APPROVED);
        module.setUpdatedAt(OffsetDateTime.now(clock));
        return toModuleDto(sopModuleRepository.save(module));
    }

    @Override
    public SopModuleDto rejectModule(Long moduleId, String reason, SopViewer viewer) {
        SopModule module = editableModule(moduleId, viewer);
        if (reason == null || reason.isBlank()) {
            throw new BadRequestException("A reason is required to reject and regenerate the module."); // FR-09-05
        }
        Target target = changeTarget(module, viewer, "module " + module.getModuleOrder() + "'s material was rejected");
        try {
            sopGenerationService.regenerateModule(target.module(), reason); // sets status REGENERATED + refreshes quiz
        } catch (RuntimeException e) {
            target.undo(sopVersionService, viewer);
            throw e;
        }
        Long subject = target.module().getId();
        recordQuizEvent(subject, QuizReviewAction.REGENERATED, "Regenerated together with the rejected module material.", null);
        notifyOwner(target.module(), NotificationType.MATERIAL_REGENERATED, "Material regenerated",
                "now has new material and a new quiz to review", "/sop/review/");
        return toModuleDto(getModuleOrThrow(subject));
    }

    // === Module 2: quiz review (FR-10) ======================================

    @Override
    @Transactional
    public SopQuizQuestionDto updateQuestion(Long questionId, SopQuizQuestionDto dto, SopViewer viewer) {
        SopQuizQuestion q = sopQuizQuestionRepository.findById(questionId)
                .orElseThrow(() -> new BadRequestException("Quiz question not found."));
        SopModule module = editableModule(q.getSopModuleId(), viewer);
        validateQuestion(dto);
        Target target = changeTarget(module, viewer, "a question in module " + module.getModuleOrder() + "'s quiz was edited");
        if (target.copy() != null) {
            q = sopQuizQuestionRepository.findById(target.copy().questionIds().get(questionId)).orElseThrow();
        }
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
        recordQuizEvent(q.getSopModuleId(), QuizReviewAction.EDITED, "Edited: " + q.getQuestionText(), viewer.userId());
        return toQuizDto(saved);
    }

    @Override
    @Transactional
    public SopQuizQuestionDto addQuestion(Long moduleId, SopQuizQuestionDto dto, SopViewer viewer) {
        SopModule module = editableModule(moduleId, viewer);
        validateQuestion(dto);
        Long target = changeTarget(module, viewer, "a question was added to module " + module.getModuleOrder() + "'s quiz")
                .module().getId();
        OffsetDateTime now = OffsetDateTime.now(clock);
        SopQuizQuestion q = new SopQuizQuestion();
        q.setSopModuleId(target);
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
        recordQuizEvent(target, QuizReviewAction.EDITED, "Added: " + saved.getQuestionText(), viewer.userId());
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
    public SopModuleDto approveModuleQuiz(Long moduleId, SopViewer viewer) {
        SopModule module = editableModule(moduleId, viewer);
        OffsetDateTime now = OffsetDateTime.now(clock);
        List<SopQuizQuestion> questions = sopQuizQuestionRepository.findAllBySopModuleIdOrderByIdAsc(moduleId);
        for (SopQuizQuestion q : questions) {
            q.setReviewStatus(ReviewStatus.APPROVED);
            q.setUpdatedAt(now);
        }
        sopQuizQuestionRepository.saveAll(questions);
        recordQuizEvent(moduleId, QuizReviewAction.APPROVED, null, viewer.userId());
        return toModuleDto(module);
    }

    @Override
    public SopModuleDto rejectModuleQuiz(Long moduleId, String reason, SopViewer viewer) {
        SopModule module = editableModule(moduleId, viewer);
        if (reason == null || reason.isBlank()) {
            throw new BadRequestException("A reason is required to reject and regenerate the quiz."); // FR-10-03
        }
        Target target = changeTarget(module, viewer, "module " + module.getModuleOrder() + "'s quiz was rejected");
        try {
            sopGenerationService.regenerateQuiz(target.module(), reason);
        } catch (RuntimeException e) {
            target.undo(sopVersionService, viewer);
            throw e;
        }
        Long subject = target.module().getId();
        // Recorded only once the regeneration succeeded; a failed attempt leaves the quiz untouched.
        recordQuizEvent(subject, QuizReviewAction.REJECTED, reason, viewer.userId());
        recordQuizEvent(subject, QuizReviewAction.REGENERATED, "Regenerated from the trainer's feedback.", null);
        notifyOwner(target.module(), NotificationType.QUIZ_REGENERATED, "Quiz regenerated", "has a new quiz to review",
                "/sop/quiz-review/");
        return toModuleDto(getModuleOrThrow(subject));
    }

    @Override
    public List<SopQuizReviewEventDto> getQuizHistory(Long moduleId, SopViewer viewer) {
        viewableModule(moduleId, viewer);
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
    public void deleteDocument(Long sopDocumentId, SopViewer viewer) {
        SopDocument doc = editableDoc(sopDocumentId, viewer);
        if (doc.isDraft()) {
            sopVersionService.discard(doc, viewer.userId()); // the live version stays as it is
            return;
        }
        if (!sopEnrollmentRepository.findAllBySopDocumentId(sopDocumentId).isEmpty()) {
            throw new BadRequestException("Staff have enrolled in this course, so it can't be deleted. Archive it instead: "
                    + "it disappears for staff, and their results and certificates are kept.");
        }
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

    @Override
    @Transactional
    public SopDocumentDto archiveDocument(Long sopDocumentId, SopViewer viewer) {
        SopDocument doc = getDocOrThrow(sopDocumentId);
        SopAccess.requireEdit(doc, viewer);
        if (!doc.isLive()) {
            throw new BadRequestException(doc.isArchived() ? "This course is already archived." : "Only a live course can be archived.");
        }
        sopVersionService.draftOf(doc).ifPresent(draft -> { // decision D
            throw new BadRequestException("Draft v" + draft.getVersionNo() + " of this course is in progress. "
                    + "Publish or discard it before archiving the course.");
        });
        OffsetDateTime now = OffsetDateTime.now(clock);
        doc.setCourseStatus(SopCourseStatus.ARCHIVED);
        doc.setArchivedAt(now);
        doc.setUpdatedAt(now);
        sopDocumentRepository.save(doc);

        // FR-06-15: staff still taking it are withdrawn and told. Their quiz attempts stay, and completed records are kept.
        List<SopEnrollment> enrollments = sopEnrollmentRepository.findAllBySopDocumentId(sopDocumentId);
        List<SopEnrollment> withdrawn = enrollments.stream().filter(SopEnrollment::isActive).toList();
        for (SopEnrollment e : withdrawn) {
            e.setStatus(SopEnrollmentStatus.WITHDRAWN);
            e.setUpdatedAt(now);
            notificationService.notify(e.getStaffId(), NotificationType.COURSE_WITHDRAWN, "Course withdrawn: " + doc.getTitle(),
                    "\"" + doc.getTitle() + "\" has been archived, so you have been withdrawn from it. "
                            + "Your progress so far is kept in My Learning.",
                    "/my-learning/course/" + doc.getId());
        }
        sopEnrollmentRepository.saveAll(withdrawn);
        long completed = enrollments.stream().filter(e -> e.getStatus() == SopEnrollmentStatus.COMPLETED).count();
        trainingAuditService.record(viewer.userId(), TrainingAuditAction.COURSE_ARCHIVED, TrainingAuditTarget.COURSE,
                doc.courseKey().toString(), null, "Staff withdrawn: " + withdrawn.size() + ". Completed records kept: " + completed + ".");
        return toDocDto(doc, sopModuleRepository.findAllBySopDocumentIdOrderByModuleOrderAsc(sopDocumentId).size(), viewer,
                enrollments, versionsOf(doc));
    }

    @Override
    @Transactional
    public SopDocumentDto restoreDocument(Long sopDocumentId, SopViewer viewer) {
        SopDocument doc = viewableDoc(sopDocumentId, viewer); // its owner, HR or the Super Admin (FR-06-16)
        if (!doc.isArchived()) {
            throw new BadRequestException("This course is not archived.");
        }
        OffsetDateTime now = OffsetDateTime.now(clock);
        doc.setCourseStatus(SopCourseStatus.LIVE);
        doc.setArchivedAt(null);
        doc.setUpdatedAt(now);
        sopDocumentRepository.save(doc);
        trainingAuditService.record(viewer.userId(), TrainingAuditAction.COURSE_RESTORED, TrainingAuditTarget.COURSE,
                doc.courseKey().toString(), null, "Live again. Staff who were withdrawn were not re-enrolled.");
        return toDocDto(doc, sopModuleRepository.findAllBySopDocumentIdOrderByModuleOrderAsc(sopDocumentId).size(),
                viewer, sopEnrollmentRepository.findAllBySopDocumentId(sopDocumentId), versionsOf(doc));
    }

    @Override
    @Transactional
    public SopDocumentDto publishDraft(Long sopDocumentId, boolean redoCompleted, SopViewer viewer) {
        SopDocument published = sopVersionService.publish(editableDoc(sopDocumentId, viewer), redoCompleted, viewer.userId());
        return toDocDto(published, sopModuleRepository.findAllBySopDocumentIdOrderByModuleOrderAsc(sopDocumentId).size(),
                viewer, sopEnrollmentRepository.findAllBySopDocumentId(sopDocumentId), versionsOf(published));
    }

    // === helpers ============================================================

    /** Tells the course owner that regenerated content is ready to review (FR-07-06, FR-08-04). */
    private void notifyOwner(SopModule module, NotificationType type, String title, String what, String page) {
        sopDocumentRepository.findById(module.getSopDocumentId())
                .filter(doc -> doc.getUploadedBy() != null)
                .ifPresent(doc -> notificationService.notify(doc.getUploadedBy(), type, title + ": " + doc.getTitle(),
                        "Module " + module.getModuleOrder() + ", \"" + module.getTitle() + "\", " + what + ".",
                        page + doc.getId()));
    }

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

    /** The course, if the viewer may see it (FR-07-08, FR-07-19). */
    private SopDocument viewableDoc(Long id, SopViewer viewer) {
        SopDocument doc = getDocOrThrow(id);
        SopAccess.requireView(doc, viewer);
        if (doc.isDraft() && !SopAccess.isOwner(doc, viewer)) {
            throw new ForbiddenRequestException("Only the course's owner can open its draft.");
        }
        return doc;
    }

    /**
     * The course, if the viewer may change it: only its owner can, and not while it is archived (FR-06-14), once a
     * newer version has replaced it, or while its draft is in progress, when changes go into the draft (P2-18).
     */
    private SopDocument editableDoc(Long id, SopViewer viewer) {
        SopDocument doc = getDocOrThrow(id);
        SopAccess.requireEdit(doc, viewer);
        if (doc.isArchived()) {
            throw new BadRequestException("This course is archived. Restore it before changing it.");
        }
        if (doc.isSuperseded()) {
            throw new BadRequestException("This is version " + doc.getVersionNo() + ", which a newer version has replaced. "
                    + "Change the live version instead.");
        }
        if (doc.isLive()) {
            sopVersionService.draftOf(doc).ifPresent(draft -> {
                throw new BadRequestException("Draft v" + draft.getVersionNo() + " of this course is in progress. "
                        + "Make changes there, or discard it first.");
            });
        }
        return doc;
    }

    /** Whether the viewer may change this version now: its owner, unless it is archived, replaced, or has a draft. */
    private static boolean editable(SopDocument doc, SopViewer viewer, List<SopDocument> versions) {
        return SopAccess.canEdit(doc, viewer) && !doc.isArchived() && !doc.isSuperseded()
                && !(doc.isLive() && versions.stream().anyMatch(SopDocument::isDraft));
    }

    /**
     * Where a quiz change or a rejection lands (D2, FR-07-16): the module itself, unless staff have been assigned or
     * joined this live version; then the same module in a new draft of the next version, which the change starts.
     */
    private Target changeTarget(SopModule module, SopViewer viewer, String reason) {
        SopDocument doc = getDocOrThrow(module.getSopDocumentId());
        if (!doc.isLive() || !sopVersionService.hasLearners(doc)) {
            return new Target(module, null);
        }
        SopVersionService.DraftCopy copy = sopVersionService.createDraft(doc, viewer.userId(), reason);
        return new Target(getModuleOrThrow(copy.moduleIds().get(module.getId())), copy);
    }

    /** Where a change landed: the module itself, or its copy in a draft made for the change. */
    private record Target(SopModule module, SopVersionService.DraftCopy copy) {
        /** A change that failed after making its draft leaves no draft behind. */
        void undo(SopVersionService versions, SopViewer viewer) {
            if (copy != null) {
                versions.discard(copy.draft(), viewer.userId());
            }
        }
    }

    private List<SopDocument> versionsOf(SopDocument doc) {
        return sopVersionService.versionsOf(doc);
    }

    private SopModule viewableModule(Long moduleId, SopViewer viewer) {
        SopModule module = getModuleOrThrow(moduleId);
        viewableDoc(module.getSopDocumentId(), viewer);
        return module;
    }

    private SopModule editableModule(Long moduleId, SopViewer viewer) {
        SopModule module = getModuleOrThrow(moduleId);
        editableDoc(module.getSopDocumentId(), viewer);
        return module;
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

    private SopDocumentDto toDocDto(SopDocument d, int moduleCount, SopViewer viewer, List<SopEnrollment> enrollments,
                                    List<SopDocument> versions) {
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
        dto.setEditable(editable(d, viewer, versions));
        dto.setPassMark(d.getPassMark());
        dto.setDurationMinutes(d.effectiveDurationMinutes());
        dto.setDurationSet(d.getDurationMinutes() != null);
        dto.setCourseStatus(d.getCourseStatus());
        dto.setArchivedAt(d.getArchivedAt());
        dto.setEnrolledCount(enrollments.size());
        dto.setActiveEnrollmentCount((int) enrollments.stream().filter(SopEnrollment::isActive).count());
        dto.setCourseId(d.courseKey());
        dto.setVersionNo(d.getVersionNo());
        // Only the owner sees a draft (FR-07-16); everyone else just sees the live version.
        dto.setDraftId(!SopAccess.isOwner(d, viewer) ? null
                : versions.stream().filter(SopDocument::isDraft).map(SopDocument::getId).findFirst().orElse(null));
        dto.setLiveVersionId(versions.stream().filter(SopDocument::isLive).map(SopDocument::getId).findFirst().orElse(null));
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
