package com.tbm.careerpathlearning.controller;

import com.tbm.careerpathlearning.config.SecurityConfig;
import com.tbm.careerpathlearning.dto.*;
import com.tbm.careerpathlearning.enums.*;
import com.tbm.careerpathlearning.exception.BadRequestException;
import com.tbm.careerpathlearning.exception.KpiVersionConflictException;
import com.tbm.careerpathlearning.service.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.MessageSource;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.http.HttpHeaders;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.util.*;

import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest({KpiCentralResultController.class, CompanyKpiPlanController.class})
@Import(SecurityConfig.class)
@TestPropertySource(properties = "frontend.origin=http://localhost:4200")
class KpiCentralResultControllerTest {
    private static final String ROOT = "/api/kpi-central-results";
    private static final String DRAFT = "{\"planVersionId\":101,\"checkpointId\":11,\"items\":[]}";

    @Autowired MockMvc mvc;
    @MockitoBean KpiCentralResultService service;
    @MockitoBean KpiPlanService plans;
    @MockitoBean TokenService tokenService;
    @MockitoBean ValidationService validationService;
    @MockitoBean MessageSource messageSource;
    private final UUID actor = UUID.randomUUID();

    @BeforeEach void messages() {
        when(messageSource.getMessage(anyString(), any(), any(Locale.class))).thenReturn("Request not allowed");
    }

    private RequestPostProcessor user(String... permissions) {
        return authentication(new UsernamePasswordAuthenticationToken(actor, null,
                Arrays.stream(permissions).map(SimpleGrantedAuthority::new).toList()));
    }

    @ParameterizedTest
    @ValueSource(strings = {"CAN_MANAGE_COMPANY_KPI", "CAN_ENTER_COMPANY_KPI_RESULTS", "CAN_MANAGE_DEPARTMENT_KPI"})
    void permittedWorkspacesForwardAuthenticatedActorAndScopeToService(String permission) throws Exception {
        var auth = user(permission);
        mvc.perform(get(ROOT + "/periods").with(auth)).andExpect(status().isOk());
        mvc.perform(get(ROOT + "/options").param("reviewPeriodId", "1").with(auth)).andExpect(status().isOk());
        mvc.perform(get(ROOT + "/checkpoints").param("planVersionId", "101").with(auth)).andExpect(status().isOk());
        mvc.perform(get(ROOT).param("reviewPeriodId", "1").with(auth)).andExpect(status().isOk());
        mvc.perform(get(ROOT + "/301").with(auth)).andExpect(status().isOk());
        verify(service).periods(actor);
        verify(service).options(1L, actor);
        verify(service).checkpoints(101L, actor);
        verify(service).list(1L, actor);
        verify(service).get(301L, actor);
        verifyNoMoreInteractions(service);
    }

    @ParameterizedTest
    @ValueSource(strings = {"CAN_MANAGE_COMPANY_KPI", "CAN_ENTER_COMPANY_KPI_RESULTS", "CAN_MANAGE_DEPARTMENT_KPI"})
    void permittedUsersCanCreateAndUpdateIncompleteDrafts(String permission) throws Exception {
        var auth = user(permission);
        mvc.perform(post(ROOT).with(auth).contentType(MediaType.APPLICATION_JSON).content(DRAFT))
                .andExpect(status().isCreated());
        verify(service).create(argThat(r -> r.getPlanVersionId().equals(101L)
                && r.getCheckpointId().equals(11L) && r.getItems().isEmpty()), eq(actor));
        mvc.perform(put(ROOT + "/301").with(auth).contentType(MediaType.APPLICATION_JSON)
                .content("{\"planVersionId\":101,\"checkpointId\":11,\"expectedLockVersion\":7,"
                        + "\"items\":[{\"kpiId\":1001,\"point\":null,\"comment\":\"Still checking\"}]}"))
                .andExpect(status().isOk());
        verify(service).update(eq(301L), argThat(r -> r.getExpectedLockVersion().equals(7L)
                && r.getItems().get(0).getPoint() == null
                && r.getItems().get(0).getActualAchievement() == null), eq(actor));
    }

    @ParameterizedTest
    @ValueSource(strings = {"ROLE_USER", "ROLE_SUPER_ADMIN", "ROLE_MD", "ROLE_HR", "ROLE_HOD",
            "CAN_APPROVE_DEPARTMENT_KPI", "CAN_REVIEW_KPI_ASSESSMENT", "CAN_MANAGE_ANNUAL_KPI_REVIEW_PERIOD"})
    void rolesAndUnrelatedAuthoritiesDoNotImplyResultEntryPermission(String permission) throws Exception {
        var auth = user(permission);
        mvc.perform(get(ROOT + "/periods").with(auth)).andExpect(status().isForbidden());
        mvc.perform(get(ROOT + "/301").with(auth)).andExpect(status().isForbidden());
        mvc.perform(post(ROOT).with(auth).contentType(MediaType.APPLICATION_JSON).content(DRAFT))
                .andExpect(status().isForbidden());
        mvc.perform(put(ROOT + "/301").with(auth).contentType(MediaType.APPLICATION_JSON).content(DRAFT))
                .andExpect(status().isForbidden());
        verifyNoInteractions(service);
    }

    @Test void anonymousCallsRequireAuthentication() throws Exception {
        mvc.perform(get(ROOT + "/periods")).andExpect(status().isUnauthorized());
        mvc.perform(post(ROOT).contentType(MediaType.APPLICATION_JSON).content(DRAFT)).andExpect(status().isUnauthorized());
        verifyNoInteractions(service);
    }

    @Test void combinedPermissionsStillReachScopedServiceChecks() throws Exception {
        mvc.perform(get(ROOT + "/options").param("reviewPeriodId", "1")
                        .with(user("CAN_ENTER_COMPANY_KPI_RESULTS", "CAN_MANAGE_DEPARTMENT_KPI")))
                .andExpect(status().isOk());
        verify(service).options(1L, actor);
    }

    @Test void onlyCompanyOwnerCanListAssignAndRevokeDelegates() throws Exception {
        var auth = user("CAN_MANAGE_COMPANY_KPI");
        var delegate = UUID.randomUUID();
        mvc.perform(get(ROOT + "/periods/1/delegates").with(auth)).andExpect(status().isOk());
        mvc.perform(post(ROOT + "/periods/1/delegates").with(auth).contentType(MediaType.APPLICATION_JSON)
                .content("{\"staffId\":\"" + delegate + "\"}")).andExpect(status().isCreated());
        mvc.perform(post(ROOT + "/periods/1/delegates/701/revoke").with(auth)).andExpect(status().isOk());
        verify(service).delegates(1L, actor);
        verify(service).assignDelegate(1L, delegate, actor);
        verify(service).revokeDelegate(1L, 701L, actor);
    }

    @ParameterizedTest
    @ValueSource(strings = {"CAN_ENTER_COMPANY_KPI_RESULTS", "CAN_MANAGE_DEPARTMENT_KPI", "ROLE_SUPER_ADMIN"})
    void delegateAndDepartmentPermissionDoNotGrantDelegationManagement(String permission) throws Exception {
        var auth = user(permission);
        mvc.perform(get(ROOT + "/periods/1/delegates").with(auth)).andExpect(status().isForbidden());
        mvc.perform(post(ROOT + "/periods/1/delegates").with(auth).contentType(MediaType.APPLICATION_JSON)
                .content("{\"staffId\":\"" + UUID.randomUUID() + "\"}")).andExpect(status().isForbidden());
        mvc.perform(post(ROOT + "/periods/1/delegates/701/revoke").with(auth)).andExpect(status().isForbidden());
        verifyNoInteractions(service);
    }

    @Test void resultEntryPermissionDoesNotGrantCompanyPlanEditingOrPublishing() throws Exception {
        var auth = user("CAN_ENTER_COMPANY_KPI_RESULTS");
        mvc.perform(put("/api/company-kpi-plans/10").with(auth).contentType(MediaType.APPLICATION_JSON)
                .content("{\"items\":[]}")).andExpect(status().isForbidden());
        mvc.perform(post("/api/company-kpi-plans/10/publish").with(auth)).andExpect(status().isForbidden());
        verifyNoInteractions(plans, service);
    }

    @ParameterizedTest
    @ValueSource(strings = {"1.5", "3.0", "\"4\"", "true", "[]", "{}", "2147483648"})
    void pointsMustBeStrictJsonIntegersOnCreateAndUpdate(String value) throws Exception {
        var body = "{\"planVersionId\":101,\"checkpointId\":11,\"expectedLockVersion\":0,"
                + "\"items\":[{\"kpiId\":1001,\"point\":" + value + "}]}";
        var auth = user("CAN_MANAGE_COMPANY_KPI");
        mvc.perform(post(ROOT).with(auth).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest());
        mvc.perform(put(ROOT + "/301").with(auth).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(service);
    }

    @Test void detailExposesReadinessAndOnlyServerCalculatedActions() throws Exception {
        var dto = new KpiCentralResultDto();
        dto.setId(301L);
        dto.setStatus(KpiCentralResultStatus.DRAFT);
        dto.setReviewFrequency(ReviewFrequency.MONTHLY);
        dto.setAllowedActions(List.of("UPDATE_DRAFT", "UPLOAD_EVIDENCE"));
        dto.setPublicationBlockers(List.of("Select a Result Point for Revenue"));
        var item = new KpiCentralResultDto.Item();
        item.setKpi(new KpiItemDto());
        dto.setItems(List.of(item));
        when(service.get(301L, actor)).thenReturn(dto);
        mvc.perform(get(ROOT + "/301").with(user("CAN_MANAGE_COMPANY_KPI"))).andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("DRAFT"))
                .andExpect(jsonPath("$.items[0].point").isEmpty())
                .andExpect(jsonPath("$.allowedActions.length()").value(2))
                .andExpect(jsonPath("$.allowedActions[0]").value("UPDATE_DRAFT"))
                .andExpect(jsonPath("$.allowedActions[1]").value("UPLOAD_EVIDENCE"))
                .andExpect(jsonPath("$.publicationBlockers[0]").value("Select a Result Point for Revenue"));
    }

    @Test void serviceScopeAndConflictFailuresRetainTheirHttpMeaning() throws Exception {
        var auth = user("CAN_ENTER_COMPANY_KPI_RESULTS");
        when(service.get(301L, actor)).thenThrow(new AccessDeniedException("Period assignment required"));
        mvc.perform(get(ROOT + "/301").with(auth)).andExpect(status().isForbidden());
        when(service.create(any(), eq(actor))).thenThrow(new BadRequestException("This period uses the existing all-level assessment workflow"));
        mvc.perform(post(ROOT).with(auth).contentType(MediaType.APPLICATION_JSON).content(DRAFT))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("This period uses the existing all-level assessment workflow"));
        when(service.update(eq(301L), any(), eq(actor))).thenThrow(new KpiVersionConflictException("Refresh before saving again."));
        mvc.perform(put(ROOT + "/301").with(auth).contentType(MediaType.APPLICATION_JSON).content(DRAFT))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.message").value("Refresh before saving again."));
    }

    @Test void contextQueryParametersAreRequiredAndTyped() throws Exception {
        var auth = user("CAN_MANAGE_COMPANY_KPI");
        for (var path : List.of(ROOT, ROOT + "/options", ROOT + "/checkpoints")) {
            mvc.perform(get(path).with(auth)).andExpect(status().isBadRequest());
        }
        mvc.perform(get(ROOT + "/options").with(auth).param("reviewPeriodId", "not-an-id"))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(service);
    }

    @ParameterizedTest
    @ValueSource(strings = {"CAN_MANAGE_COMPANY_KPI", "CAN_ENTER_COMPANY_KPI_RESULTS", "CAN_MANAGE_DEPARTMENT_KPI"})
    void publicationCorrectionAndHistoryForwardActorAndDecision(String permission) throws Exception {
        var auth = user(permission);
        mvc.perform(post(ROOT + "/301/publish").with(auth).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"expectedLockVersion\":7}"))
                .andExpect(status().isOk());
        verify(service).publish(eq(301L), argThat(r -> r.getExpectedLockVersion().equals(7L) && r.getReason() == null), eq(actor));
        mvc.perform(post(ROOT + "/301/corrections").with(auth).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"expectedLockVersion\":7,\"reason\":\" Correct actual result \"}"))
                .andExpect(status().isCreated());
        verify(service).correct(eq(301L), argThat(r -> r.getExpectedLockVersion().equals(7L)
                && r.getReason().equals(" Correct actual result ")), eq(actor));
        mvc.perform(get(ROOT + "/301/history").with(auth)).andExpect(status().isOk());
        verify(service).history(301L, actor);
    }

    @Test void evidenceMutationRoutesReturnNewLockAndNeverExposeStorageKeys() throws Exception {
        var auth = user("CAN_MANAGE_COMPANY_KPI");
        var metadata = new KpiCentralResultEvidenceDto();
        metadata.setId(701L); metadata.setItemId(1001L); metadata.setOriginalFilename("evidence.png");
        metadata.setContentType("image/png"); metadata.setSizeBytes(3L); metadata.setUploadedBy(actor);
        var input = new MockMultipartFile("file", "evidence.png", "image/png", new byte[]{1, 2, 3});
        when(service.upload(eq(1001L), eq(7L), any(), eq(actor)))
                .thenReturn(new KpiCentralResultEvidenceMutationDto(metadata, 8L));
        mvc.perform(multipart(ROOT + "/items/1001/evidence").file(input).param("expectedLockVersion", "7").with(auth))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.resultLockVersion").value(8))
                .andExpect(jsonPath("$.evidence.id").value(701))
                .andExpect(jsonPath("$.evidence.storageKey").doesNotExist());
        verify(service).upload(eq(1001L), eq(7L), argThat(file -> file.getOriginalFilename().equals("evidence.png")), eq(actor));
        when(service.evidence(1001L, actor)).thenReturn(List.of(metadata));
        mvc.perform(get(ROOT + "/items/1001/evidence").with(auth)).andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(701))
                .andExpect(jsonPath("$[0].storageKey").doesNotExist());
        when(service.deleteEvidence(701L, 8L, actor)).thenReturn(new KpiCentralResultEvidenceMutationDto(metadata, 9L));
        mvc.perform(delete(ROOT + "/evidence/701").param("expectedLockVersion", "8").with(auth))
                .andExpect(status().isOk()).andExpect(jsonPath("$.resultLockVersion").value(9));
        verify(service).deleteEvidence(701L, 8L, actor);
    }

    @Test void evidenceDownloadUsesPrivateAttachmentAndNosniffHeaders() throws Exception {
        var metadata = new KpiCentralResultEvidenceDto();
        metadata.setOriginalFilename("evidence.png"); metadata.setContentType("image/png");
        when(service.download(701L, actor)).thenReturn(new KpiCentralResultService.Download(
                new ByteArrayResource(new byte[]{1, 2, 3}), metadata));
        mvc.perform(get(ROOT + "/evidence/701").with(user("CAN_MANAGE_COMPANY_KPI")))
                .andExpect(status().isOk())
                .andExpect(content().contentType("image/png"))
                .andExpect(content().bytes(new byte[]{1, 2, 3}))
                .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "private, no-store"))
                .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION, org.hamcrest.Matchers.startsWith("attachment;")))
                .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION, org.hamcrest.Matchers.containsString("evidence.png")));
        verify(service).download(701L, actor);
    }

    @ParameterizedTest
    @ValueSource(strings = {"ROLE_USER", "ROLE_SUPER_ADMIN", "CAN_REVIEW_KPI_ASSESSMENT", "CAN_APPROVE_DEPARTMENT_KPI"})
    void newRoutesDoNotGrantImplicitCentralResultAccess(String permission) throws Exception {
        var auth = user(permission);
        for (var action : List.of("publish", "corrections")) {
            mvc.perform(post(ROOT + "/301/" + action).with(auth).contentType(MediaType.APPLICATION_JSON)
                            .content("{\"expectedLockVersion\":0,\"reason\":\"Fix\"}"))
                    .andExpect(status().isForbidden());
        }
        mvc.perform(get(ROOT + "/301/history").with(auth)).andExpect(status().isForbidden());
        mvc.perform(get(ROOT + "/items/1001/evidence").with(auth)).andExpect(status().isForbidden());
        mvc.perform(get(ROOT + "/evidence/701").with(auth)).andExpect(status().isForbidden());
        mvc.perform(delete(ROOT + "/evidence/701").param("expectedLockVersion", "0").with(auth))
                .andExpect(status().isForbidden());
        mvc.perform(multipart(ROOT + "/items/1001/evidence")
                        .file(new MockMultipartFile("file", "a.png", "image/png", new byte[]{1}))
                        .param("expectedLockVersion", "0").with(auth)).andExpect(status().isForbidden());
        verifyNoInteractions(service);
    }

    @Test void evidenceMutationsRequireTypedLocksAndAFile() throws Exception {
        var auth = user("CAN_MANAGE_COMPANY_KPI");
        mvc.perform(multipart(ROOT + "/items/1001/evidence")
                        .file(new MockMultipartFile("file", new byte[]{1})).with(auth))
                .andExpect(status().isBadRequest());
        mvc.perform(multipart(ROOT + "/items/1001/evidence").param("expectedLockVersion", "0").with(auth))
                .andExpect(status().isBadRequest());
        mvc.perform(delete(ROOT + "/evidence/701").with(auth)).andExpect(status().isBadRequest());
        mvc.perform(delete(ROOT + "/evidence/701").param("expectedLockVersion", "wrong").with(auth))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(service);
    }

    @Test void publicationCorrectionAndEvidenceErrorsRetainServiceHttpSemantics() throws Exception {
        var auth = user("CAN_MANAGE_COMPANY_KPI");
        when(service.publish(eq(301L), any(), eq(actor))).thenThrow(new BadRequestException("Select a Result Point for Revenue"));
        mvc.perform(post(ROOT + "/301/publish").with(auth).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"expectedLockVersion\":0}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.message").value("Select a Result Point for Revenue"));
        when(service.correct(eq(301L), any(), eq(actor))).thenThrow(new KpiVersionConflictException("Refresh before correcting"));
        mvc.perform(post(ROOT + "/301/corrections").with(auth).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"expectedLockVersion\":0,\"reason\":\"Fix\"}"))
                .andExpect(status().isConflict());
        when(service.download(701L, actor)).thenThrow(new AccessDeniedException("Foreign evidence"));
        mvc.perform(get(ROOT + "/evidence/701").with(auth)).andExpect(status().isForbidden());
        when(service.deleteEvidence(701L, 0L, actor)).thenThrow(new KpiVersionConflictException("Stale evidence"));
        mvc.perform(delete(ROOT + "/evidence/701").param("expectedLockVersion", "0").with(auth))
                .andExpect(status().isConflict());
    }

    @Test void cancellationAndAlternativeCorrectionRoutesAreNotIntroduced() throws Exception {
        var auth = user("CAN_MANAGE_COMPANY_KPI");
        for (var path : List.of("/301/cancel", "/301/correct")) {
            mvc.perform(post(ROOT + path).with(auth).contentType(MediaType.APPLICATION_JSON).content("{}"))
                    .andExpect(status().is4xxClientError());
        }
        verifyNoInteractions(service);
    }
}
