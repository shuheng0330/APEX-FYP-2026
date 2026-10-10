package com.tbm.careerpathlearning.dto;

import lombok.Data;
import java.util.*;

@Data
public class AttitudeAssessmentRequest {
    private Long reviewPeriodId;
    private List<Answer> items=new ArrayList<>();
    @Data public static class Answer {
        private Long criterionId;
        @com.fasterxml.jackson.databind.annotation.JsonDeserialize(using=KpiAssessmentRequest.PointDeserializer.class)
        private Integer selfPoint;
        private String selfComment;
    }
}
