package com.tbm.careerpathlearning.service;
import com.tbm.careerpathlearning.dto.*;
import com.tbm.careerpathlearning.mapper.KpiPlanMapper;
import com.tbm.careerpathlearning.model.KpiPlanVersion;
import java.util.*;

/** Compare copied items using their immediate predecessor, not their newly generated IDs. */
public final class KpiVersionComparison {
    private KpiVersionComparison() {}
    public static List<KpiPlanChangeDto> compare(KpiPlanVersion revision,KpiPlanMapper mapper) {
        if(revision.getBaseVersion()==null) return List.of();
        var before=revision.getBaseVersion().getItems().stream().map(mapper::toDto).toList();
        var actualIds=new HashMap<Long,Long>();
        var after=revision.getItems().stream().map(k->{
            var dto=mapper.toDto(k);
            // Negative IDs cannot collide with persisted predecessor IDs for added items.
            var key=k.getPredecessorId()==null?-k.getId():k.getPredecessorId();
            actualIds.put(key,k.getId());dto.setId(key);return dto;
        }).toList();
        return KpiPlanReturnFeedback.compare(before,after).stream().map(c->new KpiPlanChangeDto(c.type(),
            actualIds.getOrDefault(c.itemId(),c.itemId()),c.itemName(),c.fields())).toList();
    }
}
