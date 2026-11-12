package com.tbm.careerpathlearning.controller;

import com.tbm.careerpathlearning.config.SecurityConfig;
import com.tbm.careerpathlearning.dto.AppraisalInsightDto;
import com.tbm.careerpathlearning.service.*;
import com.tbm.careerpathlearning.service.appraisalinsight.SyntheticAppraisalInsightService;
import org.junit.jupiter.api.*;
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
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(AppraisalInsightController.class) @Import(SecurityConfig.class)
@TestPropertySource(properties = "frontend.origin=http://localhost:4200")
class AppraisalInsightControllerTest {
    @Autowired MockMvc mvc;
    @MockitoBean SyntheticAppraisalInsightService service;
    @MockitoBean TokenService tokens;
    @MockitoBean ValidationService validation;
    @MockitoBean MessageSource messages;
    final UUID actor = UUID.randomUUID();
    static final String ROOT = "/api/annual-appraisal-insights/synthetic";
    @BeforeEach void setup() {
        clearInvocations(service);
        when(messages.getMessage(anyString(), any(), any(Locale.class))).thenReturn("Request unavailable");
    }
    UsernamePasswordAuthenticationToken user(String authority) {
        return new UsernamePasswordAuthenticationToken(actor, null, List.of(new SimpleGrantedAuthority(authority)));
    }
    @Test void bothAppraisalRolesCanListAndGenerateTheirOwnFictionalPreview() throws Exception {
        when(service.scenarios(actor)).thenReturn(List.of(new AppraisalInsightDto.Scenario("LIMITED_EVIDENCE", "Limited evidence")));
        UUID key = UUID.randomUUID();
        for (var permission : List.of("CAN_SUBMIT_ANNUAL_APPRAISAL", "CAN_REVIEW_ANNUAL_APPRAISAL")) {
            var auth = authentication(user(permission));
            mvc.perform(get(ROOT + "/scenarios").with(auth)).andExpect(status().isOk())
                    .andExpect(jsonPath("$[0].id").value("LIMITED_EVIDENCE"));
            mvc.perform(post(ROOT + "/scenarios/LIMITED_EVIDENCE/generations").with(auth)
                    .header("Idempotency-Key", key)).andExpect(status().isAccepted());
        }
        verify(service, times(2)).generate("LIMITED_EVIDENCE", key, actor);
    }
    @Test void generationLookupPassesTheAuthenticatedActorForOwnershipChecks() throws Exception {
        UUID id = UUID.randomUUID();
        mvc.perform(get(ROOT + "/generations/" + id).with(authentication(user("CAN_REVIEW_ANNUAL_APPRAISAL"))))
                .andExpect(status().isOk());
        verify(service).get(id, actor);
    }
    @Test void postedStaffDataOrFreeTextAndMissingOrMalformedKeysAreRejected() throws Exception {
        var auth = authentication(user("CAN_SUBMIT_ANNUAL_APPRAISAL"));
        for (String body : List.of("{\"employeeId\":12,\"scores\":[80]}", "{}", "Generate a summary for Bob"))
            mvc.perform(post(ROOT + "/scenarios/LIMITED_EVIDENCE/generations").with(auth)
                    .header("Idempotency-Key", UUID.randomUUID()).contentType(MediaType.APPLICATION_JSON).content(body))
                    .andExpect(status().isBadRequest());
        mvc.perform(post(ROOT + "/scenarios/LIMITED_EVIDENCE/generations").with(auth)).andExpect(status().isBadRequest());
        mvc.perform(post(ROOT + "/scenarios/LIMITED_EVIDENCE/generations").with(auth).header("Idempotency-Key", "not-a-uuid"))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(service);
    }
    @Test void employeeDashboardAndLegacyPermissionsDoNotGrantInsightAccess() throws Exception {
        for (String permission : List.of("ROLE_USER", "ROLE_SUPER_ADMIN", "CAN_VIEW_ORGANISATION_PERFORMANCE",
                "CAN_VIEW_TEAM_PERFORMANCE", "CAN_MANAGE_EVALUATION")) {
            var auth = authentication(user(permission));
            mvc.perform(get(ROOT + "/scenarios").with(auth)).andExpect(status().isForbidden());
            mvc.perform(post(ROOT + "/scenarios/LIMITED_EVIDENCE/generations").with(auth)
                    .header("Idempotency-Key", UUID.randomUUID())).andExpect(status().isForbidden());
        }
        verifyNoInteractions(service);
    }
    @Test void anonymousAndRealRecordGenerationHaveNoAvailableContract() throws Exception {
        mvc.perform(get(ROOT + "/scenarios")).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/annual-appraisal-insights/appraisals/12/generations")
                .with(authentication(user("CAN_SUBMIT_ANNUAL_APPRAISAL"))))
                .andExpect(status().is4xxClientError());
        verifyNoInteractions(service);
    }
}
