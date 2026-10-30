package com.tbm.careerpathlearning.dto;

import lombok.Data;
import java.util.*;

@Data
public class AssistedIndividualKpiPlanRequest {
    private List<KpiItemDto> items=new ArrayList<>();
}
