package com.tbm.careerpathlearning.service;

import com.tbm.careerpathlearning.enums.*;
import com.tbm.careerpathlearning.model.*;
import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

/** Pure bound-configuration/calendar checks shared by the secured API and internal notification scan. */
public final class AttitudeAssessmentAvailability {
    private static final String MISSING_CONFIGURATION="The Attitude Evaluation criteria have not been configured for this Annual Review Period. Please check again later.";
    private AttitudeAssessmentAvailability() {}

    public static String configurationProblem(ReviewPeriodParticipant p) {
        var config=p.getReviewPeriod().getAttitudeConfiguration();
        if(config==null || config.getStatus()!=AttitudeConfigurationStatus.PUBLISHED) return MISSING_CONFIGURATION;
        var format=format(p);
        if(format==null) return "An Attitude Evaluation format has not been assigned to your Job Role for this Annual Review Period. Please contact your administrator.";
        return criteria(config,format).isEmpty()?MISSING_CONFIGURATION:null;
    }
    public static String openingProblem(LocalDate availableFrom,LocalDate today) {
        if(availableFrom==null) return "The Attitude Self-Assessment opening date has not been configured. Please contact your administrator.";
        return today.isBefore(availableFrom)?"Attitude Self-Assessment opens on "+availableFrom+".":null;
    }
    public static AttitudeEvaluationFormat format(ReviewPeriodParticipant p) {
        if(p.getRole()==null || p.getReviewPeriod().getAttitudeConfiguration()==null) return null;
        return p.getReviewPeriod().getAttitudeConfiguration().getRoleMappings().stream()
                .filter(m->Objects.equals(m.getRole().getId(),p.getRole().getId()))
                .map(AttitudeRoleFormatMapping::getEvaluationFormat).findFirst().orElse(null);
    }
    public static List<AttitudeCriterion> criteria(AttitudeConfiguration config,AttitudeEvaluationFormat format) {
        return config.getCriteria().stream().filter(AttitudeCriterion::isActive)
                .filter(c->c.getCriterionType()==AttitudeCriterionType.SHARED_CORE_VALUE || c.getEvaluationFormat()==format)
                .sorted(Comparator.comparingInt(AttitudeCriterion::getDisplayOrder).thenComparing(AttitudeCriterion::getId)).toList();
    }
    public static boolean actionable(ReviewPeriodParticipant p,AttitudeAssessment saved,LocalDate today) {
        var period=p.getReviewPeriod();var manager=p.getStaff().getManager();
        if(period.getStatus()!=AnnualKpiReviewPeriodStatus.OPEN || period.getAttitudeSelfAssessmentDeadline()==null
                || openingProblem(period.getAttitudeSelfAssessmentStartDate(),today)!=null || configurationProblem(p)!=null
                || manager==null || Objects.equals(manager.getId(),p.getStaff().getId()) || manager.isDeleted()
                || manager.getAccountStatus()!=StaffAccountStatus.ACTIVE) return false;
        if(saved==null) return true;
        if(saved.getStatus()!=AttitudeAssessmentStatus.DRAFT || !Objects.equals(saved.getParticipant().getId(),p.getId())
                || saved.getConfiguration()==null || !Objects.equals(saved.getConfiguration().getId(),period.getAttitudeConfiguration().getId())
                || saved.getEvaluationFormat()!=format(p)) return false;
        var expected=criteria(saved.getConfiguration(),saved.getEvaluationFormat()).stream().map(AttitudeCriterion::getId).collect(Collectors.toSet());
        return saved.getItems().size()==expected.size()
                && expected.equals(saved.getItems().stream().map(i->i.getCriterion().getId()).collect(Collectors.toSet()));
    }
}
