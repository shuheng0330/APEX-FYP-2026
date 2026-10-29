package com.tbm.careerpathlearning.controller;

import com.tbm.careerpathlearning.dto.TrainingAttendanceSheetDto;
import com.tbm.careerpathlearning.service.TokenService;
import com.tbm.careerpathlearning.service.TrainingAttendanceService;
import com.tbm.careerpathlearning.service.TrainingViewer;
import com.tbm.careerpathlearning.service.ValidationService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
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

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.UUID;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.startsWith;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Only staff with Trainer access, and HR, change attendance; the service then checks they record this session. Whoever
 * records it always comes from the sign-in.
 */
@WebMvcTest(controllers = TrainingAttendanceController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import(TrainingAttendanceControllerTest.MethodSecurity.class)
class TrainingAttendanceControllerTest {

    /** The web slice does not load SecurityConfig, which switches on @PreAuthorize in the app; switch it on here too. */
    @TestConfiguration
    @EnableMethodSecurity
    static class MethodSecurity {
    }

    @Autowired private MockMvc mockMvc;

    @MockitoBean private TrainingAttendanceService attendance;
    @MockitoBean private TokenService tokenService;
    @MockitoBean private ValidationService validationService;

    private final UUID user = UUID.randomUUID();

    @AfterEach
    void signOut() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("Other staff can't record, submit or attach evidence (FR-14-01)")
    void onlyTrainersAndHrRecord() throws Exception {
        String marks = "{\"entries\":[{\"staffId\":\"" + UUID.randomUUID() + "\",\"status\":\"ATTENDED\"}]}";
        MockMultipartFile photo = new MockMultipartFile("file", "sheet.png", "image/png", new byte[]{1});

        mockMvc.perform(as(put("/api/trainings/7/attendance"), "ROLE_USER").contentType(MediaType.APPLICATION_JSON).content(marks))
                .andExpect(status().isForbidden());
        mockMvc.perform(as(post("/api/trainings/7/attendance/submit"), "ROLE_USER")).andExpect(status().isForbidden());
        mockMvc.perform(as(multipart("/api/trainings/7/attendance/evidence").file(photo), "ROLE_USER"))
                .andExpect(status().isForbidden());
        mockMvc.perform(as(delete("/api/trainings/7/attendance/evidence/3"), "ROLE_USER")).andExpect(status().isForbidden());
        verifyNoInteractions(attendance);

        when(attendance.record(any(), any(), any())).thenReturn(new TrainingAttendanceSheetDto());
        when(attendance.submit(any(), any(), any())).thenReturn(new TrainingAttendanceSheetDto());
        mockMvc.perform(as(put("/api/trainings/7/attendance"), "ROLE_USER", "CAN_MANAGE_TRAINING")
                .contentType(MediaType.APPLICATION_JSON).content(marks)).andExpect(status().isOk());
        mockMvc.perform(as(post("/api/trainings/7/attendance/submit"), "ROLE_USER", "CAN_MANAGE_TRAINING"))
                .andExpect(status().isOk());
        TrainingViewer trainer = new TrainingViewer(user, true, false, false);
        verify(attendance).record(eq(7L), any(), eq(trainer));
        verify(attendance).submit(7L, null, trainer);
    }

    @Test
    @DisplayName("An evidence file is sent as stored, under its own name, never sniffed as another type")
    void evidenceFile(@TempDir Path folder) throws Exception {
        Path stored = Files.write(folder.resolve("a1b2.pdf"), "%PDF-1.7".getBytes());
        when(attendance.evidenceFile(any(), any(), any()))
                .thenReturn(new TrainingAttendanceService.EvidenceFile(stored, "sign-in sheet.pdf", "application/pdf"));

        mockMvc.perform(as(get("/api/trainings/7/attendance/evidence/3"), "ROLE_USER", "CAN_ASSIGN_TRAINING"))
                .andExpect(status().isOk())
                .andExpect(content().contentType("application/pdf"))
                .andExpect(content().bytes("%PDF-1.7".getBytes()))
                .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                .andExpect(header().string("Content-Disposition", startsWith("inline;")))
                .andExpect(header().string("Content-Disposition", containsString("filename*=UTF-8''sign-in%20sheet.pdf")));
        verify(attendance).evidenceFile(7L, 3L, new TrainingViewer(user, false, true, false));
    }

    /** Signs the request in with the given permissions, the way the JWT filter does. */
    private MockHttpServletRequestBuilder as(MockHttpServletRequestBuilder request, String... authorities) {
        UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(user.toString(), null,
                Arrays.stream(authorities).map(SimpleGrantedAuthority::new).toList());
        SecurityContextHolder.getContext().setAuthentication(auth);
        return request.principal(auth);
    }
}
