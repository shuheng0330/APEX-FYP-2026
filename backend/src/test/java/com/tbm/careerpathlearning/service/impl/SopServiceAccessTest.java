package com.tbm.careerpathlearning.service.impl;

import com.tbm.careerpathlearning.dto.SopDocumentDto;
import com.tbm.careerpathlearning.dto.SopQuizQuestionDto;
import com.tbm.careerpathlearning.enums.QuizQuestionType;
import com.tbm.careerpathlearning.enums.ReviewStatus;
import com.tbm.careerpathlearning.exception.ForbiddenRequestException;
import com.tbm.careerpathlearning.model.SopModule;
import com.tbm.careerpathlearning.service.SopViewer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Who may see and change SOP courses through the upload, material review and quiz review pages
 * (FR-07-08, FR-07-19, P2-05, P2-06).
 */
class SopServiceAccessTest {

    private SopTestStore store;
    private SopServiceImpl service;

    private final UUID trainerId = UUID.randomUUID();
    private final UUID superAdminId = UUID.randomUUID();
    private final SopViewer trainer = new SopViewer(trainerId, false);
    private final SopViewer otherTrainer = new SopViewer(UUID.randomUUID(), false);
    private final SopViewer hr = new SopViewer(UUID.randomUUID(), true);
    private final SopViewer superAdmin = new SopViewer(superAdminId, true);

    private Long trainersModule;

    /** Course 1 is the trainer's, course 2 the Super Admin's, and course 3 has no owner. */
    @BeforeEach
    void setUp() {
        store = new SopTestStore();
        service = store.wire(new SopServiceImpl());
        store.doc(1L, "Cash Handling", trainerId);
        trainersModule = store.module(1L, ReviewStatus.PENDING, ReviewStatus.PENDING, ReviewStatus.PENDING).getId();
        store.doc(2L, "Forklift Safety", superAdminId);
        store.module(2L, 2);
        store.doc(3L, "[EVAL] product_training.pdf", null);
        store.module(3L, 2);
    }

    @Test
    @DisplayName("Each person's list shows only what they may see, marked editable only for the owner")
    void lists() {
        assertEquals(Map.of(1L, true), editableById(service.listDocuments(trainer, false, false)));
        assertTrue(service.listDocuments(otherTrainer, false, false).isEmpty());
        assertEquals(Map.of(1L, false, 2L, false), editableById(service.listDocuments(hr, false, false)),
                "HR sees every owned course read-only, and not the ownerless one");
        assertEquals(Map.of(1L, false, 2L, true), editableById(service.listDocuments(superAdmin, false, false)));
        assertEquals(Map.of(2L, true), editableById(service.listDocuments(superAdmin, true, false)),
                "the upload page lists only the Super Admin's own courses");
    }

    @Test
    @DisplayName("Another trainer can neither open nor change the course, and nothing changes")
    void otherTrainerIsRefused() {
        Long questionId = store.questionsOf(trainersModule).get(0).getId();

        for (Executable call : List.<Executable>of(
                () -> service.getDetail(1L, otherTrainer),
                () -> service.getStatus(1L, otherTrainer),
                () -> service.getModule(trainersModule, otherTrainer),
                () -> service.getQuizHistory(trainersModule, otherTrainer),
                () -> service.updateModule(trainersModule, "Taken over", "{}", otherTrainer),
                () -> service.approveModule(trainersModule, otherTrainer),
                () -> service.rejectModule(trainersModule, "", otherTrainer),
                () -> service.approveModuleQuiz(trainersModule, otherTrainer),
                () -> service.rejectModuleQuiz(trainersModule, "", otherTrainer),
                () -> service.addQuestion(trainersModule, question(), otherTrainer),
                () -> service.updateQuestion(questionId, question(), otherTrainer),
                () -> service.regenerateDocument(1L, otherTrainer),
                () -> service.deleteDocument(1L, otherTrainer))) {
            ForbiddenRequestException e = assertThrows(ForbiddenRequestException.class, call,
                    "a blank reason must not matter: access is checked first");
            assertEquals("This course belongs to another trainer.", e.getMessage());
        }

        SopModule module = store.modules.get(trainersModule);
        assertEquals("Module 1", module.getTitle());
        assertEquals(ReviewStatus.PENDING, module.getReviewStatus());
        assertTrue(store.questionsOf(trainersModule).stream().allMatch(q -> q.getReviewStatus() == ReviewStatus.PENDING));
        assertEquals(2, store.questionsOf(trainersModule).size());
        assertTrue(store.docs.containsKey(1L));
        assertTrue(store.events.isEmpty());
    }

    @Test
    @DisplayName("HR opens any owned course read-only and is refused every change")
    void hrReadsButCannotChange() {
        assertFalse(service.getDetail(1L, hr).getDocument().isEditable());
        assertEquals(2, service.getModule(trainersModule, hr).getQuiz().size());

        ForbiddenRequestException e = assertThrows(ForbiddenRequestException.class,
                () -> service.approveModule(trainersModule, hr));
        assertEquals("You can view this course but not change it. Only its owner can.", e.getMessage());
        assertThrows(ForbiddenRequestException.class, () -> service.approveModuleQuiz(trainersModule, hr));
        assertThrows(ForbiddenRequestException.class, () -> service.deleteDocument(1L, hr));
        assertEquals(ReviewStatus.PENDING, store.modules.get(trainersModule).getReviewStatus());
    }

    @Test
    @DisplayName("A course without an owner cannot be opened, even by HR or the Super Admin")
    void ownerlessHidden() {
        ForbiddenRequestException e = assertThrows(ForbiddenRequestException.class, () -> service.getDetail(3L, superAdmin));
        assertEquals("SOP document not found.", e.getMessage());
        assertThrows(ForbiddenRequestException.class, () -> service.getDetail(3L, hr));
    }

    @Test
    @DisplayName("The owner keeps full control: open, edit, approve and delete")
    void ownerKeepsControl() {
        assertTrue(service.getDetail(1L, trainer).getDocument().isEditable());

        service.updateModule(trainersModule, "Handling Cash", "{\"summary\":\"s\",\"sections\":[]}", trainer);
        service.approveModule(trainersModule, trainer);
        service.approveModuleQuiz(trainersModule, trainer);

        assertEquals("Handling Cash", store.modules.get(trainersModule).getTitle());
        assertEquals(ReviewStatus.APPROVED, store.modules.get(trainersModule).getReviewStatus());
        assertEquals(trainerId, store.events.values().iterator().next().getActorId(), "the review history names the owner");
        service.deleteDocument(1L, trainer);
        assertFalse(store.docs.containsKey(1L));
    }

    private static Map<Long, Boolean> editableById(List<SopDocumentDto> docs) {
        return docs.stream().collect(Collectors.toMap(SopDocumentDto::getId, SopDocumentDto::isEditable));
    }

    private static SopQuizQuestionDto question() {
        SopQuizQuestionDto q = new SopQuizQuestionDto();
        q.setQuestionType(QuizQuestionType.TRUE_FALSE);
        q.setQuestionText("Cash drops are logged?");
        q.setOptions(List.of("True", "False"));
        q.setCorrectAnswer("True");
        return q;
    }
}
