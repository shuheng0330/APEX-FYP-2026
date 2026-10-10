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
import static org.mockito.ArgumentMatchers.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(AttitudeAssessmentController.class) @Import(SecurityConfig.class)
@TestPropertySource(properties="frontend.origin=http://localhost:4200")
class AttitudeAssessmentControllerTest {
    @Autowired MockMvc mvc;
    @MockitoBean AttitudeAssessmentService service;
    @MockitoBean TokenService tokenService;
    @MockitoBean ValidationService validationService;
    @MockitoBean MessageSource messageSource;
    UUID actor=UUID.randomUUID();
    UsernamePasswordAuthenticationToken user(String authority) {return new UsernamePasswordAuthenticationToken(actor,null,List.of(new SimpleGrantedAuthority(authority)));}
    @Test void employeePermissionAllowsAllOwnerActionsWithoutRoleNameOrSetupPermission() throws Exception {
        var auth=authentication(user("ROLE_USER"));
        mvc.perform(get("/api/attitude-assessments/periods").with(auth)).andExpect(status().isOk());
        mvc.perform(get("/api/attitude-assessments/mine?reviewPeriodId=1").with(auth)).andExpect(status().isOk());
        mvc.perform(get("/api/attitude-assessments/2").with(auth)).andExpect(status().isOk());
        mvc.perform(post("/api/attitude-assessments").with(auth).contentType(MediaType.APPLICATION_JSON).content("{\"reviewPeriodId\":1,\"items\":[]}")).andExpect(status().isCreated());
        mvc.perform(put("/api/attitude-assessments/2").with(auth).contentType(MediaType.APPLICATION_JSON).content("{\"reviewPeriodId\":1,\"items\":[]}")).andExpect(status().isOk());
        mvc.perform(post("/api/attitude-assessments/2/submit").with(auth)).andExpect(status().isOk());
        verify(service).mine(1L,actor);verify(service).get(2L,actor);verify(service).submit(2L,actor);
    }
    @Test void setupOrOtherReviewAuthorityAloneDoesNotGrantEmployeeAssessmentAccess() throws Exception {
        when(messageSource.getMessage(anyString(),any(),any(Locale.class))).thenReturn("Forbidden");
        for(var authority:List.of("CAN_MANAGE_ATTITUDE_CONFIGURATION","CAN_REVIEW_KPI_ASSESSMENT","CAN_REVIEW_INDIVIDUAL_KPI","CAN_MANAGE_ANNUAL_KPI_REVIEW_PERIOD")) {
            var auth=authentication(user(authority));
            mvc.perform(get("/api/attitude-assessments/periods").with(auth)).andExpect(status().isForbidden());
            mvc.perform(get("/api/attitude-assessments/2").with(auth)).andExpect(status().isForbidden());
            mvc.perform(post("/api/attitude-assessments/2/submit").with(auth)).andExpect(status().isForbidden());
        }
        verifyNoInteractions(service);
    }
    @Test void unauthenticatedRequestsDenied() throws Exception {
        mvc.perform(get("/api/attitude-assessments/periods")).andExpect(status().isUnauthorized());
    }
    @Test void nonIntegerPointsRejectedBeforeServiceButNullPointsAllowedForDrafts() throws Exception {
        var auth=authentication(user("ROLE_USER"));
        for(var point:List.of("1.5","\"3\"","true")) {
            mvc.perform(post("/api/attitude-assessments").with(auth).contentType(MediaType.APPLICATION_JSON)
                    .content("{\"reviewPeriodId\":1,\"items\":[{\"criterionId\":11,\"selfPoint\":"+point+"}]}")).andExpect(status().isBadRequest());
        }
        verifyNoInteractions(service);
        mvc.perform(post("/api/attitude-assessments").with(auth).contentType(MediaType.APPLICATION_JSON)
                .content("{\"reviewPeriodId\":1,\"items\":[{\"criterionId\":11,\"selfPoint\":null}]}")).andExpect(status().isCreated());
    }
    @Test void unavailableStateIsSuccessfulResponseNotEmptyPageOrGenericError() throws Exception {
        var dto=new AttitudeAssessmentDto();dto.setAvailabilityTitle("Attitude Evaluation Not Yet Available");
        dto.setAvailabilityMessage("The Attitude Evaluation criteria have not been configured for this Annual Review Period. Please check again later.");
        when(service.mine(1L,actor)).thenReturn(dto);
        mvc.perform(get("/api/attitude-assessments/mine?reviewPeriodId=1").with(authentication(user("ROLE_USER"))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.available").value(false))
                .andExpect(jsonPath("$.availabilityTitle").value(dto.getAvailabilityTitle()))
                .andExpect(jsonPath("$.items").isEmpty());
    }
}
