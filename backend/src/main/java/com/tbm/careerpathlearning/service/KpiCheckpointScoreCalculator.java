package com.tbm.careerpathlearning.service;

import com.tbm.careerpathlearning.enums.KpiLevel;
import com.tbm.careerpathlearning.exception.BadRequestException;
import com.tbm.careerpathlearning.model.*;
import org.springframework.stereotype.Component;
import java.math.*;
import java.util.*;

@Component
public class KpiCheckpointScoreCalculator {
    private static final BigDecimal HUNDRED=new BigDecimal("100");

    public BigDecimal calculate(KpiAssessment assessment) {
        var allocation=assessment.getParticipant().getEmployeeLevelConfiguration();
        if(allocation==null) throw new BadRequestException("The recorded KPI allocation is missing");
        Map<KpiLevel,BigDecimal> weights=new EnumMap<>(KpiLevel.class);
        weights.put(KpiLevel.COMPANY,allocation.getCompanyKpiWeight());
        weights.put(KpiLevel.DEPARTMENT,allocation.getDepartmentKpiWeight());
        weights.put(KpiLevel.INDIVIDUAL,allocation.getIndividualKpiWeight());
        BigDecimal allocated=BigDecimal.ZERO;
        for(var weight:weights.values()) {
            requireWeight(weight);allocated=allocated.add(weight);
        }
        if(allocated.compareTo(HUNDRED)!=0) throw new BadRequestException("The recorded KPI allocation must total 100%");
        if(assessment.getItems().isEmpty()) throw new BadRequestException("This assessment has no KPI items to review");
        Map<KpiLevel,BigDecimal> totals=new EnumMap<>(KpiLevel.class);
        BigDecimal score=BigDecimal.ZERO;
        for(var item:assessment.getItems()) {
            if(item.getSuperiorPoint()==null || item.getSuperiorPoint()<1 || item.getSuperiorPoint()>5)
                throw new BadRequestException("Complete every Superior Assessment Point before submitting the review");
            var kpi=item.getAssignment().getKpi();var weight=kpi.getWeightage();requireWeight(weight);
            var level=kpi.getPlan().getLevel();totals.merge(level,weight,BigDecimal::add);
            // All divisors terminate in decimal; round only the final persisted checkpoint score.
            score=score.add(BigDecimal.valueOf(item.getSuperiorPoint()).divide(new BigDecimal("5"))
                    .multiply(weight).divide(HUNDRED).multiply(weights.get(level)));
        }
        for(var level:KpiLevel.values()) {
            var total=totals.get(level);
            if((weights.get(level).signum()>0 || total!=null) && (total==null || total.compareTo(HUNDRED)!=0))
                throw new BadRequestException("The submitted "+level+" KPI item weightages must total 100%");
        }
        return score.setScale(4,RoundingMode.HALF_UP);
    }

    private void requireWeight(BigDecimal weight) {
        if(weight==null || weight.signum()<0 || weight.compareTo(HUNDRED)>0 || weight.stripTrailingZeros().scale()>2)
            throw new BadRequestException("The recorded KPI weights must be 0 to 100 with at most two decimal places");
    }
}
