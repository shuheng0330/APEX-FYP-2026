package com.tbm.careerpathlearning.dto;
import com.tbm.careerpathlearning.enums.KpiLevel;
import lombok.Data;
import java.util.*;
@Data
public class KpiAssessmentItemDto {
    private Long id;
    private Long assignmentId;
    private KpiLevel level;
    private KpiItemDto kpi;
    private Integer selfPoint;
    private String selfComment;
    private Integer superiorPoint;
    private String superiorComment;
    private List<KpiAssessmentEvidenceDto> evidence=new ArrayList<>();
}
