package com.tbm.careerpathlearning.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tbm.careerpathlearning.dto.CreateStaffSelfDeclaredSkillRequest;
import com.tbm.careerpathlearning.enums.SkillProficiency;
import com.tbm.careerpathlearning.enums.StaffAccountStatus;
import com.tbm.careerpathlearning.model.Staff;
import com.tbm.careerpathlearning.model.StaffSelfDeclaredSkill;
import com.tbm.careerpathlearning.repository.StaffRepository;
import com.tbm.careerpathlearning.repository.StaffSelfDeclaredSkillRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
public class StaffSelfDeclaredSkillIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private StaffRepository staffRepository;

    @Autowired
    private StaffSelfDeclaredSkillRepository skillRepository;

    private Staff testStaff;
    private UUID staffId;

    @BeforeEach
    void setUp() {
        // 1. Create Staff
        testStaff = new Staff();
        // Generate ID explicitly if entity lacks @GeneratedValue, else let DB handle it
        UUID tempId = UUID.randomUUID();
        testStaff.setId(tempId);

        testStaff.setName("John Doe");
        testStaff.setEmail("john@tbm.com");
        testStaff.setAccountStatus(StaffAccountStatus.ACTIVE);
        testStaff.setDeleted(false);
        testStaff.setCreatedAt(OffsetDateTime.now());
        testStaff.setUpdatedAt(OffsetDateTime.now());
        testStaff = staffRepository.saveAndFlush(testStaff);

        this.staffId = testStaff.getId();

        // 2. Setup Security Context with STRING Principal (UUID)
        // This avoids the "UUID string too large" error in the controller
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(
                        staffId.toString(),
                        "password",
                        List.of(new SimpleGrantedAuthority("ROLE_USER"))
                )
        );
    }

    // --- Scenario 1: Create Skill (Happy Path) ---
    @Test
    void createSkill_ShouldSaveToDB() throws Exception {
        CreateStaffSelfDeclaredSkillRequest req = new CreateStaffSelfDeclaredSkillRequest();
        req.setStaffId(staffId);
        req.setSkill("Java Programming");
        req.setDescription("Advanced coding");
        req.setProficiency(SkillProficiency.ADVANCED.name());

        mockMvc.perform(post("/api/staff-self-declare-skill")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").exists());

        // Verify DB
        List<StaffSelfDeclaredSkill> skills = skillRepository.findAllByStaff_Id(staffId);
        assertThat(skills).hasSize(1);
        assertThat(skills.get(0).getSkill()).isEqualTo("Java Programming");
        assertThat(skills.get(0).getProficiency()).isEqualTo(SkillProficiency.ADVANCED);
    }

    // --- Scenario 2: Create Skill (Validation Fail) ---
    @Test
    void createSkill_ShouldFail_WhenMissingFields() throws Exception {
        CreateStaffSelfDeclaredSkillRequest req = new CreateStaffSelfDeclaredSkillRequest();
        req.setStaffId(staffId);
        // Missing Skill Name
        req.setProficiency(SkillProficiency.BEGINNER.name());

        mockMvc.perform(post("/api/staff-self-declare-skill")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest());
    }

    // --- Scenario 3: Get Overview (List Skills) ---
    @Test
    void overview_ShouldReturnListOfSkills() throws Exception {
        // Pre-populate DB
        createSkillInDb("Python", SkillProficiency.INTERMEDIATE);
        createSkillInDb("SQL", SkillProficiency.BEGINNER);

        mockMvc.perform(get("/api/staff-self-declare-skill/overview")
                        .param("staffId", staffId.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.size()").value(2))
                .andExpect(jsonPath("$[0].skill").exists())
                .andExpect(jsonPath("$[1].skill").exists());
    }

    // --- Scenario 4: Edit Skill ---
    @Test
    void updateSkill_ShouldModifyDB() throws Exception {
        // 1. Create initial skill
        StaffSelfDeclaredSkill skill = createSkillInDb("Old Skill", SkillProficiency.BEGINNER);

        // 2. Prepare Update Request
        CreateStaffSelfDeclaredSkillRequest req = new CreateStaffSelfDeclaredSkillRequest();
        req.setId(skill.getId());
        req.setStaffId(staffId);
        req.setSkill("Updated Skill");
        req.setProficiency(SkillProficiency.ADVANCED.name());
        req.setDescription("Now an expert");

        mockMvc.perform(put("/api/staff-self-declare-skill/edit")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk());

        // 3. Verify DB
        StaffSelfDeclaredSkill updated = skillRepository.findById(skill.getId()).orElseThrow();
        assertThat(updated.getSkill()).isEqualTo("Updated Skill");
        assertThat(updated.getProficiency()).isEqualTo(SkillProficiency.ADVANCED);
    }

    // --- Scenario 5: Delete Single Skill ---
    @Test
    void delete_ShouldRemoveFromDB() throws Exception {
        StaffSelfDeclaredSkill skill = createSkillInDb("To Delete", SkillProficiency.BEGINNER);

        mockMvc.perform(delete("/api/staff-self-declare-skill/delete")
                        .param("selectedId", skill.getId().toString()))
                .andExpect(status().isOk());

        assertThat(skillRepository.findById(skill.getId())).isEmpty();
    }

    // --- Scenario 6: Bulk Delete ---
    @Test
    void bulkDelete_ShouldRemoveMultiple() throws Exception {
        StaffSelfDeclaredSkill s1 = createSkillInDb("Skill A", SkillProficiency.BEGINNER);
        StaffSelfDeclaredSkill s2 = createSkillInDb("Skill B", SkillProficiency.BEGINNER);
        StaffSelfDeclaredSkill s3 = createSkillInDb("Skill C", SkillProficiency.BEGINNER); // Keep this one

        String ids = s1.getId() + "," + s2.getId();

        mockMvc.perform(delete("/api/staff-self-declare-skill/bulk-delete")
                        .param("selectedIds", ids))
                .andExpect(status().isOk());

        assertThat(skillRepository.findById(s1.getId())).isEmpty();
        assertThat(skillRepository.findById(s2.getId())).isEmpty();
        assertThat(skillRepository.findById(s3.getId())).isPresent();
    }

    // Helper method to create data directly in DB
    private StaffSelfDeclaredSkill createSkillInDb(String skillName, SkillProficiency proficiency) {
        StaffSelfDeclaredSkill skill = new StaffSelfDeclaredSkill();
        skill.setStaff(testStaff);
        skill.setSkill(skillName);
        skill.setProficiency(proficiency);
        skill.setCreatedAt(OffsetDateTime.now());
        skill.setUpdatedAt(OffsetDateTime.now());
        skill.setCreatedBy(staffId);
        skill.setUpdatedBy(staffId);
        return skillRepository.saveAndFlush(skill);
    }
}