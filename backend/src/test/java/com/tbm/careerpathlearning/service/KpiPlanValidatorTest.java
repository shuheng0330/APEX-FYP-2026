package com.tbm.careerpathlearning.service;
import com.tbm.careerpathlearning.dto.KpiItemDto;
import com.tbm.careerpathlearning.exception.BadRequestException;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
public class KpiPlanValidatorTest {
    private final KpiPlanValidator validator=new KpiPlanValidator();
    @Test void incompleteDraftIsAllowed() {assertEquals(BigDecimal.ZERO,validator.validate(List.of(new KpiItemDto()),false));}
    @Test void exactDecimalTotal() {var a=item("A","33.33");var b=item("B","66.67");assertEquals(new BigDecimal("100.00"),validator.validate(List.of(a,b),true));}
    @Test void rejectsIncompletePublication() {assertThrows(BadRequestException.class,()->validator.validate(List.of(new KpiItemDto()),true));}
    @Test void rejectsIncorrectTotal() {assertThrows(BadRequestException.class,()->validator.validate(List.of(item("A","99.99")),true));}
    @Test void rejectsDuplicatesIgnoringCaseAndSpaces() {assertThrows(BadRequestException.class,()->validator.validate(List.of(item("A","50"),item(" a ","50")),false));}
    @Test void rejectsZeroPoint() {var a=item("A","100");a.getScoringDefinitions().put(0,"Zero");assertThrows(BadRequestException.class,()->validator.validate(List.of(a),false));}
    @Test void rejectsMoreThanTwoDecimals() {assertThrows(BadRequestException.class,()->validator.validate(List.of(item("A","1.001")),false));}
    @Test void requiresAllFiveCriteria() {var a=item("A","100");a.getScoringDefinitions().remove(5);assertThrows(BadRequestException.class,()->validator.validate(List.of(a),true));}
    @Test void completeUiItemDoesNotRequireHiddenMeasurementUnitOrDescription() {
        var a=item("A","100");a.setPerspective("Financial");a.setKra("Revenue growth");
        a.setMeasurementUnit(null);a.setDescription(null);
        assertEquals(new BigDecimal("100"),validator.validate(List.of(a),true));
    }
    @Test void validatesRetainedMeasurementUnitWhenProvided() {
        var a=item("A","100");a.setMeasurementUnit("x".repeat(101));
        assertThrows(BadRequestException.class,()->validator.validate(List.of(a),true));
    }
    public static KpiItemDto item(String name,String weight) {
        var i=new KpiItemDto();i.setName(name);i.setTarget("8% growth");i.setMeasurementUnit("%");i.setWeightage(new BigDecimal(weight));
        for(int p=1;p<=5;p++) i.getScoringDefinitions().put(p,"Criterion "+p);
        return i;
    }
}
