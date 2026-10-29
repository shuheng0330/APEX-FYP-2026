package com.tbm.careerpathlearning.controller;

import com.tbm.careerpathlearning.dto.TrainingSessionReportDto;
import com.tbm.careerpathlearning.service.TokenService;
import com.tbm.careerpathlearning.service.TrainingDownload;
import com.tbm.careerpathlearning.service.TrainingReportService;
import com.tbm.careerpathlearning.service.TrainingTemplateService;
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

import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.startsWith;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Session templates are for staff with Trainer access and HR (FR-12-08 to FR-12-10). A session's report is open to the
 * Super Admin too, so the service, not the route, decides who sees it (FR-12-07).
 */
@WebMvcTest(controllers = {TrainingTemplateController.class, TrainingReportController.class})
@AutoConfigureMockMvc(addFilters = false)
@Import(TrainingTemplateControllerTest.MethodSecurity.class)
class TrainingTemplateControllerTest {

    /** The web slice does not load SecurityConfig, which switches on @PreAuthorize in the app; switch it on here too. */
    @TestConfiguration
    @EnableMethodSecurity
    static class MethodSecurity {
    }

    @Autowired private MockMvc mockMvc;

    @MockitoBean private TrainingTemplateService templates;
    @MockitoBean private TrainingReportService reports;
    @MockitoBean private TokenService tokenService;
    @MockitoBean private ValidationService validationService;

    private final UUID user = UUID.randomUUID();

    @AfterEach
    void signOut() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("Other staff can't list or delete templates")
    void templatesForSchedulers() throws Exception {
        when(templates.list(any())).thenReturn(List.of());

        mockMvc.perform(as(get("/api/trainings/templates"), "ROLE_USER")).andExpect(status().isForbidden());
        mockMvc.perform(as(delete("/api/trainings/templates/4"), "ROLE_USER")).andExpect(status().isForbidden());
        verifyNoInteractions(templates);

        mockMvc.perform(as(get("/api/trainings/templates"), "ROLE_USER", "CAN_MANAGE_TRAINING")).andExpect(status().isOk());
        mockMvc.perform(as(delete("/api/trainings/templates/4"), "ROLE_USER", "CAN_ASSIGN_TRAINING"))
                .andExpect(status().isNoContent());
        verify(templates).list(new TrainingViewer(user, true, false, false));
        verify(templates).delete(4L, new TrainingViewer(user, false, true, false));
    }

    @Test
    @DisplayName("The report goes to the service with the sign-in; its Excel copy downloads, never cached")
    void report() throws Exception {
        when(reports.report(any(), any())).thenReturn(new TrainingSessionReportDto());
        when(reports.reportFile(any(), any())).thenReturn(new TrainingDownload("PK".getBytes(),
                "Session report - Launch - 2026-10-20.xlsx", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"));

        mockMvc.perform(as(get("/api/trainings/7/report"), "ROLE_USER", "CAN_MANAGE_ACCESS_CONTROL")).andExpect(status().isOk());
        mockMvc.perform(as(get("/api/trainings/7/report/file"), "ROLE_USER", "CAN_MANAGE_ACCESS_CONTROL"))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition", startsWith("attachment;")))
                .andExpect(header().string("Cache-Control", containsString("no-store")));
        TrainingViewer superAdmin = new TrainingViewer(user, false, false, true);
        verify(reports).report(7L, superAdmin);
        verify(reports).reportFile(7L, superAdmin);
    }

    /** Signs the request in with the given permissions, the way the JWT filter does. */
    private MockHttpServletRequestBuilder as(MockHttpServletRequestBuilder request, String... authorities) {
        UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(user.toString(), null,
                Arrays.stream(authorities).map(SimpleGrantedAuthority::new).toList());
        SecurityContextHolder.getContext().setAuthentication(auth);
        return request.principal(auth);
    }
}
