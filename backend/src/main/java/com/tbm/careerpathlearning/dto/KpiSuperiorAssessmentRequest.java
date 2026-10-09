package com.tbm.careerpathlearning.dto;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import lombok.Data;
import java.util.*;

@Data
public class KpiSuperiorAssessmentRequest {
    private List<Answer> items=new ArrayList<>();

    @Data public static class Answer {
        private Long itemId;
        @JsonDeserialize(using=KpiAssessmentRequest.PointDeserializer.class)
        private Integer superiorPoint;
        private String superiorComment;
    }
}
