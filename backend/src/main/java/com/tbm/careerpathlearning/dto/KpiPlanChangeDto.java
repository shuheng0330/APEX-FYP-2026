package com.tbm.careerpathlearning.dto;

import java.util.List;

public record KpiPlanChangeDto(String type, Long itemId, String itemName, List<FieldChange> fields) {
    public record FieldChange(String field, String previousValue, String currentValue) {}
}
