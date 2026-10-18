package com.tbm.careerpathlearning.controller;

import com.tbm.careerpathlearning.service.SopService;
import com.tbm.careerpathlearning.service.SopViewer;
import com.tbm.careerpathlearning.service.TokenService;
import com.tbm.careerpathlearning.service.ValidationService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Who may reach the SOP endpoints (FR-07-08, FR-07-19): the service then checks ownership. */
@WebMvcTest(controllers = SopController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import(SopControllerTest.MethodSecurity.class)
class SopControllerTest {

    /** The web slice does not load SecurityConfig, which switches on @PreAuthorize in the app; switch it on here too. */
    @TestConfiguration
    @EnableMethodSecurity
    static class MethodSecurity {
    }

    @Autowired private MockMvc mockMvc;

    @MockitoBean private SopService sopService;
    @MockitoBean private TokenService tokenService;
    @MockitoBean private ValidationService validationService;

    private final UUID me = UUID.randomUUID();
    private final String[] trainer = {"ROLE_USER", "CAN_MANAGE_TRAINING"};
    private final String[] hr = {"ROLE_USER", "CAN_ASSIGN_TRAINING", "CAN_VIEW_STAFF"};

    @AfterEach
    void signOut() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("HR can open courses, as a viewer who sees every course")
    void hrCanRead() throws Exception {
        when(sopService.listDocuments(any(), eq(false), eq(false))).thenReturn(List.of());

        mockMvc.perform(as(get("/api/sop"), hr)).andExpect(status().isOk());
        mockMvc.perform(as(get("/api/sop/1"), hr)).andExpect(status().isOk());
        mockMvc.perform(as(get("/api/sop/module/5/quiz/history"), hr)).andExpect(status().isOk());

        verify(sopService).listDocuments(new SopViewer(me, true), false, false);
        verify(sopService).getDetail(1L, new SopViewer(me, true));
    }

    @Test
    @DisplayName("HR is refused every change before the service is reached")
    void hrCannotChange() throws Exception {
        String body = "{\"reason\":\"x\",\"title\":\"t\",\"content\":\"{}\"}";
        for (MockHttpServletRequestBuilder change : List.of(
                put("/api/sop/module/5"), post("/api/sop/module/5/approve"), post("/api/sop/module/5/reject"),
                put("/api/sop/quiz/9"), post("/api/sop/module/5/quiz"), post("/api/sop/module/5/quiz/approve"),
                post("/api/sop/module/5/quiz/reject"), post("/api/sop/1/generate"), delete("/api/sop/1"),
                put("/api/sop/1/settings"), post("/api/sop/1/archive"), post("/api/sop/1/publish"))) {
            mockMvc.perform(as(change, hr).contentType(MediaType.APPLICATION_JSON).content(body))
                    .andExpect(status().isForbidden());
        }

        verify(sopService, never()).updateModule(any(), any(), any(), any());
        verify(sopService, never()).approveModule(any(), any());
        verify(sopService, never()).deleteDocument(any(), any());
        verify(sopService, never()).updateSettings(any(), any(), any());
        verify(sopService, never()).archiveDocument(any(), any());
        verify(sopService, never()).publishDraft(any(), anyBoolean(), any());
    }

    @Test
    @DisplayName("A trainer publishes their draft with the redo choice, and uploads a revised SOP as a new version (FR-06-06, FR-07-18)")
    void trainerVersions() throws Exception {
        mockMvc.perform(as(post("/api/sop/1/publish"), trainer).contentType(MediaType.APPLICATION_JSON)
                .content("{\"redoCompleted\":true}")).andExpect(status().isOk());
        mockMvc.perform(as(multipart("/api/sop/upload")
                        .file(new MockMultipartFile("file", "cash_v2.pdf", "application/pdf", new byte[]{1}))
                        .param("newVersionOf", "7"), trainer))
                .andExpect(status().isOk());

        verify(sopService).publishDraft(1L, true, new SopViewer(me, false));
        verify(sopService).upload(any(), any(), any(), any(), eq(me), eq(7L));
    }

    @Test
    @DisplayName("HR can list archived courses and restore one (FR-06-16); archiving is for the course's trainer")
    void hrRestores() throws Exception {
        mockMvc.perform(as(get("/api/sop").param("archived", "true"), hr)).andExpect(status().isOk());
        mockMvc.perform(as(post("/api/sop/1/restore"), hr)).andExpect(status().isOk());
        mockMvc.perform(as(post("/api/sop/1/archive"), trainer)).andExpect(status().isOk());

        verify(sopService).listDocuments(new SopViewer(me, true), false, true);
        verify(sopService).restoreDocument(1L, new SopViewer(me, true));
        verify(sopService).archiveDocument(1L, new SopViewer(me, false));
    }

    @Test
    @DisplayName("A trainer's changes reach the service as a trainer who sees only their own courses")
    void trainerChanges() throws Exception {
        mockMvc.perform(as(put("/api/sop/module/5"), trainer).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"Handling Cash\",\"content\":\"{}\"}"))
                .andExpect(status().isOk());
        mockMvc.perform(as(get("/api/sop").param("mine", "true"), trainer)).andExpect(status().isOk());

        verify(sopService).updateModule(eq(5L), eq("Handling Cash"), anyString(), eq(new SopViewer(me, false)));
        verify(sopService).listDocuments(new SopViewer(me, false), true, false);
    }

    @Test
    @DisplayName("Staff without a training role cannot reach the SOP pages at all")
    void otherStaffRefused() throws Exception {
        mockMvc.perform(as(get("/api/sop"), "ROLE_USER")).andExpect(status().isForbidden());

        verifyNoInteractions(sopService);
    }

    /** Signs the request in with the given permissions, the way the JWT filter does. */
    private MockHttpServletRequestBuilder as(MockHttpServletRequestBuilder request, String... authorities) {
        UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(me.toString(), null,
                Arrays.stream(authorities).map(SimpleGrantedAuthority::new).toList());
        SecurityContextHolder.getContext().setAuthentication(auth);
        return request.principal(auth);
    }
}
