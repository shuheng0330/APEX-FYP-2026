package com.tbm.careerpathlearning.service.impl;

import com.tbm.careerpathlearning.enums.MaterialReviewAction;
import com.tbm.careerpathlearning.enums.NotificationType;
import com.tbm.careerpathlearning.enums.QuizReviewAction;
import com.tbm.careerpathlearning.enums.SopAssignmentMode;
import com.tbm.careerpathlearning.enums.SopCourseStatus;
import com.tbm.careerpathlearning.enums.SopEnrollmentStatus;
import com.tbm.careerpathlearning.enums.SopGenerationStatus;
import com.tbm.careerpathlearning.enums.TrainingAuditAction;
import com.tbm.careerpathlearning.enums.TrainingAuditTarget;
import com.tbm.careerpathlearning.exception.BadRequestException;
import com.tbm.careerpathlearning.model.SopDocument;
import com.tbm.careerpathlearning.model.SopEnrollment;
import com.tbm.careerpathlearning.model.SopMaterialReviewEvent;
import com.tbm.careerpathlearning.model.SopModule;
import com.tbm.careerpathlearning.model.SopQuizQuestion;
import com.tbm.careerpathlearning.model.SopQuizReviewEvent;
import com.tbm.careerpathlearning.repository.SopDocumentRepository;
import com.tbm.careerpathlearning.repository.SopEnrollmentRepository;
import com.tbm.careerpathlearning.repository.SopMaterialReviewEventRepository;
import com.tbm.careerpathlearning.repository.SopModuleRepository;
import com.tbm.careerpathlearning.repository.SopQuizQuestionRepository;
import com.tbm.careerpathlearning.repository.SopQuizReviewEventRepository;
import com.tbm.careerpathlearning.service.NotificationService;
import com.tbm.careerpathlearning.service.SopCourseReadiness;
import com.tbm.careerpathlearning.service.SopVersionService;
import com.tbm.careerpathlearning.service.TrainingAuditService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Service
public class SopVersionServiceImpl implements SopVersionService {

    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.ENGLISH);

    @Autowired private SopDocumentRepository sopDocumentRepository;
    @Autowired private SopModuleRepository sopModuleRepository;
    @Autowired private SopQuizQuestionRepository sopQuizQuestionRepository;
    @Autowired private SopQuizReviewEventRepository sopQuizReviewEventRepository;
    @Autowired private SopMaterialReviewEventRepository sopMaterialReviewEventRepository;
    @Autowired private SopEnrollmentRepository sopEnrollmentRepository;
    @Autowired private SopCourseReadiness sopCourseReadiness;
    @Autowired private NotificationService notificationService;
    @Autowired private TrainingAuditService trainingAuditService;
    @Autowired private Clock clock;

    @Override
    public List<SopDocument> versionsOf(SopDocument version) {
        return sopDocumentRepository.findVersions(version.courseKey());
    }

    @Override
    public Optional<SopDocument> draftOf(SopDocument version) {
        return versionsOf(version).stream().filter(SopDocument::isDraft).findFirst();
    }

    @Override
    public boolean hasLearners(SopDocument version) {
        return !sopEnrollmentRepository.findAllBySopDocumentId(version.getId()).isEmpty();
    }

    @Override
    public SopDocument newDraftOf(SopDocument live) {
        if (!live.isLive()) {
            throw new BadRequestException("Only the live version of a course can get a new version.");
        }
        List<SopDocument> versions = versionsOf(live);
        for (SopDocument v : versions) {
            if (v.isDraft()) {
                throw new BadRequestException("Draft v" + v.getVersionNo() + " of this course is in progress. "
                        + "Publish or discard it first.");
            }
        }
        OffsetDateTime now = OffsetDateTime.now(clock);
        SopDocument draft = new SopDocument();
        draft.setCourseId(live.courseKey());
        draft.setVersionNo(versions.stream().mapToInt(SopDocument::getVersionNo).max().orElse(live.getVersionNo()) + 1);
        draft.setPreviousVersionId(live.getId());
        draft.setCourseStatus(SopCourseStatus.DRAFT);
        draft.setTitle(live.getTitle());
        draft.setVersion(live.getVersion());
        draft.setDepartmentTag(live.getDepartmentTag());
        draft.setUploadedBy(live.getUploadedBy());
        draft.setVisibility(live.getVisibility());
        draft.setPassMark(live.getPassMark());
        draft.setDurationMinutes(live.getDurationMinutes());
        draft.setCreatedAt(now);
        draft.setUpdatedAt(now);
        return draft;
    }

    @Override
    @Transactional
    public DraftCopy createDraft(SopDocument live, UUID actorId, String reason) {
        SopDocument shell = newDraftOf(live);
        shell.setOriginalFilename(live.getOriginalFilename());
        shell.setFilePath(live.getFilePath());
        shell.setExtractedText(live.getExtractedText());
        shell.setGenerationStatus(SopGenerationStatus.COMPLETED);
        SopDocument draft = sopDocumentRepository.save(shell);
        OffsetDateTime now = draft.getCreatedAt();

        Map<Long, Long> moduleIds = new HashMap<>();
        Map<Long, Long> questionIds = new HashMap<>();
        List<SopModule> modules = sopModuleRepository.findAllBySopDocumentIdOrderByModuleOrderAsc(live.getId());
        Map<Long, List<SopQuizQuestion>> quizzes = sopCourseReadiness.quizzesOf(modules);
        String copiedNote = "Copied from v" + live.getVersionNo() + " into draft v" + draft.getVersionNo() + ".";
        for (SopModule module : modules) {
            SopModule copy = sopModuleRepository.save(copyOf(module, draft.getId(), now));
            moduleIds.put(module.getId(), copy.getId());
            for (SopQuizQuestion question : quizzes.getOrDefault(module.getId(), List.of())) {
                questionIds.put(question.getId(), sopQuizQuestionRepository.save(copyOf(question, copy.getId(), now)).getId());
            }
            // The draft's material and quiz histories start here; the live version keeps its own.
            SopQuizReviewEvent copied = new SopQuizReviewEvent();
            copied.setSopModuleId(copy.getId());
            copied.setAction(QuizReviewAction.COPIED);
            copied.setDetail(copiedNote);
            copied.setActorId(actorId);
            copied.setCreatedAt(now);
            sopQuizReviewEventRepository.save(copied);
            sopMaterialReviewEventRepository.save(
                    SopMaterialReviewEvent.of(copy.getId(), MaterialReviewAction.COPIED, copiedNote, actorId, now));
        }
        trainingAuditService.record(actorId, TrainingAuditAction.COURSE_DRAFT_CREATED, TrainingAuditTarget.COURSE,
                live.courseKey().toString(), null,
                "Draft v" + draft.getVersionNo() + " started from v" + live.getVersionNo() + ", because " + reason + ".");
        return new DraftCopy(draft, moduleIds, questionIds);
    }

    @Override
    @Transactional
    public SopDocument publish(SopDocument draft, boolean redoCompleted, UUID actorId) {
        if (!draft.isDraft()) {
            throw new BadRequestException("Only a draft can be published.");
        }
        SopCourseReadiness.Readiness readiness = sopCourseReadiness.of(draft);
        if (!readiness.ready()) {
            throw new BadRequestException("This draft can't be published yet. " + readiness.blocker());
        }
        SopDocument previous = versionsOf(draft).stream().filter(SopDocument::isLive).findFirst().orElse(null);
        LocalDate today = LocalDate.now(clock);
        OffsetDateTime now = OffsetDateTime.now(clock);
        draft.setCourseStatus(SopCourseStatus.LIVE);
        draft.setUpdatedAt(now);
        sopDocumentRepository.save(draft);

        int moved = 0;
        int redo = 0;
        int kept = 0;
        if (previous != null) {
            previous.setCourseStatus(SopCourseStatus.SUPERSEDED);
            previous.setUpdatedAt(now);
            sopDocumentRepository.save(previous);
            for (SopEnrollment e : sopEnrollmentRepository.findAllBySopDocumentId(previous.getId())) {
                if (e.isActive()) {
                    // FR-07-17, decision E: they restart on the new version, with a fresh window from today.
                    e.setStatus(SopEnrollmentStatus.WITHDRAWN);
                    e.setUpdatedAt(now);
                    sopEnrollmentRepository.save(e);
                    SopEnrollment next = sopEnrollmentRepository.save(carriedOver(e, draft, today, now, false));
                    notifyMoved(next, draft, previous);
                    moved++;
                } else if (e.getStatus() == SopEnrollmentStatus.COMPLETED) {
                    if (redoCompleted) {
                        SopEnrollment next = sopEnrollmentRepository.save(carriedOver(e, draft, today, now, true));
                        notifyRedo(next, draft, previous);
                        redo++;
                    } else {
                        notifyKept(e, draft, previous);
                        kept++;
                    }
                }
                // Staff withdrawn earlier, when the course was archived, stay as they are.
            }
        }
        String detail = previous == null ? "v" + draft.getVersionNo() + " is live."
                : "v" + draft.getVersionNo() + " is live, replacing v" + previous.getVersionNo() + ". Staff moved to it: " + moved
                + ". " + (redoCompleted ? "Completed staff asked to complete it too: " + redo
                : "Completed staff keeping their certificate: " + kept) + ".";
        trainingAuditService.record(actorId, TrainingAuditAction.COURSE_VERSION_PUBLISHED, TrainingAuditTarget.COURSE,
                draft.courseKey().toString(), null, detail);
        return draft;
    }

    @Override
    @Transactional
    public void discard(SopDocument draft, UUID actorId) {
        if (!draft.isDraft()) {
            throw new BadRequestException("Only a draft can be discarded.");
        }
        List<Long> moduleIds = sopModuleRepository.findAllBySopDocumentIdOrderByModuleOrderAsc(draft.getId()).stream()
                .map(SopModule::getId).toList();
        if (!moduleIds.isEmpty()) {
            sopQuizQuestionRepository.deleteAllBySopModuleIdIn(moduleIds);
            sopQuizReviewEventRepository.deleteAllBySopModuleIdIn(moduleIds);
            sopMaterialReviewEventRepository.deleteAllBySopModuleIdIn(moduleIds);
            sopModuleRepository.deleteAllBySopDocumentId(draft.getId());
        }
        sopDocumentRepository.delete(draft);
        trainingAuditService.record(actorId, TrainingAuditAction.COURSE_DRAFT_DISCARDED, TrainingAuditTarget.COURSE,
                draft.courseKey().toString(), null, "Draft v" + draft.getVersionNo() + " discarded.");
    }

    // === helpers ============================================================

    private static SopModule copyOf(SopModule module, Long draftId, OffsetDateTime now) {
        SopModule copy = new SopModule();
        copy.setSopDocumentId(draftId);
        copy.setModuleOrder(module.getModuleOrder());
        copy.setTitle(module.getTitle());
        copy.setContent(module.getContent());
        copy.setReviewStatus(module.getReviewStatus());
        copy.setRejectionReason(module.getRejectionReason());
        copy.setQuestionsPerAttempt(module.getQuestionsPerAttempt());
        copy.setCreatedAt(now);
        copy.setUpdatedAt(now);
        return copy;
    }

    private static SopQuizQuestion copyOf(SopQuizQuestion question, Long moduleId, OffsetDateTime now) {
        SopQuizQuestion copy = new SopQuizQuestion();
        copy.setSopModuleId(moduleId);
        copy.setQuestionType(question.getQuestionType());
        copy.setQuestionText(question.getQuestionText());
        copy.setOptions(question.getOptions());
        copy.setCorrectAnswer(question.getCorrectAnswer());
        copy.setExplanation(question.getExplanation());
        copy.setDifficulty(question.getDifficulty());
        copy.setReviewStatus(question.getReviewStatus());
        copy.setRejectionReason(question.getRejectionReason());
        copy.setCreatedAt(now);
        copy.setUpdatedAt(now);
        return copy;
    }

    /** The same person's enrolment in the new version, on their old terms (FR-07-17, FR-07-18, decision E). */
    private static SopEnrollment carriedOver(SopEnrollment old, SopDocument version, LocalDate today, OffsetDateTime now,
                                             boolean redo) {
        SopEnrollment e = new SopEnrollment();
        e.setSopDocumentId(version.getId());
        e.setStaffId(old.getStaffId());
        e.setAssignmentId(old.getAssignmentId());
        e.setMandatory(old.isMandatory());
        e.setPassMark(Math.max(old.getPassMark(), version.getPassMark()));
        e.setDurationMinutes(version.effectiveDurationMinutes());
        e.setDaysToComplete(old.getDaysToComplete());
        e.setStatus(SopEnrollmentStatus.NOT_STARTED);
        e.setAssignedAt(now);
        e.setUpdatedAt(now);
        Integer days = old.getDaysToComplete();
        if (redo) {
            // FR-07-18: their original days to complete, counted from the day the new version goes live.
            e.setMode(days == null ? SopAssignmentMode.PORTAL : SopAssignmentMode.IMMEDIATE);
            e.setEffectiveDate(today);
            e.setDeadline(days == null ? null : today.plusDays(days));
        } else {
            // Decision E: a fresh window from go-live on the same terms; the old deadline never cuts it short.
            LocalDate effective = old.getEffectiveDate() == null || old.getEffectiveDate().isBefore(today)
                    ? today : old.getEffectiveDate();
            LocalDate window = days == null ? null : effective.plusDays(days);
            e.setMode(old.getMode());
            e.setEffectiveDate(effective);
            e.setDeadline(old.getDeadline() == null || window == null || !old.getDeadline().isBefore(window)
                    ? old.getDeadline() : window);
        }
        return e;
    }

    private void notifyMoved(SopEnrollment e, SopDocument version, SopDocument previous) {
        notificationService.notify(e.getStaffId(), NotificationType.COURSE_VERSION_PUBLISHED,
                "New version of " + version.getTitle(),
                "Version " + version.getVersionNo() + " of \"" + version.getTitle() + "\" has replaced version "
                        + previous.getVersionNo() + ", which you were taking, so you start again on it. Your earlier "
                        + "progress is kept in your history." + dueText(e),
                "/my-learning/course/" + version.getId());
    }

    private void notifyRedo(SopEnrollment e, SopDocument version, SopDocument previous) {
        notificationService.notify(e.getStaffId(), NotificationType.COURSE_VERSION_PUBLISHED,
                "Please complete the new version: " + version.getTitle(),
                "Version " + version.getVersionNo() + " of \"" + version.getTitle() + "\" is live, and everyone who "
                        + "completed an earlier version must complete it too." + dueText(e) + " You keep your "
                        + "certificate for version " + previous.getVersionNo() + ".",
                "/my-learning/course/" + version.getId());
    }

    private void notifyKept(SopEnrollment old, SopDocument version, SopDocument previous) {
        notificationService.notify(old.getStaffId(), NotificationType.COURSE_VERSION_PUBLISHED,
                "New version of " + version.getTitle(),
                "Version " + version.getVersionNo() + " of \"" + version.getTitle() + "\" is live. You don't need to "
                        + "take it again: your certificate for version " + previous.getVersionNo() + " stays valid.",
                "/my-learning/course/" + previous.getId());
    }

    private static String dueText(SopEnrollment e) {
        LocalDate due = e.dueDate();
        return due == null ? "" : " Complete it by " + DATE.format(due) + ".";
    }
}
