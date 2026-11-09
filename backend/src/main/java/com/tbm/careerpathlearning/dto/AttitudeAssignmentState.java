package com.tbm.careerpathlearning.dto;

import com.tbm.careerpathlearning.enums.AttitudeAssessmentStatus;
import java.time.OffsetDateTime;
import java.util.List;

public record AttitudeAssignmentState(AttitudePeriodConfigurationDto period,boolean canUpdate,
        List<String> blockers,List<Choice> choices,List<Assessment> assessments,List<Activity> activity,boolean replayed) {
    public record Choice(Long id,String name,OffsetDateTime publishedAt,List<String> missingRoleNames) {}
    public record Assessment(Long id,Long participantId,String employeeName,String roleName,String departmentName,
            AttitudeAssessmentStatus status,Long configurationId) {}
    public record Activity(Long id,String previousConfigurationName,String configurationName,
            String actorName,OffsetDateTime recordedAt,String reason) {}
}
