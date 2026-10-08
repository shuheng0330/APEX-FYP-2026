package com.tbm.careerpathlearning.controller;

import com.tbm.careerpathlearning.config.SecurityConfig;
import com.tbm.careerpathlearning.service.*;
import org.junit.jupiter.api.Test;
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

@WebMvcTest(IndividualKpiPlanController.class) @Import(SecurityConfig.class)
@TestPropertySource(properties="frontend.origin=http://localhost:4200")
class IndividualKpiPlanControllerTest {
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

    @Test void employeeMayCreateUpdateAndSubmitOwnPlan() throws Exception {
        var employee=user("ROLE_USER");
        mvc.perform(get("/api/individual-kpi-plans/periods").with(authentication(employee))).andExpect(status().isOk());
        mvc.perform(get("/api/individual-kpi-plans/mine").with(authentication(employee))).andExpect(status().isOk());
        mvc.perform(get("/api/individual-kpi-plans/my-assigned").param("reviewPeriodId","1")
                .with(authentication(employee))).andExpect(status().isOk());
        mvc.perform(post("/api/individual-kpi-plans").with(authentication(employee)).contentType(MediaType.APPLICATION_JSON)
                .content("{\"reviewPeriodId\":1,\"items\":[]}")).andExpect(status().isCreated());
        verify(service).createIndividual(argThat(r->r.getReviewPeriodId()==1L&&r.getItems().isEmpty()),eq(actor));
        mvc.perform(put("/api/individual-kpi-plans/10").with(authentication(employee)).contentType(MediaType.APPLICATION_JSON)
                .content("{\"reviewPeriodId\":1,\"items\":[]}")).andExpect(status().isOk());
        verify(service).updateIndividual(eq(10L),any(),eq(actor));
        mvc.perform(post("/api/individual-kpi-plans/10/submit").with(authentication(employee))).andExpect(status().isOk());
        verify(service).submitIndividual(10L,actor);
    }
    @Test void employeeCannotApproveOrReturnEvenWithRoleUser() throws Exception {
        messages();var employee=user("ROLE_USER");
        mvc.perform(get("/api/individual-kpi-plans/pending").with(authentication(employee))).andExpect(status().isForbidden());
        mvc.perform(post("/api/individual-kpi-plans/10/approve").with(authentication(employee))).andExpect(status().isForbidden());
        mvc.perform(post("/api/individual-kpi-plans/10/return").with(authentication(employee))
                .contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"Fix target\"}"))
                .andExpect(status().isForbidden());
        verifyNoInteractions(service);
    }
    @Test void reviewPermissionCanSeeQueueAndDecideWithoutEmployeeEditing() throws Exception {
        var reviewer=user("CAN_REVIEW_INDIVIDUAL_KPI");
        mvc.perform(get("/api/individual-kpi-plans/pending").with(authentication(reviewer))).andExpect(status().isOk());
        verify(service).pendingIndividualPlans(actor);
        mvc.perform(get("/api/individual-kpi-plans/10").with(authentication(reviewer))).andExpect(status().isOk());
        verify(service).individualPlan(10L,actor);
        mvc.perform(post("/api/individual-kpi-plans/10/approve").with(authentication(reviewer))).andExpect(status().isOk());
        verify(service).approveIndividual(10L,actor);
        mvc.perform(post("/api/individual-kpi-plans/11/return").with(authentication(reviewer))
                .contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"Clarify target\"}"))
                .andExpect(status().isOk());
        verify(service).returnIndividual(eq(11L),argThat(r->r.getReason().equals("Clarify target")),eq(actor));
        messages();
        mvc.perform(post("/api/individual-kpi-plans").with(authentication(reviewer)).contentType(MediaType.APPLICATION_JSON)
                .content("{\"reviewPeriodId\":1,\"items\":[]}")).andExpect(status().isForbidden());
        verify(service,never()).createIndividual(any(),any());
    }
    @Test void unrelatedPermissionCannotAccessIndividualPlans() throws Exception {
        messages();mvc.perform(get("/api/individual-kpi-plans/mine")
                .with(authentication(user("CAN_MANAGE_ANNUAL_KPI_REVIEW_PERIOD")))).andExpect(status().isForbidden());
        verifyNoInteractions(service);
    }
}
