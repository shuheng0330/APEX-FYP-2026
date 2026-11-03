package com.tbm.careerpathlearning.service.impl;

import com.tbm.careerpathlearning.dto.*;
import com.tbm.careerpathlearning.enums.*;
import com.tbm.careerpathlearning.exception.*;
import com.tbm.careerpathlearning.mapper.*;
import com.tbm.careerpathlearning.model.*;
import com.tbm.careerpathlearning.repository.*;
import com.tbm.careerpathlearning.service.*;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.time.*;
import java.util.*;

@Service @Transactional
@PreAuthorize("hasAnyAuthority('CAN_MANAGE_COMPANY_KPI','CAN_ENTER_COMPANY_KPI_RESULTS','CAN_MANAGE_DEPARTMENT_KPI')")
public class KpiCentralResultServiceImpl implements KpiCentralResultService {
    private static final Logger log=LoggerFactory.getLogger(KpiCentralResultServiceImpl.class);
    private final AnnualKpiReviewPeriodRepository periods;
    private final ReviewCheckpointRepository checkpoints;
    private final KpiPlanVersionRepository versions;
    private final KpiCentralResultRepository results;
    private final CompanyKpiResultDelegateRepository delegates;
    private final StaffRepository staff;
    private final PerformanceDepartmentResolver departments;
    private final KpiCentralResultMapper mapper;
    private final KpiPlanMapper plans;
    private final AnnualKpiReviewPeriodMapper periodMapper;
    private final Clock clock;
    private final KpiCentralResultItemRepository items;
    private final KpiCentralResultEvidenceRepository files;
    private final KpiAssessmentEvidenceStorage storage;
    private final KpiCheckpointAggregateService aggregates;

    public KpiCentralResultServiceImpl(AnnualKpiReviewPeriodRepository periods,ReviewCheckpointRepository checkpoints,
            KpiPlanVersionRepository versions,KpiCentralResultRepository results,CompanyKpiResultDelegateRepository delegates,
            StaffRepository staff,PerformanceDepartmentResolver departments,KpiCentralResultMapper mapper,KpiPlanMapper plans,
            AnnualKpiReviewPeriodMapper periodMapper,@Qualifier("annualKpiReviewClock") Clock clock,
            KpiCentralResultItemRepository items,KpiCentralResultEvidenceRepository files,KpiAssessmentEvidenceStorage storage,
            KpiCheckpointAggregateService aggregates) {
        this.periods=periods;this.checkpoints=checkpoints;this.versions=versions;this.results=results;
        this.delegates=delegates;this.staff=staff;this.departments=departments;this.mapper=mapper;
        this.plans=plans;this.periodMapper=periodMapper;this.clock=clock;
        this.items=items;this.files=files;this.storage=storage;this.aggregates=aggregates;
    }
    @Override @Transactional(readOnly=true)
    public List<KpiPeriodContextDto> periods(UUID actor) {
        active(actor);
        return periods.findAllByOrderByStartDateDescIdDesc().stream()
                .filter(p->p.getAssessmentModel()==KpiAssessmentModel.CENTRAL_RESULTS && p.getStatus()!=AnnualKpiReviewPeriodStatus.DRAFT)
                .filter(p->companyAccess(p.getId(),actor) || hasDepartmentAccess(actor))
                .map(plans::toContext).toList();
    }
    @Override @Transactional(readOnly=true)
    public List<KpiCentralResultOptionDto> options(Long periodId,UUID actor) {
        var period=period(periodId);active(actor);requirePeriodAccess(period,actor);
        return versions.findAllByReviewPeriodIdOrderByVersionNumberDescIdDesc(periodId).stream()
                .filter(v->confirmed(v) && canAccess(v,actor))
                .filter(v->eligibleCheckpoints(v).size()>0)
                .map(v->{var plan=v.getPlan();var department=plan.getDepartment();
                    return new KpiCentralResultOptionDto(v.getId(),v.getVersionNumber(),v.getLevel(),
                            department==null?null:department.getId(),department==null?null:department.getName(),
                            plan.getCurrentVersion()!=null && Objects.equals(plan.getCurrentVersion().getId(),v.getId()));})
                .toList();
    }
    @Override @Transactional(readOnly=true)
    public List<AnnualKpiReviewPeriodDto.Checkpoint> checkpoints(Long versionId,UUID actor) {
        var version=version(versionId);active(actor);requireAccess(version,actor);requireConfirmed(version);
        return eligibleCheckpoints(version).stream().map(periodMapper::toDto).toList();
    }
    @Override @Transactional(readOnly=true)
    public List<KpiCentralResultDto> list(Long periodId,UUID actor) {
        var period=period(periodId);active(actor);requirePeriodAccess(period,actor);
        return results.findAllByReviewPeriodIdOrderByUpdatedAtDescIdDesc(periodId).stream()
                .filter(r->canAccess(r.getPlanVersion(),actor)).map(this::details).toList();
    }
    @Override @Transactional(readOnly=true)
    public KpiCentralResultDto get(Long id,UUID actor) {
        var result=result(id,false);active(actor);requireAccess(result.getPlanVersion(),actor);return details(result);
    }
    @Override
    public KpiCentralResultDto create(KpiCentralResultRequest request,UUID actor) {
        periods.lockConfiguration();active(actor);
        if(request==null || request.getPlanVersionId()==null || request.getCheckpointId()==null)
            throw new BadRequestException("Select a KPI version and Review Checkpoint");
        var version=version(request.getPlanVersionId());requireAccess(version,actor);requireConfirmed(version);
        var checkpoint=checkpoints.findById(request.getCheckpointId()).orElseThrow(()->new BadRequestException("Review Checkpoint not found"));
        requireWritable(version,checkpoint);
        if(results.eligibleParticipants(version.getId(),checkpoint.getId())==0)
            throw new BadRequestException("No enrolled employees require this KPI version for the selected checkpoint");
        if(results.existsByPlanVersionIdAndCheckpointId(version.getId(),checkpoint.getId()))
            throw new BadRequestException("Results already exist for this KPI version and checkpoint; open the existing results");
        var result=new KpiCentralResult();result.setReviewPeriodId(version.getReviewPeriodId());result.setPlanVersion(version);
        result.setCheckpoint(checkpoint);result.setReviewFrequency(checkpoint.getReviewFrequency());result.setLevel(version.getLevel());
        result.setEditionNumber(1);result.setCreatedBy(actor);result.setUpdatedBy(actor);
        result.setCreatedAt(now());result.setUpdatedAt(result.getCreatedAt());
        for(var kpi:version.getItems()) {
            var item=new KpiCentralResultItem();item.setResult(result);item.setKpi(kpi);item.setPlanVersionId(version.getId());result.getItems().add(item);
        }
        apply(result,request);results.saveAndFlush(result);return details(result);
    }
    @Override
    public KpiCentralResultDto update(Long id,KpiCentralResultRequest request,UUID actor) {
        periods.lockConfiguration();active(actor);var result=result(id,true);requireAccess(result.getPlanVersion(),actor);
        draftWritable(result);
        if(request==null || !Objects.equals(request.getPlanVersionId(),result.getPlanVersion().getId())
                || !Objects.equals(request.getCheckpointId(),result.getCheckpoint().getId()))
            throw new BadRequestException("The KPI version and checkpoint cannot be changed");
        expectedVersion(result,request.getExpectedLockVersion());
        apply(result,request);touch(result,actor);
        results.saveAndFlush(result);return details(result);
    }
    @Override
    public KpiCentralResultDto publish(Long id,KpiCentralResultDecisionRequest request,UUID actor) {
        periods.lockConfiguration();active(actor);var result=result(id,true);requireAccess(result.getPlanVersion(),actor);
        draftWritable(result);expectedVersion(result,request==null?null:request.getExpectedLockVersion());
        var blockers=publicationBlockers(result);
        if(!blockers.isEmpty()) throw new BadRequestException(String.join("; ",blockers));
        var current=results.findByPlanVersionIdAndCheckpointIdAndStatus(result.getPlanVersion().getId(),
                result.getCheckpoint().getId(),KpiCentralResultStatus.PUBLISHED);
        if(result.getPreviousEditionId()==null) {
            if(current.isPresent()) throw new KpiVersionConflictException("Published results have changed. Refresh before publishing.");
        } else {
            if(current.isEmpty() || !Objects.equals(current.get().getId(),result.getPreviousEditionId()))
                throw new KpiVersionConflictException("This correction no longer follows the current publication. Refresh before continuing.");
            var previous=result(result.getPreviousEditionId(),true);
            if(previous.getStatus()!=KpiCentralResultStatus.PUBLISHED || !sameScope(previous,result))
                throw new KpiVersionConflictException("The previous publication has changed. Refresh before continuing.");
            correctionReason(result.getCorrectionReason());
            // Published content and its original audit stay immutable; only its effective status changes.
            previous.setStatus(KpiCentralResultStatus.SUPERSEDED);results.saveAndFlush(previous);
        }
        result.setStatus(KpiCentralResultStatus.PUBLISHED);result.setPublishedBy(actor);result.setPublishedAt(now());
        touch(result,actor);results.saveAndFlush(result);
        aggregates.refreshForPublication(result,actor);
        return details(result);
    }
    @Override
    public KpiCentralResultDto correct(Long id,KpiCentralResultDecisionRequest request,UUID actor) {
        periods.lockConfiguration();active(actor);var previous=result(id,true);requireAccess(previous.getPlanVersion(),actor);
        requireResultWritable(previous);expectedVersion(previous,request==null?null:request.getExpectedLockVersion());
        if(previous.getStatus()!=KpiCentralResultStatus.PUBLISHED) throw new BadRequestException("Create a correction from the current published results");
        var reason=correctionReason(request==null?null:request.getReason());
        if(results.findByPlanVersionIdAndCheckpointIdAndStatus(previous.getPlanVersion().getId(),previous.getCheckpoint().getId(),KpiCentralResultStatus.DRAFT).isPresent())
            throw new BadRequestException("A correction Draft already exists; continue the existing correction");
        var correction=new KpiCentralResult();correction.setReviewPeriodId(previous.getReviewPeriodId());
        correction.setCheckpoint(previous.getCheckpoint());correction.setReviewFrequency(previous.getReviewFrequency());
        correction.setPlanVersion(previous.getPlanVersion());correction.setLevel(previous.getLevel());
        if(previous.getEditionNumber()==Integer.MAX_VALUE) throw new BadRequestException("No further result editions can be created");
        correction.setEditionNumber(previous.getEditionNumber()+1);correction.setPreviousEditionId(previous.getId());
        correction.setCorrectionReason(reason);correction.setCreatedBy(actor);correction.setUpdatedBy(actor);
        correction.setCreatedAt(now());correction.setUpdatedAt(correction.getCreatedAt());
        for(var original:previous.getItems()) {
            var item=new KpiCentralResultItem();item.setResult(correction);item.setKpi(original.getKpi());
            item.setPlanVersionId(original.getPlanVersionId());item.setActualAchievement(original.getActualAchievement());
            item.setPoint(original.getPoint());item.setComment(original.getComment());correction.getItems().add(item);
        }
        var copiedKeys=new ArrayList<String>();
        try {
            results.saveAndFlush(correction);
            var attachments=files.findAllByItemIdInOrderByUploadedAtAscIdAsc(previous.getItems().stream().map(KpiCentralResultItem::getId).toList());
            var newItems=new HashMap<Long,KpiCentralResultItem>();correction.getItems().forEach(i->newItems.put(i.getKpi().getId(),i));
            var originalItems=new HashMap<Long,KpiCentralResultItem>();previous.getItems().forEach(i->originalItems.put(i.getId(),i));
            for(var attachment:attachments) {
                var stored=storage.copy(attachment.getStorageKey(),attachment.getOriginalFilename());
                copiedKeys.add(stored.key());deleteOnRollback(stored.key());
                var target=newItems.get(originalItems.get(attachment.getItemId()).getKpi().getId());
                // Inherited evidence retains its real uploader/time rather than inventing a new upload.
                files.saveAndFlush(evidenceRow(target.getId(),stored,attachment.getUploadedBy(),attachment.getUploadedAt()));
            }
            return details(correction);
        } catch(RuntimeException failure) {copiedKeys.forEach(this::safeDelete);throw failure;}
    }
    @Override @Transactional(readOnly=true)
    public List<KpiCentralResultDto> history(Long id,UUID actor) {
        active(actor);var result=result(id,false);requireAccess(result.getPlanVersion(),actor);
        return results.findAllByPlanVersionIdAndCheckpointIdOrderByEditionNumberDesc(result.getPlanVersion().getId(),result.getCheckpoint().getId())
                .stream().map(this::details).toList();
    }
    @Override
    public KpiCentralResultEvidenceMutationDto upload(Long itemId,Long expectedLockVersion,MultipartFile file,UUID actor) {
        periods.lockConfiguration();active(actor);var item=item(itemId);var result=result(item.getResult().getId(),true);
        requireAccess(result.getPlanVersion(),actor);draftWritable(result);expectedVersion(result,expectedLockVersion);
        var stored=storage.store(file);deleteOnRollback(stored.key());
        try {
            var evidence=evidenceRow(itemId,stored,actor,now());files.saveAndFlush(evidence);
            touch(result,actor);results.saveAndFlush(result);
            return new KpiCentralResultEvidenceMutationDto(mapper.toDto(evidence),result.getLockVersion());
        } catch(RuntimeException failure) {safeDelete(stored.key());throw failure;}
    }
    @Override @Transactional(readOnly=true)
    public List<KpiCentralResultEvidenceDto> evidence(Long itemId,UUID actor) {
        active(actor);var item=item(itemId);requireAccess(item.getResult().getPlanVersion(),actor);
        return files.findAllByItemIdOrderByUploadedAtAscIdAsc(itemId).stream().map(mapper::toDto).toList();
    }
    @Override @Transactional(readOnly=true)
    public Download download(Long evidenceId,UUID actor) {
        active(actor);var evidence=file(evidenceId);requireAccess(item(evidence.getItemId()).getResult().getPlanVersion(),actor);
        return new Download(storage.resource(evidence.getStorageKey()),mapper.toDto(evidence));
    }
    @Override
    public KpiCentralResultEvidenceMutationDto deleteEvidence(Long evidenceId,Long expectedLockVersion,UUID actor) {
        periods.lockConfiguration();active(actor);var evidence=file(evidenceId);var item=item(evidence.getItemId());
        var result=result(item.getResult().getId(),true);requireAccess(result.getPlanVersion(),actor);
        draftWritable(result);expectedVersion(result,expectedLockVersion);
        var metadata=mapper.toDto(evidence);files.delete(evidence);files.flush();touch(result,actor);results.saveAndFlush(result);
        afterCommit(()->safeDelete(evidence.getStorageKey()));
        return new KpiCentralResultEvidenceMutationDto(metadata,result.getLockVersion());
    }
    @Override @Transactional(readOnly=true) @PreAuthorize("hasAuthority('CAN_MANAGE_COMPANY_KPI')")
    public List<CompanyKpiResultDelegateDto> delegates(Long periodId,UUID actor) {
        owner(periodId,actor,false);return delegates.findAllByReviewPeriodIdOrderByAssignedAtDescIdDesc(periodId).stream().map(mapper::toDto).toList();
    }
    @Override @PreAuthorize("hasAuthority('CAN_MANAGE_COMPANY_KPI')")
    public CompanyKpiResultDelegateDto assignDelegate(Long periodId,UUID staffId,UUID actor) {
        periods.lockConfiguration();owner(periodId,actor,true);active(staffId);
        if(delegates.existsByReviewPeriodIdAndStaffIdAndRevokedAtIsNull(periodId,staffId))
            throw new BadRequestException("This employee is already assigned to enter Company KPI results for this period");
        var delegate=new CompanyKpiResultDelegate();delegate.setReviewPeriodId(periodId);delegate.setStaffId(staffId);
        delegate.setAssignedBy(actor);delegate.setAssignedAt(now());delegates.saveAndFlush(delegate);return mapper.toDto(delegate);
    }
    @Override @PreAuthorize("hasAuthority('CAN_MANAGE_COMPANY_KPI')")
    public CompanyKpiResultDelegateDto revokeDelegate(Long periodId,Long delegateId,UUID actor) {
        periods.lockConfiguration();owner(periodId,actor,true);
        var delegate=delegates.lockById(delegateId).orElseThrow(()->new BadRequestException("Delegate assignment not found"));
        if(!Objects.equals(delegate.getReviewPeriodId(),periodId)) throw new AccessDeniedException("Delegate assignment belongs to another Review Period");
        if(delegate.getRevokedAt()!=null) throw new BadRequestException("This assignment has already been removed");
        delegate.setRevokedBy(actor);delegate.setRevokedAt(now());delegates.saveAndFlush(delegate);return mapper.toDto(delegate);
    }
    private void apply(KpiCentralResult result,KpiCentralResultRequest request) {
        if(request.getItems()==null) throw new BadRequestException("Provide a result item collection; it may be empty for a Draft");
        var items=new HashMap<Long,KpiCentralResultItem>();result.getItems().forEach(i->items.put(i.getKpi().getId(),i));
        var seen=new HashSet<Long>();
        for(var answer:request.getItems()) {
            if(answer==null || answer.getKpiId()==null || !items.containsKey(answer.getKpiId()) || !seen.add(answer.getKpiId()))
                throw new BadRequestException("Use each KPI from the selected version only once");
            if(answer.getPoint()!=null && (answer.getPoint()<1 || answer.getPoint()>5)) throw new BadRequestException("Select a Result Point from 1 to 5");
            if(answer.getActualAchievement()!=null && answer.getActualAchievement().length()>10000
                    || answer.getComment()!=null && answer.getComment().length()>10000)
                throw new BadRequestException("Keep each achievement and comment within 10000 characters");
        }
        // Omitted answers retain saved values, allowing partial Draft updates.
        for(var answer:request.getItems()) {
            var item=items.get(answer.getKpiId());item.setActualAchievement(trim(answer.getActualAchievement()));
            item.setPoint(answer.getPoint());item.setComment(trim(answer.getComment()));
        }
    }
    private List<String> publicationBlockers(KpiCentralResult result) {
        var blockers=new ArrayList<String>();
        var expected=new HashSet<>(result.getPlanVersion().getItems().stream().map(Kpi::getId).toList());
        var supplied=new HashSet<>(result.getItems().stream().map(i->i.getKpi().getId()).toList());
        if(!expected.equals(supplied) || supplied.size()!=result.getItems().size())
            blockers.add("Results must include every KPI from this confirmed plan version");
        for(var item:result.getItems()) {
            var name=item.getKpi().getName();
            if(item.getActualAchievement()==null || item.getActualAchievement().isBlank()) blockers.add("Enter Actual Achievement for "+name);
            if(item.getPoint()==null || item.getPoint()<1 || item.getPoint()>5) blockers.add("Select a Result Point for "+name);
        }
        if(result.getItems().isEmpty()) blockers.add("The confirmed KPI plan has no items");
        if(result.getPreviousEditionId()!=null && (result.getCorrectionReason()==null || result.getCorrectionReason().isBlank()
                || result.getCorrectionReason().strip().length()>10000)) blockers.add("Provide a correction reason within 10000 characters");
        return blockers;
    }
    private KpiCentralResultDto details(KpiCentralResult result) {
        var dto=mapper.toDto(result);var blockers=publicationBlockers(result);dto.setPublicationBlockers(blockers);
        var itemIds=result.getItems().stream().map(KpiCentralResultItem::getId).filter(Objects::nonNull).toList();
        var attachments=new HashMap<Long,List<KpiCentralResultEvidenceDto>>();
        if(!itemIds.isEmpty()) for(var evidence:files.findAllByItemIdInOrderByUploadedAtAscIdAsc(itemIds))
            attachments.computeIfAbsent(evidence.getItemId(),ignored->new ArrayList<>()).add(mapper.toDto(evidence));
        dto.getItems().forEach(i->i.setEvidence(attachments.getOrDefault(i.getId(),List.of())));
        var actions=new ArrayList<String>();
        boolean writable=confirmed(result.getPlanVersion())
                && result.getPlanVersion().getPlan().getReviewPeriod().getStatus()==AnnualKpiReviewPeriodStatus.OPEN
                && !LocalDate.now(clock).isBefore(result.getCheckpoint().getEndDate().plusDays(1))
                && results.eligibleParticipants(result.getPlanVersion().getId(),result.getCheckpoint().getId())>0;
        if(result.getStatus()==KpiCentralResultStatus.DRAFT && writable) {
            actions.add("UPDATE_DRAFT");actions.add("UPLOAD_EVIDENCE");
            if(blockers.isEmpty()) actions.add("PUBLISH");
        }
        if(result.getStatus()==KpiCentralResultStatus.PUBLISHED) {
            var draft=results.findByPlanVersionIdAndCheckpointIdAndStatus(result.getPlanVersion().getId(),result.getCheckpoint().getId(),KpiCentralResultStatus.DRAFT);
            dto.setWorkingCorrectionId(draft.map(KpiCentralResult::getId).orElse(null));
            if(writable) actions.add(draft.isPresent()?"CONTINUE_CORRECTION":"CREATE_CORRECTION");
        }
        dto.setAllowedActions(actions);
        return dto;
    }
    private void requireResultWritable(KpiCentralResult result) {
        requireConfirmed(result.getPlanVersion());requireWritable(result.getPlanVersion(),result.getCheckpoint());
        if(results.eligibleParticipants(result.getPlanVersion().getId(),result.getCheckpoint().getId())==0)
            throw new BadRequestException("No enrolled employees currently require these checkpoint results");
    }
    private void draftWritable(KpiCentralResult result) {
        requireResultWritable(result);
        if(result.getStatus()!=KpiCentralResultStatus.DRAFT) throw new BadRequestException("Published results and evidence are read-only; create a correction instead");
    }
    private void expectedVersion(KpiCentralResult result,Long expected) {
        if(expected==null || expected!=result.getLockVersion()) throw new KpiVersionConflictException("Results or access have changed. Refresh before continuing.");
    }
    private String correctionReason(String reason) {
        if(reason==null || reason.isBlank() || reason.strip().length()>10000)
            throw new BadRequestException("Provide a correction reason within 10000 characters");
        return reason.strip();
    }
    private boolean sameScope(KpiCentralResult first,KpiCentralResult second) {
        return Objects.equals(first.getPlanVersion().getId(),second.getPlanVersion().getId())
                && Objects.equals(first.getCheckpoint().getId(),second.getCheckpoint().getId());
    }
    private void touch(KpiCentralResult result,UUID actor) {
        result.setUpdatedBy(actor);var time=now();
        result.setUpdatedAt(time.isAfter(result.getUpdatedAt())?time:result.getUpdatedAt().plusNanos(1000));
    }
    private KpiCentralResultItem item(Long id) {
        return items.findById(id).orElseThrow(()->new BadRequestException("Central KPI result item not found"));
    }
    private KpiCentralResultEvidence file(Long id) {
        return files.findById(id).orElseThrow(()->new BadRequestException("Central KPI evidence not found"));
    }
    private KpiCentralResultEvidence evidenceRow(Long itemId,KpiAssessmentEvidenceStorage.Stored stored,UUID uploader,OffsetDateTime uploadedAt) {
        var row=new KpiCentralResultEvidence();row.setItemId(itemId);row.setStorageKey(stored.key());row.setOriginalFilename(stored.filename());
        row.setContentType(stored.contentType());row.setSizeBytes(stored.size());row.setUploadedBy(uploader);row.setUploadedAt(uploadedAt);return row;
    }
    private void deleteOnRollback(String key) {
        if(TransactionSynchronizationManager.isSynchronizationActive()) TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override public void afterCompletion(int status){if(status!=STATUS_COMMITTED) safeDelete(key);}
        });
    }
    private void afterCommit(Runnable action) {
        if(TransactionSynchronizationManager.isSynchronizationActive()) TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override public void afterCommit(){action.run();}
        });else action.run();
    }
    private void safeDelete(String key) {
        try {storage.delete(key);} catch(RuntimeException failure){log.warn("Could not clean up central KPI evidence",failure);}
    }
    private List<ReviewCheckpoint> eligibleCheckpoints(KpiPlanVersion version) {
        return checkpoints.findAllByReviewPeriodIdOrderByReviewFrequencyAscSequenceNumberAsc(version.getReviewPeriodId()).stream()
                .filter(c->results.eligibleParticipants(version.getId(),c.getId())>0).toList();
    }
    private boolean confirmed(KpiPlanVersion v) {
        return (v.getStatus()==KpiVersionStatus.EFFECTIVE || v.getStatus()==KpiVersionStatus.SUPERSEDED)
                && (v.getLevel()==KpiLevel.COMPANY && v.getPlan().getStatus()==KpiPlanStatus.PUBLISHED
                || v.getLevel()==KpiLevel.DEPARTMENT && v.getPlan().getStatus()==KpiPlanStatus.APPROVED);
    }
    private void requireConfirmed(KpiPlanVersion v) {
        if(!confirmed(v)) throw new BadRequestException("Select a published Company or approved Department KPI version");
    }
    private boolean canAccess(KpiPlanVersion v,UUID actor) {
        if(v.getLevel()==KpiLevel.COMPANY) return companyAccess(v.getReviewPeriodId(),actor);
        if(v.getLevel()!=KpiLevel.DEPARTMENT || !hasDepartmentAccess(actor)) return false;
        var department=departments.resolve(active(actor).getRole());
        return department!=null && v.getPlan().getDepartment()!=null && Objects.equals(department.getId(),v.getPlan().getDepartment().getId());
    }
    private boolean hasDepartmentAccess(UUID actor) {
        if(!has("CAN_MANAGE_DEPARTMENT_KPI")) return false;
        var role=active(actor).getRole();return role!=null && !role.isDeleted() && role.isPerformanceReviewEligible();
    }
    private boolean companyAccess(Long periodId,UUID actor) {
        return has("CAN_MANAGE_COMPANY_KPI") || has("CAN_ENTER_COMPANY_KPI_RESULTS")
                && delegates.existsByReviewPeriodIdAndStaffIdAndRevokedAtIsNull(periodId,actor);
    }
    private void requireAccess(KpiPlanVersion version,UUID actor) {
        period(version.getReviewPeriodId());
        if(!canAccess(version,actor)) throw new AccessDeniedException("You do not have access to enter results for this KPI plan");
    }
    private void requirePeriodAccess(AnnualKpiReviewPeriod p,UUID actor) {
        if(!companyAccess(p.getId(),actor) && !hasDepartmentAccess(actor)) throw new AccessDeniedException("Company result assignment or Department management permission required");
    }
    private void owner(Long periodId,UUID actor,boolean write) {
        active(actor);var p=period(periodId);
        if(!has("CAN_MANAGE_COMPANY_KPI")) throw new AccessDeniedException("Only an authorised Company KPI owner can manage delegates");
        if(write && p.getStatus()==AnnualKpiReviewPeriodStatus.CLOSED) throw new BadRequestException("Closed Review Periods are read-only");
    }
    private AnnualKpiReviewPeriod period(Long id) {
        if(id==null) throw new BadRequestException("Select an Annual Review Period");
        var p=periods.findById(id).orElseThrow(()->new BadRequestException("Annual Review Period not found"));
        if(p.getAssessmentModel()!=KpiAssessmentModel.CENTRAL_RESULTS) throw new BadRequestException("This period uses the existing all-level assessment workflow");
        if(p.getStatus()==AnnualKpiReviewPeriodStatus.DRAFT) throw new BadRequestException("Publish the Annual Review Period first");
        return p;
    }
    private void requireWritable(KpiPlanVersion version,ReviewCheckpoint c) {
        var p=period(version.getReviewPeriodId());
        if(!Objects.equals(c.getReviewPeriod().getId(),p.getId())) throw new BadRequestException("The checkpoint belongs to another Review Period");
        if(p.getStatus()!=AnnualKpiReviewPeriodStatus.OPEN) throw new BadRequestException("Central results can be saved only while the Review Period is Open");
        if(LocalDate.now(clock).isBefore(c.getEndDate().plusDays(1))) throw new BadRequestException("Result entry opens the day after the checkpoint End Date");
    }
    private KpiPlanVersion version(Long id) {
        if(id==null) throw new BadRequestException("Select a KPI version");
        return versions.findById(id).orElseThrow(()->new BadRequestException("KPI version not found"));
    }
    private KpiCentralResult result(Long id,boolean lock) {
        return (lock?results.lockById(id):results.findById(id)).orElseThrow(()->new BadRequestException("Central KPI results not found"));
    }
    private Staff active(UUID id) {
        if(id==null) throw new BadRequestException("Select an active employee");
        var s=staff.findById(id).orElseThrow(()->new AccessDeniedException("Active staff account required"));
        if(s.isDeleted() || s.getAccountStatus()!=StaffAccountStatus.ACTIVE) throw new AccessDeniedException("Active staff account required");
        return s;
    }
    private boolean has(String authority) {
        var auth=SecurityContextHolder.getContext().getAuthentication();
        return auth!=null && auth.getAuthorities().stream().anyMatch(a->a.getAuthority().equals(authority));
    }
    private OffsetDateTime now(){return OffsetDateTime.now(clock);}
    private String trim(String value){return value==null?null:value.strip();}
}
