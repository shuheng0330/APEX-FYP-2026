package com.tbm.careerpathlearning.service.impl;

import com.tbm.careerpathlearning.dto.SopMaterialReviewEventDto;
import com.tbm.careerpathlearning.enums.ReviewStatus;
import com.tbm.careerpathlearning.enums.SopAssignmentMode;
import com.tbm.careerpathlearning.enums.SopGenerationStatus;
import com.tbm.careerpathlearning.exception.ForbiddenRequestException;
import com.tbm.careerpathlearning.model.SopDocument;
import com.tbm.careerpathlearning.model.SopModule;
import com.tbm.careerpathlearning.model.Staff;
import com.tbm.careerpathlearning.service.LocalSopGenerator;
import com.tbm.careerpathlearning.service.SopGenerationService;
import com.tbm.careerpathlearning.service.SopViewer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** Each module's material keeps a review history, like its quiz (FR-07-14, P2-30). */
class SopMaterialHistoryTest {

    private static final String SOP_TEXT = "1. Opening the Drawer\nCount the float at the start of every shift.\n\n"
            + "2. Coin Tray\nCoins go in the left tray.\n\n3. Receiving Payments\nCount the cash in front of the guest.";

    private SopTestStore store;
    private SopServiceImplTest.FakeGemini gemini;
    private SopGenerationService generation;
    private SopServiceImpl courses;
    private Staff trainer, aisyah;
    private SopViewer owner, hr, otherTrainer;
    private SopModule first, second;

    @BeforeEach
    void setUp() {
        store = new SopTestStore();
        gemini = new SopServiceImplTest.FakeGemini();
        generation = store.wire(new SopGenerationService());
        ReflectionTestUtils.setField(generation, "geminiClient", gemini);
        ReflectionTestUtils.setField(generation, "localSopGenerator", new LocalSopGenerator());
        ReflectionTestUtils.setField(generation, "self", generation);
        courses = store.wire(new SopServiceImpl());
        ReflectionTestUtils.setField(courses, "sopGenerationService", generation);
        trainer = store.person("Tan Wei Ming", null);
        Staff hrPerson = store.person("Nadia Rahman", null);
        aisyah = store.person("Nur Aisyah", null);
        owner = new SopViewer(trainer.getId(), false);
        hr = new SopViewer(hrPerson.getId(), true);
        otherTrainer = new SopViewer(store.person("Farid Hassan", null).getId(), false);

        // A course nobody has enrolled in: two modules waiting for review.
        SopDocument course = store.doc(1L, "Cash Handling", trainer.getId());
        course.setExtractedText("4.1 Handling Cash\nDo not leave the cash drawer unattended.");
        first = store.module(1L, ReviewStatus.PENDING, ReviewStatus.PENDING, ReviewStatus.PENDING);
        second = store.module(1L, ReviewStatus.PENDING, ReviewStatus.PENDING, ReviewStatus.PENDING);
    }

    private List<SopMaterialReviewEventDto> history(SopModule module) {
        return courses.getMaterialHistory(module.getId(), owner);
    }

    private static List<String> actions(List<SopMaterialReviewEventDto> history) {
        return history.stream().map(SopMaterialReviewEventDto::getAction).toList();
    }

    private List<SopModule> modulesOf(long docId) {
        return store.modules.values().stream().filter(m -> m.getSopDocumentId().equals(docId)).toList();
    }

    @Test
    @DisplayName("A generated module's history starts with how it was made: by the AI, or from the SOP text without it")
    void generationStartsTheHistory() {
        SopDocument byAi = store.doc(2L, "Cash Handling v2", trainer.getId());
        byAi.setGenerationStatus(SopGenerationStatus.PENDING);
        byAi.setExtractedText(SOP_TEXT);
        ReflectionTestUtils.setField(generation, "geminiClient", new SopGenerationTest.ScriptedGemini());
        generation.generateForDocument(2L);

        List<SopModule> aiModules = modulesOf(2L);
        assertEquals(2, aiModules.size());
        for (SopModule m : aiModules) {
            List<SopMaterialReviewEventDto> h = history(m);
            assertEquals(List.of("GENERATED"), actions(h), m.getTitle());
            assertEquals("Written by the AI from the uploaded SOP.", h.get(0).getDetail());
            assertNull(h.get(0).getActorName(), "the AI wrote it");
        }

        SopDocument offline = store.doc(3L, "Cash Handling v3", trainer.getId());
        offline.setGenerationStatus(SopGenerationStatus.PENDING);
        offline.setExtractedText(SOP_TEXT);
        ReflectionTestUtils.setField(generation, "geminiClient", gemini);
        gemini.down = true;
        generation.generateForDocument(3L);

        List<SopModule> offlineModules = modulesOf(3L);
        assertFalse(offlineModules.isEmpty());
        for (SopModule m : offlineModules) {
            List<SopMaterialReviewEventDto> h = history(m);
            assertEquals(List.of("GENERATED"), actions(h), m.getTitle());
            assertEquals("Built from the SOP text because the AI was unavailable. Review it carefully.", h.get(0).getDetail());
        }
    }

    @Test
    @DisplayName("An edit records what changed and who changed it; a save that changed nothing adds nothing")
    void editsAreRecorded() {
        String content = "{\"summary\":\"Count the float.\",\"sections\":[]}";
        courses.updateModule(first.getId(), "Counting the Float", content, owner);

        List<SopMaterialReviewEventDto> h = history(first);
        assertEquals(List.of("EDITED"), actions(h));
        assertEquals("Title changed from \"Module 1\" to \"Counting the Float\". Content edited.", h.get(0).getDetail());
        assertEquals("Tan Wei Ming", h.get(0).getActorName());

        // The same content laid out differently, as the browser may send it, is no change.
        courses.updateModule(first.getId(), "Counting the Float", "{ \"summary\": \"Count the float.\", \"sections\": [] }", owner);
        assertEquals(1, history(first).size());

        courses.updateModule(first.getId(), null, "{\"summary\":\"Count the float twice.\",\"sections\":[]}", owner);
        h = history(first);
        assertEquals(List.of("EDITED", "EDITED"), actions(h));
        assertEquals("Content edited.", h.get(0).getDetail(), "newest first; a blank title keeps the old one");
    }

    @Test
    @DisplayName("An approval is recorded once, with who approved it")
    void approvalIsRecorded() {
        courses.approveModule(first.getId(), owner);
        courses.approveModule(first.getId(), owner); // already approved

        List<SopMaterialReviewEventDto> h = history(first);
        assertEquals(List.of("APPROVED"), actions(h));
        assertEquals("Tan Wei Ming", h.get(0).getActorName());
        assertNull(h.get(0).getDetail());
        assertTrue(history(second).isEmpty(), "each module keeps its own history");
    }

    @Test
    @DisplayName("A rejection records the reason and who gave it, then the AI's regeneration, newest first")
    void rejectionIsRecorded() {
        courses.rejectModule(first.getId(), "  Add the cash drop steps  ", owner);

        List<SopMaterialReviewEventDto> h = history(first);
        assertEquals(List.of("REGENERATED", "REJECTED"), actions(h));
        assertNull(h.get(0).getActorName(), "the AI regenerated it");
        assertEquals("Rewritten by the AI from the trainer's feedback, with a new quiz to match.", h.get(0).getDetail());
        assertEquals("Tan Wei Ming", h.get(1).getActorName());
        assertEquals("Add the cash drop steps", h.get(1).getDetail());
        assertEquals(ReviewStatus.REGENERATED, store.modules.get(first.getId()).getReviewStatus());
        assertEquals(List.of("REGENERATED"), courses.getQuizHistory(first.getId(), owner).stream()
                .map(e -> e.getAction().name()).toList(), "the quiz history still notes its regeneration");
    }

    @Test
    @DisplayName("When the AI can't regenerate, nothing is recorded and the material is unchanged")
    void failedRegenerationRecordsNothing() {
        gemini.down = true;

        assertThrows(RuntimeException.class, () -> courses.rejectModule(first.getId(), "Add the cash drop steps", owner));

        assertTrue(history(first).isEmpty());
        assertEquals(ReviewStatus.PENDING, store.modules.get(first.getId()).getReviewStatus());
    }

    @Test
    @DisplayName("Once staff take the course, a rejection goes into a draft whose history starts with the copy")
    void draftHistoryStartsWithTheCopy() {
        courses.approveModule(first.getId(), owner);
        store.enrollment(1L, aisyah.getId(), SopAssignmentMode.IMMEDIATE, store.today(), store.today().plusDays(14), 14, 70);

        courses.rejectModule(first.getId(), "Add the cash drop steps", owner);

        SopDocument draft = store.docs.values().stream().filter(SopDocument::isDraft).findFirst().orElseThrow();
        List<SopModule> draftModules = modulesOf(draft.getId());
        SopModule draftFirst = draftModules.get(0), draftSecond = draftModules.get(1);
        List<SopMaterialReviewEventDto> h = courses.getMaterialHistory(draftFirst.getId(), owner);
        assertEquals(List.of("REGENERATED", "REJECTED", "COPIED"), actions(h));
        assertEquals("Copied from v1 into draft v2.", h.get(2).getDetail());
        assertEquals("Tan Wei Ming", h.get(2).getActorName());
        assertEquals(List.of("COPIED"), actions(courses.getMaterialHistory(draftSecond.getId(), owner)));

        assertEquals(List.of("APPROVED"), actions(history(first)), "the live version keeps its own history");
        assertEquals(ReviewStatus.APPROVED, store.modules.get(first.getId()).getReviewStatus());
    }

    @Test
    @DisplayName("Anyone who can open the course sees its history; a draft's history is its owner's alone")
    void whoSeesTheHistory() {
        courses.approveModule(first.getId(), owner);
        store.enrollment(1L, aisyah.getId(), SopAssignmentMode.IMMEDIATE, store.today(), store.today().plusDays(14), 14, 70);
        courses.rejectModule(second.getId(), "Shorter please", owner);
        Long draftModule = store.modules.values().stream()
                .filter(m -> store.docs.get(m.getSopDocumentId()).isDraft()).findFirst().orElseThrow().getId();

        assertEquals(List.of("APPROVED"), actions(courses.getMaterialHistory(first.getId(), hr)), "read only, but visible");
        assertThrows(ForbiddenRequestException.class, () -> courses.getMaterialHistory(first.getId(), otherTrainer));
        assertThrows(ForbiddenRequestException.class, () -> courses.getMaterialHistory(draftModule, hr));
    }

    @Test
    @DisplayName("The history goes with its modules: discarding a draft, deleting a course, or generating it again")
    void historyGoesWithItsModules() {
        courses.approveModule(first.getId(), owner);
        store.enrollment(1L, aisyah.getId(), SopAssignmentMode.IMMEDIATE, store.today(), store.today().plusDays(14), 14, 70);
        courses.rejectModule(first.getId(), "Add the cash drop steps", owner);
        SopDocument draft = store.docs.values().stream().filter(SopDocument::isDraft).findFirst().orElseThrow();
        List<Long> draftModuleIds = modulesOf(draft.getId()).stream().map(SopModule::getId).toList();

        courses.deleteDocument(draft.getId(), owner); // a draft is discarded

        assertTrue(store.materialEvents.values().stream().noneMatch(e -> draftModuleIds.contains(e.getSopModuleId())));
        assertEquals(List.of("APPROVED"), actions(history(first)), "the live version's history stays");

        SopDocument other = store.doc(4L, "Cash Counting", trainer.getId());
        other.setExtractedText(SOP_TEXT);
        SopModule otherModule = store.module(4L, ReviewStatus.PENDING, ReviewStatus.PENDING);
        courses.approveModule(otherModule.getId(), owner);
        generation.clearGenerated(4L); // generating the course again starts from nothing
        assertTrue(store.materialEvents.values().stream().noneMatch(e -> e.getSopModuleId().equals(otherModule.getId())));

        SopModule third = store.module(4L, ReviewStatus.PENDING, ReviewStatus.PENDING);
        courses.approveModule(third.getId(), owner);
        courses.deleteDocument(4L, owner);
        assertTrue(store.materialEvents.values().stream().noneMatch(e -> e.getSopModuleId().equals(third.getId())));
    }
}
