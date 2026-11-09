package com.tbm.careerpathlearning.service.appraisalinsight;

import com.tbm.careerpathlearning.aspect.*;
import org.slf4j.*;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import java.time.OffsetDateTime;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

/** Bounded local execution; PostgreSQL decides when a job may occupy a shared worker. */
@MetadataOnlyLogging
public class SharedAppraisalExecutor implements Executor {
    private record Local(AppraisalGenerationTask task,OffsetDateTime queuedAt,AtomicBoolean started) {}
    private static final Logger log=LoggerFactory.getLogger(SharedAppraisalExecutor.class);
    private final UUID instance=UUID.randomUUID();
    private final GeminiDispatchGate gate;
    private final ThreadPoolTaskExecutor workers;
    private final Map<UUID,Local> jobs=new ConcurrentHashMap<>();
    private final AtomicBoolean closed=new AtomicBoolean();
    public SharedAppraisalExecutor(GeminiDispatchGate gate,ThreadPoolTaskExecutor workers) { this.gate=gate;this.workers=workers; }
    public long queueLeaseSeconds() { return gate.queueLeaseSeconds(); }
    public void cacheHit() { gate.cacheHit(); }
    public OffsetDateTime retryAt(String model) { return gate.retryAt(model); }
    @Override public void execute(Runnable command) {
        if (closed.get() || !(command instanceof AppraisalGenerationTask task)) throw new RejectedExecutionException("AI generation is busy");
        if (jobs.containsKey(task.id())) return;
        var admitted=gate.enqueue(task,instance);var local=new Local(task,admitted.queuedAt(),new AtomicBoolean());
        if (jobs.putIfAbsent(task.id(),local)!=null) return;
        if (admitted.permit()!=null) start(local,admitted.permit());
    }
    @Scheduled(fixedDelay=1000,initialDelay=1000)
    public void dispatchWaiting() {
        if (closed.get()) return;
        for (var local:jobs.values().stream().sorted(Comparator.comparing(Local::queuedAt)).toList()) {
            if (local.started().get()) continue;
            try {
                var decision=gate.claim(local.task().id(),instance,local.task().model());
                if (decision.permit()!=null) start(local,decision.permit());
                else if ("CANCELLED".equals(decision.reason())) discard(local,"INTERRUPTED");
            } catch (RuntimeException unavailable) {
                // Keep the existing queued job. A transient coordinator failure never creates another generation.
                log.warn("Appraisal dispatcher unavailable; queued work retained");
            }
        }
    }
    private void start(Local local,GeminiDispatchGate.Permit permit) {
        if (!local.started().compareAndSet(false,true)) return;
        try {
            workers.execute(()->{
                try (var ignored=MetadataOnlyLogScope.enter();var context=ProviderDispatchContext.enter(permit)) { local.task().run(); }
                finally {
                    try { gate.finishWork(local.task().id(),local.task().model()); }
                    finally { jobs.remove(local.task().id(),local); }
                }
            });
        } catch (RejectedExecutionException full) {
            gate.cancelQueued(local.task().id(),local.task().model());discard(local,"BUSY");throw full;
        }
    }
    private void discard(Local local,String code) {
        jobs.remove(local.task().id(),local);local.task().cancelled().accept(code);
    }
    @jakarta.annotation.PreDestroy
    public void shutdown() {
        if (!closed.compareAndSet(false,true)) return;
        for (var local:List.copyOf(jobs.values())) {
            try { gate.cancelQueued(local.task().id(),local.task().model()); }
            catch (RuntimeException unavailable) { log.warn("Appraisal shutdown deferred to persisted leases"); }
        }
        workers.shutdown();
    }
}
