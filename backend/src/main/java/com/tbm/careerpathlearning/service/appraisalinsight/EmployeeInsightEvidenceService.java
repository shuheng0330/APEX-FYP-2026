package com.tbm.careerpathlearning.service.appraisalinsight;

import com.fasterxml.jackson.databind.*;
import com.tbm.careerpathlearning.aspect.MetadataOnlyLogging;
import com.tbm.careerpathlearning.dto.*;
import com.tbm.careerpathlearning.dto.AppraisalInsightDto.*;
import com.tbm.careerpathlearning.enums.*;
import com.tbm.careerpathlearning.exception.BadRequestException;
import com.tbm.careerpathlearning.model.*;
import com.tbm.careerpathlearning.repository.*;
import com.tbm.careerpathlearning.service.*;
import org.springframework.core.env.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.temporal.ChronoUnit;
import java.util.*;

/** Projects only verified fictional performance sources into the identity-free provider contract. */
@Service @Transactional(readOnly=true,noRollbackFor={AccessDeniedException.class,BadRequestException.class}) @MetadataOnlyLogging
public class EmployeeInsightEvidenceService {
    public record Captured(Evidence evidence,String fingerprint,JsonNode sourceReferences) {}
    private final EmployeeInsightSettings settings;
    private final Environment environment;
    private final JdbcTemplate database;
    private final EmployeeInsightScopeRepository scopes;
    private final ReviewPeriodParticipantRepository participants;
    private final AnnualAppraisalReadinessService readiness;
    private final AnnualPerformanceHistoryService history;
    private final AnnualAppraisalRepository appraisals;
    private final KpiAssessmentRepository assessments;
    private final KpiPlanRepository plans;
    private final KpiPlanVersionRepository versions;
    private final KpiCentralResultRepository results;
    private final KpiRepository kpis;
    private final AttitudeAssessmentRepository attitudes;
    private final CompanyKpiCheckpointResolver companyCheckpoints;
    private final AppraisalInsightTemplate template;
    private final ObjectMapper json;

    public EmployeeInsightEvidenceService(EmployeeInsightSettings settings,Environment environment,JdbcTemplate database,
            EmployeeInsightScopeRepository scopes,ReviewPeriodParticipantRepository participants,
            AnnualAppraisalReadinessService readiness,AnnualPerformanceHistoryService history,
            AnnualAppraisalRepository appraisals,KpiAssessmentRepository assessments,KpiPlanRepository plans,
            KpiPlanVersionRepository versions,KpiCentralResultRepository results,KpiRepository kpis,
            AttitudeAssessmentRepository attitudes,CompanyKpiCheckpointResolver companyCheckpoints,
            AppraisalInsightTemplate template,ObjectMapper json) {
        this.settings=settings;this.environment=environment;this.database=database;this.scopes=scopes;
        this.participants=participants;this.readiness=readiness;this.history=history;this.appraisals=appraisals;
        this.assessments=assessments;this.plans=plans;this.versions=versions;this.results=results;this.kpis=kpis;
        this.attitudes=attitudes;this.companyCheckpoints=companyCheckpoints;this.template=template;
        this.json=json.copy().disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS).enable(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS);
    }

    static void requireEnvironment(EmployeeInsightSettings settings,Environment environment,JdbcTemplate database) {
        if (!settings.isEnabled() || !environment.acceptsProfiles(Profiles.of("uat"))
                || environment.acceptsProfiles(Profiles.of("dev","prod"))
                || settings.getDatabaseName()==null || settings.getDatabaseName().isBlank()
                || settings.getDatabaseMarker()==null || settings.getDatabaseMarker().isBlank()) throw denied();
        String name=database.queryForObject("select current_database()",String.class);
        String marker=database.queryForObject("select shobj_description(oid,'pg_database') from pg_database where datname=current_database()",String.class);
        if (!settings.getDatabaseName().equals(name) || !settings.getDatabaseMarker().equals(marker)) throw denied();
    }

    public EmployeeInsightScope verifyScope(ReviewPeriodParticipant p) {
        requireEnvironment(settings,environment,database);
        var scope=scopes.findById(p.getId()).orElseThrow(EmployeeInsightEvidenceService::denied);
        if (!scope.isEnabled() || !Objects.equals(scope.getEmployeeId(),p.getStaff().getId())
                || !Objects.equals(scope.getReviewPeriodId(),p.getReviewPeriod().getId())
                || !Objects.equals(scope.getExpectedDatabaseMarker(),settings.getDatabaseMarker())
                || scope.getProvenance()==null || scope.getProvenance().isBlank() || scope.getSourceRoots()==null) throw denied();
        var roots=scope.getSourceRoots();
        if (roots.plans()==null || !roots.plans().keySet().equals(Set.of("COMPANY","DEPARTMENT","INDIVIDUAL"))
                || roots.attitudeConfigurationId()==null || roots.historicalParticipantIds()==null
                || p.getReviewPeriod().getAttitudeConfiguration()==null
                || !roots.attitudeConfigurationId().equals(p.getReviewPeriod().getAttitudeConfiguration().getId())) throw denied();
        for (var level:KpiLevel.values()) {
            var plan=plans.findById(roots.plans().get(level.name())).orElseThrow(EmployeeInsightEvidenceService::denied);
            checkPlan(plan,p,level,scope);
        }
        return scope;
    }
    public void verifyEnvironment() { requireEnvironment(settings,environment,database); }

    @Transactional(readOnly=true,propagation=org.springframework.transaction.annotation.Propagation.REQUIRES_NEW)
    public Captured captureForSubmission(Long participantId) {
        // Isolate optional historical-source failures from the appraisal's write transaction.
        return capture(participants.findById(participantId).orElseThrow(EmployeeInsightEvidenceService::denied));
    }

    public Captured capture(ReviewPeriodParticipant p) {
        var scope=verifyScope(p);var evaluated=readiness.evaluate(p);
        if (!evaluated.ready() || evaluated.performance()==null)
            throw new BadRequestException("Complete required KPI and Attitude results before generating an insight");
        var snapshot=evaluated.performance();verifySnapshot(p,scope,snapshot,false);
        var refs=json.createObjectNode();refs.put("participantId",p.getId());refs.put("employeeId",p.getStaff().getId().toString());
        refs.put("reviewPeriodId",p.getReviewPeriod().getId());refs.set("roots",json.valueToTree(scope.getSourceRoots()));
        refs.set("performance",json.valueToTree(snapshot));
        var facts=new ArrayList<Fact>();var gaps=new ArrayList<String>();addFacts(snapshot,facts);
        gaps.add("Promotion readiness is not established: higher-role responsibilities and sustained higher-role evidence are not recorded here.");
        gaps.add("Individual KPI records contain targets and completed Superior ratings, but no separate actual-achievement field. Comments and attachments are excluded.");
        var trusted=new TreeSet<>(scope.getSourceRoots().historicalParticipantIds());
        var previousParticipants=new HashMap<Long,ReviewPeriodParticipant>();
        for (var id:trusted) {
            var h=participants.findById(id).orElseThrow(EmployeeInsightEvidenceService::denied);
            if (!Objects.equals(h.getStaff().getId(),p.getStaff().getId())
                    || !h.getReviewPeriod().getEndDate().isBefore(p.getReviewPeriod().getStartDate())) throw denied();
            verifyScope(h);previousParticipants.put(id,h);
        }
        var historical=new ArrayList<History>();var historyRefs=refs.putArray("history");
        for (var row:history.readSelected(p,trusted)) {
            var h=previousParticipants.get(row.participantId());
            if (h==null || !Objects.equals(row.employeeId(),p.getStaff().getId())
                    || !Objects.equals(row.reviewPeriodId(),h.getReviewPeriod().getId())) throw denied();
            boolean submitted=row.sourceType()==AnnualPerformanceHistoryDto.SourceType.SUBMISSION_SNAPSHOT;
            var priorAppraisal=appraisals.findByParticipantId(h.getId()).orElse(null);
            if (!submitted && priorAppraisal!=null && priorAppraisal.getSubmittedAt()!=null
                    && priorAppraisal.getSnapshot()!=null) throw denied();
            var recorded=submitted?Optional.ofNullable(priorAppraisal).orElseThrow(EmployeeInsightEvidenceService::denied).getSnapshot()
                    :readiness.evaluate(h).performance();
            verifySnapshot(h,verifyScope(h),recorded,submitted);
            if (recorded.annualKpiScore().compareTo(row.annualKpiScore())!=0
                    || recorded.attitudeScore().compareTo(row.attitudeScore())!=0 || recorded.finalScore().compareTo(row.finalScore())!=0) throw denied();
            boolean comparable=comparable(p,snapshot,h,recorded);
            String note=comparable?"Comparable duration, evaluation format, allocations and consolidation settings."
                    :"Not directly comparable: review duration, frequency, allocations, evaluation format or consolidation settings differ.";
            historical.add(new History("H"+(historical.size()+1),"Historical review period "+h.getReviewPeriod().getStartDate().getYear(),
                    row.startDate(),row.endDate(),new Scores(row.annualKpiScore(),row.attitudeScore(),row.finalScore()),comparable,note));
            var source=historyRefs.addObject();source.put("participantId",h.getId());source.put("sourceType",row.sourceType().name());
            source.set("snapshot",json.valueToTree(recorded));
        }
        if (historical.isEmpty()) gaps.add("No completed, verified fictional historical performance results are available.");
        else if (historical.size()<trusted.size()) gaps.add("Some verified historical review periods have no completed results.");
        var provider=new Evidence("Selected fictional review period",p.getReviewPeriod().getStartDate(),p.getReviewPeriod().getEndDate(),true,
                snapshot.reviewFrequency().name(),snapshot.consolidationMethod().name(),snapshot.kpiWeight(),snapshot.attitudeWeight(),
                new Scores(snapshot.annualKpiScore(),snapshot.attitudeScore(),snapshot.finalScore()),List.copyOf(facts),List.copyOf(historical),List.copyOf(gaps));
        try {
            if (json.writeValueAsBytes(provider).length>65536) throw new BadRequestException("The verified evidence exceeds the AI preview input limit");
            String hash=HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(
                    (template.hash(provider)+"\n"+json.writeValueAsString(refs)).getBytes(StandardCharsets.UTF_8)));
            return new Captured(provider,hash,refs);
        } catch (BadRequestException safe) { throw safe; }
        catch (Exception invalid) { throw new BadRequestException("Verified AI evidence is unavailable"); }
    }

    private void verifySnapshot(ReviewPeriodParticipant p,EmployeeInsightScope scope,AnnualAppraisalSnapshotDto s,boolean historical) {
        if (s==null || !Objects.equals(s.participantId(),p.getId()) || !Objects.equals(s.employeeId(),p.getStaff().getId())
                || !Objects.equals(s.reviewPeriodId(),p.getReviewPeriod().getId()) || s.checkpoints()==null || s.attitude()==null) throw denied();
        for (var c:s.checkpoints()) {
            var a=assessments.findById(c.assessmentId()).orElseThrow(EmployeeInsightEvidenceService::denied);
            if (!Objects.equals(a.getParticipant().getId(),p.getId()) || !Objects.equals(a.getReviewPeriodId(),p.getReviewPeriod().getId())
                    || !Objects.equals(a.getCheckpoint().getId(),c.checkpointId()) || a.getCheckpoint().getReviewPeriod()==null
                    || !Objects.equals(a.getCheckpoint().getReviewPeriod().getId(),p.getReviewPeriod().getId())
                    || !Objects.equals(a.getCheckpoint().getStartDate(),c.startDate()) || !Objects.equals(a.getCheckpoint().getEndDate(),c.endDate())
                    || c.breakdown()==null || !Objects.equals(c.breakdown().assessmentId(),a.getId()) || c.versions()==null) throw denied();
            var bindings=new EnumMap<KpiLevel,KpiPlanVersion>(KpiLevel.class);
            for (var b:c.versions()) {
                var v=versions.findById(b.versionId()).orElseThrow(EmployeeInsightEvidenceService::denied);verifyVersion(v,p,b.level(),scope);
                if (b.versionNumber()!=v.getVersionNumber() || bindings.put(b.level(),v)!=null) throw denied();
            }
            for (var level:c.breakdown().levels()) {
                if (level.allocation()==null || level.allocation().signum()==0) continue;
                var v=bindings.get(level.level());if (v==null) throw denied();
                for (var item:level.items()) {
                    var k=kpis.findById(item.kpi().getId()).orElseThrow(EmployeeInsightEvidenceService::denied);
                    if (!Objects.equals(k.getVersion().getId(),v.getId()) || !Objects.equals(k.getReviewPeriodId(),p.getReviewPeriod().getId())
                            || !Objects.equals(k.getPlanId(),scope.getSourceRoots().plans().get(level.level().name()))) throw denied();
                }
                if (a.getAssessmentModel()==KpiAssessmentModel.CENTRAL_RESULTS && level.level()!=KpiLevel.INDIVIDUAL) {
                    var checkpoint=level.level()==KpiLevel.COMPANY?companyCheckpoints.resolve(a):a.getCheckpoint();
                    var result=results.findAllByPlanVersionIdAndCheckpointIdOrderByEditionNumberDesc(v.getId(),checkpoint.getId()).stream()
                            .filter(r->Objects.equals(r.getEditionNumber(),level.resultEditionNumber())).findFirst().orElseThrow(EmployeeInsightEvidenceService::denied);
                    verifyResult(result,p,v,checkpoint,new HashSet<>());
                    if ((!historical && result.getStatus()!=KpiCentralResultStatus.PUBLISHED) || result.getPublishedAt()==null
                            || !Objects.equals(result.getPublishedAt(),level.publishedAt())) throw denied();
                }
            }
        }
        var attitude=attitudes.findById(s.attitude().assessmentId()).orElseThrow(EmployeeInsightEvidenceService::denied);
        if (!Objects.equals(attitude.getParticipant().getId(),p.getId()) || !Objects.equals(attitude.getReviewPeriodId(),p.getReviewPeriod().getId())
                || !Objects.equals(attitude.getConfiguration().getId(),scope.getSourceRoots().attitudeConfigurationId())
                || !Objects.equals(s.attitude().configurationId(),attitude.getConfiguration().getId())
                || s.attitude().status()!=AttitudeAssessmentStatus.REVIEWED) throw denied();
        var criteria=new HashSet<Long>();attitude.getConfiguration().getCriteria().forEach(k->criteria.add(k.getId()));
        if (s.attitude().criteria().stream().anyMatch(k->!criteria.contains(k.criterionId()))) throw denied();
    }
    private void verifyVersion(KpiPlanVersion v,ReviewPeriodParticipant p,KpiLevel level,EmployeeInsightScope scope) {
        Set<Long> seen=new HashSet<>();
        for (var current=v;current!=null;current=current.getBaseVersion()) {
            if (!seen.add(current.getId()) || !Objects.equals(current.getReviewPeriodId(),p.getReviewPeriod().getId())
                    || current.getLevel()!=level || !Set.of(KpiVersionStatus.EFFECTIVE,KpiVersionStatus.SUPERSEDED).contains(current.getStatus())) throw denied();
            checkPlan(current.getPlan(),p,level,scope);
        }
    }
    private void checkPlan(KpiPlan plan,ReviewPeriodParticipant p,KpiLevel level,EmployeeInsightScope scope) {
        if (plan==null || plan.getLevel()!=level || !Objects.equals(plan.getId(),scope.getSourceRoots().plans().get(level.name()))
                || !Objects.equals(plan.getReviewPeriod().getId(),p.getReviewPeriod().getId())
                || (level==KpiLevel.INDIVIDUAL && !Objects.equals(plan.getOwnerParticipantId(),p.getId()))
                || (level==KpiLevel.DEPARTMENT && (plan.getDepartment()==null || p.getDepartment()==null
                    || !Objects.equals(plan.getDepartment().getId(),p.getDepartment().getId())))) throw denied();
    }
    private void verifyResult(KpiCentralResult r,ReviewPeriodParticipant p,KpiPlanVersion v,ReviewCheckpoint checkpoint,Set<Long> seen) {
        if (!seen.add(r.getId()) || !Objects.equals(r.getReviewPeriodId(),p.getReviewPeriod().getId())
                || !Objects.equals(r.getPlanVersion().getId(),v.getId()) || r.getLevel()!=v.getLevel()
                || !Objects.equals(r.getCheckpoint().getId(),checkpoint.getId()) || !Objects.equals(r.getCheckpoint().getEndDate(),checkpoint.getEndDate())
                || r.getReviewFrequency()!=checkpoint.getReviewFrequency() || r.getPublishedAt()==null
                || !Set.of(KpiCentralResultStatus.PUBLISHED,KpiCentralResultStatus.SUPERSEDED).contains(r.getStatus())) throw denied();
        for (var item:r.getItems()) if (!Objects.equals(item.getKpi().getVersion().getId(),v.getId())) throw denied();
        if (r.getPreviousEditionId()!=null) {
            var previous=results.findById(r.getPreviousEditionId()).orElseThrow(EmployeeInsightEvidenceService::denied);
            if (previous.getEditionNumber()>=r.getEditionNumber()) throw denied();verifyResult(previous,p,v,checkpoint,seen);
        }
    }
    private void addFacts(AnnualAppraisalSnapshotDto s,List<Fact> facts) {
        facts.add(new Fact("SCORES","PERIOD","Recorded KPI Score: "+formatted(s.annualKpiScore())+"%; Attitude Score: "
                +formatted(s.attitudeScore())+"%; Final Performance Score: "+formatted(s.finalScore())+"%."));
        for (var c:s.checkpoints()) for (var level:c.breakdown().levels()) {
            if (level.allocation()==null || level.allocation().signum()==0) continue;
            for (var item:level.items()) {
            var k=item.kpi();String text="Checkpoint "+c.startDate()+" to "+c.endDate()+". KPI: "+value(k.getName())
                    +". Target: "+value(k.getTarget())+". Weightage: "+value(k.getWeightage())+"%. Superior/result point: "+value(item.point())+" / 5.";
            if (level.level()!=KpiLevel.INDIVIDUAL && item.actualAchievement()!=null) text+=" Recorded shared achievement: "+item.actualAchievement()+".";
            facts.add(new Fact("F"+facts.size(),level.level().name(),text));
            }
        }
        for (var c:s.attitude().criteria()) facts.add(new Fact("F"+facts.size(),"ATTITUDE",
                "Criterion: "+value(c.name())+". Completed Superior point: "+value(c.superiorPoint())+" / 5."));
    }
    private boolean comparable(ReviewPeriodParticipant p,AnnualAppraisalSnapshotDto current,ReviewPeriodParticipant h,AnnualAppraisalSnapshotDto previous) {
        long days=ChronoUnit.DAYS.between(p.getReviewPeriod().getStartDate(),p.getReviewPeriod().getEndDate());
        long oldDays=ChronoUnit.DAYS.between(h.getReviewPeriod().getStartDate(),h.getReviewPeriod().getEndDate());
        return Math.abs(days-oldDays)<=1 && current.reviewFrequency()==previous.reviewFrequency()
                && current.consolidationMethod()==previous.consolidationMethod() && current.kpiWeight().compareTo(previous.kpiWeight())==0
                && current.attitudeWeight().compareTo(previous.attitudeWeight())==0 && current.attitude().format()==previous.attitude().format()
                && current.checkpoints().size()==previous.checkpoints().size()
                && Objects.equals(criteria(current),criteria(previous))
                && Objects.equals(allocations(current),allocations(previous));
    }
    private List<String> criteria(AnnualAppraisalSnapshotDto s) {
        return s.attitude().criteria().stream().map(c->value(c.name())+"\n"+value(c.description())).sorted().toList();
    }
    private List<String> allocations(AnnualAppraisalSnapshotDto s) {
        return s.checkpoints().stream().flatMap(c->c.breakdown().levels().stream())
                .map(l->l.level()+":"+(l.allocation()==null?"unknown":l.allocation().stripTrailingZeros().toPlainString())).distinct().sorted().toList();
    }
    private String formatted(java.math.BigDecimal score) { return score==null?"Unavailable":score.setScale(2,RoundingMode.HALF_UP).toPlainString(); }
    private String value(Object value) { return value==null?"Unavailable":value.toString(); }
    private static AccessDeniedException denied() { return new AccessDeniedException("Verified fictional UAT evidence is unavailable"); }
}
