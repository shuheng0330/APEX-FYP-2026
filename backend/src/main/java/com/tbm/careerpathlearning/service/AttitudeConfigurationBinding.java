package com.tbm.careerpathlearning.service;

import com.tbm.careerpathlearning.enums.*;
import com.tbm.careerpathlearning.model.AnnualKpiReviewPeriod;
import com.tbm.careerpathlearning.repository.AttitudeConfigurationRepository;
import org.springframework.stereotype.Service;

@Service
public class AttitudeConfigurationBinding {
    private final AttitudeConfigurationRepository configurations;
    public AttitudeConfigurationBinding(AttitudeConfigurationRepository configurations) {this.configurations=configurations;}

    // Called only by real opening transitions, never by preview or GET endpoints.
    public void bindOnOpening(AnnualKpiReviewPeriod period) {
        if(period.getStatus()==AnnualKpiReviewPeriodStatus.OPEN && period.getAttitudeConfiguration()==null) {
            configurations.findFirstByStatusOrderByPublishedAtDescIdDesc(AttitudeConfigurationStatus.PUBLISHED)
                    .ifPresent(period::setAttitudeConfiguration);
        }
    }
}
