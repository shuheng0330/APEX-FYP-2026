package com.tbm.careerpathlearning.service;

import com.tbm.careerpathlearning.dto.GeneratedSopContent.GeneratedModule;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** The offline builder, used when the AI fails: five-question quizzes and no module too short for one (FR-06-13). */
class LocalSopGeneratorTest {

    /** One long sentence: over the 200-character length rule, but too little to write five questions from. */
    private static final String COIN_TRAY = "Coins go in the left tray of the drawer, sorted by value from the largest coin at "
            + "the back to the smallest coin at the front, so that change can be given quickly during the busy lunch hours "
            + "when the queue at the counter is at its longest and every second counts.";

    private static final String SOP = """
            1. Opening the Cash Drawer
            Count the float at the start of every shift. The float must be RM 200 in mixed notes. Record the
            count in the cash log before serving any guest. Never leave the drawer open when you step away.
            Lock the drawer with the key held by the duty manager. Report any shortage to the supervisor
            immediately. Staff must not lend the drawer key to anyone.

            2. Coin Tray
            %s

            3. Receiving Payments
            Greet the guest and state the amount due. Count the cash in front of the guest. Check every note
            above RM 50 with the UV lamp. Give the change before the receipt. Place large notes under the tray.
            Never accept torn or taped notes. Always thank the guest after the payment.
            """.formatted(COIN_TRAY);

    private final LocalSopGenerator generator = new LocalSopGenerator();

    @Test
    @DisplayName("Every module built from the SOP text gets a five-question quiz (FR-06-13)")
    void fiveQuestionQuizzes() {
        List<GeneratedModule> modules = generator.generate("Cash Handling", SOP);

        assertEquals(5, LocalSopGenerator.QUIZ_SIZE);
        assertFalse(modules.isEmpty());
        for (GeneratedModule m : modules) {
            assertEquals(LocalSopGenerator.QUIZ_SIZE, m.getQuiz().size(), m.getTitle());
        }
    }

    @Test
    @DisplayName("A section too short for a five-question quiz is merged into a neighbouring module, keeping its text (FR-06-13)")
    void shortSectionIsMerged() {
        assertTrue(COIN_TRAY.length() > 200, "long enough that the length rule alone would keep it as a module");

        List<GeneratedModule> modules = generator.generate("Cash Handling", SOP);

        assertEquals(2, modules.size(), titles(modules));
        assertTrue(modules.get(0).getTitle().contains("Opening the Cash Drawer"), titles(modules));
        assertTrue(modules.get(1).getTitle().contains("Receiving Payments"), titles(modules));
        assertTrue(modules.stream().noneMatch(m -> m.getTitle().equals("Coin Tray")), titles(modules));
        assertTrue(modules.stream().anyMatch(m -> text(m).contains("Coins go in the left tray")),
                "the short section's text is kept in a neighbour");
        assertTrue(modules.stream().anyMatch(m -> text(m).contains("UV lamp")), "nothing else is lost");
    }

    private static String text(GeneratedModule m) {
        StringBuilder sb = new StringBuilder(m.getTitle()).append(' ');
        m.getSections().forEach(s -> {
            sb.append(s.getHeading()).append(' ').append(s.getBody()).append(' ');
            if (s.getItems() != null) {
                s.getItems().forEach(i -> sb.append(i).append(' '));
            }
        });
        return sb.toString();
    }

    private static String titles(List<GeneratedModule> modules) {
        return modules.stream().map(m -> m.getTitle() + " (" + m.getQuiz().size() + " questions)").toList().toString();
    }
}
