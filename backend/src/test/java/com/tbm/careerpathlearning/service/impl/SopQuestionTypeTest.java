package com.tbm.careerpathlearning.service.impl;

import com.tbm.careerpathlearning.dto.SopQuizQuestionDto;
import com.tbm.careerpathlearning.enums.QuizQuestionType;
import com.tbm.careerpathlearning.enums.ReviewStatus;
import com.tbm.careerpathlearning.enums.SopAssignmentMode;
import com.tbm.careerpathlearning.exception.BadRequestException;
import com.tbm.careerpathlearning.model.SopDocument;
import com.tbm.careerpathlearning.model.SopModule;
import com.tbm.careerpathlearning.model.SopQuizQuestion;
import com.tbm.careerpathlearning.model.Staff;
import com.tbm.careerpathlearning.service.SopViewer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** Quiz questions are Multiple choice or True/False; older fill-in-the-blank questions can still be edited (FR-08-01). */
class SopQuestionTypeTest {

    private SopTestStore store;
    private SopServiceImpl courses;
    private Staff trainer, aisyah;
    private SopViewer owner;
    private SopModule module;

    @BeforeEach
    void setUp() {
        store = new SopTestStore();
        courses = store.wire(new SopServiceImpl());
        trainer = store.person("Tan Wei Ming", null);
        aisyah = store.person("Nur Aisyah", null);
        owner = new SopViewer(trainer.getId(), false);
        store.doc(1L, "Cash Handling", trainer.getId());
        module = store.module(1L, ReviewStatus.APPROVED, SopTestStore.quiz(5, ReviewStatus.APPROVED));
    }

    private static void assertRefused(String message, Executable call) {
        BadRequestException e = assertThrows(BadRequestException.class, call);
        assertEquals(message, e.getMessage());
    }

    private static SopQuizQuestionDto question(QuizQuestionType type, String text, List<String> options, String answer) {
        SopQuizQuestionDto dto = new SopQuizQuestionDto();
        dto.setQuestionType(type);
        dto.setQuestionText(text);
        dto.setOptions(options);
        dto.setCorrectAnswer(answer);
        return dto;
    }

    private static SopQuizQuestionDto fillIn(String text, String answer) {
        return question(QuizQuestionType.FILL_IN_THE_BLANK, text, List.of(), answer);
    }

    /** A question saved before fill-in-the-blank was dropped. */
    private SopQuizQuestion oldFillIn() {
        SopQuizQuestion q = store.questionsOf(module.getId()).get(0);
        q.setQuestionType(QuizQuestionType.FILL_IN_THE_BLANK);
        q.setQuestionText("Count the ____ before opening.");
        q.setOptions("[]");
        q.setCorrectAnswer("float");
        return q;
    }

    private List<SopDocument> drafts() {
        return store.docs.values().stream().filter(SopDocument::isDraft).toList();
    }

    @Test
    @DisplayName("Multiple choice and True/False questions can be added")
    void choiceQuestionsAreAdded() {
        courses.addQuestion(module.getId(), question(QuizQuestionType.MULTIPLE_CHOICE, "Who counts the float?",
                List.of("Cashier", "Guard", "Driver"), "Cashier"), owner);
        courses.addQuestion(module.getId(), question(QuizQuestionType.TRUE_FALSE, "The float is counted twice.",
                List.of("True", "False"), "True"), owner);

        assertEquals(7, store.questionsOf(module.getId()).size());
    }

    @Test
    @DisplayName("A new fill-in-the-blank question is refused and nothing is saved")
    void newFillInRefused() {
        assertRefused("New questions must be Multiple choice or True/False.",
                () -> courses.addQuestion(module.getId(), fillIn("Count the ____.", "float"), owner));

        assertEquals(5, store.questionsOf(module.getId()).size());
        assertTrue(courses.getQuizHistory(module.getId(), owner).isEmpty(), "nothing recorded");
    }

    @Test
    @DisplayName("While staff take the course, a refused fill-in question starts no draft")
    void refusalStartsNoDraft() {
        store.enrollment(1L, aisyah.getId(), SopAssignmentMode.IMMEDIATE, store.today(), store.today().plusDays(14), 14, 70);

        assertRefused("New questions must be Multiple choice or True/False.",
                () -> courses.addQuestion(module.getId(), fillIn("Count the ____.", "float"), owner));

        assertTrue(drafts().isEmpty());
    }

    @Test
    @DisplayName("A choice question can't be changed to fill-in-the-blank")
    void changeToFillInRefused() {
        SopQuizQuestion q = store.questionsOf(module.getId()).get(1);
        String text = q.getQuestionText();

        assertRefused("A question can't be changed to Fill in the blank. Use Multiple choice or True/False.",
                () -> courses.updateQuestion(q.getId(), fillIn(text, "B1"), owner));

        assertEquals(QuizQuestionType.MULTIPLE_CHOICE, store.questionsOf(module.getId()).get(1).getQuestionType());
    }

    @Test
    @DisplayName("An older fill-in-the-blank question can still be edited, keeping its type")
    void oldFillInStillEditable() {
        SopQuizQuestion q = oldFillIn();

        SopQuizQuestionDto saved = courses.updateQuestion(q.getId(), fillIn("Count the ____ before you open the drawer.", "float"), owner);

        assertEquals(QuizQuestionType.FILL_IN_THE_BLANK, saved.getQuestionType());
        assertEquals("Count the ____ before you open the drawer.", saved.getQuestionText());
    }

    @Test
    @DisplayName("An older fill-in-the-blank question can be changed to a choice question")
    void oldFillInCanBecomeChoice() {
        SopQuizQuestion q = oldFillIn();

        SopQuizQuestionDto saved = courses.updateQuestion(q.getId(), question(QuizQuestionType.TRUE_FALSE,
                "The float is counted before opening.", List.of("True", "False"), "True"), owner);

        assertEquals(QuizQuestionType.TRUE_FALSE, saved.getQuestionType());
        assertEquals(List.of("True", "False"), saved.getOptions());
    }
}
