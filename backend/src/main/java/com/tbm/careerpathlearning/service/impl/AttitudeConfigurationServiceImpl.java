package com.tbm.careerpathlearning.service.impl;

import com.tbm.careerpathlearning.dto.*;
import com.tbm.careerpathlearning.enums.*;
import com.tbm.careerpathlearning.exception.BadRequestException;
import com.tbm.careerpathlearning.mapper.AttitudeConfigurationMapper;
import com.tbm.careerpathlearning.model.*;
import com.tbm.careerpathlearning.repository.*;
import com.tbm.careerpathlearning.service.AttitudeConfigurationService;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.*;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service @Transactional @PreAuthorize("hasAuthority('CAN_MANAGE_ATTITUDE_CONFIGURATION')")
public class AttitudeConfigurationServiceImpl implements AttitudeConfigurationService {
    private final AttitudeConfigurationRepository configurations;
    private final AnnualKpiReviewPeriodRepository periods;
    private final ReviewPeriodRoleConfigurationRepository periodRoles;
    private final RoleRepository roles;
    private final StaffRepository staff;
    private final AttitudeConfigurationMapper mapper;
    private final Clock clock;

    public AttitudeConfigurationServiceImpl(AttitudeConfigurationRepository configurations,
            AnnualKpiReviewPeriodRepository periods,ReviewPeriodRoleConfigurationRepository periodRoles,
            RoleRepository roles,StaffRepository staff,AttitudeConfigurationMapper mapper,
            @Qualifier("annualKpiReviewClock") Clock clock) {
        this.configurations=configurations;this.periods=periods;this.periodRoles=periodRoles;
        this.roles=roles;this.staff=staff;this.mapper=mapper;this.clock=clock;
    }
    @Override @Transactional(readOnly=true)
    public List<AttitudeConfigurationDto> list(UUID actor) {
        active(actor);return configurations.findAllByOrderByCreatedAtDescIdDesc().stream().map(mapper::toDto).toList();
    }
    @Override @Transactional(readOnly=true)
    public AttitudeConfigurationDto get(Long id,UUID actor) {active(actor);return mapper.toDto(require(id));}
    @Override @Transactional(readOnly=true)
    public AttitudeConfigurationDto current(UUID actor) {
        active(actor);return configurations.findFirstByStatusOrderByPublishedAtDescIdDesc(AttitudeConfigurationStatus.PUBLISHED)
                .map(mapper::toDto).orElse(null);
    }
    @Override @Transactional(readOnly=true)
    public AttitudeConfigurationOptionsDto options(UUID actor) {
        active(actor);var dto=new AttitudeConfigurationOptionsDto();dto.setFormats(List.of(AttitudeEvaluationFormat.values()));
        dto.setRoles(roles.findAllByIsDeletedIsFalse().stream().filter(Role::isPerformanceReviewEligible)
                .sorted(Comparator.comparing(Role::getName).thenComparing(Role::getId)).map(role->{
                    var option=new AttitudeConfigurationDto.RoleMapping();option.setRoleId(role.getId());option.setRoleName(role.getName());return option;
                }).toList());return dto;
    }
    @Override public AttitudeConfigurationDto create(AttitudeConfigurationRequest request,UUID actor) {
        periods.lockConfiguration();active(actor);var configuration=new AttitudeConfiguration();
        configuration.setCreatedAt(OffsetDateTime.now(clock));configuration.setCreatedBy(actor);
        apply(configuration,request);touch(configuration,actor);configurations.saveAndFlush(configuration);return mapper.toDto(configuration);
    }
    @Override public AttitudeConfigurationDto update(Long id,AttitudeConfigurationRequest request,UUID actor) {
        periods.lockConfiguration();active(actor);var configuration=require(id);draft(configuration);
        apply(configuration,request);touch(configuration,actor);configurations.saveAndFlush(configuration);return mapper.toDto(configuration);
    }
    @Override public AttitudeConfigurationDto copy(Long id,UUID actor) {
        periods.lockConfiguration();active(actor);var source=require(id);
        if(source.getStatus()!=AttitudeConfigurationStatus.PUBLISHED) throw new BadRequestException("Only a published configuration can be copied into a new Draft");
        var copy=new AttitudeConfiguration();copy.setName(source.getName());copy.setCreatedAt(OffsetDateTime.now(clock));copy.setCreatedBy(actor);
        for(var original:source.getCriteria()) {
            var criterion=new AttitudeCriterion();criterion.setConfiguration(copy);criterion.setName(original.getName());
            criterion.setDescription(original.getDescription());criterion.setCriterionType(original.getCriterionType());
            criterion.setEvaluationFormat(original.getEvaluationFormat());criterion.setActive(original.isActive());
            criterion.setDisplayOrder(original.getDisplayOrder());copy.getCriteria().add(criterion);
        }
        for(var original:source.getRatingDefinitions()) {
            var rating=new AttitudeRatingDefinition();rating.setConfiguration(copy);rating.setPoint(original.getPoint());
            rating.setLabel(original.getLabel());rating.setDescription(original.getDescription());copy.getRatingDefinitions().add(rating);
        }
        for(var original:source.getRoleMappings()) {
            var mapping=new AttitudeRoleFormatMapping();mapping.setConfiguration(copy);mapping.setRole(original.getRole());
            mapping.setEvaluationFormat(original.getEvaluationFormat());copy.getRoleMappings().add(mapping);
        }
        touch(copy,actor);configurations.saveAndFlush(copy);return mapper.toDto(copy);
    }
    @Override public AttitudeConfigurationDto publish(Long id,UUID actor) {
        periods.lockConfiguration();active(actor);var configuration=require(id);draft(configuration);validatePublication(configuration);
        configuration.setStatus(AttitudeConfigurationStatus.PUBLISHED);configuration.setPublishedAt(OffsetDateTime.now(clock));
        configuration.setPublishedBy(actor);touch(configuration,actor);configurations.saveAndFlush(configuration);
        return mapper.toDto(configuration);
    }
    @Override @Transactional(readOnly=true)
    public AttitudePeriodConfigurationDto period(Long id,UUID actor) {active(actor);return periodDto(requirePeriod(id));}
    @Override public AttitudePeriodConfigurationDto bindInitially(Long periodId,Long configurationId,UUID actor) {
        periods.lockConfiguration();active(actor);var period=requirePeriod(periodId);
        if(period.getStatus()!=AnnualKpiReviewPeriodStatus.OPEN || period.getAttitudeConfiguration()!=null)
            throw new BadRequestException("Only an Open review period without an attitude configuration can be initially bound");
        var configuration=require(configurationId);
        if(configuration.getStatus()!=AttitudeConfigurationStatus.PUBLISHED) throw new BadRequestException("Select a published attitude configuration");
        period.setAttitudeConfiguration(configuration);period.setUpdatedBy(actor);periods.saveAndFlush(period);return periodDto(period);
    }

    private void apply(AttitudeConfiguration configuration,AttitudeConfigurationRequest request) {
        if(request==null) throw new BadRequestException("Attitude configuration is required");
        configuration.setName(text(request.getName(),255,"Configuration name"));
        var oldCriteria=configuration.getCriteria().stream().filter(c->c.getId()!=null).collect(Collectors.toMap(AttitudeCriterion::getId,Function.identity()));
        var criteria=new ArrayList<AttitudeCriterion>();var ids=new HashSet<Long>();var names=new HashSet<String>();
        for(var input:collection(request.getCriteria())) {
            if(input==null) throw new BadRequestException("Criterion details are required");
            if(input.getId()!=null && (!ids.add(input.getId()) || !oldCriteria.containsKey(input.getId())))
                throw new BadRequestException("Criterion does not belong to this Draft or is duplicated");
            if(input.getCriterionType()==null || (input.getCriterionType()==AttitudeCriterionType.SHARED_CORE_VALUE ?
                    input.getEvaluationFormat()!=null : input.getEvaluationFormat()==null))
                throw new BadRequestException("Use Shared Core Value without a format, or Format-specific with a format");
            var name=text(input.getName(),255,"Criterion name");
            var scope=input.getCriterionType()+":"+input.getEvaluationFormat()+":"+(name==null?"":name.toLowerCase(Locale.ROOT));
            if(name!=null && !names.add(scope)) throw new BadRequestException("Criterion names must be unique within their scope");
            var criterion=input.getId()==null?new AttitudeCriterion():oldCriteria.get(input.getId());
            criterion.setConfiguration(configuration);criterion.setName(name);criterion.setDescription(text(input.getDescription(),10000,"Criterion description"));
            criterion.setCriterionType(input.getCriterionType());criterion.setEvaluationFormat(input.getEvaluationFormat());
            criterion.setActive(input.isActive());criterion.setDisplayOrder(criteria.size());criteria.add(criterion);
        }
        var oldRatings=configuration.getRatingDefinitions().stream().collect(Collectors.toMap(AttitudeRatingDefinition::getPoint,Function.identity()));
        var ratings=new ArrayList<AttitudeRatingDefinition>();var points=new HashSet<Integer>();
        for(var input:collection(request.getRatingDefinitions())) {
            if(input==null || input.getPoint()==null || input.getPoint()<1 || input.getPoint()>5 || !points.add(input.getPoint()))
                throw new BadRequestException("Rating points must be distinct integers from 1 to 5");
            var rating=oldRatings.getOrDefault(input.getPoint(),new AttitudeRatingDefinition());rating.setConfiguration(configuration);
            rating.setPoint(input.getPoint());rating.setLabel(text(input.getLabel(),255,"Rating label"));
            rating.setDescription(text(input.getDescription(),10000,"Rating description"));ratings.add(rating);
        }
        var oldMappings=configuration.getRoleMappings().stream().collect(Collectors.toMap(m->m.getRole().getId(),Function.identity()));
        var mappings=new ArrayList<AttitudeRoleFormatMapping>();var roleIds=new HashSet<Long>();
        for(var input:collection(request.getRoleMappings())) {
            if(input==null || input.getRoleId()==null || input.getEvaluationFormat()==null || !roleIds.add(input.getRoleId()))
                throw new BadRequestException("Each Role requires one evaluation format and must not be duplicated");
            var role=roles.findById(input.getRoleId()).filter(r->!r.isDeleted() && r.isPerformanceReviewEligible())
                    .orElseThrow(()->new BadRequestException("Select an eligible employee Job Role"));
            var mapping=oldMappings.getOrDefault(role.getId(),new AttitudeRoleFormatMapping());mapping.setConfiguration(configuration);
            mapping.setRole(role);mapping.setEvaluationFormat(input.getEvaluationFormat());mappings.add(mapping);
        }
        replace(configuration.getCriteria(),criteria);replace(configuration.getRatingDefinitions(),ratings);replace(configuration.getRoleMappings(),mappings);
    }
    private void validatePublication(AttitudeConfiguration configuration) {
        required(configuration.getName(),"Configuration name");
        if(configuration.getRatingDefinitions().size()!=5) throw new BadRequestException("Complete all five rating definitions before publishing");
        for(var rating:configuration.getRatingDefinitions()) {required(rating.getLabel(),"Rating label");required(rating.getDescription(),"Rating description");}
        var criteria=configuration.getCriteria().stream().filter(AttitudeCriterion::isActive).toList();
        for(var criterion:criteria) {required(criterion.getName(),"Active criterion name");required(criterion.getDescription(),"Active criterion description");}
        for(var format:AttitudeEvaluationFormat.values()) {
            if(criteria.stream().noneMatch(c->c.getCriterionType()==AttitudeCriterionType.SHARED_CORE_VALUE || c.getEvaluationFormat()==format))
                throw new BadRequestException("At least one active criterion is required for "+format);
        }
        for(var mapping:configuration.getRoleMappings()) {
            if(mapping.getRole().isDeleted() || !mapping.getRole().isPerformanceReviewEligible())
                throw new BadRequestException("Remove ineligible Job Roles before publishing");
        }
        // Unmapped Roles are reported per period; they must not block unrelated KPI workflows or period opening.
    }
    private AttitudePeriodConfigurationDto periodDto(AnnualKpiReviewPeriod period) {
        var dto=new AttitudePeriodConfigurationDto();dto.setReviewPeriodId(period.getId());dto.setReviewPeriodName(period.getName());dto.setReviewPeriodStatus(period.getStatus());
        var configuration=period.getAttitudeConfiguration();dto.setConfiguration(configuration==null?null:mapper.toDto(configuration));
        var mapped=configuration==null?Set.<Long>of():configuration.getRoleMappings().stream().map(m->m.getRole().getId()).collect(Collectors.toSet());
        dto.setUnmappedRoleNames(periodRoles.findAllByReviewPeriodIdOrderByIdAsc(period.getId()).stream()
                .filter(c->!mapped.contains(c.getRole().getId())).map(c->c.getRole().getName()).distinct().toList());
        dto.setCanBindInitially(period.getStatus()==AnnualKpiReviewPeriodStatus.OPEN && configuration==null);return dto;
    }
    private AttitudeConfiguration require(Long id) {
        if(id==null) throw new BadRequestException("Select an attitude configuration");
        return configurations.findById(id).orElseThrow(()->new BadRequestException("Attitude configuration not found"));
    }
    private AnnualKpiReviewPeriod requirePeriod(Long id) {return periods.findById(id).orElseThrow(()->new BadRequestException("Annual KPI Review Period not found"));}
    private void draft(AttitudeConfiguration configuration) {
        if(configuration.getStatus()!=AttitudeConfigurationStatus.DRAFT) throw new BadRequestException("Published configurations are read-only; copy into a new Draft to make changes");
    }
    private void active(UUID actor) {
        var account=staff.findById(actor).orElseThrow(()->new AccessDeniedException("Active account required"));
        if(account.isDeleted() || account.getAccountStatus()!=StaffAccountStatus.ACTIVE) throw new AccessDeniedException("Active account required");
    }
    private void touch(AttitudeConfiguration configuration,UUID actor) {configuration.setUpdatedAt(OffsetDateTime.now(clock));configuration.setUpdatedBy(actor);}
    private String text(String value,int max,String field) {
        if(value==null) return null;var trimmed=value.trim();if(trimmed.length()>max) throw new BadRequestException(field+" must not exceed "+max+" characters");
        return trimmed.isEmpty()?null:trimmed;
    }
    private void required(String value,String field) {if(value==null || value.isBlank()) throw new BadRequestException(field+" is required before publishing");}
    private <T> List<T> collection(List<T> values) {return values==null?List.of():values;}
    private <T> void replace(List<T> saved,List<T> replacement) {saved.retainAll(replacement);for(var item:replacement) if(!saved.contains(item)) saved.add(item);}
}
