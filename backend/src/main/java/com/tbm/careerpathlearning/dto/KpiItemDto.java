package com.tbm.careerpathlearning.dto;
import lombok.Data;
import java.math.BigDecimal;
import java.util.*;
@Data
public class KpiItemDto {
    private Long id;
    // Read-only lineage lets revision editors compare an item with its effective baseline.
    @com.fasterxml.jackson.annotation.JsonProperty(access=com.fasterxml.jackson.annotation.JsonProperty.Access.READ_ONLY)
    private Long predecessorId;
    private String name;
    private String description;
    private String perspective;
    private String kra;
    private String target;
    private String measurementUnit;
    private BigDecimal weightage;
    private Map<Integer,String> scoringDefinitions = new TreeMap<>();
}
