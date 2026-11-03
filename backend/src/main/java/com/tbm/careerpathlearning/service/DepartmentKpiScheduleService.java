package com.tbm.careerpathlearning.service;

import com.tbm.careerpathlearning.dto.*;
import com.tbm.careerpathlearning.enums.*;
import com.tbm.careerpathlearning.exception.BadRequestException;
import com.tbm.careerpathlearning.model.*;
import com.tbm.careerpathlearning.repository.OrgChartRepository;
import org.springframework.stereotype.Service;
import java.util.*;

@Service
public class DepartmentKpiScheduleService {
    private final OrgChartRepository departments;
    private final PerformanceDepartmentResolver resolver;

    public DepartmentKpiScheduleService(OrgChartRepository departments, PerformanceDepartmentResolver resolver) {
        this.departments = departments;
        this.resolver = resolver;
    }

    public void apply(AnnualKpiReviewPeriod period, AnnualKpiReviewPeriodRequest request) {
        if (request.getFrequencyPolicy() != null) {
            if (request.getFrequencyPolicy() == KpiFrequencyPolicy.ROLE_BASED
                    && !period.getDepartmentConfigurations().isEmpty())
                throw new BadRequestException("Remove Draft Department schedules before changing its frequency policy");
            period.setFrequencyPolicy(request.getFrequencyPolicy());
        }
        var supplied = request.getDepartmentConfigurations();
        if (period.getFrequencyPolicy() != KpiFrequencyPolicy.DEPARTMENT_BASED) {
            if (supplied != null && !supplied.isEmpty())
                throw new BadRequestException("Select Department-based frequency before configuring Departments");
            period.getDepartmentConfigurations().clear();
            return;
        }
        if (period.getAssessmentModel() != KpiAssessmentModel.CENTRAL_RESULTS)
            throw new BadRequestException("Department frequency requires the central-results assessment model");
        if (supplied == null) return;
        Set<Long> seen = new HashSet<>();
        var replacement = new ArrayList<ReviewPeriodDepartmentConfiguration>();
        for (var input : supplied) {
            if (input == null || input.getDepartmentId() == null || !seen.add(input.getDepartmentId())
                    || input.getReviewFrequency() == null
                    || !List.of(ReviewFrequency.MONTHLY, ReviewFrequency.QUARTERLY).contains(input.getReviewFrequency()))
                throw new BadRequestException("Each Department requires a unique ID and Monthly or Quarterly frequency");
            var department = departments.findById(input.getDepartmentId())
                    .filter(d -> !d.isDeleted() && d.getType() == OrgChartType.D)
                    .orElseThrow(() -> new BadRequestException("A configured Department does not exist or is deleted"));
            var row = period.getDepartmentConfigurations().stream()
                    .filter(d -> d.getDepartment().getId().equals(department.getId())).findFirst()
                    .orElseGet(ReviewPeriodDepartmentConfiguration::new);
            row.setReviewPeriod(period); row.setDepartment(department); row.setDepartmentName(department.getName());
            row.setReviewFrequency(input.getReviewFrequency()); replacement.add(row);
        }
        period.getDepartmentConfigurations().removeIf(d -> !seen.contains(d.getDepartment().getId()));
        replacement.forEach(d -> { if (!period.getDepartmentConfigurations().contains(d)) period.getDepartmentConfigurations().add(d); });
    }

    public void bindRoleFrequencies(AnnualKpiReviewPeriod period, List<ReviewPeriodRoleConfiguration> roles, boolean publish) {
        if (period.getFrequencyPolicy() != KpiFrequencyPolicy.DEPARTMENT_BASED) return;
        for (var role : roles) {
            OrgChart department;
            try { department = resolver.resolve(role.getRole()); }
            catch (BadRequestException ex) { if (publish) throw ex; else continue; }
            var setting = department == null ? null : period.getDepartmentConfigurations().stream()
                    .filter(d -> d.getDepartment().getId().equals(department.getId())).findFirst().orElse(null);
            if (setting == null) {
                if (publish) throw new BadRequestException("Configure a Department schedule for Job Role: " + role.getRole().getName());
            } else role.setReviewFrequency(setting.getReviewFrequency());
        }
    }

    public void requireUnchanged(AnnualKpiReviewPeriod period, AnnualKpiReviewPeriodRequest request) {
        if (request.getFrequencyPolicy() != null && request.getFrequencyPolicy() != period.getFrequencyPolicy())
            throw new BadRequestException("Published frequency policy cannot change");
        if (request.getDepartmentConfigurations() != null) {
            var saved = period.getDepartmentConfigurations();
            var supplied = request.getDepartmentConfigurations();
            var ids = new HashSet<Long>();
            if (supplied.size() != saved.size() || supplied.stream().anyMatch(d -> d == null
                    || !ids.add(d.getDepartmentId()) || saved.stream().noneMatch(s ->
                    s.getDepartment().getId().equals(d.getDepartmentId()) && s.getReviewFrequency() == d.getReviewFrequency())))
                throw new BadRequestException("Published Department schedules cannot change");
        }
        if (period.getFrequencyPolicy() == KpiFrequencyPolicy.DEPARTMENT_BASED
                && (!Objects.equals(period.getStartDate(), request.getStartDate())
                || !Objects.equals(period.getEndDate(), request.getEndDate())
                || !Objects.equals(period.getSelfAssessmentDaysAfterCheckpoint(), request.getSelfAssessmentDaysAfterCheckpoint())
                || !Objects.equals(period.getSuperiorAssessmentDaysAfterSelfDeadline(), request.getSuperiorAssessmentDaysAfterSelfDeadline())))
            throw new BadRequestException("Published Department checkpoint dates and deadline offsets cannot change");
    }

    public OrgChart department(Role role) { return resolver.resolve(role); }
}
