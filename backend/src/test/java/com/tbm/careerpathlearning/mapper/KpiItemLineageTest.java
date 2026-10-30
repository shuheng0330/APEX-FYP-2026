package com.tbm.careerpathlearning.mapper;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tbm.careerpathlearning.dto.KpiItemDto;
import com.tbm.careerpathlearning.model.Kpi;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;
import static org.junit.jupiter.api.Assertions.*;

class KpiItemLineageTest {
    @Test void responseExposesLineageButRequestCannotChangeIt() throws Exception {
        var mapper = Mappers.getMapper(KpiPlanMapper.class);
        var item = new Kpi();
        item.setId(20L); item.setPredecessorId(10L); item.setTarget("100 units");
        var json = new ObjectMapper();
        var response = json.readTree(json.writeValueAsString(mapper.toDto(item)));
        assertEquals(10L, response.get("predecessorId").asLong());
        var request = json.readValue("{\"predecessorId\":999,\"target\":\"200 units\"}", KpiItemDto.class);
        assertNull(request.getPredecessorId());
        mapper.update(request, item);
        assertEquals(10L, item.getPredecessorId());
        assertEquals("200 units", item.getTarget());
    }
}
