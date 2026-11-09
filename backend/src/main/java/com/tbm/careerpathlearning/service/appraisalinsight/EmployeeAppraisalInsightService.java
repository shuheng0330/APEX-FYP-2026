package com.tbm.careerpathlearning.service.appraisalinsight;

import com.tbm.careerpathlearning.aspect.*;
import com.tbm.careerpathlearning.config.ClockConfig;
import com.tbm.careerpathlearning.dto.EmployeeInsightDto.*;
import com.tbm.careerpathlearning.exception.*;
import com.tbm.careerpathlearning.model.*;
import com.tbm.careerpathlearning.repository.*;
import com.tbm.careerpathlearning.service.GeminiClient;
import com.tbm.careerpathlearning.service.GeminiClient.AppraisalProviderException;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.*;
import org.springframework.transaction.support.TransactionTemplate;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;

/** Persistent, participant-scoped admission. No external request runs inside a database transaction. */
@Service @MetadataOnlyLogging
public class EmployeeAppraisalInsightService {
    private final EmployeeInsightScopeRepository scopes;
    private final EmployeeInsightGenerationRepository generations;
    private final EmployeeInsightRequestRepository requests;
    private final AnnualKpiReviewPeriodRepository periods;
    private final EmployeeInsightAccess access;
    private final EmployeeInsightEvidenceService evidence;
    private final EmployeeInsightSettings settings;
    private final AppraisalInsightTemplate template;
    private final GeminiClient gemini;
    private final Executor executor;
    private final Clock clock;
    private final TransactionTemplate transaction;
    private final TransactionTemplate dispatchTransaction;
    private final Set<UUID> localJobs=ConcurrentHashMap.newKeySet();

    public EmployeeAppraisalInsightService(EmployeeInsightScopeRepository scopes,
            EmployeeInsightGenerationRepository generations, EmployeeInsightRequestRepository requests,
            AnnualKpiReviewPeriodRepository periods, EmployeeInsightAccess access,
            EmployeeInsightEvidenceService evidence, EmployeeInsightSettings settings,
            AppraisalInsightTemplate template, GeminiClient gemini,
            @Qualifier("appraisalInsightExecutor") Executor executor, PlatformTransactionManager manager, Clock clock) {
        this.scopes=scopes; this.generations=generations; this.requests=requests; this.periods=periods;
        this.access=access; this.evidence=evidence; this.settings=settings; this.template=template;
        this.gemini=gemini; this.executor=executor; this.clock=clock.withZone(ClockConfig.ZONE);
        transaction=new TransactionTemplate(manager);
        transaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        dispatchTransaction=new TransactionTemplate(manager);
    }

    public State state(Long participantId, UUID actor) {
        try (var ignored=MetadataOnlyLogScope.enter()) {
            access.authoriseRequest(actor);
            if (!settings.isEnabled()) return new State(participantId,false,"DISABLED",null,null,List.of(),limits(List.of()));
            return transaction.execute(s -> {
                var p=access.superior(participantId,actor,false);
                evidence.verifyScope(p);
                EmployeeInsightEvidenceService.Captured captured;
                try { captured=evidence.capture(p); }
                catch (BadRequestException incomplete) { captured=null; }
                final String fingerprint=captured==null?null:captured.fingerprint();
                var rows=rows(participantId);
                boolean editable=captured!=null;
                try { access.superior(participantId,actor,true); }
                catch (BadRequestException | KpiVersionConflictException unavailable) { editable=false; }
                String model=model();
                return new State(participantId,editable,captured==null?"RESULTS_INCOMPLETE":editable?null:"NOT_EDITABLE",
                        newest(rows,fingerprint,model,actor).map(g->dto(g,false)).orElse(null),
                        rows.isEmpty()?null:dto(rows.get(0),!matches(rows.get(0),fingerprint,model)),
                        rows.stream().map(g->dto(g,!matches(g,fingerprint,model))).toList(),limits(rows));
            });
        }
    }

    public Result generate(Long participantId, UUID key, UUID actor, boolean regenerate) {
        try (var ignored=MetadataOnlyLogScope.enter()) {
            access.authoriseRequest(actor);
            if (!settings.isEnabled()) throw new BadRequestException("Fictional UAT employee insights are disabled");
            if (key==null) throw new BadRequestException("An idempotency key is required");
            String operation=regenerate?"REGENERATE":"GENERATE";
            Admission admission;
            try { admission=transaction.execute(s->admit(participantId,key,actor,operation)); }
            catch (DataIntegrityViolationException concurrentKey) {
                // A key used concurrently in another context must not leave an unreceipted job.
                admission=transaction.execute(s->{
                    access.superior(participantId,actor,false);
                    var receipt=requests.findByActorIdAndRequestKey(actor,key).orElseThrow(()->concurrentKey);
                    return replay(receipt,participantId,operation);
                });
            }
            var accepted=Objects.requireNonNull(admission);
            if (accepted.dispatch()) {
                localJobs.add(accepted.generationId());
                try {
                    var job=transaction.execute(s->generations.findById(accepted.generationId()).orElseThrow());
                    executor.execute(new AppraisalGenerationTask(job.getId(),"EMPLOYEE",job.getModel(),
                            gemini.appraisalTokenReservation(template.prompt(job.getEvidence())),()->run(job.getId()),code->fail(job.getId(),code,null)));
                }
                catch (RejectedExecutionException full) {
                    String code=full instanceof GeminiDispatchGate.Rejected refused?refused.code():"BUSY";
                    fail(accepted.generationId(),code,null);
                    localJobs.remove(accepted.generationId());
                    if ("NOT_CONFIGURED".equals(code)) throw new BadRequestException("AI generation coordination is not configured");
                    var retry=full instanceof GeminiDispatchGate.Rejected refused?refused.retryAt():now().plusSeconds(5);
                    throw limit(code,"AI generation is busy or temporarily rate limited. Try again after the indicated time.",retry,participantId);
                }
            }
            var result=read(participantId,accepted.generationId(),actor);
            return new Result(accepted.disposition(),result.generation(),result.limits());
        }
    }

    public Result read(Long participantId, UUID generationId, UUID actor) {
        try (var ignored=MetadataOnlyLogScope.enter()) {
            access.authoriseRequest(actor);
            if (!settings.isEnabled()) throw new BadRequestException("Fictional UAT employee insights are disabled");
            return transaction.execute(s->{
                var p=access.superior(participantId,actor,false);
                evidence.verifyScope(p);
                EmployeeInsightEvidenceService.Captured captured;
                try { captured=evidence.capture(p); }
                catch (BadRequestException incomplete) { captured=null; }
                var g=generations.findById(generationId).orElseThrow(EmployeeAppraisalInsightService::denied);
                if (!Objects.equals(g.getParticipantId(),participantId)) throw denied();
                return new Result(null,dto(g,captured==null || !matches(g,captured.fingerprint(),model())),limits(rows(participantId)));
            });
        }
    }

    private Admission admit(Long participantId, UUID key, UUID actor, String operation) {
        // Same lock order as performance correction/submission transactions, then the fixture context.
        periods.lockConfiguration();
        access.superior(participantId,actor,false);
        scopes.lockByParticipantId(participantId).orElseThrow(EmployeeAppraisalInsightService::denied);
        var p=access.superior(participantId,actor,false);
        var captured=evidence.capture(p);
        var receipt=requests.findByActorIdAndRequestKey(actor,key).orElse(null);
        if (receipt!=null) return replay(receipt,participantId,operation);
        access.superior(participantId,actor,true);
        generations.interruptExpired(now());
        var rows=rows(participantId);
        String model=model();
        if ("GENERATE".equals(operation)) {
            var cached=newest(rows,captured.fingerprint(),model,actor).orElse(null);
            if (cached!=null) {
                if (executor instanceof SharedAppraisalExecutor shared) shared.cacheHit();
                return receipt(actor,key,participantId,operation,cached,"REUSED",false);
            }
        }
        var active=rows.stream().filter(this::active).findFirst().orElse(null);
        if (active!=null) {
            if (!matches(active,captured.fingerprint(),model) || !Objects.equals(active.getOwnerId(),actor))
                throw new KpiVersionConflictException("Different evidence is already being processed. Check its status first.");
            return receipt(actor,key,participantId,operation,active,"ALREADY_IN_PROGRESS",false);
        }
        checkLimits(rows);
        var g=new EmployeeInsightGeneration();
        g.setId(UUID.randomUUID()); g.setParticipantId(participantId); g.setEmployeeId(p.getStaff().getId());
        g.setReviewPeriodId(p.getReviewPeriod().getId()); g.setOwnerId(actor); g.setFingerprint(captured.fingerprint());
        g.setEvidence(captured.evidence()); g.setSourceReferences(captured.sourceReferences());
        g.setModel(model); g.setTemplateVersion(AppraisalInsightTemplate.VERSION);
        g.setStatus("QUEUED"); g.setActiveContext(participantId); g.setCreatedAt(now()); g.setQuotaDay(today());
        // Covers a full bounded queue plus one timed provider call; never replay an expired lease.
        g.setLeaseUntil(now().plusSeconds(executor instanceof SharedAppraisalExecutor shared?shared.queueLeaseSeconds():600));
        generations.saveAndFlush(g);
        return receipt(actor,key,participantId,operation,g,"NEWLY_QUEUED",true);
    }

    private Admission receipt(UUID actor, UUID key, Long participantId, String operation,
            EmployeeInsightGeneration g, String disposition, boolean dispatch) {
        var r=new EmployeeInsightRequest(); r.setId(UUID.randomUUID()); r.setActorId(actor); r.setRequestKey(key);
        r.setParticipantId(participantId); r.setOperation(operation); r.setGenerationId(g.getId());
        r.setDisposition(disposition); r.setCreatedAt(now()); requests.saveAndFlush(r);
        return new Admission(g.getId(),disposition,dispatch);
    }
    private Admission replay(EmployeeInsightRequest receipt, Long participantId, String operation) {
        if (!Objects.equals(participantId,receipt.getParticipantId()) || !operation.equals(receipt.getOperation()))
            throw new KpiVersionConflictException("Use a new idempotency key for another context or operation");
        return new Admission(receipt.getGenerationId(),receipt.getDisposition(),false);
    }

    private void run(UUID id) {
        try (var ignored=MetadataOnlyLogScope.enter()) {
            try {
                var work=transaction.execute(s->{
                    periods.lockConfiguration();
                    var initial=generations.findById(id).orElseThrow();
                    scopes.lockByParticipantId(initial.getParticipantId()).orElseThrow(EmployeeAppraisalInsightService::denied);
                    var pending=generations.lockById(id).orElseThrow();
                    if (!"QUEUED".equals(pending.getStatus())) return null;
                    checkCurrent(pending);
                    gemini.validateAppraisalConfiguration();
                    String prompt=template.prompt(pending.getEvidence());
                    var rows=rows(pending.getParticipantId());
                    var backoff=providerRetry();
                    if (backoff!=null && backoff.isAfter(now())) throw new AppraisalProviderException("RATE_LIMITED");
                    long consumed=rows.stream().filter(other->!other.getId().equals(id))
                            .filter(other->today().equals(other.getQuotaDay()) && (other.getDispatchedAt()!=null || active(other))).count();
                    if (consumed>=allowance()) throw new AppraisalProviderException("QUOTA_EXHAUSTED");
                    pending.setQuotaDay(today()); pending.setStatus("RUNNING"); pending.setStartedAt(now());
                    if (ProviderDispatchContext.current()==null) pending.setDispatchedAt(now());
                    pending.setLeaseUntil(now().plusSeconds(90));
                    return new Dispatch(generations.saveAndFlush(pending),prompt);
                });
                if (work==null) return;
                var g=work.generation();
                var context=ProviderDispatchContext.current();
                if (context!=null) context.beforeDispatch(()->markDispatched(id));
                var summary=template.validate(gemini.generateAppraisalJson(work.prompt()),g.getEvidence());
                transaction.executeWithoutResult(s->{
                    periods.lockConfiguration();
                    scopes.lockByParticipantId(g.getParticipantId()).orElseThrow(EmployeeAppraisalInsightService::denied);
                    var current=generations.lockById(id).orElseThrow();
                    if (!"RUNNING".equals(current.getStatus())) return;
                    checkCurrent(current);
                    int version=rows(g.getParticipantId()).stream().map(EmployeeInsightGeneration::getInsightVersion)
                            .filter(Objects::nonNull).max(Integer::compareTo).orElse(0)+1;
                    current.setInsightVersion(version); current.setSummary(summary); current.setStatus("COMPLETED");
                    current.setCompletedAt(now()); current.setActiveContext(null); current.setLeaseUntil(null);
                    generations.saveAndFlush(current);
                });
            } catch (AppraisalProviderException failure) {
                fail(id,failure.code(),"RATE_LIMITED".equals(failure.code())?failure.retryAt(now()):null);
            } catch (AccessDeniedException changed) { fail(id,"ACCESS_CHANGED",null); }
            catch (RuntimeException failure) { fail(id,"GENERATION_FAILED",null); }
            finally { localJobs.remove(id); }
        }
    }

    private void markDispatched(UUID id) {
        dispatchTransaction.executeWithoutResult(s->{
            periods.lockConfiguration();
            var initial=generations.findById(id).orElseThrow();
            scopes.lockByParticipantId(initial.getParticipantId()).orElseThrow(EmployeeAppraisalInsightService::denied);
            var g=generations.lockById(id).orElseThrow();
            if (!"RUNNING".equals(g.getStatus()) || g.getDispatchedAt()!=null) throw new AppraisalProviderException("INTERRUPTED");
            long charged=rows(g.getParticipantId()).stream().filter(other->!other.getId().equals(id)
                    && today().equals(other.getQuotaDay()) && other.getDispatchedAt()!=null).count();
            if (charged>=allowance()) throw new AppraisalProviderException("QUOTA_EXHAUSTED");
            g.setQuotaDay(today());g.setDispatchedAt(now());g.setLeaseUntil(now().plusSeconds(90));generations.saveAndFlush(g);
        });
    }

    @jakarta.annotation.PreDestroy
    public void cancelLocalWork() {
        try (var ignored=MetadataOnlyLogScope.enter()) {
            // A graceful stop releases this instance's unused reservations, never another worker's jobs.
            for (var id:List.copyOf(localJobs)) {
                try { fail(id,"INTERRUPTED",null,true); }
                catch (RuntimeException unavailable) { /* The persisted lease handles an interrupted database connection. */ }
            }
            localJobs.clear();
        }
    }

    @org.springframework.scheduling.annotation.Scheduled(fixedDelay=30000,initialDelay=30000)
    public void recoverExpiredWork() {
        if (!settings.isEnabled()) return;
        try (var ignored=MetadataOnlyLogScope.enter()) {
            try {
                evidence.verifyEnvironment();
                transaction.executeWithoutResult(s->{periods.lockConfiguration();generations.interruptExpired(now());});
            } catch (AccessDeniedException disabledEnvironment) {
                // An incorrectly configured environment must never mutate another database.
            }
        }
    }
    private void checkCurrent(EmployeeInsightGeneration g) {
        if (g.getLeaseUntil()==null || !g.getLeaseUntil().isAfter(now())) throw new AppraisalProviderException("INTERRUPTED");
        var p=access.worker(g.getParticipantId(),g.getOwnerId());
        var captured=evidence.capture(p);
        if (!Objects.equals(g.getEmployeeId(),p.getStaff().getId())
                || !Objects.equals(g.getReviewPeriodId(),p.getReviewPeriod().getId())
                || !matches(g,captured.fingerprint(),model())
                || !template.hash(g.getEvidence()).equals(template.hash(captured.evidence()))
                || !"FICTIONAL_UAT_EMPLOYEE".equals(g.getDataMode()))
            throw new AppraisalProviderException("EVIDENCE_CHANGED");
    }
    private void fail(UUID id, String code, OffsetDateTime retryAt) {
        fail(id,code,retryAt,false);
    }
    private void fail(UUID id, String code, OffsetDateTime retryAt,boolean queuedOnly) {
        transaction.executeWithoutResult(s->{
            var initial=generations.findById(id).orElseThrow();
            scopes.lockByParticipantId(initial.getParticipantId()).orElseThrow(EmployeeAppraisalInsightService::denied);
            var g=generations.lockById(id).orElseThrow();
            if (!active(g)) return;
            if (queuedOnly && !"QUEUED".equals(g.getStatus())) return;
            var completedAt=now();
            g.setStatus("FAILED"); g.setErrorCode(code); g.setCompletedAt(completedAt);
            g.setRetryAt(retryAt==null?null:retryAt.isBefore(completedAt)?completedAt:retryAt);
            g.setActiveContext(null); g.setLeaseUntil(null); generations.saveAndFlush(g);
        });
    }

    private void checkLimits(List<EmployeeInsightGeneration> rows) {
        var limits=limits(rows);
        if (limits.providerRetryAt()!=null && limits.providerRetryAt().isAfter(now()))
            throw limit("PROVIDER_RATE_LIMIT","The AI provider is temporarily rate limited.",limits.providerRetryAt(),limits);
        if (limits.remainingAttempts()==0)
            throw limit("DAILY_LIMIT","The daily AI generation allowance has been used.",limits.resetAt(),limits);
        if (limits.cooldownUntil()!=null && limits.cooldownUntil().isAfter(now()))
            throw limit("COOLDOWN","Wait briefly before requesting a fresh insight.",limits.cooldownUntil(),limits);
    }
    private Limits limits(List<EmployeeInsightGeneration> rows) {
        long used=rows.stream().filter(g->today().equals(g.getQuotaDay()) && (g.getDispatchedAt()!=null || active(g))).count();
        var cooldown=rows.stream().filter(g->g.getDispatchedAt()!=null || active(g)).map(EmployeeInsightGeneration::getCreatedAt)
                .max(OffsetDateTime::compareTo).map(t->t.plusSeconds(Math.max(0,settings.getCooldownSeconds()))).orElse(null);
        return new Limits(now(),allowance(),Math.max(0,allowance()-(int)used),
                today().plusDays(1).atStartOfDay(clock.getZone()).toOffsetDateTime(),cooldown,
                settings.isEnabled()?providerRetry():null);
    }
    private OffsetDateTime providerRetry() {
        var recorded=generations.maxProviderRetryAt().orElse(null);
        var shared=executor instanceof SharedAppraisalExecutor coordinated?coordinated.retryAt(gemini.appraisalModel()):null;
        return recorded==null?shared:shared==null || recorded.isAfter(shared)?recorded:shared;
    }
    private EmployeeInsightLimitException limit(String reason,String message,OffsetDateTime retryAt,Long participantId) {
        return transaction.execute(s->limit(reason,message,retryAt,limits(rows(participantId))));
    }
    private EmployeeInsightLimitException limit(String reason,String message,OffsetDateTime retryAt,Limits limits) {
        return new EmployeeInsightLimitException(new LimitError(reason,message,retryAt,limits));
    }
    private List<EmployeeInsightGeneration> rows(Long participantId) {
        return generations.findAllByParticipantIdOrderByCreatedAtDescIdDesc(participantId);
    }
    private Optional<EmployeeInsightGeneration> newest(List<EmployeeInsightGeneration> rows,String fingerprint,String model,UUID actor) {
        return rows.stream().filter(g->"COMPLETED".equals(g.getStatus()) && Objects.equals(actor,g.getOwnerId())
                && matches(g,fingerprint,model)).max(Comparator.comparing(EmployeeInsightGeneration::getInsightVersion));
    }
    private boolean matches(EmployeeInsightGeneration g,String fingerprint,String model) {
        return Objects.equals(g.getFingerprint(),fingerprint) && Objects.equals(g.getModel(),model)
                && AppraisalInsightTemplate.VERSION.equals(g.getTemplateVersion());
    }
    static Insight dto(EmployeeInsightGeneration g,boolean outdated) {
        return new Insight(g.getId(),g.getInsightVersion(),g.getStatus(),outdated,true,g.getModel(),g.getTemplateVersion(),
                g.getCreatedAt(),g.getCompletedAt(),g.getErrorCode(),g.getRetryAt(),g.getEvidence(),g.getSummary(),AppraisalInsightTemplate.DISCLAIMER);
    }
    private boolean active(EmployeeInsightGeneration g) { return Set.of("QUEUED","RUNNING").contains(g.getStatus()); }
    private int allowance() { return Math.max(1,settings.getDailyAttempts()); }
    private String model() {
        String model=gemini.appraisalModel();
        if (model==null || !model.matches("[A-Za-z0-9._-]{1,100}")) throw new BadRequestException("The AI provider is not configured");
        return model;
    }
    private OffsetDateTime now() { return OffsetDateTime.now(clock); }
    private LocalDate today() { return LocalDate.now(clock); }
    private static AccessDeniedException denied() { return new AccessDeniedException("Fictional UAT insight is unavailable"); }
    private record Admission(UUID generationId,String disposition,boolean dispatch) {}
    private record Dispatch(EmployeeInsightGeneration generation,String prompt) {}
}
