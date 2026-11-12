package com.tbm.careerpathlearning.controller;

import com.tbm.careerpathlearning.config.SecurityConfig;
import com.tbm.careerpathlearning.dto.ReviewPeriodClosureDto;
import com.tbm.careerpathlearning.dto.ReviewPeriodClosureDto.*;
import com.tbm.careerpathlearning.enums.AnnualKpiReviewPeriodStatus;
import com.tbm.careerpathlearning.exception.ReviewPeriodClosureBlockedException;
import com.tbm.careerpathlearning.service.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.MessageSource;
import org.springframework.context.annotation.Import;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import java.time.*;
import java.util.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(AnnualReviewPeriodClosureController.class)
@Import(SecurityConfig.class)
@TestPropertySource(properties="frontend.origin=http://localhost:4200")
class AnnualReviewPeriodClosureControllerTest {
    static final String BASE="/api/annual-kpi-review-periods/1";
    final UUID actor=UUID.randomUUID();
    @Autowired MockMvc mvc;
    @MockitoBean AnnualReviewPeriodClosureService service;
    @MockitoBean TokenService tokens;
    @MockitoBean ValidationService validation;
    @MockitoBean MessageSource messages;
    UsernamePasswordAuthenticationToken user(String permission) {return new UsernamePasswordAuthenticationToken(actor,null,List.of(new SimpleGrantedAuthority(permission)));}
    ReviewPeriodClosureDto state(boolean ready) {
        return new ReviewPeriodClosureDto(1L,AnnualKpiReviewPeriodStatus.OPEN,ready,LocalDate.of(2028,1,1),OffsetDateTime.parse("2028-01-01T08:00:00+08:00"),null,null,2,ready?2:1,new Completion(8,ready?8:7),new Completion(2,2),
                ready?List.of():List.of(new Blocker(Code.KPI_SELF_ASSESSMENT,3L,"Fictional Employee","Test Department",4L,LocalDate.of(2027,10,1),LocalDate.of(2027,12,31),"Employee KPI Self-Assessment has not been submitted")));
    }
    @Test void authorisedReadinessReturnsStructuredTaskAndEmployeeCountsWithoutWriting() throws Exception {
        when(service.readiness(1L,actor)).thenReturn(state(false));
        mvc.perform(get(BASE+"/closure-readiness").with(authentication(user("CAN_MANAGE_ANNUAL_KPI_REVIEW_PERIOD"))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalEmployees").value(2)).andExpect(jsonPath("$.completedEmployees").value(1))
                .andExpect(jsonPath("$.kpiReviews.required").value(8)).andExpect(jsonPath("$.blockers[0].code").value("KPI_SELF_ASSESSMENT"))
                .andExpect(jsonPath("$.blockers[0].checkpointId").value(4));
        verify(service,never()).close(any(),any());
    }
    @Test void blockedCloseReturnsFreshReadinessAsConflict() throws Exception {
        when(service.close(1L,actor)).thenThrow(new ReviewPeriodClosureBlockedException(state(false)));
        mvc.perform(post(BASE+"/close").with(authentication(user("CAN_MANAGE_ANNUAL_KPI_REVIEW_PERIOD"))))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.canClose").value(false)).andExpect(jsonPath("$.blockers[0].employeeName").value("Fictional Employee"));
    }
    @Test void successfulCloseReturnsRecordedMetadataAndIsNotAFrontendCalculation() throws Exception {
        var closed=new ReviewPeriodClosureDto(1L,AnnualKpiReviewPeriodStatus.CLOSED,false,LocalDate.of(2028,1,1),OffsetDateTime.parse("2028-01-01T08:00:00+08:00"),OffsetDateTime.parse("2028-01-01T08:00:00+08:00"),"HR Administrator",2,2,new Completion(8,8),new Completion(2,2),List.of());
        when(service.close(1L,actor)).thenReturn(closed);
        mvc.perform(post(BASE+"/close").with(authentication(user("CAN_MANAGE_ANNUAL_KPI_REVIEW_PERIOD"))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("CLOSED")).andExpect(jsonPath("$.closedByName").value("HR Administrator"))
                .andExpect(jsonPath("$.closedAt").isNotEmpty()).andExpect(jsonPath("$.closedBy").doesNotExist());
        verify(service).close(1L,actor);
    }
    @ParameterizedTest @ValueSource(strings={"ROLE_USER","CAN_REVIEW_ANNUAL_APPRAISAL","CAN_SUBMIT_ANNUAL_APPRAISAL","CAN_VIEW_ORGANISATION_PERFORMANCE","CAN_MANAGE_EVALUATION_CYCLE"})
    void otherPermissionsDoNotAuthoriseClosureOrRosterAccess(String permission) throws Exception {
        when(messages.getMessage(anyString(),any(),any(Locale.class))).thenReturn("Forbidden");
        mvc.perform(get(BASE+"/closure-readiness").with(authentication(user(permission)))).andExpect(status().isForbidden());
        mvc.perform(post(BASE+"/close").with(authentication(user(permission)))).andExpect(status().isForbidden());
        verifyNoInteractions(service);
    }
    @Test void anonymousCannotReadOrClose() throws Exception {
        mvc.perform(get(BASE+"/closure-readiness")).andExpect(status().isUnauthorized());
        mvc.perform(post(BASE+"/close")).andExpect(status().isUnauthorized());verifyNoInteractions(service);
    }
}
