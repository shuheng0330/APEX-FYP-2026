package com.tbm.careerpathlearning.controller;

import com.tbm.careerpathlearning.config.SecurityConfig;
import com.tbm.careerpathlearning.dto.*;
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

@WebMvcTest(IndividualKpiAssistanceController.class) @Import(SecurityConfig.class)
@TestPropertySource(properties="frontend.origin=http://localhost:4200")
class IndividualKpiAssistanceControllerTest {
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
    @Test void superiorCanRequestAndPrepareOnlyThroughCaseEndpoints() throws Exception {
        var auth=user("CAN_REVIEW_INDIVIDUAL_KPI");
        mvc.perform(get("/api/individual-kpi-assistance/employees").with(authentication(auth))).andExpect(status().isOk());
        mvc.perform(get("/api/individual-kpi-assistance").with(authentication(auth))).andExpect(status().isOk());
        mvc.perform(get("/api/individual-kpi-assistance/8").with(authentication(auth))).andExpect(status().isOk());
        mvc.perform(post("/api/individual-kpi-assistance").with(authentication(auth)).contentType(MediaType.APPLICATION_JSON)
            .content("{\"ownerParticipantId\":7,\"requestReason\":\"Needs help preparing KPIs\"}")).andExpect(status().isCreated());
        verify(service).requestAssistance(argThat(r->r.getOwnerParticipantId()==7L && r.getRequestReason().equals("Needs help preparing KPIs")),eq(actor));
        mvc.perform(get("/api/individual-kpi-assistance/8/plan").with(authentication(auth))).andExpect(status().isOk());
        mvc.perform(post("/api/individual-kpi-assistance/8/plan").with(authentication(auth)).contentType(MediaType.APPLICATION_JSON)
            .content("{\"items\":[],\"ownerParticipantId\":999,\"employeeId\":\"arbitrary\"}")).andExpect(status().isCreated());
        verify(service).createAssistedIndividual(eq(8L),argThat(r->r.getItems().isEmpty()),eq(actor));
        mvc.perform(put("/api/individual-kpi-assistance/8/plan").with(authentication(auth)).contentType(MediaType.APPLICATION_JSON)
            .content("{\"items\":[]}")).andExpect(status().isOk());
        verify(service).updateAssistedIndividual(eq(8L),any(AssistedIndividualKpiPlanRequest.class),eq(actor));
        mvc.perform(post("/api/individual-kpi-assistance/8/confirm").with(authentication(auth))).andExpect(status().isOk());
        verify(service).confirmAssistedIndividual(8L,actor);
    }
    @Test void hrPermissionCanReadAndAuthorizeWithoutCreatingOrApprovingKpis() throws Exception {
        messages();var hr=user("CAN_AUTHORIZE_INDIVIDUAL_KPI_ASSISTANCE");
        mvc.perform(get("/api/individual-kpi-assistance").with(authentication(hr))).andExpect(status().isOk());
        mvc.perform(get("/api/individual-kpi-assistance/8").with(authentication(hr))).andExpect(status().isOk());
        mvc.perform(post("/api/individual-kpi-assistance/8/authorize").with(authentication(hr))).andExpect(status().isOk());
        verify(service).authorizeAssistance(8L,actor);
        mvc.perform(post("/api/individual-kpi-assistance/8/reject").with(authentication(hr)).contentType(MediaType.APPLICATION_JSON)
            .content("{\"reason\":\"Please clarify the need\"}")).andExpect(status().isOk());
        verify(service).rejectAssistance(eq(8L),argThat(r->r.getReason().equals("Please clarify the need")),eq(actor));
        mvc.perform(get("/api/individual-kpi-assistance/employees").with(authentication(hr))).andExpect(status().isForbidden());
        mvc.perform(post("/api/individual-kpi-assistance").with(authentication(hr)).contentType(MediaType.APPLICATION_JSON)
            .content("{\"ownerParticipantId\":7,\"requestReason\":\"Needs help preparing KPIs\"}")).andExpect(status().isForbidden());
        mvc.perform(post("/api/individual-kpi-assistance/8/plan").with(authentication(hr)).contentType(MediaType.APPLICATION_JSON)
            .content("{\"items\":[]}")).andExpect(status().isForbidden());
        mvc.perform(post("/api/individual-kpi-assistance/8/confirm").with(authentication(hr))).andExpect(status().isForbidden());
        verify(service,never()).createAssistedIndividual(anyLong(),any(),any());
        verify(service,never()).confirmAssistedIndividual(anyLong(),any());
    }
    @Test void superiorCannotAuthorizeOwnRequestWithReviewPermissionAlone() throws Exception {
        messages();mvc.perform(post("/api/individual-kpi-assistance/8/authorize")
            .with(authentication(user("CAN_REVIEW_INDIVIDUAL_KPI")))).andExpect(status().isForbidden());
        mvc.perform(post("/api/individual-kpi-assistance/8/reject").with(authentication(user("CAN_REVIEW_INDIVIDUAL_KPI")))
            .contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"Not needed\"}")).andExpect(status().isForbidden());
        verifyNoInteractions(service);
    }
    @Test void roleNamesAndUnrelatedPermissionsDoNotGrantAssistanceAccess() throws Exception {
        messages();
        for(var permission:List.of("ROLE_USER","Super Admin","MD","CAN_MANAGE_ANNUAL_KPI_REVIEW_PERIOD","CAN_APPROVE_DEPARTMENT_KPI")) {
            mvc.perform(get("/api/individual-kpi-assistance").with(authentication(user(permission)))).andExpect(status().isForbidden());
            mvc.perform(post("/api/individual-kpi-assistance/8/authorize").with(authentication(user(permission)))).andExpect(status().isForbidden());
        }
        verifyNoInteractions(service);
    }
    @Test void malformedOrMissingParticipantIsRejectedBeforeService() throws Exception {
        var superior=user("CAN_REVIEW_INDIVIDUAL_KPI");
        for(var body:List.of("{}","{\"ownerParticipantId\":0}","{\"ownerParticipantId\":-1}","{\"ownerParticipantId\":\"bad\"}"))
            mvc.perform(post("/api/individual-kpi-assistance").with(authentication(superior)).contentType(MediaType.APPLICATION_JSON)
                .content(body)).andExpect(status().isBadRequest());
        verifyNoInteractions(service);
    }
    @Test void newRequestsRejectMissingBlankOrOverlongReasons() throws Exception {
        var superior=user("CAN_REVIEW_INDIVIDUAL_KPI");
        for(var body:List.of("{\"ownerParticipantId\":7}","{\"ownerParticipantId\":7,\"requestReason\":null}",
            "{\"ownerParticipantId\":7,\"requestReason\":\"   \"}","{\"ownerParticipantId\":7,\"requestReason\":\""+"x".repeat(10001)+"\"}"))
            mvc.perform(post("/api/individual-kpi-assistance").with(authentication(superior)).contentType(MediaType.APPLICATION_JSON)
                .content(body)).andExpect(status().isBadRequest());
        verifyNoInteractions(service);
    }
}
