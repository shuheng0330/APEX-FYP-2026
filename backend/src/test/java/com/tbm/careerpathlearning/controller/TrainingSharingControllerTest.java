package com.tbm.careerpathlearning.controller;

import com.tbm.careerpathlearning.dto.TrainingSessionDto;
import com.tbm.careerpathlearning.dto.TrainingSharingBoardDto;
import com.tbm.careerpathlearning.service.TokenService;
import com.tbm.careerpathlearning.service.TrainingSharingService;
import com.tbm.careerpathlearning.service.TrainingViewer;
import com.tbm.careerpathlearning.service.ValidationService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Import;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Any signed-in staff member opens the Staff Sharing page and asks for Trainer access as themselves (FR-15). */
@WebMvcTest(controllers = TrainingSharingController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import(TrainingSharingControllerTest.MethodSecurity.class)
class TrainingSharingControllerTest {

    /** The web slice does not load SecurityConfig, which switches on @PreAuthorize in the app; switch it on here too. */
    @TestConfiguration
    @EnableMethodSecurity
    static class MethodSecurity {
    }

    @Autowired private MockMvc mockMvc;

    @MockitoBean private TrainingSharingService sharing;
    @MockitoBean private TokenService tokenService;
    @MockitoBean private ValidationService validationService;

    private final UUID user = UUID.randomUUID();

    @AfterEach
    void signOut() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("Staff open the page for a year, and ask for Trainer access as the signed-in person")
    void forAllStaff() throws Exception {
        when(sharing.board(any(), any())).thenReturn(new TrainingSharingBoardDto());
        when(sharing.requestTrainerAccess(any(), any())).thenReturn(new TrainingSessionDto());

        mockMvc.perform(as(get("/api/trainings/sharing").param("year", "2026"))).andExpect(status().isOk());
        mockMvc.perform(as(get("/api/trainings/sharing"))).andExpect(status().isOk());
        mockMvc.perform(as(post("/api/trainings/7/request-trainer-access"))).andExpect(status().isOk());

        TrainingViewer staff = new TrainingViewer(user, false, false, false);
        verify(sharing).board(2026, staff);
        verify(sharing).board(null, staff);
        verify(sharing).requestTrainerAccess(7L, staff);
    }

    /** Signs the request in as an ordinary staff member, the way the JWT filter does. */
    private MockHttpServletRequestBuilder as(MockHttpServletRequestBuilder request) {
        UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(user.toString(), null,
                java.util.List.of(new SimpleGrantedAuthority("ROLE_USER")));
        SecurityContextHolder.getContext().setAuthentication(auth);
        return request.principal(auth);
    }
}
