package com.tbm.careerpathlearning.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tbm.careerpathlearning.dto.CourseOwnerDto;
import com.tbm.careerpathlearning.dto.CourseTransferRequest;
import com.tbm.careerpathlearning.dto.TrainerAccessRequest;
import com.tbm.careerpathlearning.dto.TrainerAccessStaffDto;
import com.tbm.careerpathlearning.service.TokenService;
import com.tbm.careerpathlearning.service.TrainerAccessService;
import com.tbm.careerpathlearning.service.ValidationService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Only the Super Admin (CAN_MANAGE_ACCESS_CONTROL) may manage Trainer access (FR-20-01, P1-01). */
@WebMvcTest(controllers = TrainerAccessController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import(TrainerAccessControllerTest.MethodSecurity.class)
class TrainerAccessControllerTest {

    /** The web slice does not load SecurityConfig, which switches on @PreAuthorize in the app; switch it on here too. */
    @TestConfiguration
    @EnableMethodSecurity
    static class MethodSecurity {
    }

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;

    @MockitoBean private TrainerAccessService trainerAccessService;
    @MockitoBean private TokenService tokenService;
    @MockitoBean private ValidationService validationService;

    private final UUID admin = UUID.randomUUID();
    private final UUID staff = UUID.randomUUID();

    @AfterEach
    void signOut() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("The Super Admin can list staff and their Trainer access")
    void superAdminCanList() throws Exception {
        TrainerAccessStaffDto dto = new TrainerAccessStaffDto();
        dto.setName("Nur Aisyah");
        when(trainerAccessService.list()).thenReturn(List.of(dto));

        mockMvc.perform(as(get("/api/trainer-access"), "CAN_MANAGE_ACCESS_CONTROL"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name").value("Nur Aisyah"));
    }

    @Test
    @DisplayName("A grant is made in the name of the signed-in Super Admin")
    void grantUsesTheSignedInAdmin() throws Exception {
        TrainerAccessRequest request = new TrainerAccessRequest();
        request.setReason("Sharing session");
        when(trainerAccessService.grant(eq(staff), any(), eq(admin))).thenReturn(new TrainerAccessStaffDto());

        mockMvc.perform(as(post("/api/trainer-access/" + staff + "/grant"), "CAN_MANAGE_ACCESS_CONTROL")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk());

        verify(trainerAccessService).grant(eq(staff), any(), eq(admin));
    }

    @Test
    @DisplayName("A course transfer is made in the name of the signed-in Super Admin")
    void transferUsesTheSignedInAdmin() throws Exception {
        CourseTransferRequest request = new CourseTransferRequest();
        request.setNewOwnerId(staff);
        request.setReason("The owner left");
        CourseOwnerDto moved = new CourseOwnerDto();
        moved.setTitle("Cash Handling");
        when(trainerAccessService.transferCourse(eq(40L), any(), eq(admin))).thenReturn(moved);

        mockMvc.perform(as(post("/api/trainer-access/courses/40/transfer"), "CAN_MANAGE_ACCESS_CONTROL")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("Cash Handling"));

        verify(trainerAccessService).transferCourse(eq(40L), any(), eq(admin));
    }

    @Test
    @DisplayName("Trainers, HR and other staff are refused every action")
    void othersAreRefused() throws Exception {
        String[] hr = {"ROLE_USER", "CAN_MANAGE_TRAINING", "CAN_ASSIGN_TRAINING", "CAN_VIEW_STAFF"};
        String body = "{\"reason\":\"x\"}";

        mockMvc.perform(as(get("/api/trainer-access"), hr)).andExpect(status().isForbidden());
        mockMvc.perform(as(post("/api/trainer-access/" + staff + "/grant"), hr)
                .contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isForbidden());
        mockMvc.perform(as(post("/api/trainer-access/" + staff + "/revoke"), hr)
                .contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isForbidden());
        mockMvc.perform(as(get("/api/trainer-access/" + staff + "/history"), hr)).andExpect(status().isForbidden());
        mockMvc.perform(as(get("/api/trainer-access/" + staff + "/courses"), hr)).andExpect(status().isForbidden());
        mockMvc.perform(as(get("/api/trainer-access/courses"), hr)).andExpect(status().isForbidden());
        mockMvc.perform(as(post("/api/trainer-access/courses/40/transfer"), hr)
                .contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isForbidden());
        mockMvc.perform(as(get("/api/trainer-access/courses/40/history"), hr)).andExpect(status().isForbidden());

        verifyNoInteractions(trainerAccessService);
    }

    /** Signs the request in as the admin with the given permissions, the way the JWT filter does. */
    private MockHttpServletRequestBuilder as(MockHttpServletRequestBuilder request, String... authorities) {
        UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(admin.toString(), null,
                Arrays.stream(authorities).map(SimpleGrantedAuthority::new).toList());
        SecurityContextHolder.getContext().setAuthentication(auth);
        return request.principal(auth);
    }
}
