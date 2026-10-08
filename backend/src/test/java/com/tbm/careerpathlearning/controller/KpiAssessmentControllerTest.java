package com.tbm.careerpathlearning.controller;

import com.tbm.careerpathlearning.config.SecurityConfig;
import com.tbm.careerpathlearning.dto.*;
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
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import java.util.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest({KpiAssessmentController.class,FileController.class}) @Import(SecurityConfig.class)
@TestPropertySource(properties="frontend.origin=http://localhost:4200")
class KpiAssessmentControllerTest {
    @Autowired MockMvc mvc;
    @MockitoBean KpiAssessmentService service;
    @MockitoBean FileService genericFiles;
    @MockitoBean TokenService tokenService;
    @MockitoBean ValidationService validationService;
    @MockitoBean MessageSource messageSource;
    UUID actor=UUID.randomUUID();
    UsernamePasswordAuthenticationToken user(String authority) {return new UsernamePasswordAuthenticationToken(actor,null,List.of(new SimpleGrantedAuthority(authority)));}
    void messages(){when(messageSource.getMessage(anyString(),any(),any(Locale.class))).thenReturn("Forbidden");}
    @Test void employeeCanReadAndSaveIncompleteDraftAndSubmit() throws Exception {
        var auth=authentication(user("ROLE_USER"));
        mvc.perform(get("/api/kpi-assessments/periods").with(auth)).andExpect(status().isOk());
        mvc.perform(get("/api/kpi-assessments/checkpoints").param("reviewPeriodId","1").with(auth)).andExpect(status().isOk());
        mvc.perform(get("/api/kpi-assessments/mine").param("checkpointId","2").with(auth)).andExpect(status().isOk());
        mvc.perform(post("/api/kpi-assessments").with(auth).contentType(MediaType.APPLICATION_JSON)
                .content("{\"checkpointId\":2,\"items\":[]}")).andExpect(status().isCreated());
        verify(service).create(argThat(r->r.getCheckpointId()==2L && r.getItems().isEmpty()),eq(actor));
        mvc.perform(put("/api/kpi-assessments/5").with(auth).contentType(MediaType.APPLICATION_JSON)
                .content("{\"checkpointId\":2,\"items\":[]}")).andExpect(status().isOk());
        mvc.perform(post("/api/kpi-assessments/5/submit").with(auth)).andExpect(status().isOk());
        verify(service).submit(5L,actor);
    }
    @Test void reviewPermissionAllowsScopedReadButNotEmployeeMutation() throws Exception {
        var auth=authentication(user("CAN_REVIEW_KPI_ASSESSMENT"));
        mvc.perform(get("/api/kpi-assessments/5").with(auth)).andExpect(status().isOk());verify(service).get(5L,actor);
        messages();mvc.perform(post("/api/kpi-assessments/5/submit").with(auth)).andExpect(status().isForbidden());
        mvc.perform(post("/api/kpi-assessments").with(auth).contentType(MediaType.APPLICATION_JSON)
                .content("{\"checkpointId\":2,\"items\":[]}")).andExpect(status().isForbidden());
        verify(service,never()).submit(any(),any());
    }
    @Test void oldPlanReviewOrAdministrationPermissionIsInsufficient() throws Exception {
        messages();for(var permission:List.of("CAN_REVIEW_INDIVIDUAL_KPI","CAN_MANAGE_ANNUAL_KPI_REVIEW_PERIOD"))
            mvc.perform(get("/api/kpi-assessments/5").with(authentication(user(permission)))).andExpect(status().isForbidden());
        verifyNoInteractions(service);
    }
    @Test void fractionalAndTextPointsCannotBeSilentlyCoerced() throws Exception {
        for(var value:List.of("1.5","\"4\"")) mvc.perform(post("/api/kpi-assessments").with(authentication(user("ROLE_USER")))
                .contentType(MediaType.APPLICATION_JSON).content("{\"checkpointId\":2,\"items\":[{\"assignmentId\":3,\"selfPoint\":"+value+"}]}"))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(service);
    }
    @Test void evidenceRoutesAuthenticateAndReturnSafeDownloadHeaders() throws Exception {
        var auth=authentication(user("ROLE_USER"));var file=new MockMultipartFile("file","proof.pdf","application/pdf","test".getBytes());
        mvc.perform(multipart("/api/kpi-assessments/items/8/evidence").file(file).with(auth)).andExpect(status().isCreated());
        mvc.perform(get("/api/kpi-assessments/items/8/evidence").with(auth)).andExpect(status().isOk());
        var metadata=new KpiAssessmentEvidenceDto();metadata.setContentType("application/pdf");metadata.setOriginalFilename("proof.pdf");
        when(service.download(9L,actor)).thenReturn(new KpiAssessmentService.Download(new ByteArrayResource(new byte[]{1,2}),metadata));
        mvc.perform(get("/api/kpi-assessments/evidence/9").with(auth)).andExpect(status().isOk())
                .andExpect(header().string("X-Content-Type-Options","nosniff")).andExpect(header().string("Cache-Control","private, no-store"));
        mvc.perform(delete("/api/kpi-assessments/evidence/9").with(auth)).andExpect(status().isNoContent());
    }
    @Test void genericFileEndpointCannotBypassAssessmentEvidenceAccess() throws Exception {
        messages();var key=UUID.randomUUID().toString();
        when(genericFiles.getFilePath(".kpi-assessment-evidence/"+key))
                .thenThrow(new org.springframework.security.access.AccessDeniedException("Scoped evidence access required"));
        mvc.perform(get("/api/files/.kpi-assessment-evidence/"+key).with(authentication(user("ROLE_USER"))))
                .andExpect(status().isForbidden());
        verifyNoInteractions(service);
    }
}
