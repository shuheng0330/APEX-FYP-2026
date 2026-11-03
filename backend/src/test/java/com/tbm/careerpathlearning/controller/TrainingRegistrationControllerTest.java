package com.tbm.careerpathlearning.controller;

import com.tbm.careerpathlearning.dto.TrainingSessionDto;
import com.tbm.careerpathlearning.service.TokenService;
import com.tbm.careerpathlearning.service.TrainingRegistrationService;
import com.tbm.careerpathlearning.service.TrainingService;
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
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.util.Arrays;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Staff register, cancel and answer for themselves only: the person always comes from the sign-in, and the request
 * names nobody (old problem 2). Only staff with Trainer access, and HR, change who is on a session.
 */
@WebMvcTest(controllers = TrainingRegistrationController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import(TrainingRegistrationControllerTest.MethodSecurity.class)
class TrainingRegistrationControllerTest {

    /** The web slice does not load SecurityConfig, which switches on @PreAuthorize in the app; switch it on here too. */
    @TestConfiguration
    @EnableMethodSecurity
    static class MethodSecurity {
    }

    @Autowired private MockMvc mockMvc;

    @MockitoBean private TrainingRegistrationService registrations;
    @MockitoBean private TrainingService trainingService;
    @MockitoBean private TokenService tokenService;
    @MockitoBean private ValidationService validationService;

    private final UUID user = UUID.randomUUID();

    @AfterEach
    void signOut() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("A staff member registers, cancels and accepts as themselves, whatever the request says")
    void staffActForThemselves() throws Exception {
        when(trainingService.get(any(), any())).thenReturn(new TrainingSessionDto());
        TrainingViewer me = new TrainingViewer(user, false, false, false);
        String body = "{\"staffId\":\"" + UUID.randomUUID() + "\",\"branch\":\"Bangsar Village\",\"pdpaConsent\":true}";

        mockMvc.perform(as(post("/api/trainings/7/register"), "ROLE_USER").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk());
        mockMvc.perform(as(post("/api/trainings/7/cancel-registration"), "ROLE_USER")).andExpect(status().isOk());
        mockMvc.perform(as(post("/api/trainings/7/accept-offer"), "ROLE_USER")).andExpect(status().isOk());

        verify(registrations).register(eq(7L), any(), eq(me));
        verify(registrations).cancelMine(7L, me);
        verify(registrations).acceptOffer(7L, me);
    }

    @Test
    @DisplayName("Other staff can't add, remove or offer seats to anyone (FR-12-05)")
    void onlyTrainersAndHrChangeWhoIsOn() throws Exception {
        String body = "{\"staffId\":\"" + UUID.randomUUID() + "\"}";

        mockMvc.perform(as(post("/api/trainings/7/people"), "ROLE_USER").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isForbidden());
        mockMvc.perform(as(post("/api/trainings/7/people/3/remove"), "ROLE_USER")).andExpect(status().isForbidden());
        mockMvc.perform(as(post("/api/trainings/7/people/3/offer"), "ROLE_USER")).andExpect(status().isForbidden());
        verifyNoInteractions(registrations);

        mockMvc.perform(as(post("/api/trainings/7/people"), "ROLE_USER", "CAN_ASSIGN_TRAINING")
                .contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isOk());
        verify(registrations).add(eq(7L), any(), eq(new TrainingViewer(user, false, true, false)));
    }

    /** Signs the request in with the given permissions, the way the JWT filter does. */
    private MockHttpServletRequestBuilder as(MockHttpServletRequestBuilder request, String... authorities) {
        UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(user.toString(), null,
                Arrays.stream(authorities).map(SimpleGrantedAuthority::new).toList());
        SecurityContextHolder.getContext().setAuthentication(auth);
        return request.principal(auth);
    }
}
