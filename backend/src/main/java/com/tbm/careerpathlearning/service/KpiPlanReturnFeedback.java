package com.tbm.careerpathlearning.service;

import com.tbm.careerpathlearning.dto.*;
import com.tbm.careerpathlearning.mapper.KpiPlanMapper;
import com.tbm.careerpathlearning.model.KpiPlan;
import java.time.*;
import java.util.*;

/** A single return baseline, not a version history of the plan. */
public final class KpiPlanReturnFeedback {
    private KpiPlanReturnFeedback() {}

    public static void capture(KpiPlan plan, KpiPlanMapper mapper, UUID reviewer, Clock clock) {
        plan.setLatestReturnReason(plan.getReturnReason());
        plan.setLatestReturnedAt(OffsetDateTime.now(clock));
        plan.setLatestReturnedBy(reviewer);
        plan.setLatestReturnItems(plan.getItems().stream().map(mapper::toDto).toList());
    }

    public static List<KpiPlanChangeDto> compare(List<KpiItemDto> previous, List<KpiItemDto> current) {
        if (previous == null) return List.of();
        Map<Long,KpiItemDto> remaining = new LinkedHashMap<>();
        previous.forEach(item -> remaining.put(item.getId(), item));
        List<KpiPlanChangeDto> result = new ArrayList<>();
        for (var item : current) {
            var before = remaining.remove(item.getId());
            var fields = changes(before, item);
            if (before == null || !fields.isEmpty())
                result.add(new KpiPlanChangeDto(before == null ? "ADDED" : "EDITED", item.getId(), item.getName(), fields));
        }
        remaining.values().forEach(item -> result.add(new KpiPlanChangeDto("REMOVED", item.getId(), item.getName(), changes(item, null))));
        return result;
    }

    private static List<KpiPlanChangeDto.FieldChange> changes(KpiItemDto before, KpiItemDto after) {
        var oldFields = fields(before); var newFields = fields(after);
        List<KpiPlanChangeDto.FieldChange> changes = new ArrayList<>();
        oldFields.forEach((key, value) -> {
            if (!Objects.equals(value, newFields.get(key)))
                changes.add(new KpiPlanChangeDto.FieldChange(key, value, newFields.get(key)));
        });
        return changes;
    }

    private static Map<String,String> fields(KpiItemDto item) {
        Map<String,String> fields = new LinkedHashMap<>();
        fields.put("name", item == null ? null : text(item.getName()));
        fields.put("description", item == null ? null : text(item.getDescription()));
        fields.put("perspective", item == null ? null : text(item.getPerspective()));
        fields.put("kra", item == null ? null : text(item.getKra()));
        fields.put("target", item == null ? null : text(item.getTarget()));
        fields.put("measurementUnit", item == null ? null : text(item.getMeasurementUnit()));
        fields.put("weightage", item == null || item.getWeightage() == null ? null : item.getWeightage().stripTrailingZeros().toPlainString());
        for (int point = 1; point <= 5; point++)
            fields.put("point" + point, item == null || item.getScoringDefinitions() == null ? null : text(item.getScoringDefinitions().get(point)));
        return fields;
    }

    private static String text(String value) {
        return value == null || value.isBlank() ? null : value.strip().replaceAll("\\s+", " ");
    }
}
