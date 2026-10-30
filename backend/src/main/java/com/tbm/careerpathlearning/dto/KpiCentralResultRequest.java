package com.tbm.careerpathlearning.dto;

import lombok.Data;
import java.util.*;

@Data
public class KpiCentralResultRequest {
    private Long planVersionId;
    private Long checkpointId;
    private Long expectedLockVersion;
    private List<Answer> items=new ArrayList<>();
    @Data public static class Answer {
        private Long kpiId;
        private String actualAchievement;
        @com.fasterxml.jackson.databind.annotation.JsonDeserialize(using=KpiAssessmentRequest.PointDeserializer.class)
        private Integer point;
        private String comment;
    }
}
