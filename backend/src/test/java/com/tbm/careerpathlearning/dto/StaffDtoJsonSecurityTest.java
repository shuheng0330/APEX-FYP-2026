package com.tbm.careerpathlearning.dto;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class StaffDtoJsonSecurityTest {
    private final ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();

    @Test
    void omitsPasswordsAtEveryManagerDepthWithoutMutatingAuthenticationData() throws Exception {
        StaffDto director = staff("Adrian Lim", "director-test-hash");
        StaffDto manager = staff("Alice Johnson", "manager-test-hash");
        StaffDto employee = staff("Nina Patel", "employee-test-hash");
        manager.setManager(director);
        employee.setManager(manager);

        JsonNode json = mapper.readTree(mapper.writeValueAsString(Map.of("staffs", Map.of("209", new StaffDto[]{employee}))));
        JsonNode result = json.path("staffs").path("209").get(0);

        assertFalse(result.has("password"));
        assertFalse(result.path("manager").has("password"));
        assertFalse(result.path("manager").path("manager").has("password"));
        assertEquals("Nina Patel", result.path("name").asText());
        assertEquals("Alice Johnson", result.path("manager").path("name").asText());
        assertEquals("Adrian Lim", result.path("manager").path("manager").path("name").asText());
        assertEquals("employee-test-hash", employee.getPassword());
        assertEquals("manager-test-hash", manager.getPassword());
        assertEquals("director-test-hash", director.getPassword());
    }

    @Test
    void retainsPasswordInputAndDoesNotSerializeItBack() throws Exception {
        StaffDto result = mapper.readValue("{\"name\":\"Test Staff\",\"password\":\"input-test-value\"}", StaffDto.class);
        assertEquals("input-test-value", result.getPassword());
        assertFalse(mapper.readTree(mapper.writeValueAsString(result)).has("password"));
    }

    private StaffDto staff(String name, String password) {
        StaffDto staff = new StaffDto();
        staff.setId(UUID.randomUUID());
        staff.setName(name);
        staff.setPassword(password);
        return staff;
    }
}
