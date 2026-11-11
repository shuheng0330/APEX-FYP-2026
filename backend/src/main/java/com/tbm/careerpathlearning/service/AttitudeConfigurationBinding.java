package com.tbm.careerpathlearning.service;

import com.tbm.careerpathlearning.enums.*;
import com.tbm.careerpathlearning.model.AnnualKpiReviewPeriod;
import com.tbm.careerpathlearning.model.AttitudeConfiguration;
import com.tbm.careerpathlearning.model.ReviewPeriodRoleConfiguration;
import com.tbm.careerpathlearning.dto.ReviewPeriodAttitudeConfigurationOption;
import com.tbm.careerpathlearning.exception.BadRequestException;
import com.tbm.careerpathlearning.repository.AttitudeConfigurationRepository;
import org.springframework.stereotype.Service;
import java.util.List;

@Service
public class AttitudeConfigurationBinding {
    private final AttitudeConfigurationRepository configurations;
    public AttitudeConfigurationBinding(AttitudeConfigurationRepository configurations) {this.configurations=configurations;}

    public AttitudeConfiguration published(Long id) {
        if (id == null) return null;
        return configurations.findById(id).filter(c -> c.getStatus() == AttitudeConfigurationStatus.PUBLISHED)
                .orElseThrow(() -> new BadRequestException("Select a published Attitude Evaluation configuration"));
    }

    public List<ReviewPeriodAttitudeConfigurationOption> publishedOptions() {
        return configurations.findAllByOrderByCreatedAtDescIdDesc().stream()
                .filter(c -> c.getStatus() == AttitudeConfigurationStatus.PUBLISHED)
                .map(c -> new ReviewPeriodAttitudeConfigurationOption(c.getId(), c.getName(), c.getPublishedAt(),
                        c.getRoleMappings().stream()
                                .filter(m -> m.getEvaluationFormat() != null
                                        && !AttitudeAssessmentAvailability.criteria(c, m.getEvaluationFormat()).isEmpty())
                                .map(m -> m.getRole().getId()).distinct().toList()))
                .toList();
    }

    public void requireCoverage(AnnualKpiReviewPeriod period, List<ReviewPeriodRoleConfiguration> roles) {
        var selected = period.getAttitudeConfiguration();
        if (selected == null) throw new BadRequestException("Select a published Attitude Evaluation configuration before publishing the Review Period");
        var configuration = published(selected.getId());
        var missing = roles.stream().filter(r -> configuration.getRoleMappings().stream().noneMatch(m ->
                        m.getRole().getId().equals(r.getRole().getId()) && m.getEvaluationFormat() != null
                                && !AttitudeAssessmentAvailability.criteria(configuration, m.getEvaluationFormat()).isEmpty()))
                .map(r -> r.getRole().getName()).distinct().sorted().toList();
        if (!missing.isEmpty()) throw new BadRequestException("Attitude Evaluation formats are missing for participating Job Roles: "
                + String.join(", ", missing));
        period.setAttitudeConfiguration(configuration);
    }

    // Called only by real opening transitions, never by preview or GET endpoints.
    public void bindOnOpening(AnnualKpiReviewPeriod period) {
        if(period.getStatus()==AnnualKpiReviewPeriodStatus.OPEN && period.getAttitudeConfiguration()==null) {
            configurations.findFirstByStatusOrderByPublishedAtDescIdDesc(AttitudeConfigurationStatus.PUBLISHED)
                    .ifPresent(period::setAttitudeConfiguration);
        }
    }
}
