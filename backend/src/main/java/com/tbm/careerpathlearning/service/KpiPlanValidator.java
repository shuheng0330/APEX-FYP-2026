package com.tbm.careerpathlearning.service;
import com.tbm.careerpathlearning.dto.KpiItemDto;
import com.tbm.careerpathlearning.exception.BadRequestException;
import org.springframework.stereotype.Component;
import java.math.BigDecimal;
import java.util.*;
@Component
public class KpiPlanValidator {
    public BigDecimal validate(List<KpiItemDto> items, boolean complete) {
        if (items == null) throw new BadRequestException("KPI items are required; use an empty list for an incomplete Draft");
        if (complete && items.isEmpty()) throw new BadRequestException("Add at least one complete KPI before submitting or publishing");
        Set<String> names = new HashSet<>();
        BigDecimal total = BigDecimal.ZERO;
        for (var item : items) {
            if (item == null) throw new BadRequestException("KPI items cannot be null");
            text(item.getName(),255,"KPI Name",complete);
            text(item.getPerspective(),255,"Perspective",false);
            text(item.getKra(),255,"KRA",false);
            text(item.getDescription(),10000,"Description",false);
            text(item.getTarget(),10000,"Target",complete);
            text(item.getMeasurementUnit(),100,"Measurement Unit",false);
            if (item.getName()!=null && !item.getName().isBlank() && !names.add(item.getName().strip().toLowerCase(Locale.ROOT)))
                throw new BadRequestException("KPI names must be unique within the plan");
            var weight=item.getWeightage();
            if (weight!=null && (weight.signum()<0 || weight.compareTo(new BigDecimal("100"))>0 || weight.stripTrailingZeros().scale()>2))
                throw new BadRequestException("KPI weightage must be 0 to 100 with at most two decimal places");
            if (complete && weight==null) throw new BadRequestException("Every KPI requires a weightage");
            if (weight!=null) total=total.add(weight);
            var definitions=item.getScoringDefinitions();
            if (definitions==null) throw new BadRequestException("Scoring definitions are required; use an empty map in Drafts");
            for (var entry:definitions.entrySet()) {
                if (entry.getKey()==null || entry.getKey()<1 || entry.getKey()>5) throw new BadRequestException("Scoring points must be 1 to 5");
                text(entry.getValue(),10000,"Scoring definition",true);
            }
            if (complete && definitions.size()!=5) throw new BadRequestException("Define scoring criteria for all five points (1 to 5) for every KPI");
        }
        if (complete && total.compareTo(new BigDecimal("100"))!=0)
            throw new BadRequestException("KPI item weightages within this plan must total exactly 100%");
        return total;
    }
    private void text(String value,int limit,String label,boolean required) {
        if (required && (value==null || value.isBlank())) throw new BadRequestException(label+" is required");
        if (value!=null && value.length()>limit) throw new BadRequestException(label+" is too long (maximum "+limit+")");
    }
}
