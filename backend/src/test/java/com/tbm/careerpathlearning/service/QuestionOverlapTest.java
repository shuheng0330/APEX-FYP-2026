package com.tbm.careerpathlearning.service;

import com.tbm.careerpathlearning.dto.GeneratedSopContent;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** Telling a question another module already asks from a new one (test note AR-03). */
class QuestionOverlapTest {

    // Modules 3 and 5 of the E-Hailing course, as generated before this rule.
    private static final String MODULE_3 = "What is the consequence if a customer does not reply to the delivery confirmation message within 12 hours?";
    private static final String MODULE_5 = "What is the consequence if a customer does not reply to the confirmation message within 12 hours?";

    private static GeneratedSopContent.GeneratedQuestion q(String text) {
        GeneratedSopContent.GeneratedQuestion q = new GeneratedSopContent.GeneratedQuestion();
        q.setQuestion(text);
        return q;
    }

    @Test
    @DisplayName("The same question in other words is a repeat; another question on the same subject is not")
    void repeats() {
        assertTrue(QuestionOverlap.repeats(MODULE_3, MODULE_5));
        assertFalse(QuestionOverlap.repeats("What is the first step when receiving cash from a guest?",
                "What is the first step if a discrepancy is found during the cash count at the end of a shift?"));
    }

    @Test
    @DisplayName("A short question repeats only one with exactly the same words, so templated questions stay apart")
    void shortQuestions() {
        assertTrue(QuestionOverlap.repeats("When is the float counted?", "When is the float counted?"));
        assertFalse(QuestionOverlap.repeats("When is the float counted?", "When is the drawer counted?"));
        assertFalse(QuestionOverlap.repeats("Which of these is part of \"Cash Handling: Opening\"?",
                "Which of these is part of \"Cash Handling: Closing\"?"));
    }

    @Test
    @DisplayName("Only questions other modules ask are left out; the quiz keeps its order and is never compared with itself")
    void withoutRepeats() {
        GeneratedSopContent.GeneratedQuestion first = q("When is the float counted?");
        GeneratedSopContent.GeneratedQuestion repeat = q(MODULE_5);
        GeneratedSopContent.GeneratedQuestion again = q("When is the float counted?");

        List<GeneratedSopContent.GeneratedQuestion> kept = QuestionOverlap.withoutRepeats(List.of(first, repeat, again), List.of(MODULE_3));

        assertEquals(List.of(first, again), kept);
        assertEquals(List.of(first, repeat), QuestionOverlap.withoutRepeats(List.of(first, repeat), List.of()));
    }
}
