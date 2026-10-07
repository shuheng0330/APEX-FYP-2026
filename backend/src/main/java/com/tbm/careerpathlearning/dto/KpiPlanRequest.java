package com.tbm.careerpathlearning.dto;
import lombok.Data;
import java.util.*;
@Data
public class KpiPlanRequest {
    private Long reviewPeriodId;
    private Long departmentId;
    private Long ownerParticipantId;
    private List<KpiItemDto> items;
}
