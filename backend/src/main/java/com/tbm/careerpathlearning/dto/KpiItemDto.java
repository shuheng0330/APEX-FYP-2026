package com.tbm.careerpathlearning.dto;
import lombok.Data;
import java.math.BigDecimal;
import java.util.*;
@Data
public class KpiItemDto {
    private Long id;
    private String name;
    private String description;
    private String perspective;
    private String kra;
    private String target;
    private String measurementUnit;
    private BigDecimal weightage;
    private Map<Integer,String> scoringDefinitions = new TreeMap<>();
}
