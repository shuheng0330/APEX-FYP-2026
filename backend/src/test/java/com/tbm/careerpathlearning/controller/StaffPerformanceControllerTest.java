package com.tbm.careerpathlearning.controller;

import com.tbm.careerpathlearning.config.SecurityConfig;
import com.tbm.careerpathlearning.dto.StaffPerformanceDto;
import com.tbm.careerpathlearning.enums.*;
import com.tbm.careerpathlearning.service.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.MessageSource;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest({StaffPerformanceController.class, AnnualAppraisalController.class})
@Import(SecurityConfig.class)
@TestPropertySource(properties = "frontend.origin=http://localhost:4200")
class StaffPerformanceControllerTest {
    @Autowired MockMvc mvc;
    @MockitoBean StaffPerformanceService service;
    @MockitoBean AnnualAppraisalService appraisalService;
    @MockitoBean TokenService tokens;
    @MockitoBean ValidationService validation;
    @MockitoBean MessageSource messages;
    final UUID actor = UUID.randomUUID();

    @BeforeEach void messages() {
        when(messages.getMessage(anyString(), any(), any(Locale.class))).thenReturn("Unavailable");
    }

    @Test void routesForwardAuthenticatedIdentityAndValidatedRecordContext() throws Exception {
        var auth = authentication(user("ROLE_USER"));
        mvc.perform(get("/api/annual-performance/staff/" + actor + "/periods").with(auth)).andExpect(status().isOk());
        mvc.perform(get("/api/annual-performance/staff/" + actor + "?reviewPeriodId=1").with(auth)).andExpect(status().isOk());
        mvc.perform(get("/api/annual-performance/staff/" + actor + "?reviewPeriodId=1&historyYears=3").with(auth)).andExpect(status().isOk());
        mvc.perform(get("/api/annual-performance/participants/11/checkpoints/101").with(auth)).andExpect(status().isOk());
        mvc.perform(get("/api/annual-performance/participants/11/attitude").with(auth)).andExpect(status().isOk());
        mvc.perform(get("/api/annual-performance/participants/11/appraisals/30").with(auth)).andExpect(status().isOk());
        verify(service).periods(actor, actor); verify(service).dashboard(actor, 1L, 5, actor); verify(service).dashboard(actor, 1L, 3, actor);
        verify(service).checkpoint(11L, 101L, actor); verify(service).attitude(11L, actor); verify(service).appraisal(11L, 30L, actor);
    }

    @Test void employeeOutcomeResponseContainsOnlyTheApprovedProjectionAndRecordedScores() throws Exception {
        var period = new StaffPerformanceDto.Period(11L, 1L, "Annual Review", LocalDate.of(2026, 1, 1),
                LocalDate.of(2026, 12, 31), AnnualKpiReviewPeriodStatus.CLOSED, ReviewFrequency.QUARTERLY,
                KpiFrequencyPolicy.DEPARTMENT_BASED, KpiAssessmentModel.CENTRAL_RESULTS);
        var scores = new StaffPerformanceDto.Scores(BigDecimal.ZERO, new BigDecimal("80"), new BigDecimal("40"),
                new BigDecimal("50"), new BigDecimal("50"), AnnualKpiConsolidationMethod.AVERAGE);
        var outcome = new StaffPerformanceDto.ApprovedAppraisal(30L, period, AppraisalStatus.APPROVED,
                AnnualAppraisalRecommendation.NO_RECOMMENDATION, OffsetDateTime.parse("2026-10-29T09:00:00+08:00"),
                scores, StaffPerformanceDto.RecordedComparison.UNCHANGED, null);
        when(service.appraisal(11L, 30L, actor)).thenReturn(outcome);
        mvc.perform(get("/api/annual-performance/participants/11/appraisals/30").with(authentication(user("ROLE_USER"))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("APPROVED"))
                .andExpect(jsonPath("$.finalDecision").value("NO_RECOMMENDATION"))
                .andExpect(jsonPath("$.approvedAt").exists()).andExpect(jsonPath("$.recordedScores.annualKpiScore").value(0))
                .andExpect(jsonPath("$.internalDetails").doesNotExist()).andExpect(jsonPath("$.recommendation").doesNotExist())
                .andExpect(jsonPath("$.justification").doesNotExist()).andExpect(jsonPath("$.overrideReason").doesNotExist())
                .andExpect(jsonPath("$.latestReturnReason").doesNotExist()).andExpect(jsonPath("$.snapshot").doesNotExist());
    }

    @Test void newViewingPermissionsNeverAuthoriseAppraisalActions() throws Exception {
        for (var permission : List.of(StaffPerformanceAccess.TEAM, StaffPerformanceAccess.DEPARTMENT, StaffPerformanceAccess.ORGANISATION)) {
            var auth = authentication(user(permission));
            mvc.perform(get("/api/annual-performance/staff/" + actor + "/periods").with(auth)).andExpect(status().isOk());
            for (var action : List.of("approve", "override-approve", "return", "submit"))
                mvc.perform(post("/api/annual-appraisals/30/" + action).with(auth).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"expectedLockVersion\":0}")).andExpect(status().isForbidden());
            mvc.perform(post("/api/annual-appraisals").with(auth).contentType(MediaType.APPLICATION_JSON)
                    .content("{\"participantId\":11}")).andExpect(status().isForbidden());
        }
        verifyNoInteractions(appraisalService);
    }

    @Test void organisationScopeNeedsAnExplicitPermissionRatherThanAnAdminRoleName() throws Exception {
        for (var permission : List.of("ROLE_SUPER_ADMIN", "CAN_MANAGE_EVALUATION", "CAN_VIEW_STAFF", "CAN_REVIEW_ANNUAL_APPRAISAL"))
            mvc.perform(get("/api/annual-performance/staff/" + actor + "/periods").with(authentication(user(permission))))
                    .andExpect(status().isForbidden());
        verifyNoInteractions(service);
    }

    @Test void serviceScopeDenialsRemainForbiddenAndDoNotLeakData() throws Exception {
        when(service.attitude(11L, actor)).thenThrow(new AccessDeniedException("outside scope"));
        mvc.perform(get("/api/annual-performance/participants/11/attitude").with(authentication(user("ROLE_USER"))))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.criteria").doesNotExist());
    }

    @Test void unauthenticatedMalformedAndMissingContextRequestsAreRejected() throws Exception {
        mvc.perform(get("/api/annual-performance/staff/" + actor + "/periods")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/annual-performance/staff/not-a-uuid/periods").with(authentication(user("ROLE_USER"))))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/api/annual-performance/staff/" + actor).with(authentication(user("ROLE_USER"))))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(service);
    }

    private UsernamePasswordAuthenticationToken user(String... permissions) {
        return new UsernamePasswordAuthenticationToken(actor, null,
                Arrays.stream(permissions).map(SimpleGrantedAuthority::new).toList());
    }
}
