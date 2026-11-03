package com.tbm.careerpathlearning.controller;

import com.tbm.careerpathlearning.config.SecurityConfig;
import com.tbm.careerpathlearning.dto.*;
import com.tbm.careerpathlearning.enums.AnnualKpiConsolidationMethod;
import com.tbm.careerpathlearning.service.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.MessageSource;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import java.util.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(AnnualAppraisalController.class) @Import(SecurityConfig.class)
@TestPropertySource(properties="frontend.origin=http://localhost:4200")
class AnnualAppraisalControllerTest {
    @Autowired MockMvc mvc;
    @MockitoBean AnnualAppraisalService service;
    @MockitoBean TokenService tokens;
    @MockitoBean ValidationService validation;
    @MockitoBean MessageSource messages;
    UUID actor=UUID.randomUUID();
    UsernamePasswordAuthenticationToken user(String... authorities) {
        return new UsernamePasswordAuthenticationToken(actor,null,Arrays.stream(authorities).map(SimpleGrantedAuthority::new).toList());
    }
    @Test void superiorCanUseTeamDraftAndSubmissionContracts() throws Exception {
        var auth=authentication(user("CAN_SUBMIT_ANNUAL_APPRAISAL"));
        mvc.perform(get("/api/annual-appraisals/periods").with(auth)).andExpect(status().isOk());
        mvc.perform(get("/api/annual-appraisals/team?reviewPeriodId=1").with(auth)).andExpect(status().isOk());
        mvc.perform(get("/api/annual-appraisals/participants/7/readiness").with(auth)).andExpect(status().isOk());
        mvc.perform(post("/api/annual-appraisals").with(auth).contentType(MediaType.APPLICATION_JSON)
                .content("{\"participantId\":7,\"recommendation\":null,\"justification\":null}")).andExpect(status().isCreated());
        mvc.perform(put("/api/annual-appraisals/10").with(auth).contentType(MediaType.APPLICATION_JSON)
                .content("{\"participantId\":7,\"expectedLockVersion\":0,\"recommendation\":\"NO_RECOMMENDATION\",\"justification\":\"Reason\"}")).andExpect(status().isOk());
        mvc.perform(post("/api/annual-appraisals/10/submit").with(auth).contentType(MediaType.APPLICATION_JSON)
                .content("{\"expectedLockVersion\":1}")).andExpect(status().isOk());
        verify(service).team(1L,actor);verify(service).readiness(7L,actor);
        verify(service).submit(eq(10L),eq(new AnnualAppraisalActionRequest(1L,null,null)),eq(actor));
    }
    @Test void reviewerCanReadQueueApproveOverrideAndReturn() throws Exception {
        var auth=authentication(user("CAN_REVIEW_ANNUAL_APPRAISAL"));
        mvc.perform(get("/api/annual-appraisals/periods?hrReview=true").with(auth)).andExpect(status().isOk());
        mvc.perform(get("/api/annual-appraisals/reviews?reviewPeriodId=1").with(auth)).andExpect(status().isOk());
        for(var action:List.of("approve","override-approve","return")) {
            mvc.perform(post("/api/annual-appraisals/10/"+action).with(auth).contentType(MediaType.APPLICATION_JSON)
                    .content("{\"expectedLockVersion\":1,\"finalDecision\":\"PROMOTION\",\"reason\":\"Reason\"}")).andExpect(status().isOk());
        }
        verify(service).periods(true,actor);verify(service).reviews(1L,actor);
    }
    @Test void broadLegacyAndSuperAdminAuthoritiesDoNotInferNewBusinessPermissions() throws Exception {
        when(messages.getMessage(anyString(),any(),any(Locale.class))).thenReturn("Forbidden");
        for(var authority:List.of("ROLE_USER","ROLE_SUPER_ADMIN","CAN_MANAGE_EVALUATION","CAN_MANAGE_EVALUATION_CYCLE",
                "CAN_REVIEW_KPI_ASSESSMENT","CAN_REVIEW_ATTITUDE_EVALUATION")) {
            var auth=authentication(user(authority));
            mvc.perform(get("/api/annual-appraisals/10").with(auth)).andExpect(status().isForbidden());
            mvc.perform(get("/api/annual-appraisals/periods").with(auth)).andExpect(status().isForbidden());
        }
        verifyNoInteractions(service);
    }
    @Test void superiorAndReviewerWritesAreNotInterchangeable() throws Exception {
        when(messages.getMessage(anyString(),any(),any(Locale.class))).thenReturn("Forbidden");
        mvc.perform(post("/api/annual-appraisals/10/approve").with(authentication(user("CAN_SUBMIT_ANNUAL_APPRAISAL")))
                .contentType(MediaType.APPLICATION_JSON).content("{\"expectedLockVersion\":0}")).andExpect(status().isForbidden());
        mvc.perform(post("/api/annual-appraisals").with(authentication(user("CAN_REVIEW_ANNUAL_APPRAISAL")))
                .contentType(MediaType.APPLICATION_JSON).content("{\"participantId\":7}")).andExpect(status().isForbidden());
        verifyNoInteractions(service);
    }
    @Test void combinedPermissionsRetainBothWorkspaces() throws Exception {
        var auth=authentication(user("CAN_SUBMIT_ANNUAL_APPRAISAL","CAN_REVIEW_ANNUAL_APPRAISAL"));
        mvc.perform(get("/api/annual-appraisals/team?reviewPeriodId=1").with(auth)).andExpect(status().isOk());
        mvc.perform(get("/api/annual-appraisals/reviews?reviewPeriodId=1").with(auth)).andExpect(status().isOk());
    }
    @Test void unauthenticatedRequestsAndInvalidRecommendationAreRejected() throws Exception {
        mvc.perform(get("/api/annual-appraisals/periods")).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/annual-appraisals").with(authentication(user("CAN_SUBMIT_ANNUAL_APPRAISAL")))
                .contentType(MediaType.APPLICATION_JSON).content("{\"participantId\":7,\"recommendation\":\"MADE_UP\"}"))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(service);
    }

    @Test void performanceHistoryReturnsOnlyTheLightweightPerformanceContractForEitherPermission() throws Exception {
        var result=new AnnualPerformanceHistoryDto(UUID.randomUUID(),8L,2L,"[UAT SIMULATED] 2024 Annual Review",
                LocalDate.of(2024,1,1),LocalDate.of(2024,12,31),new BigDecimal("77.0000"),new BigDecimal("80.0000"),
                new BigDecimal("78.5000"),new BigDecimal("50"),new BigDecimal("50"),AnnualKpiConsolidationMethod.AVERAGE,
                AnnualPerformanceHistoryDto.SourceType.SUBMISSION_SNAPSHOT,AnnualPerformanceHistoryDto.ComparisonStatus.DIFFERENT);
        when(service.performanceHistory(7L,actor)).thenReturn(List.of(result));
        for(var authority:List.of("CAN_SUBMIT_ANNUAL_APPRAISAL","CAN_REVIEW_ANNUAL_APPRAISAL")) {
            mvc.perform(get("/api/annual-appraisals/participants/7/performance-history").with(authentication(user(authority))))
                    .andExpect(status().isOk()).andExpect(jsonPath("$[0].reviewPeriodId").value(2))
                    .andExpect(jsonPath("$[0].periodName").value(result.periodName()))
                    .andExpect(jsonPath("$[0].startDate").value("2024-01-01"))
                    .andExpect(jsonPath("$[0].endDate").value("2024-12-31"))
                    .andExpect(jsonPath("$[0].finalScore").value(78.5))
                    .andExpect(jsonPath("$[0].sourceType").value("SUBMISSION_SNAPSHOT"))
                    .andExpect(jsonPath("$[0].comparisonStatus").value("DIFFERENT"))
                    .andExpect(jsonPath("$[0].justification").doesNotExist())
                    .andExpect(jsonPath("$[0].overrideReason").doesNotExist())
                    .andExpect(jsonPath("$[0].finalDecision").doesNotExist());
        }
        verify(service,times(2)).performanceHistory(7L,actor);
    }

    @Test void performanceHistoryDoesNotGrantAccessToEmployeesOrLegacyOrSuperAdminAuthorities() throws Exception {
        when(messages.getMessage(anyString(),any(),any(Locale.class))).thenReturn("Forbidden");
        for(var authority:List.of("ROLE_USER","ROLE_SUPER_ADMIN","CAN_MANAGE_EVALUATION","CAN_REVIEW_KPI_ASSESSMENT"))
            mvc.perform(get("/api/annual-appraisals/participants/7/performance-history").with(authentication(user(authority))))
                    .andExpect(status().isForbidden());
        mvc.perform(get("/api/annual-appraisals/participants/7/performance-history")).andExpect(status().isUnauthorized());
        verifyNoInteractions(service);
    }
}
