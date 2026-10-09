package com.tbm.careerpathlearning.dto;

import com.tbm.careerpathlearning.enums.*;
import lombok.Data;
import java.time.OffsetDateTime;
import java.util.*;

@Data
public class AttitudeConfigurationDto {
    private Long id;
    private String name;
    private AttitudeConfigurationStatus status;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;
    private UUID createdBy;
    private UUID updatedBy;
    private OffsetDateTime publishedAt;
    private UUID publishedBy;
    private List<Criterion> criteria;
    private List<AttitudeConfigurationRequest.Rating> ratingDefinitions;
    private List<RoleMapping> roleMappings;
    @Data public static class Criterion {
        private Long id;
        private String name;
        private String description;
        private AttitudeCriterionType criterionType;
        private AttitudeEvaluationFormat evaluationFormat;
        private boolean active;
        private int displayOrder;
    }
    @Data public static class RoleMapping {
        private Long roleId;
        private String roleName;
        private AttitudeEvaluationFormat evaluationFormat;
    }
}
