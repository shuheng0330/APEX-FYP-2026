package com.tbm.careerpathlearning.dto;

import com.tbm.careerpathlearning.enums.*;
import lombok.Data;
import java.util.*;

@Data
public class AttitudeConfigurationRequest {
    private String name;
    private List<Criterion> criteria=new ArrayList<>();
    private List<Rating> ratingDefinitions=new ArrayList<>();
    private List<RoleMapping> roleMappings=new ArrayList<>();
    @Data public static class Criterion {
        private Long id;
        private String name;
        private String description;
        private AttitudeCriterionType criterionType;
        private AttitudeEvaluationFormat evaluationFormat;
        private boolean active=true;
    }
    @Data public static class Rating {
        @com.fasterxml.jackson.databind.annotation.JsonDeserialize(using=KpiAssessmentRequest.PointDeserializer.class)
        private Integer point;
        private String label;
        private String description;
    }
    @Data public static class RoleMapping {
        private Long roleId;
        private AttitudeEvaluationFormat evaluationFormat;
    }
}
