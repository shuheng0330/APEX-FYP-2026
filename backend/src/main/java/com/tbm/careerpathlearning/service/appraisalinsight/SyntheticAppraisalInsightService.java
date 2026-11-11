package com.tbm.careerpathlearning.service.appraisalinsight;

import com.tbm.careerpathlearning.aspect.MetadataOnlyLogging;
import com.tbm.careerpathlearning.dto.AppraisalInsightDto;
import com.tbm.careerpathlearning.dto.AppraisalInsightDto.*;
import com.tbm.careerpathlearning.enums.AuthorityName;
import com.tbm.careerpathlearning.exception.*;
import com.tbm.careerpathlearning.model.AppraisalInsightGeneration;
import com.tbm.careerpathlearning.repository.AppraisalInsightGenerationRepository;
import com.tbm.careerpathlearning.service.GeminiClient;
import com.tbm.careerpathlearning.service.GeminiClient.AppraisalProviderException;
import org.springframework.beans.factory.annotation.*;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.env.*;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.*;
import org.springframework.transaction.support.TransactionTemplate;
import java.time.*;
import java.util.*;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;

/** No dependency on appraisal/assessment repositories: only server-owned fictional evidence can leave APEX. */
@Service @MetadataOnlyLogging
public class SyntheticAppraisalInsightService {
    private static final Set<AuthorityName> PERMISSIONS = Set.of(AuthorityName.CAN_SUBMIT_ANNUAL_APPRAISAL,
            AuthorityName.CAN_REVIEW_ANNUAL_APPRAISAL);
    private final AppraisalInsightGenerationRepository generations;
    private final AppraisalInsightTemplate template;
    private final GeminiClient gemini;
    private final Executor executor;
    private final TransactionTemplate transaction;
    private final Clock clock;
    private final OffsetDateTime recoveryBefore;
    private final Environment environment;
    private final boolean syntheticEnabled;

    public SyntheticAppraisalInsightService(AppraisalInsightGenerationRepository generations, AppraisalInsightTemplate template,
            GeminiClient gemini, @Qualifier("appraisalInsightExecutor") Executor executor,
            PlatformTransactionManager manager, Clock clock, Environment environment,
            @Value("${appraisal.insight.synthetic-enabled:false}") boolean syntheticEnabled) {
        this.generations = generations; this.template = template; this.gemini = gemini; this.executor = executor;
        this.clock = clock; this.environment = environment; this.syntheticEnabled = syntheticEnabled;
        // Match the database timestamp precision at the recovery boundary.
        this.recoveryBefore = OffsetDateTime.now(clock).truncatedTo(java.time.temporal.ChronoUnit.MICROS);
        transaction = new TransactionTemplate(manager);
        transaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    public List<Scenario> scenarios(UUID actor) { authorise(actor); return template.scenarios(); }

    public AppraisalInsightDto generate(String scenarioId, UUID requestKey, UUID actor) {
        authorise(actor);
        if (requestKey == null) throw new BadRequestException("A request identifier is required");
        var scenario = template.scenario(scenarioId);
        var replay = transaction.execute(s -> replay(actor, requestKey, scenario.name()));
        if (replay != null) return dto(replay);
        var evidence = template.evidence(scenario);
        String model = gemini.appraisalModel();
        if (model == null || !model.matches("[A-Za-z0-9._-]{1,100}"))
            throw new BadRequestException("The AI preview provider is not configured");
        AppraisalInsightGeneration created;
        try {
            created = transaction.execute(s -> {
                var generation = new AppraisalInsightGeneration();
                generation.setId(UUID.randomUUID()); generation.setOwnerId(actor); generation.setRequestKey(requestKey);
                generation.setScenario(scenario.name()); generation.setFixtureVersion(AppraisalInsightTemplate.FIXTURE_VERSION);
                generation.setTemplateVersion(AppraisalInsightTemplate.VERSION); generation.setModel(model);
                generation.setEvidence(evidence); generation.setEvidenceHash(template.hash(evidence));
                generation.setStatus("QUEUED"); generation.setActiveKey(actor.toString()); generation.setCreatedAt(now());
                return generations.saveAndFlush(generation);
            });
        } catch (DataIntegrityViolationException duplicate) {
            replay = transaction.execute(s -> replay(actor, requestKey, scenario.name()));
            if (replay != null) return dto(replay);
            throw new KpiVersionConflictException("An AI preview is already being generated. Wait for it to finish.");
        }
        UUID id = Objects.requireNonNull(created).getId();
        try {
            executor.execute(new AppraisalGenerationTask(id,"SYNTHETIC",created.getModel(),
                    gemini.appraisalTokenReservation(template.prompt(created.getEvidence())),()->run(id),code->fail(id,code)));
        }
        catch (RejectedExecutionException busy) {
            fail(id,busy instanceof GeminiDispatchGate.Rejected rejected?rejected.code():"BUSY");
            if (busy instanceof GeminiDispatchGate.Rejected rejected) throw rejected;
        }
        return get(id, actor);
    }

    public AppraisalInsightDto get(UUID id, UUID actor) {
        authorise(actor);
        return transaction.execute(s -> {
            var generation = generations.findById(id).orElseThrow(SyntheticAppraisalInsightService::denied);
            if (!actor.equals(generation.getOwnerId())) throw denied();
            return dto(generation);
        });
    }

    @EventListener(ApplicationReadyEvent.class)
    public void interruptPreviousRun() {
        // Never automatically replay provider calls after a restart.
        // Coordinated instances recover only expired leases, never another live instance's job.
        if (enabled() && !(executor instanceof SharedAppraisalExecutor))
            transaction.executeWithoutResult(s -> generations.interruptUnfinished(recoveryBefore, now()));
    }

    private void run(UUID id) {
        try {
            var generation = transaction.execute(s -> {
                var pending = generations.findById(id).orElseThrow();
                if (!"QUEUED".equals(pending.getStatus())) return null;
                checkCurrent(pending);
                pending.setStatus("RUNNING"); pending.setStartedAt(now());
                return generations.saveAndFlush(pending);
            });
            if (generation == null) return;
            // The transaction above has committed before the bounded external request begins.
            var summary = template.validate(gemini.generateAppraisalJson(template.prompt(generation.getEvidence())), generation.getEvidence());
            transaction.executeWithoutResult(s -> {
                var current = generations.findById(id).orElseThrow();
                if (!"RUNNING".equals(current.getStatus())) return;
                checkCurrent(current);
                current.setSummary(summary); current.setStatus("COMPLETED"); current.setActiveKey(null); current.setCompletedAt(now());
                generations.saveAndFlush(current);
            });
        } catch (AppraisalProviderException failure) { fail(id, failure.code()); }
        catch (AccessDeniedException revoked) { fail(id, "ACCESS_CHANGED"); }
        catch (RuntimeException failure) { fail(id, "GENERATION_FAILED"); }
    }
    private void checkCurrent(AppraisalInsightGeneration generation) {
        if (!enabled() || !accountAllowed(generation.getOwnerId())) throw denied();
        if (!"SYNTHETIC_ONLY".equals(generation.getDataMode())
                || !AppraisalInsightTemplate.VERSION.equals(generation.getTemplateVersion())
                || !AppraisalInsightTemplate.FIXTURE_VERSION.equals(generation.getFixtureVersion())
                || !generation.getEvidenceHash().equals(template.hash(generation.getEvidence()))
                || !generation.getEvidenceHash().equals(template.hash(template.evidence(template.scenario(generation.getScenario())))))
            throw new AppraisalProviderException("EVIDENCE_CHANGED");
    }
    private AppraisalInsightGeneration replay(UUID actor, UUID key, String scenario) {
        var found = generations.findByOwnerIdAndRequestKey(actor, key).orElse(null);
        if (found != null && !scenario.equals(found.getScenario()))
            throw new KpiVersionConflictException("Use a new request identifier for a different fictional scenario");
        return found;
    }
    private void fail(UUID id, String code) {
        transaction.executeWithoutResult(s -> generations.findById(id).ifPresent(g -> {
            if (!Set.of("QUEUED", "RUNNING").contains(g.getStatus())) return;
            g.setStatus("FAILED"); g.setErrorCode(code); g.setActiveKey(null); g.setCompletedAt(now());
            generations.saveAndFlush(g);
        }));
    }
    private void authorise(UUID actor) {
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        if (actor == null || authentication == null || !authentication.isAuthenticated()
                || !actor.toString().equals(authentication.getName())
                || authentication.getAuthorities().stream().noneMatch(a -> PERMISSIONS.stream().anyMatch(p -> p.name().equals(a.getAuthority()))))
            throw denied();
        if (!enabled()) throw new BadRequestException("AI preview is disabled. Real staff data is not enabled for AI processing.");
        if (!Boolean.TRUE.equals(transaction.execute(s -> accountAllowed(actor)))) throw denied();
    }
    private boolean accountAllowed(UUID actor) { return generations.countAuthorisedPreviewAccounts(actor, PERMISSIONS) > 0; }
    private boolean enabled() { return syntheticEnabled && environment.acceptsProfiles(Profiles.of("uat", "test")); }
    private OffsetDateTime now() { return OffsetDateTime.now(clock); }
    private AppraisalInsightDto dto(AppraisalInsightGeneration generation) {
        return new AppraisalInsightDto(generation.getId(), generation.getScenario(), generation.getStatus(), true,
                generation.getTemplateVersion(), generation.getModel(), generation.getCreatedAt(), generation.getCompletedAt(),
                generation.getErrorCode(), generation.getEvidence(), generation.getSummary(), AppraisalInsightTemplate.DISCLAIMER);
    }
    private static AccessDeniedException denied() { return new AccessDeniedException("AI preview is unavailable for this account"); }
}
