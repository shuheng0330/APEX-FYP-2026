package com.tbm.careerpathlearning.service;

import com.tbm.careerpathlearning.enums.*;
import com.tbm.careerpathlearning.exception.BadRequestException;
import com.tbm.careerpathlearning.model.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import java.math.BigDecimal;
import static org.junit.jupiter.api.Assertions.*;

class KpiCheckpointScoreCalculatorTest {
    KpiCheckpointScoreCalculator calculator=new KpiCheckpointScoreCalculator();
    KpiAssessment assessment(String company,String department,String individual) {
        var allocation=new ReviewPeriodEmployeeLevelConfiguration();
        allocation.setCompanyKpiWeight(new BigDecimal(company));allocation.setDepartmentKpiWeight(new BigDecimal(department));
        allocation.setIndividualKpiWeight(new BigDecimal(individual));
        var participant=new ReviewPeriodParticipant();participant.setEmployeeLevelConfiguration(allocation);
        var assessment=new KpiAssessment();assessment.setParticipant(participant);return assessment;
    }
    void item(KpiAssessment assessment,KpiLevel level,String weight,Integer superior) {
        var plan=new KpiPlan();plan.setLevel(level);var kpi=new Kpi();kpi.setPlan(plan);kpi.setWeightage(new BigDecimal(weight));
        var assignment=new EmployeeKpiAssignment();assignment.setKpi(kpi);var item=new KpiAssessmentItem();item.setAssignment(assignment);
        item.setSuperiorPoint(superior);item.setSelfPoint(1);assessment.getItems().add(item);
    }
    @ParameterizedTest @CsvSource({"50,30,20,78.0000","35,35,30,79.0000","25,35,40,81.0000",
            "15,25,60,87.0000","10,10,80,94.0000","5,5,90,97.0000"})
    void allSixEmployeeLevelAllocationsUseSuperiorPointsOnly(String company,String department,String individual,String expected) {
        var a=assessment(company,department,individual);item(a,KpiLevel.COMPANY,"100",4);
        item(a,KpiLevel.DEPARTMENT,"100",3);item(a,KpiLevel.INDIVIDUAL,"100",5);
        assertEquals(new BigDecimal(expected),calculator.calculate(a));
        a.getItems().forEach(i->i.setSelfPoint(5));assertEquals(new BigDecimal(expected),calculator.calculate(a));
    }
    @Test void unequalDecimalItemWeightsRoundOnlyAtTheEnd() {
        var a=assessment("15","25","60");item(a,KpiLevel.COMPANY,"33.33",4);item(a,KpiLevel.COMPANY,"66.67",3);
        item(a,KpiLevel.DEPARTMENT,"100",3);item(a,KpiLevel.INDIVIDUAL,"100",5);
        assertEquals(new BigDecimal("84.9999"),calculator.calculate(a));
    }
    @Test void zeroAllocationRequiresNoMissingLevelAndAllFivePointsYields100() {
        var a=assessment("0","0","100");item(a,KpiLevel.INDIVIDUAL,"100",5);
        assertEquals(new BigDecimal("100.0000"),calculator.calculate(a));
        a.getItems().get(0).setSuperiorPoint(1);assertEquals(new BigDecimal("20.0000"),calculator.calculate(a));
    }
    @Test void rejectsInvalidAllocationsWeightsMissingLevelsAndPoints() {
        var a=assessment("15","25","60");item(a,KpiLevel.INDIVIDUAL,"100",5);
        assertThrows(BadRequestException.class,()->calculator.calculate(a));
        item(a,KpiLevel.COMPANY,"100",4);item(a,KpiLevel.DEPARTMENT,"100",null);
        assertThrows(BadRequestException.class,()->calculator.calculate(a));
        a.getItems().get(2).setSuperiorPoint(6);assertThrows(BadRequestException.class,()->calculator.calculate(a));
        a.getItems().get(2).setSuperiorPoint(3);a.getItems().get(0).getAssignment().getKpi().setWeightage(new BigDecimal("99.99"));
        assertThrows(BadRequestException.class,()->calculator.calculate(a));
        a.getItems().get(0).getAssignment().getKpi().setWeightage(new BigDecimal("100"));
        a.getParticipant().getEmployeeLevelConfiguration().setCompanyKpiWeight(new BigDecimal("16"));
        assertThrows(BadRequestException.class,()->calculator.calculate(a));
        a.getParticipant().getEmployeeLevelConfiguration().setCompanyKpiWeight(null);
        assertThrows(BadRequestException.class,()->calculator.calculate(a));
        a.getParticipant().setEmployeeLevelConfiguration(null);assertThrows(BadRequestException.class,()->calculator.calculate(a));
    }
}
