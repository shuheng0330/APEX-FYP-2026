package com.tbm.careerpathlearning.controller;

import com.tbm.careerpathlearning.config.SecurityConfig;
import com.tbm.careerpathlearning.service.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.MessageSource;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import java.util.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(KpiRevisionController.class) @Import(SecurityConfig.class)
@TestPropertySource(properties="frontend.origin=http://localhost:4200")
class KpiRevisionControllerTest {
    @Autowired MockMvc mvc;
    @MockitoBean KpiRevisionService service;
    @MockitoBean TokenService tokenService;
    @MockitoBean ValidationService validationService;
    @MockitoBean MessageSource messageSource;
    UUID actor=UUID.randomUUID();
    UsernamePasswordAuthenticationToken user(String permission) {return new UsernamePasswordAuthenticationToken(actor,null,List.of(new SimpleGrantedAuthority(permission)));}
    @ParameterizedTest @ValueSource(strings={"ROLE_USER","CAN_MANAGE_COMPANY_KPI","CAN_MANAGE_KPI_AMENDMENTS","CAN_PREPARE_DEPARTMENT_KPI_REVISION","CAN_MANAGE_ANNUAL_KPI_REVIEW_PERIOD"})
    void onlyExplicitCompanyRevisionAuthorityCanStart(String permission) throws Exception {
        when(messageSource.getMessage(anyString(),any(),any(Locale.class))).thenReturn("Forbidden");
        mvc.perform(post("/api/company-kpi-plans/1/revisions").with(authentication(user(permission)))
            .contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"Change target\"}")).andExpect(status().isForbidden());verifyNoInteractions(service);
    }
    @Test void companyRevisionUsesAuthenticatedActorAndWholePlan() throws Exception {
        mvc.perform(post("/api/company-kpi-plans/1/revisions").with(authentication(user("CAN_REVISE_COMPANY_KPI")))
            .contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"Change target\"}")).andExpect(status().isOk());
        verify(service).startCompany(eq(1L),argThat(r->r.getReason().equals("Change target")),eq(actor));
    }
    @ParameterizedTest @ValueSource(strings={"prepare","submit","approve","return","confirm","cancel"})
    void employeeCannotTransitionRevisions(String action) throws Exception {
        when(messageSource.getMessage(anyString(),any(),any(Locale.class))).thenReturn("Forbidden");
        mvc.perform(post("/api/kpi-plan-revisions/2/"+action).with(authentication(user("ROLE_USER")))
            .contentType(MediaType.APPLICATION_JSON).content("{\"expectedLockVersion\":0}")).andExpect(status().isForbidden());verifyNoInteractions(service);
    }
    @Test void hrCanManageAccessOrCancelButCannotApproveOrConfirm() throws Exception {
        when(messageSource.getMessage(anyString(),any(),any(Locale.class))).thenReturn("Forbidden");
        var hr=user("CAN_MANAGE_KPI_AMENDMENTS");
        mvc.perform(put("/api/kpi-amendment-access/1").with(authentication(hr)).contentType(MediaType.APPLICATION_JSON)
            .content("{\"enabled\":true,\"closeDate\":\"2026-10-31\"}")).andExpect(status().isOk());
        verify(service).updateAccess(eq(1L),argThat(r->r.enabled()&&r.closeDate().toString().equals("2026-10-31")),eq(actor));
        mvc.perform(post("/api/kpi-plan-revisions/2/cancel").with(authentication(hr)).contentType(MediaType.APPLICATION_JSON)
            .content("{\"expectedLockVersion\":4,\"reason\":\"Duplicate request\"}")).andExpect(status().isOk());
        verify(service).cancel(eq(2L),argThat(r->r.getExpectedLockVersion()==4),eq(actor));
        for(var action:List.of("approve","confirm")) mvc.perform(post("/api/kpi-plan-revisions/2/"+action).with(authentication(hr))
            .contentType(MediaType.APPLICATION_JSON).content("{\"expectedLockVersion\":4}")).andExpect(status().isForbidden());
        verify(service,never()).approve(anyLong(),any(),any());verify(service,never()).confirm(anyLong(),any(),any());
    }
    @Test void noIndividualRevisionShortcutExists() throws Exception {
        mvc.perform(post("/api/individual-kpi-plans/1/revisions").with(authentication(user("CAN_REVISE_COMPANY_KPI")))
            .contentType(MediaType.APPLICATION_JSON).content("{}")).andExpect(status().isMethodNotAllowed());verifyNoInteractions(service);
    }
    @Test void individualRequestUsesExplicitPermissionAndPreparationMode() throws Exception {
        mvc.perform(post("/api/individual-kpi-plans/1/revision-requests").with(authentication(user("CAN_MANAGE_INDIVIDUAL_KPI_REVISIONS")))
            .contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"Change target\",\"preparationMode\":\"HR_MD\"}"))
            .andExpect(status().isOk());
        verify(service).requestIndividual(eq(1L),argThat(r->r.getReason().equals("Change target")&&
            r.getPreparationMode()==com.tbm.careerpathlearning.enums.KpiRevisionPreparationMode.HR_MD),eq(actor));
    }
    @ParameterizedTest @ValueSource(strings={"ROLE_USER","CAN_REVIEW_INDIVIDUAL_KPI","CAN_MANAGE_KPI_AMENDMENTS","CAN_PREPARE_DEPARTMENT_KPI_REVISION"})
    void noOtherAuthorityCanInitiateIndividualRevision(String permission) throws Exception {
        when(messageSource.getMessage(anyString(),any(),any(Locale.class))).thenReturn("Forbidden");
        mvc.perform(post("/api/individual-kpi-plans/1/revision-requests").with(authentication(user(permission)))
            .contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"Change target\"}")).andExpect(status().isForbidden());verifyNoInteractions(service);
    }
    @ParameterizedTest @ValueSource(strings={"prepare","approve","return","confirm"})
    void superiorCanReachScopedRevisionActions(String action) throws Exception {
        mvc.perform(post("/api/kpi-plan-revisions/2/"+action).with(authentication(user("CAN_REVIEW_INDIVIDUAL_KPI")))
            .contentType(MediaType.APPLICATION_JSON).content("{\"expectedLockVersion\":0,\"reason\":\"Clarify target\"}"))
            .andExpect(status().isOk());
    }
    @Test void conflictIsActionableAndDoesNotLookLikeServerFailure() throws Exception {
        when(service.confirm(eq(2L),any(),eq(actor))).thenThrow(new com.tbm.careerpathlearning.exception.KpiVersionConflictException("Refresh this revision"));
        mvc.perform(post("/api/kpi-plan-revisions/2/confirm").with(authentication(user("CAN_REVISE_COMPANY_KPI")))
            .contentType(MediaType.APPLICATION_JSON).content("{\"expectedLockVersion\":0}")).andExpect(status().isConflict()).andExpect(jsonPath("message").value("Refresh this revision"));
    }
}
