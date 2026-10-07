package com.tbm.careerpathlearning.controller;
import com.tbm.careerpathlearning.config.SecurityConfig;
import com.tbm.careerpathlearning.service.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.MessageSource;
import org.springframework.context.annotation.Import;
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
@WebMvcTest(CompanyKpiPlanController.class) @Import(SecurityConfig.class)
@TestPropertySource(properties="frontend.origin=http://localhost:4200")
class CompanyKpiPlanControllerTest {
    @Autowired MockMvc mvc;
    @MockitoBean KpiPlanService service;
    @MockitoBean TokenService tokenService;
    @MockitoBean ValidationService validationService;
    @MockitoBean MessageSource messageSource;
    @Test void administrativePermissionDoesNotImplyMd() throws Exception {
        when(messageSource.getMessage(anyString(),any(),any(Locale.class))).thenReturn("Forbidden");
        var admin=new UsernamePasswordAuthenticationToken(UUID.randomUUID(),null,List.of(new SimpleGrantedAuthority("CAN_MANAGE_ANNUAL_KPI_REVIEW_PERIOD")));
        mvc.perform(get("/api/company-kpi-plans/periods").with(authentication(admin))).andExpect(status().isForbidden());
        verifyNoInteractions(service);
    }
    @Test void businessPermissionCanReadScopedPeriodContext() throws Exception {
        var md=new UsernamePasswordAuthenticationToken(UUID.randomUUID(),null,List.of(new SimpleGrantedAuthority("CAN_MANAGE_COMPANY_KPI")));
        when(service.companyPeriods()).thenReturn(List.of());
        mvc.perform(get("/api/company-kpi-plans/periods").with(authentication(md))).andExpect(status().isOk());
    }
}
