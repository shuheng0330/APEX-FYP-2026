package com.tbm.careerpathlearning.controller;

import com.tbm.careerpathlearning.config.SecurityConfig;
import com.tbm.careerpathlearning.dto.AnnualKpiReviewPeriodDto;
import com.tbm.careerpathlearning.enums.AnnualKpiReviewPeriodStatus;
import com.tbm.careerpathlearning.exception.BadRequestException;
import com.tbm.careerpathlearning.service.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
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
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(AnnualKpiReviewPeriodController.class)
@Import(SecurityConfig.class)
@TestPropertySource(properties = "frontend.origin=http://localhost:4200")
class AnnualKpiReviewPeriodControllerTest {
    private static final String BASE = "/api/annual-kpi-review-periods";
    private final UUID actor = UUID.randomUUID();
    @Autowired private MockMvc mvc;
    @MockitoBean private AnnualKpiReviewPeriodService service;
    @MockitoBean private TokenService tokenService;
    @MockitoBean private ValidationService validationService;
    @MockitoBean private MessageSource messageSource;

    private UsernamePasswordAuthenticationToken admin() {
        return new UsernamePasswordAuthenticationToken(actor, null,
                List.of(new SimpleGrantedAuthority("CAN_MANAGE_EVALUATION_CYCLE")));
    }

    @Test
    void anonymousIsUnauthorised() throws Exception {
        mvc.perform(get(BASE)).andExpect(status().isUnauthorized());
        verifyNoInteractions(service);
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "/1", "/roles"})
    void unprivilegedStaffCannotReadReviewPeriodConfiguration(String route) throws Exception {
        when(messageSource.getMessage(anyString(), any(), any(Locale.class))).thenReturn("Forbidden");
        var employee = new UsernamePasswordAuthenticationToken(actor, null,
                List.of(new SimpleGrantedAuthority("ROLE_USER")));
        mvc.perform(get(BASE + route).with(authentication(employee))).andExpect(status().isForbidden());
        verifyNoInteractions(service);
    }

    @Test
    void unprivilegedStaffCannotCreateOrModify() throws Exception {
        when(messageSource.getMessage(anyString(), any(), any(Locale.class))).thenReturn("Forbidden");
        var employee = new UsernamePasswordAuthenticationToken(actor, null,
                List.of(new SimpleGrantedAuthority("ROLE_USER")));
        mvc.perform(post(BASE).with(authentication(employee)).contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isForbidden());
        mvc.perform(put(BASE + "/1").with(authentication(employee)).contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isForbidden());
        mvc.perform(delete(BASE + "/1").with(authentication(employee))).andExpect(status().isForbidden());
        mvc.perform(post(BASE + "/1/publish").with(authentication(employee))).andExpect(status().isForbidden());
        verifyNoInteractions(service);
    }

    @Test
    void createDefaultsToDraftAndUsesAuthenticatedActor() throws Exception {
        var dto = new AnnualKpiReviewPeriodDto(); dto.setId(1L); dto.setStatus(AnnualKpiReviewPeriodStatus.DRAFT);
        when(service.create(any(), eq(false), eq(actor))).thenReturn(dto);
        mvc.perform(post(BASE).with(authentication(admin())).contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.status").value("DRAFT"));
        verify(service).create(any(), eq(false), eq(actor));
    }

    @Test
    void createCanExplicitlyPublish() throws Exception {
        when(service.create(any(), eq(true), eq(actor))).thenReturn(new AnnualKpiReviewPeriodDto());
        mvc.perform(post(BASE + "?publish=true").with(authentication(admin()))
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isCreated());
        verify(service).create(any(), eq(true), eq(actor));
    }

    @Test
    void updateAndPublishUseAuthenticatedActor() throws Exception {
        when(service.update(eq(1L), any(), eq(actor))).thenReturn(new AnnualKpiReviewPeriodDto());
        when(service.publish(1L, actor)).thenReturn(new AnnualKpiReviewPeriodDto());
        mvc.perform(put(BASE + "/1").with(authentication(admin())).contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isOk());
        mvc.perform(post(BASE + "/1/publish").with(authentication(admin()))).andExpect(status().isOk());
        verify(service).update(eq(1L), any(), eq(actor));
        verify(service).publish(1L, actor);
    }

    @Test
    void detailListAndRoleOptionsAreAccessibleToAdmin() throws Exception {
        var dto = new AnnualKpiReviewPeriodDto(); dto.setId(1L); dto.setName("2027 Annual KPI Review");
        when(service.get(1L)).thenReturn(dto);
        when(service.list()).thenReturn(List.of(dto));
        var role = new AnnualKpiReviewPeriodDto.RoleConfiguration(); role.setRoleId(2L);
        when(service.availableRoles()).thenReturn(List.of(role));
        mvc.perform(get(BASE + "/1").with(authentication(admin()))).andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value(dto.getName()));
        mvc.perform(get(BASE).with(authentication(admin()))).andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(1));
        mvc.perform(get(BASE + "/roles").with(authentication(admin()))).andExpect(status().isOk())
                .andExpect(jsonPath("$[0].roleId").value(2));
    }

    @Test
    void previewPassesTheExistingPeriodExclusionWithoutSaving() throws Exception {
        when(service.preview(any(), eq(1L))).thenReturn(new AnnualKpiReviewPeriodDto());
        mvc.perform(post(BASE + "/preview?excludedPeriodId=1").with(authentication(admin()))
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isOk());
        verify(service).preview(any(), eq(1L));
        verify(service, never()).create(any(), anyBoolean(), any());
    }

    @Test
    void deletionReturnsNoContent() throws Exception {
        mvc.perform(delete(BASE + "/1").with(authentication(admin()))).andExpect(status().isNoContent());
        verify(service).delete(1L);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "{\"name\":\"", "{\"annualKpiConsolidationMethod\":\"UNKNOWN\"}",
            "{\"startDate\":\"not-a-date\"}", "{\"roleConfigurations\":[{\"roleId\":null}]}",
            "{\"roleConfigurations\":[null]}", "{\"roleConfigurations\":null}",
            "{\"roleConfigurations\":[{\"roleId\":-1}]}",
            "{\"roleConfigurations\":[{\"roleId\":1,\"reviewFrequency\":\"WEEKLY\"}]}"
    })
    void malformedInputsReturnBadRequestWithoutCallingService(String json) throws Exception {
        mvc.perform(post(BASE).with(authentication(admin())).contentType(MediaType.APPLICATION_JSON).content(json))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(service);
    }

    @Test
    void businessErrorsUseTheExistingErrorResponse() throws Exception {
        when(messageSource.getMessage(anyString(), any(), any(Locale.class))).thenReturn("Bad Request");
        when(service.publish(1L, actor)).thenThrow(new BadRequestException("Configured weights must total 100 percent"));
        mvc.perform(post(BASE + "/1/publish").with(authentication(admin())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Configured weights must total 100 percent"));
    }

    @Test
    void thereIsNoCloseEndpointWhilePrerequisitesAreUnconfirmed() throws Exception {
        mvc.perform(post(BASE + "/1/close").with(authentication(admin()))).andExpect(status().isMethodNotAllowed());
        verifyNoInteractions(service);
    }
}
