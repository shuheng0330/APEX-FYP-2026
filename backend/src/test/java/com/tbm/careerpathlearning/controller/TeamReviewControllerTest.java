package com.tbm.careerpathlearning.controller;

import com.tbm.careerpathlearning.config.SecurityConfig;
import com.tbm.careerpathlearning.dto.TeamReviewPeriodsDto;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(TeamReviewController.class) @Import(SecurityConfig.class)
@TestPropertySource(properties="frontend.origin=http://localhost:4200")
class TeamReviewControllerTest {
    @Autowired MockMvc mvc;
    @MockitoBean TeamReviewPeriodService service;
    @MockitoBean TokenService tokenService;
    @MockitoBean ValidationService validationService;
    @MockitoBean MessageSource messageSource;
    UUID actor=UUID.randomUUID();
    UsernamePasswordAuthenticationToken user(String authority) {
        return new UsernamePasswordAuthenticationToken(actor,null,List.of(new SimpleGrantedAuthority(authority)));
    }
    @Test void eachTeamAuthorityMayReadScopedPeriodsWithoutAdministrativePermission() throws Exception {
        when(service.periods(actor)).thenReturn(new TeamReviewPeriodsDto(List.of(),null));
        for(var authority:List.of("CAN_REVIEW_INDIVIDUAL_KPI","CAN_REVIEW_KPI_ASSESSMENT","CAN_REVIEW_ATTITUDE_EVALUATION"))
            mvc.perform(get("/api/team-reviews/periods").with(authentication(user(authority))))
                .andExpect(status().isOk()).andExpect(jsonPath("periods").isArray());
        verify(service,times(3)).periods(actor);
    }
    @Test void unrelatedAuthoritiesAndAnonymousCannotReadTeamPeriods() throws Exception {
        when(messageSource.getMessage(anyString(),any(),any(Locale.class))).thenReturn("Forbidden");
        for(var authority:List.of("ROLE_USER","CAN_MANAGE_ANNUAL_KPI_REVIEW_PERIOD","CAN_AUTHORIZE_INDIVIDUAL_KPI_ASSISTANCE"))
            mvc.perform(get("/api/team-reviews/periods").with(authentication(user(authority)))).andExpect(status().isForbidden());
        mvc.perform(get("/api/team-reviews/periods")).andExpect(status().isUnauthorized());
        verifyNoInteractions(service);
    }
}
