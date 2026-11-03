package com.tbm.careerpathlearning.service.impl;

import com.tbm.careerpathlearning.enums.SopGenerationStatus;
import com.tbm.careerpathlearning.service.SopGenerationQueue;
import com.tbm.careerpathlearning.service.SopGenerationService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/** Uploads are generated one at a time, in order, and survive a restart (FR-06-11). */
class SopGenerationQueueTest {

    private SopTestStore store;
    private SopGenerationQueue queue;
    private final List<Long> generated = Collections.synchronizedList(new ArrayList<>());
    /** Holds the first generation until the test lets it finish. */
    private final CountDownLatch release = new CountDownLatch(1);
    private final CountDownLatch firstStarted = new CountDownLatch(1);
    private CountDownLatch finished;

    /** Records each generation instead of calling the AI; the first one waits for {@link #release}. */
    private class RecordingGeneration extends SopGenerationService {
        @Override
        public void generateForDocument(Long sopDocumentId) {
            generated.add(sopDocumentId);
            if (generated.size() == 1) {
                firstStarted.countDown();
                await(release);
            }
            finished.countDown();
        }
    }

    @BeforeEach
    void setUp() {
        store = new SopTestStore();
        queue = store.wire(new SopGenerationQueue());
        ReflectionTestUtils.setField(queue, "sopGenerationService", new RecordingGeneration());
    }

    @AfterEach
    void tearDown() {
        ReflectionTestUtils.invokeMethod(queue, "shutdown");
    }

    @Test
    @DisplayName("Documents are generated one at a time in the order queued, and each waiting one knows its place")
    void oneAtATime() {
        finished = new CountDownLatch(3);
        queue.enqueue(1L);
        await(firstStarted);
        queue.enqueue(2L);
        queue.enqueue(3L);
        queue.enqueue(2L); // already waiting

        assertEquals(List.of(1L), generated, "the others wait while one is generated");
        assertEquals(0, queue.positionOf(1L), "the one being generated isn't waiting");
        assertEquals(1, queue.positionOf(2L));
        assertEquals(2, queue.positionOf(3L));

        release.countDown();
        await(finished);
        assertEquals(List.of(1L, 2L, 3L), generated);
        assertEquals(0, queue.positionOf(3L));
    }

    @Test
    @DisplayName("A document taken out of the queue while it waits, because it was deleted, is skipped")
    void cancelled() {
        finished = new CountDownLatch(2);
        queue.enqueue(1L);
        await(firstStarted);
        queue.enqueue(2L);
        queue.enqueue(3L);

        queue.cancel(2L);

        assertEquals(1, queue.positionOf(3L));
        release.countDown();
        await(finished);
        assertEquals(List.of(1L, 3L), generated);
    }

    @Test
    @DisplayName("After a restart, documents whose generation never finished go back in the queue, oldest first")
    void resumeAfterRestart() {
        store.doc(5L, "Older upload", null).setGenerationStatus(SopGenerationStatus.PENDING);
        store.doc(3L, "Stopped part-way", null).setGenerationStatus(SopGenerationStatus.GENERATING);
        store.doc(1L, "Already generated", null); // COMPLETED
        release.countDown();
        finished = new CountDownLatch(2);

        queue.resume();

        await(finished);
        assertEquals(List.of(5L, 3L), generated, "oldest first; the finished one isn't touched");
        assertEquals(SopGenerationStatus.PENDING, store.docs.get(3L).getGenerationStatus());
        assertEquals("Queued again after the server restarted.", store.docs.get(3L).getStatusMessage());
        assertEquals(SopGenerationStatus.COMPLETED, store.docs.get(1L).getGenerationStatus());
    }

    @Test
    @DisplayName("With resuming switched off, as in tests that boot the app on a real database, nothing is queued")
    void resumeSwitchedOff() {
        store.doc(5L, "Older upload", null).setGenerationStatus(SopGenerationStatus.PENDING);
        ReflectionTestUtils.setField(queue, "resumeOnStartup", false);

        queue.resume();

        assertEquals(0, queue.positionOf(5L));
        assertTrue(generated.isEmpty());
    }

    private static void await(CountDownLatch latch) {
        try {
            assertTrue(latch.await(5, TimeUnit.SECONDS), "timed out");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            fail(e);
        }
    }
}
