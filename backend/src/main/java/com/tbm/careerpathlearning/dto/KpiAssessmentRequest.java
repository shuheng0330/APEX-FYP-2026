package com.tbm.careerpathlearning.dto;
import lombok.Data;
import java.util.*;
@Data
public class KpiAssessmentRequest {
    private Long checkpointId;
    private List<Answer> items=new ArrayList<>();
    @Data public static class Answer {
        private Long assignmentId;
        @com.fasterxml.jackson.databind.annotation.JsonDeserialize(using=PointDeserializer.class)
        private Integer selfPoint;
        private String selfComment;
    }
    public static class PointDeserializer extends com.fasterxml.jackson.databind.JsonDeserializer<Integer> {
        @Override public Integer deserialize(com.fasterxml.jackson.core.JsonParser parser,
                com.fasterxml.jackson.databind.DeserializationContext context) throws java.io.IOException {
            if(parser.currentToken()!=com.fasterxml.jackson.core.JsonToken.VALUE_NUMBER_INT)
                return context.reportInputMismatch(Integer.class,"Assessment Point must be an integer from 1 to 5");
            return parser.getIntValue();
        }
    }
}
