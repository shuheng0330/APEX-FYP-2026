package com.tbm.careerpathlearning.service;

import com.tbm.careerpathlearning.dto.*;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class KpiPlanReturnFeedbackTest {
    KpiItemDto item(long id,String name) {
        var item=new KpiItemDto();item.setId(id);item.setName(name);item.setWeightage(new BigDecimal("100"));
        item.setTarget("100 units");item.setScoringDefinitions(new TreeMap<>(Map.of(1,"Low",5,"High")));return item;
    }
    @Test void comparesEveryEditableFieldAndFiveDefinitions() {
        var before=item(1,"Sales");var after=item(1,"Revenue");after.setTarget("200 units");
        after.getScoringDefinitions().put(5,"200 units or more");
        var changes=KpiPlanReturnFeedback.compare(List.of(before),List.of(after));
        assertEquals("EDITED",changes.get(0).type());
        assertEquals(List.of("name","target","point5"),changes.get(0).fields().stream().map(KpiPlanChangeDto.FieldChange::field).toList());
        assertEquals("100 units",changes.get(0).fields().get(1).previousValue());
        assertEquals("200 units",changes.get(0).fields().get(1).currentValue());
    }
    @Test void identifiesAddedRemovedAndIgnoresOrderingWhitespaceAndDecimalScale() {
        var a=item(1,"Sales");var same=item(1,"  Sales ");same.setTarget("100  units\n");same.setWeightage(new BigDecimal("100.00"));
        assertTrue(KpiPlanReturnFeedback.compare(List.of(a,item(2,"Customers")),List.of(item(2,"Customers"),same)).isEmpty());
        var changes=KpiPlanReturnFeedback.compare(List.of(a),List.of(item(3,"Delivery")));
        assertEquals(List.of("ADDED","REMOVED"),changes.stream().map(KpiPlanChangeDto::type).toList());
    }
    @Test void missingBaselineDoesNotFabricateChanges() {
        assertTrue(KpiPlanReturnFeedback.compare(null,List.of(item(1,"Sales"))).isEmpty());
    }
}
