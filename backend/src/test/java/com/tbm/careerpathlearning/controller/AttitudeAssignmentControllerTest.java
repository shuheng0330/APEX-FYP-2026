package com.tbm.careerpathlearning.controller;

import com.tbm.careerpathlearning.config.SecurityConfig;
import com.tbm.careerpathlearning.dto.*;
import com.tbm.careerpathlearning.exception.AttitudeAssignmentBlockedException;
import com.tbm.careerpathlearning.exception.BadRequestException;
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
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(AttitudeAssignmentController.class) @Import(SecurityConfig.class)
@TestPropertySource(properties="frontend.origin=http://localhost:4200")
class AttitudeAssignmentControllerTest {
    @Autowired MockMvc mvc;
    @MockitoBean AttitudeAssignmentService service;
    @MockitoBean TokenService tokenService;
    @MockitoBean ValidationService validationService;
    @MockitoBean MessageSource messageSource;
    final UUID actor=UUID.randomUUID();final String base="/api/attitude-configurations/periods/5/assignment";
    UsernamePasswordAuthenticationToken user(String authority) {return new UsernamePasswordAuthenticationToken(actor,null,List.of(new SimpleGrantedAuthority(authority)));}
    String request(String reason) {return "{\"requestId\":\""+UUID.randomUUID()+"\",\"expectedConfigurationId\":1,\"configurationId\":2,\"reason\":\""+reason+"\"}";}
    @Test void onlyExistingSetupAuthorityCanReadOrUpdate() throws Exception {
        when(messageSource.getMessage(anyString(),any(),any(Locale.class))).thenReturn("Forbidden");
        for(var permission:List.of("ROLE_USER","CAN_MANAGE_ANNUAL_KPI_REVIEW_PERIOD","CAN_REVIEW_ANNUAL_APPRAISAL","CAN_VIEW_ORGANISATION_PERFORMANCE")) {
            mvc.perform(get(base).with(authentication(user(permission)))).andExpect(status().isForbidden());
            mvc.perform(post(base).with(authentication(user(permission))).contentType(MediaType.APPLICATION_JSON).content(request("Correction"))).andExpect(status().isForbidden());
        }
        mvc.perform(get(base)).andExpect(status().isUnauthorized());verifyNoInteractions(service);
        mvc.perform(get(base).with(authentication(user("CAN_MANAGE_ATTITUDE_CONFIGURATION")))).andExpect(status().isOk());verify(service).state(5L,actor);
    }
    @Test void validatedActionPreservesActorExpectedVersionAndReasonAndReturnsStructured409() throws Exception {
        var auth=authentication(user("CAN_MANAGE_ATTITUDE_CONFIGURATION"));
        var p=new AttitudePeriodConfigurationDto();p.setReviewPeriodId(5L);
        var blocked=new AttitudeAssignmentState(p,false,List.of("Assessments already started"),List.of(),List.of(),List.of(),false);
        when(service.update(eq(5L),any(),eq(actor))).thenThrow(new AttitudeAssignmentBlockedException(blocked));
        mvc.perform(post(base).with(auth).contentType(MediaType.APPLICATION_JSON).content(request("Correction")))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.period.reviewPeriodId").value(5)).andExpect(jsonPath("$.canUpdate").value(false));
        verify(service).update(eq(5L),argThat(r->r.expectedConfigurationId()==1L && r.configurationId()==2L && r.reason().equals("Correction")),eq(actor));
    }
    @Test void invalidBodiesNeverReachWorkflow() throws Exception {
        var auth=authentication(user("CAN_MANAGE_ATTITUDE_CONFIGURATION"));
        for(var content:List.of("{}",request(" "),request("x".repeat(1001)),"{\"requestId\":\"not-a-uuid\"}"))
            mvc.perform(post(base).with(auth).contentType(MediaType.APPLICATION_JSON).content(content)).andExpect(status().isBadRequest());
        verifyNoInteractions(service);
    }
    @Test void disabledServiceUsesExistingBadRequestContractForBothCorrectionEndpoints() throws Exception {
        when(messageSource.getMessage(anyString(),any(),any(Locale.class))).thenReturn("Request unavailable");
        String message="Updating an existing Review Period's Attitude configuration is currently unavailable.";
        when(service.state(5L,actor)).thenThrow(new BadRequestException(message));
        when(service.update(eq(5L),any(),eq(actor))).thenThrow(new BadRequestException(message));
        var auth=authentication(user("CAN_MANAGE_ATTITUDE_CONFIGURATION"));
        mvc.perform(get(base).with(auth)).andExpect(status().isBadRequest()).andExpect(jsonPath("$.message").value(message));
        mvc.perform(post(base).with(auth).contentType(MediaType.APPLICATION_JSON).content(request("Correction")))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.message").value(message));
    }
}
