package com.tbm.careerpathlearning.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tbm.careerpathlearning.dto.CreateStaffSelfDeclaredSkillRequest;
import com.tbm.careerpathlearning.dto.StaffDto;
import com.tbm.careerpathlearning.dto.StaffSelfDeclaredSkillDto;
import com.tbm.careerpathlearning.enums.SkillProficiency;
import com.tbm.careerpathlearning.service.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.MessageSource;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = StaffSelfDeclaredSkillController.class)
@AutoConfigureMockMvc(addFilters = false)
class StaffSelfDeclaredSkillControllerTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private StaffService staffService;
    @MockitoBean
    private MessageSource messageSource;
    @MockitoBean
    private ValidationService validationService;
    @MockitoBean
    private StaffSelfDeclaredSkillService skillService;
    @MockitoBean
    private TokenService tokenService; // Required for Security Filter

    private UUID staffId;
    private CreateStaffSelfDeclaredSkillRequest request;

    @BeforeEach
    void setUp() {
        staffId = UUID.randomUUID();

        request = new CreateStaffSelfDeclaredSkillRequest();
        request.setStaffId(staffId);
        request.setSkill("Java");
        request.setProficiency(SkillProficiency.INTERMEDIATE.getProficiency());
        request.setDescription("Backend Dev");

        when(messageSource.getMessage(any(), any(), any(Locale.class))).thenReturn("Mock Message");
    }

    // --- Overview (Get List) ---

    @Test
    void overview_ShouldReturn200() throws Exception {
        when(skillService.findByStaffId(staffId)).thenReturn(List.of(new StaffSelfDeclaredSkillDto()));

        mockMvc.perform(get("/api/staff-self-declare-skill/overview")
                        .param("staffId", staffId.toString())
                        .principal(mock(Authentication.class)))
                .andExpect(status().isOk());
    }

    // --- Create ---

    @Test
    void create_ShouldReturn200_WhenValid() throws Exception {
        Authentication auth = mock(Authentication.class);
        when(auth.getPrincipal()).thenReturn(staffId.toString());

        when(validationService.isNullOrBlank("Java")).thenReturn(false);
        when(staffService.findById(staffId)).thenReturn(new StaffDto());

        mockMvc.perform(post("/api/staff-self-declare-skill")
                        .principal(auth)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk());

        verify(skillService).create(any(StaffSelfDeclaredSkillDto.class));
    }

    @Test
    void create_ShouldReturn400_WhenSkillMissing() throws Exception {
        request.setSkill(null);
        when(validationService.isNullOrBlank(null)).thenReturn(true);

        mockMvc.perform(post("/api/staff-self-declare-skill")
                        .principal(mock(Authentication.class))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void create_ShouldReturn400_WhenStaffIdMissing() throws Exception {
        request.setStaffId(null); // Validation Fail

        mockMvc.perform(post("/api/staff-self-declare-skill")
                        .principal(mock(Authentication.class))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void create_ShouldReturn400_WhenProficiencyMissing() throws Exception {
        request.setProficiency(null); // Validation Fail

        mockMvc.perform(post("/api/staff-self-declare-skill")
                        .principal(mock(Authentication.class))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    // --- Update ---

    @Test
    void update_ShouldReturn200_WhenValid() throws Exception {
        Authentication auth = mock(Authentication.class);
        when(auth.getPrincipal()).thenReturn(staffId.toString());

        request.setId(1L);

        StaffSelfDeclaredSkillDto existingDto = new StaffSelfDeclaredSkillDto();
        existingDto.setId(1L);
        when(skillService.findById(1L)).thenReturn(existingDto);
        when(validationService.isNullOrBlank("Java")).thenReturn(false);

        mockMvc.perform(put("/api/staff-self-declare-skill/edit")
                        .principal(auth)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk());

        verify(skillService).update(eq(1L), any(StaffSelfDeclaredSkillDto.class));
    }

    @Test
    void update_ShouldReturn400_WhenIdMissing() throws Exception {
        request.setId(null); // Missing ID

        mockMvc.perform(put("/api/staff-self-declare-skill/edit")
                        .principal(mock(Authentication.class))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void update_ShouldReturn400_WhenStaffIdMissing() throws Exception {
        request.setId(1L);
        request.setStaffId(null); // Validation Fail inside Update

        mockMvc.perform(put("/api/staff-self-declare-skill/edit")
                        .principal(mock(Authentication.class))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void update_ShouldReturn400_WhenProficiencyMissing() throws Exception {
        request.setId(1L);
        request.setProficiency(null); // Validation Fail inside Update

        mockMvc.perform(put("/api/staff-self-declare-skill/edit")
                        .principal(mock(Authentication.class))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void update_ShouldReturn400_WhenSkillEmpty() throws Exception {
        request.setId(1L);
        request.setSkill(""); // Blank String
        when(validationService.isNullOrBlank("")).thenReturn(true);

        mockMvc.perform(put("/api/staff-self-declare-skill/edit")
                        .principal(mock(Authentication.class))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    // --- Delete ---

    @Test
    void delete_ShouldReturn200_WhenIdProvided() throws Exception {
        mockMvc.perform(delete("/api/staff-self-declare-skill/delete")
                        .param("selectedId", "1")
                        .principal(mock(Authentication.class)))
                .andExpect(status().isOk());

        verify(skillService).delete(1L);
    }

    @Test
    void bulkDelete_ShouldReturn200_WhenIdsProvided() throws Exception {
        mockMvc.perform(delete("/api/staff-self-declare-skill/bulk-delete")
                        .param("selectedIds", "1,2,3")
                        .principal(mock(Authentication.class)))
                .andExpect(status().isOk());

        verify(skillService).deleteAllById(any(Set.class));
    }

    @Test
    void bulkDelete_ShouldReturn400_WhenListIsEmpty() throws Exception {
        // Sending empty param: ?selectedIds=
        mockMvc.perform(delete("/api/staff-self-declare-skill/bulk-delete")
                        .param("selectedIds", "")
                        .principal(mock(Authentication.class)))
                .andExpect(status().isBadRequest());
    }
}