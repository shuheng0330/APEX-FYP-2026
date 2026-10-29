package com.tbm.careerpathlearning.controller;

import com.tbm.careerpathlearning.config.SecurityConfig;
import com.tbm.careerpathlearning.dto.TeamPerformanceDto.Metric;
import com.tbm.careerpathlearning.exception.BadRequestException;
import com.tbm.careerpathlearning.service.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.MessageSource;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import java.util.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(OrganisationPerformanceController.class)
@Import(SecurityConfig.class)
@TestPropertySource(properties="frontend.origin=http://localhost:4200")
class OrganisationPerformanceControllerTest {
    @Autowired MockMvc mvc;
    @MockitoBean OrganisationPerformanceService service;
    @MockitoBean OrganisationPerformanceExportService export;
    @MockitoBean TokenService tokens;
    @MockitoBean ValidationService validation;
    @MockitoBean MessageSource messages;
    final UUID actor=UUID.randomUUID();final String path="/api/annual-performance/organisation";
    @BeforeEach void setup(){when(messages.getMessage(anyString(),any(),any(Locale.class))).thenReturn("Unavailable");}
    @Test void forwardsAuthenticatedActorPeriodRangeAndMetric() throws Exception {
        var auth=authentication(user(StaffPerformanceAccess.ORGANISATION));
        mvc.perform(get(path+"/periods").with(auth)).andExpect(status().isOk());
        mvc.perform(get(path+"?reviewPeriodId=1").with(auth)).andExpect(status().isOk());
        mvc.perform(get(path+"?reviewPeriodId=2&historyYears=3&metric=ATTITUDE").with(auth)).andExpect(status().isOk());
        verify(service).periods(actor);verify(service).dashboard(1L,5,Metric.FINAL,actor);verify(service).dashboard(2L,3,Metric.ATTITUDE,actor);
    }
    @Test void teamDepartmentEmployeeAndLegacyRoleNamesCannotEnterOrExport() throws Exception {
        for(var role:List.of("ROLE_USER","ROLE_SUPER_ADMIN",StaffPerformanceAccess.TEAM,StaffPerformanceAccess.DEPARTMENT,"CAN_REVIEW_ANNUAL_APPRAISAL","CAN_MANAGE_EVALUATION_CYCLE"))
            for(var suffix:List.of("/periods","?reviewPeriodId=1","/export?reviewPeriodId=1"))mvc.perform(get(path+suffix).with(authentication(user(role)))).andExpect(status().isForbidden());
        verifyNoInteractions(service,export);
    }
    @Test void unauthenticatedAndMalformedCallsFailBeforeReadingData() throws Exception {
        mvc.perform(get(path+"/periods")).andExpect(status().isUnauthorized());
        for(var suffix:List.of("", "?reviewPeriodId=bad","?reviewPeriodId=1&metric=OTHER","?reviewPeriodId=1&historyYears=abc","/export"))
            mvc.perform(get(path+suffix).with(authentication(user(StaffPerformanceAccess.ORGANISATION)))).andExpect(status().isBadRequest());
        verifyNoInteractions(service,export);
    }
    @Test void inaccessiblePeriodsAndInvalidRangesReturnNoWorkbookOrPopulation() throws Exception {
        when(service.dashboard(99L,5,Metric.FINAL,actor)).thenThrow(new AccessDeniedException("Unavailable"));
        when(service.dashboard(1L,4,Metric.FINAL,actor)).thenThrow(new BadRequestException("Range"));
        var auth=authentication(user(StaffPerformanceAccess.ORGANISATION));
        mvc.perform(get(path+"/export?reviewPeriodId=99").with(auth)).andExpect(status().isForbidden());
        mvc.perform(get(path+"?reviewPeriodId=1&historyYears=4").with(auth)).andExpect(status().isBadRequest());verifyNoInteractions(export);
    }
    @Test void exportsExcelFromTheSameAuthorisedSelectionWithNoStore() throws Exception {
        byte[] content={80,75,3,4};when(export.write(null)).thenReturn(content);
        mvc.perform(get(path+"/export?reviewPeriodId=7&historyYears=3&metric=KPI").with(authentication(user(StaffPerformanceAccess.ORGANISATION))))
                .andExpect(status().isOk()).andExpect(content().contentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION,"attachment; filename=\"organisation-performance-period-7.xlsx\""))
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL,"no-store")).andExpect(content().bytes(content));
        verify(service).dashboard(7L,3,Metric.KPI,actor);verify(export).write(null);
    }
    private UsernamePasswordAuthenticationToken user(String role){return new UsernamePasswordAuthenticationToken(actor,null,List.of(new SimpleGrantedAuthority(role)));}
}
