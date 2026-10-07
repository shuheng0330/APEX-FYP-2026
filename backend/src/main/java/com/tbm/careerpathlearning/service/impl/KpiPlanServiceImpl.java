package com.tbm.careerpathlearning.service.impl;

import com.tbm.careerpathlearning.dto.*;
import com.tbm.careerpathlearning.enums.*;
import com.tbm.careerpathlearning.exception.BadRequestException;
import com.tbm.careerpathlearning.mapper.KpiPlanMapper;
import com.tbm.careerpathlearning.model.*;
import com.tbm.careerpathlearning.repository.*;
import com.tbm.careerpathlearning.service.*;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.*;
import java.util.*;

@Service @Transactional
public class KpiPlanServiceImpl implements KpiPlanService {
    private final KpiPlanRepository plans;
    private final AnnualKpiReviewPeriodRepository periods;
    private final KpiPlanMapper mapper;
    private final KpiPlanValidator validator;
    private final Clock clock;
    public KpiPlanServiceImpl(KpiPlanRepository plans,AnnualKpiReviewPeriodRepository periods,
            KpiPlanMapper mapper,KpiPlanValidator validator,@Qualifier("annualKpiReviewClock") Clock clock) {
        this.plans=plans; this.periods=periods; this.mapper=mapper; this.validator=validator; this.clock=clock;
    }
    @Override @Transactional(readOnly=true) @PreAuthorize("hasAuthority('CAN_MANAGE_COMPANY_KPI')")
    public List<KpiPeriodContextDto> companyPeriods() {
        return periods.findAllByOrderByStartDateDescIdDesc().stream().map(mapper::toContext).toList();
    }
    @Override @Transactional(readOnly=true) @PreAuthorize("hasAuthority('CAN_MANAGE_COMPANY_KPI')")
    public List<KpiPlanDto> companyPlans() {
        return plans.findAllByLevelOrderByUpdatedAtDesc(KpiLevel.COMPANY).stream().map(this::details).toList();
    }
    @Override @Transactional(readOnly=true) @PreAuthorize("hasAuthority('CAN_MANAGE_COMPANY_KPI')")
    public KpiPlanDto companyPlan(Long id) { return details(requirePlan(id,KpiLevel.COMPANY,false)); }
    @Override @PreAuthorize("hasAuthority('CAN_MANAGE_COMPANY_KPI')")
    public KpiPlanDto createCompany(KpiPlanRequest request,UUID actor) {
        periods.lockConfiguration();
        if(request==null || request.getReviewPeriodId()==null) throw new BadRequestException("Select an Annual KPI Review Period");
        AnnualKpiReviewPeriod period=periods.findById(request.getReviewPeriodId()).orElseThrow(()->new BadRequestException("Review period not found"));
        requireWritable(period);
        if(plans.findByReviewPeriodIdAndLevel(period.getId(),KpiLevel.COMPANY).isPresent())
            throw new BadRequestException("This review period already has a Company KPI plan; open the existing plan");
        if(request.getDepartmentId()!=null || request.getOwnerParticipantId()!=null) throw new BadRequestException("Company plans cannot have a Department or Employee owner");
        var plan=new KpiPlan(); plan.setReviewPeriod(period); plan.setLevel(KpiLevel.COMPANY);
        plan.setCreatedAt(OffsetDateTime.now(clock)); plan.setCreatedBy(actor);
        plan.setUpdatedAt(plan.getCreatedAt()); plan.setUpdatedBy(actor);
        validator.validate(request.getItems(),false);
        plans.saveAndFlush(plan);
        replaceItems(plan,request.getItems());
        plans.saveAndFlush(plan);
        return details(plan);
    }
    @Override @PreAuthorize("hasAuthority('CAN_MANAGE_COMPANY_KPI')")
    public KpiPlanDto updateCompany(Long id,KpiPlanRequest request,UUID actor) {
        periods.lockConfiguration();
        var plan=requirePlan(id,KpiLevel.COMPANY,true); requireEditable(plan);
        if(request==null || !Objects.equals(plan.getReviewPeriod().getId(),request.getReviewPeriodId())
                || request.getDepartmentId()!=null || request.getOwnerParticipantId()!=null)
            throw new BadRequestException("The KPI plan scope cannot be changed");
        replaceItems(plan,request.getItems()); touch(plan,actor); plans.saveAndFlush(plan); return details(plan);
    }
    private KpiPlan requirePlan(Long id,KpiLevel level,boolean lock) {
        var plan=(lock?plans.lockById(id):plans.findById(id)).orElseThrow(()->new BadRequestException("KPI plan not found"));
        if(plan.getLevel()!=level) throw new BadRequestException("KPI plan does not match this workflow");
        return plan;
    }
    private void requireWritable(AnnualKpiReviewPeriod period) {
        if(period.getStatus()==AnnualKpiReviewPeriodStatus.CLOSED) throw new BadRequestException("Closed review periods are read-only");
    }
    private void requireEditable(KpiPlan plan) {
        requireWritable(plan.getReviewPeriod());
        if(plan.getStatus()!=KpiPlanStatus.DRAFT && plan.getStatus()!=KpiPlanStatus.RETURNED)
            throw new BadRequestException("Only Draft or Returned KPI plans can be edited");
    }
    private void replaceItems(KpiPlan plan,List<KpiItemDto> inputs) {
        validator.validate(inputs,false);
        Map<Long,Kpi> existing=new HashMap<>(); plan.getItems().forEach(i->existing.put(i.getId(),i));
        Set<Long> seen=new HashSet<>(); List<Kpi> replacement=new ArrayList<>();
        for(var input:inputs) {
            Kpi item;
            if(input.getId()!=null) {
                item=existing.get(input.getId());
                if(item==null || !seen.add(input.getId())) throw new BadRequestException("KPI item IDs must be unique and belong to this plan");
            } else { item=new Kpi(); item.setPlan(plan); item.setPlanId(plan.getId()); item.setReviewPeriodId(plan.getReviewPeriod().getId()); }
            mapper.update(input,item);
            if(item.getName()!=null) item.setName(item.getName().strip());
            replacement.add(item);
        }
        plan.getItems().removeIf(i->!replacement.contains(i));
        replacement.forEach(i->{if(!plan.getItems().contains(i)) plan.getItems().add(i);});
    }
    private void touch(KpiPlan plan,UUID actor) { plan.setUpdatedBy(actor); plan.setUpdatedAt(OffsetDateTime.now(clock)); }
    private KpiPlanDto details(KpiPlan plan) {
        var dto=mapper.toDto(plan); dto.setTotalWeightage(validator.validate(dto.getItems(),false));
        dto.setOverdue(plan.getReviewPeriod().getKpiSetupDeadline()!=null && LocalDate.now(clock).isAfter(plan.getReviewPeriod().getKpiSetupDeadline())
                && plan.getStatus()!=KpiPlanStatus.PUBLISHED && plan.getStatus()!=KpiPlanStatus.APPROVED);
        return dto;
    }
}
