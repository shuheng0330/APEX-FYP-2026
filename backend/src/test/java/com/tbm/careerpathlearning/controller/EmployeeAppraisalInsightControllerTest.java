package com.tbm.careerpathlearning.controller;

import com.tbm.careerpathlearning.config.SecurityConfig;
import com.tbm.careerpathlearning.dto.EmployeeInsightDto.*;
import com.tbm.careerpathlearning.exception.EmployeeInsightLimitException;
import com.tbm.careerpathlearning.service.*;
import com.tbm.careerpathlearning.service.appraisalinsight.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.MessageSource;
import org.springframework.context.annotation.Import;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import java.time.OffsetDateTime;
import java.util.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(EmployeeAppraisalInsightController.class) @Import(SecurityConfig.class)
@TestPropertySource(properties="frontend.origin=http://localhost:4200")
class EmployeeAppraisalInsightControllerTest {
    @Autowired MockMvc mvc;
    @MockitoBean EmployeeAppraisalInsightService service;
    @MockitoBean EmployeeInsightSubmissionService submissions;
    @MockitoBean TokenService tokens;
    @MockitoBean ValidationService validation;
    @MockitoBean MessageSource messages;
    final UUID actor=UUID.randomUUID();
    final String root="/api/annual-appraisals/participants/7/ai-insights";
    UsernamePasswordAuthenticationToken user(String authority) {
        return new UsernamePasswordAuthenticationToken(actor,null,List.of(new SimpleGrantedAuthority(authority)));
    }
    @BeforeEach void setup() {
        clearInvocations(service,submissions);
        when(messages.getMessage(anyString(),any(),any(Locale.class))).thenReturn("Request unavailable");
    }
    @Test void generateAndRegenerateAreExplicitAndHaveNoEvidenceBody() throws Exception {
        var auth=authentication(user("CAN_SUBMIT_ANNUAL_APPRAISAL"));
        var key=UUID.randomUUID();
        when(service.generate(7L,key,actor,false)).thenReturn(new Result("REUSED",null,null));
        when(service.generate(7L,key,actor,true)).thenReturn(new Result("NEWLY_QUEUED",null,null));
        mvc.perform(post(root).with(auth).header("Idempotency-Key",key)).andExpect(status().isOk());
        mvc.perform(post(root+"/regenerate").with(auth).header("Idempotency-Key",key)).andExpect(status().isAccepted());
        verify(service).generate(7L,key,actor,false); verify(service).generate(7L,key,actor,true);
        for (var body:List.of("{}","employee performance","{\"scores\":[80]}"))
            mvc.perform(post(root).with(auth).header("Idempotency-Key",UUID.randomUUID()).content(body))
                    .andExpect(status().isBadRequest());
        mvc.perform(post(root).with(auth)).andExpect(status().isBadRequest());
        mvc.perform(post(root).with(auth).header("Idempotency-Key","bad-key")).andExpect(status().isBadRequest());
        verifyNoMoreInteractions(service);
    }
    @Test void hrCanReadSubmittedButCannotReadOrGenerateUnsubmittedWork() throws Exception {
        var auth=authentication(user("CAN_REVIEW_ANNUAL_APPRAISAL"));
        mvc.perform(get("/api/annual-appraisals/12/ai-insight").param("submissionActivityId","19").with(auth))
                .andExpect(status().isOk());
        verify(submissions).get(12L,19L,actor);
        mvc.perform(get(root).with(auth)).andExpect(status().isForbidden());
        mvc.perform(post(root).with(auth).header("Idempotency-Key",UUID.randomUUID())).andExpect(status().isForbidden());
        mvc.perform(post(root+"/regenerate").with(auth).header("Idempotency-Key",UUID.randomUUID())).andExpect(status().isForbidden());
        verifyNoInteractions(service);
    }
    @Test void employeeAndDashboardAuthoritiesNeverGrantAccess() throws Exception {
        for (String permission:List.of("ROLE_USER","CAN_VIEW_ORGANISATION_PERFORMANCE","CAN_VIEW_TEAM_PERFORMANCE","ROLE_SUPER_ADMIN")) {
            var auth=authentication(user(permission));
            mvc.perform(get(root).with(auth)).andExpect(status().isForbidden());
            mvc.perform(get("/api/annual-appraisals/12/ai-insight").with(auth)).andExpect(status().isForbidden());
        }
        mvc.perform(get(root)).andExpect(status().isUnauthorized());
        verifyNoInteractions(service,submissions);
    }
    @Test void pollingIsScopedAndLimitsReturnSafeServerTiming() throws Exception {
        var auth=authentication(user("CAN_SUBMIT_ANNUAL_APPRAISAL"));
        UUID id=UUID.randomUUID();
        mvc.perform(get(root+"/generations/"+id).with(auth)).andExpect(status().isOk());
        verify(service).read(7L,id,actor);
        var now=OffsetDateTime.parse("2026-11-06T09:00:00+08:00");
        var limits=new Limits(now,5,4,now.plusDays(1),now.plusSeconds(60),null);
        when(service.generate(eq(7L),any(),eq(actor),eq(true))).thenThrow(new EmployeeInsightLimitException(
                new LimitError("COOLDOWN","Wait briefly before requesting a fresh insight.",now.plusSeconds(60),limits)));
        mvc.perform(post(root+"/regenerate").with(auth).header("Idempotency-Key",UUID.randomUUID()))
                .andExpect(status().isTooManyRequests()).andExpect(header().string("Retry-After","60"))
                .andExpect(jsonPath("$.reason").value("COOLDOWN")).andExpect(jsonPath("$.limits.remainingAttempts").value(4));
    }
}
