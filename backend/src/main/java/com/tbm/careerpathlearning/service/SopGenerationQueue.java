package com.tbm.careerpathlearning.service;

import com.tbm.careerpathlearning.enums.SopGenerationStatus;
import com.tbm.careerpathlearning.model.SopDocument;
import com.tbm.careerpathlearning.repository.SopDocumentRepository;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.EnumSet;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Generates uploaded SOPs one at a time, in the order they were queued (FR-06-11), so the AI's rate limit holds and
 * each waiting document can show its place. A restart puts documents whose generation never finished back in the
 * queue. The worker is this class's own thread, not a Spring executor, so the app's other background tasks, such
 * as notification emails, never wait behind a generation.
 */
@Component
public class SopGenerationQueue {

    private static final Logger log = LoggerFactory.getLogger(SopGenerationQueue.class);
    private static final Set<SopGenerationStatus> UNFINISHED =
            EnumSet.of(SopGenerationStatus.PENDING, SopGenerationStatus.PARSING, SopGenerationStatus.GENERATING);

    @Autowired private SopGenerationService sopGenerationService;
    @Autowired private SopDocumentRepository sopDocumentRepository;
    @Autowired private Clock clock;

    /** Off in tests that boot the app against a real database, so they never start the AI. */
    @Value("${training.generation.resume-on-startup:true}")
    private boolean resumeOnStartup = true;

    /** Documents waiting their turn, in order; the one being generated has already left. */
    private final Set<Long> waiting = new LinkedHashSet<>();
    private final ExecutorService worker = Executors.newSingleThreadExecutor(task -> {
        Thread thread = new Thread(task, "sop-generation");
        thread.setDaemon(true);
        return thread;
    });

    /** Adds the document to the end of the queue, unless it is already waiting. */
    public void enqueue(Long sopDocumentId) {
        synchronized (waiting) {
            if (!waiting.add(sopDocumentId)) {
                return;
            }
        }
        worker.execute(() -> {
            synchronized (waiting) {
                if (!waiting.remove(sopDocumentId)) {
                    return; // taken out while it waited
                }
            }
            try {
                sopGenerationService.generateForDocument(sopDocumentId);
            } catch (RuntimeException e) {
                log.error("SOP generation stopped unexpectedly for document {}", sopDocumentId, e);
            }
        });
    }

    /** Takes a waiting document out of the queue, for one being deleted. */
    public void cancel(Long sopDocumentId) {
        synchronized (waiting) {
            waiting.remove(sopDocumentId);
        }
    }

    /** The document's place among those waiting: 1 for the next to go, 0 when it isn't waiting. */
    public int positionOf(Long sopDocumentId) {
        synchronized (waiting) {
            int position = 1;
            for (Long id : waiting) {
                if (id.equals(sopDocumentId)) {
                    return position;
                }
                position++;
            }
            return 0;
        }
    }

    /** After a restart, documents whose generation never finished go back in the queue, oldest first. */
    @EventListener(ApplicationReadyEvent.class)
    public void resume() {
        if (!resumeOnStartup) {
            return;
        }
        OffsetDateTime now = OffsetDateTime.now(clock);
        for (SopDocument doc : sopDocumentRepository.findAllByIsDeletedFalseAndGenerationStatusInOrderByCreatedAtAsc(UNFINISHED)) {
            if (doc.getGenerationStatus() != SopGenerationStatus.PENDING) {
                doc.setGenerationStatus(SopGenerationStatus.PENDING);
                doc.setStatusMessage("Queued again after the server restarted.");
                doc.setUpdatedAt(now);
                sopDocumentRepository.save(doc);
            }
            enqueue(doc.getId());
        }
    }

    @PreDestroy
    void shutdown() {
        worker.shutdownNow();
    }
}
