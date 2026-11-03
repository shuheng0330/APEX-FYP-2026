package com.tbm.careerpathlearning.controller;

import com.tbm.careerpathlearning.config.SecurityConfig;
import com.tbm.careerpathlearning.dto.*;
import com.tbm.careerpathlearning.enums.AnnualKpiReviewPeriodStatus;
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

@WebMvcTest(AttitudeConfigurationController.class) @Import(SecurityConfig.class)
@TestPropertySource(properties="frontend.origin=http://localhost:4200")
class AttitudeConfigurationControllerTest {
    @Autowired MockMvc mvc;
    @MockitoBean AttitudeConfigurationService service;
    @MockitoBean TokenService tokenService;
    @MockitoBean ValidationService validationService;
    @MockitoBean MessageSource messageSource;
    UUID actor=UUID.randomUUID();
    UsernamePasswordAuthenticationToken user(String permission) {return new UsernamePasswordAuthenticationToken(actor,null,List.of(new SimpleGrantedAuthority(permission)));}
    @Test void permissionAuthorisesEveryConfigurationActionWithoutJobRoleCheck() throws Exception {
        var auth=authentication(user("CAN_MANAGE_ATTITUDE_CONFIGURATION"));
        mvc.perform(get("/api/attitude-configurations").with(auth)).andExpect(status().isOk());
        mvc.perform(get("/api/attitude-configurations/options").with(auth)).andExpect(status().isOk());
        mvc.perform(get("/api/attitude-configurations/1").with(auth)).andExpect(status().isOk());
        mvc.perform(post("/api/attitude-configurations").with(auth).contentType(MediaType.APPLICATION_JSON).content("{}")).andExpect(status().isCreated());
        mvc.perform(put("/api/attitude-configurations/1").with(auth).contentType(MediaType.APPLICATION_JSON).content("{}")).andExpect(status().isOk());
        mvc.perform(post("/api/attitude-configurations/1/copy").with(auth)).andExpect(status().isCreated());
        mvc.perform(post("/api/attitude-configurations/1/publish").with(auth)).andExpect(status().isOk());
        mvc.perform(get("/api/attitude-configurations/periods/2").with(auth)).andExpect(status().isOk());
        mvc.perform(post("/api/attitude-configurations/periods/2/bind").with(auth).contentType(MediaType.APPLICATION_JSON).content("{\"configurationId\":1}")).andExpect(status().isOk());
        verify(service).bindInitially(2L,1L,actor);
    }
    @Test void noPublishedConfigurationReturnsNoContentRatherThanInventedDefaults() throws Exception {
        mvc.perform(get("/api/attitude-configurations/current").with(authentication(user("CAN_MANAGE_ATTITUDE_CONFIGURATION")))).andExpect(status().isNoContent());
    }
    @Test void scopedOptionsExposeDepartmentAndOpenPeriodContextWithConfigurationPermissionAlone() throws Exception {
        var options=new AttitudeConfigurationOptionsDto();var role=new AttitudeConfigurationDto.RoleMapping();
        role.setRoleId(7L);role.setRoleName("Executive");role.setDepartmentName("Sales");options.setRoles(List.of(role));
        options.setReviewPeriods(List.of(new AttitudeConfigurationOptionsDto.ReviewPeriodOption(4L,"Annual Review",AnnualKpiReviewPeriodStatus.OPEN)));
        when(service.options(actor)).thenReturn(options);
        mvc.perform(get("/api/attitude-configurations/options").with(authentication(user("CAN_MANAGE_ATTITUDE_CONFIGURATION"))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.roles[0].departmentName").value("Sales"))
                .andExpect(jsonPath("$.reviewPeriods[0].id").value(4)).andExpect(jsonPath("$.reviewPeriods[0].status").value("OPEN"));
    }
    @Test void employeeReviewAndPeriodPermissionsDoNotGrantConfigurationAccess() throws Exception {
        when(messageSource.getMessage(anyString(),any(),any(Locale.class))).thenReturn("Forbidden");
        for(var permission:List.of("ROLE_USER","CAN_REVIEW_KPI_ASSESSMENT","CAN_MANAGE_ANNUAL_KPI_REVIEW_PERIOD","CAN_AUTHORIZE_INDIVIDUAL_KPI_ASSISTANCE")) {
            var auth=authentication(user(permission));
            mvc.perform(get("/api/attitude-configurations").with(auth)).andExpect(status().isForbidden());
            mvc.perform(post("/api/attitude-configurations/1/publish").with(auth)).andExpect(status().isForbidden());
            mvc.perform(post("/api/attitude-configurations/periods/2/bind").with(auth).contentType(MediaType.APPLICATION_JSON).content("{\"configurationId\":1}")).andExpect(status().isForbidden());
        }
        verifyNoInteractions(service);
    }
    @Test void unauthenticatedAccessDenied() throws Exception {mvc.perform(get("/api/attitude-configurations")).andExpect(status().isUnauthorized());}
    @Test void invalidFormatsAndFractionalRatingPointsRejectedAtJsonBoundary() throws Exception {
        var auth=authentication(user("CAN_MANAGE_ATTITUDE_CONFIGURATION"));
        for(var content:List.of("{\"ratingDefinitions\":[{\"point\":1.5}]}","{\"criteria\":[{\"evaluationFormat\":\"UNKNOWN\"}]}"))
            mvc.perform(post("/api/attitude-configurations").with(auth).contentType(MediaType.APPLICATION_JSON).content(content)).andExpect(status().isBadRequest());
        verifyNoInteractions(service);
    }
}
