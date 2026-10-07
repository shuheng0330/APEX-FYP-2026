package com.tbm.careerpathlearning.controller;

import com.tbm.careerpathlearning.config.SecurityConfig;
import com.tbm.careerpathlearning.dto.KpiPlanReturnRequest;
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

@WebMvcTest(DepartmentKpiPlanController.class) @Import(SecurityConfig.class)
@TestPropertySource(properties="frontend.origin=http://localhost:4200")
class DepartmentKpiPlanControllerTest {
    @Autowired MockMvc mvc;
    @MockitoBean KpiPlanService service;
    @MockitoBean TokenService tokenService;
    @MockitoBean ValidationService validationService;
    @MockitoBean MessageSource messageSource;
    UUID actor=UUID.randomUUID();
    UsernamePasswordAuthenticationToken user(String authority) {
        return new UsernamePasswordAuthenticationToken(actor,null,List.of(new SimpleGrantedAuthority(authority)));
    }
    void messages() {when(messageSource.getMessage(anyString(),any(),any(Locale.class))).thenReturn("Forbidden");}

    @ParameterizedTest @ValueSource(strings={"ROLE_USER","CAN_MANAGE_ANNUAL_KPI_REVIEW_PERIOD","CAN_MANAGE_COMPANY_KPI","CAN_MANAGE_STAFF"})
    void unrelatedPermissionsDoNotGrantDepartmentAccess(String permission) throws Exception {
        messages();mvc.perform(get("/api/department-kpi-plans").with(authentication(user(permission)))).andExpect(status().isForbidden());
        verifyNoInteractions(service);
    }
    @Test void hodCanCreateDraftAndSubmitWithAuthenticatedIdentity() throws Exception {
        var hod=user("CAN_MANAGE_DEPARTMENT_KPI");
        mvc.perform(post("/api/department-kpi-plans").with(authentication(hod)).contentType(MediaType.APPLICATION_JSON)
            .content("{\"reviewPeriodId\":1,\"departmentId\":2,\"items\":[]}")).andExpect(status().isCreated());
        verify(service).createDepartment(argThat(r->r.getDepartmentId()==2L&&r.getItems().isEmpty()),eq(actor));
        mvc.perform(post("/api/department-kpi-plans/10/submit").with(authentication(hod))).andExpect(status().isOk());
        verify(service).submitDepartment(10L,actor);
    }
    @ParameterizedTest @ValueSource(strings={"approve","return"})
    void hodCannotMakeMdDecision(String action) throws Exception {
        messages();mvc.perform(post("/api/department-kpi-plans/10/"+action).with(authentication(user("CAN_MANAGE_DEPARTMENT_KPI")))
            .contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"Fix target\"}")).andExpect(status().isForbidden());
        verifyNoInteractions(service);
    }
    @Test void mdCanReviewQueueApproveAndReturn() throws Exception {
        var md=user("CAN_APPROVE_DEPARTMENT_KPI");
        mvc.perform(get("/api/department-kpi-plans/pending").with(authentication(md))).andExpect(status().isOk());
        verify(service).pendingDepartmentPlans(actor);
        mvc.perform(post("/api/department-kpi-plans/10/approve").with(authentication(md))).andExpect(status().isOk());
        verify(service).approveDepartment(10L,actor);
        mvc.perform(post("/api/department-kpi-plans/11/return").with(authentication(md)).contentType(MediaType.APPLICATION_JSON)
            .content("{\"reason\":\"Clarify target\"}")).andExpect(status().isOk());
        verify(service).returnDepartment(eq(11L),argThat(r->r.getReason().equals("Clarify target")),eq(actor));
    }
    @Test void reviewerPermissionDoesNotGrantHodEditing() throws Exception {
        messages();var md=user("CAN_APPROVE_DEPARTMENT_KPI");
        mvc.perform(post("/api/department-kpi-plans").with(authentication(md)).contentType(MediaType.APPLICATION_JSON)
            .content("{\"reviewPeriodId\":1,\"departmentId\":2,\"items\":[]}")).andExpect(status().isForbidden());
        mvc.perform(put("/api/department-kpi-plans/10").with(authentication(md)).contentType(MediaType.APPLICATION_JSON)
            .content("{\"reviewPeriodId\":1,\"departmentId\":2,\"items\":[]}")).andExpect(status().isForbidden());
        mvc.perform(post("/api/department-kpi-plans/10/submit").with(authentication(md))).andExpect(status().isForbidden());
        verifyNoInteractions(service);
    }
    @Test void periodContextDoesNotRequireAnnualAdministration() throws Exception {
        var hod=user("CAN_MANAGE_DEPARTMENT_KPI");
        mvc.perform(get("/api/department-kpi-plans/periods").with(authentication(hod))).andExpect(status().isOk());
        mvc.perform(get("/api/department-kpi-plans/departments").with(authentication(hod))).andExpect(status().isOk());
        verify(service).departmentPeriods(actor);verify(service).departmentOptions(actor);
    }
    @Test void scopeFailureUsesExistingForbiddenResponse() throws Exception {
        messages();when(service.departmentPlan(10L,actor)).thenThrow(new org.springframework.security.access.AccessDeniedException("Other department"));
        mvc.perform(get("/api/department-kpi-plans/10").with(authentication(user("CAN_MANAGE_DEPARTMENT_KPI")))).andExpect(status().isForbidden());
    }
}
