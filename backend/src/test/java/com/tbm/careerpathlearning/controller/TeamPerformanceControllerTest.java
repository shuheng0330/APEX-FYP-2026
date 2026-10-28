package com.tbm.careerpathlearning.controller;

import com.tbm.careerpathlearning.config.SecurityConfig;
import com.tbm.careerpathlearning.dto.TeamPerformanceDto.*;
import com.tbm.careerpathlearning.exception.BadRequestException;
import com.tbm.careerpathlearning.service.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.MessageSource;
import org.springframework.context.annotation.Import;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import java.util.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(TeamPerformanceController.class)
@Import(SecurityConfig.class)
@TestPropertySource(properties="frontend.origin=http://localhost:4200")
class TeamPerformanceControllerTest {
    @Autowired MockMvc mvc;
    @MockitoBean TeamPerformanceService service;
    @MockitoBean TokenService tokens;
    @MockitoBean ValidationService validation;
    @MockitoBean MessageSource messages;
    final UUID actor = UUID.randomUUID();
    final String path = "/api/annual-performance/team";

    @BeforeEach void setup() {
        when(messages.getMessage(anyString(), any(), any(Locale.class))).thenReturn("Unavailable");
    }

    @Test void routesForwardOnlyAuthenticatedIdentityAndExplicitScope() throws Exception {
        var auth = authentication(user(StaffPerformanceAccess.TEAM));
        mvc.perform(get(path + "/scopes").with(auth)).andExpect(status().isOk());
        mvc.perform(get(path + "/periods?scope=TEAM").with(auth)).andExpect(status().isOk());
        mvc.perform(get(path + "?scope=TEAM&reviewPeriodId=1").with(auth)).andExpect(status().isOk());
        mvc.perform(get(path + "?scope=DEPARTMENT&departmentId=10&reviewPeriodId=2&historyYears=3&metric=KPI").with(auth)).andExpect(status().isOk());
        verify(service).scopes(actor); verify(service).periods(Scope.TEAM, null, actor);
        verify(service).dashboard(Scope.TEAM, null, 1L, 5, Metric.FINAL, actor);
        verify(service).dashboard(Scope.DEPARTMENT, 10L, 2L, 3, Metric.KPI, actor);
    }

    @Test void allThreeExplicitViewingPermissionsCanEnterButRoleNamesCannot() throws Exception {
        for (var permission : List.of(StaffPerformanceAccess.TEAM, StaffPerformanceAccess.DEPARTMENT, StaffPerformanceAccess.ORGANISATION))
            mvc.perform(get(path + "/scopes").with(authentication(user(permission)))).andExpect(status().isOk());
        for (var permission : List.of("ROLE_USER", "ROLE_SUPER_ADMIN", "CAN_MANAGE_EVALUATION", "CAN_REVIEW_KPI_ASSESSMENT", "CAN_REVIEW_ANNUAL_APPRAISAL"))
            mvc.perform(get(path + "/scopes").with(authentication(user(permission)))).andExpect(status().isForbidden());
        verify(service, times(3)).scopes(actor);
    }

    @Test void unauthenticatedAndMalformedRequestsAreRejected() throws Exception {
        mvc.perform(get(path + "/scopes")).andExpect(status().isUnauthorized());
        var auth = authentication(user(StaffPerformanceAccess.TEAM));
        for (var query : List.of("?reviewPeriodId=1", "?scope=TEAM", "?scope=ALL&reviewPeriodId=1",
                "?scope=TEAM&reviewPeriodId=no", "?scope=TEAM&reviewPeriodId=1&metric=SECRET"))
            mvc.perform(get(path + query).with(auth)).andExpect(status().isBadRequest());
        verifyNoInteractions(service);
    }

    @Test void serviceScopeAndRangeFailuresDoNotReturnEmployeeData() throws Exception {
        when(service.dashboard(Scope.DEPARTMENT, 20L, 1L, 5, Metric.FINAL, actor)).thenThrow(new AccessDeniedException("outside scope"));
        mvc.perform(get(path + "?scope=DEPARTMENT&departmentId=20&reviewPeriodId=1").with(authentication(user(StaffPerformanceAccess.DEPARTMENT))))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.employees").doesNotExist());
        when(service.dashboard(Scope.TEAM, null, 1L, 4, Metric.FINAL, actor)).thenThrow(new BadRequestException("Choose 3 or 5 years"));
        mvc.perform(get(path + "?scope=TEAM&reviewPeriodId=1&historyYears=4").with(authentication(user(StaffPerformanceAccess.TEAM))))
                .andExpect(status().isBadRequest());
    }

    @Test void scopesResponseContainsOnlyAvailableWorkspaceContext() throws Exception {
        when(service.scopes(actor)).thenReturn(List.of(new ScopeOption(Scope.TEAM, null, "My Direct Reports")));
        mvc.perform(get(path + "/scopes").with(authentication(user(StaffPerformanceAccess.TEAM))))
                .andExpect(status().isOk()).andExpect(jsonPath("$[0].type").value("TEAM"))
                .andExpect(jsonPath("$[0].name").value("My Direct Reports"))
                .andExpect(jsonPath("$[0].permissions").doesNotExist());
    }

    private UsernamePasswordAuthenticationToken user(String permission) {
        return new UsernamePasswordAuthenticationToken(actor, null, List.of(new SimpleGrantedAuthority(permission)));
    }
}
