package com.tbm.careerpathlearning.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tbm.careerpathlearning.dto.AssignCareerPathwayRequestDto;
import com.tbm.careerpathlearning.enums.AuthorityName;
import com.tbm.careerpathlearning.enums.OrgChartType;
import com.tbm.careerpathlearning.enums.StaffAccountStatus;
import com.tbm.careerpathlearning.model.*;
import com.tbm.careerpathlearning.repository.*;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayOutputStream;
import java.time.OffsetDateTime;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
public class CareerPathwayAssignmentIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private StaffRepository staffRepository;

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private OrgChartRepository orgChartRepository;

    @Autowired
    private CareerPathwayRepository careerPathwayRepository;

    @Autowired
    private CareerPathwayRoleRepository careerPathwayRoleRepository;

    @Autowired
    private AuthorityRepository authorityRepository;

    private static final String MOCK_ADMIN_ID = "d371028b-cfd7-4315-bcd5-3790e5eac036";
    private CareerPathway engineeringTrack;
    private Role juniorDev;
    private Role hrManager; // Not in engineering track
    private Staff alice;
    private Staff bob;

    @BeforeEach
    void setUp() {
        // Cleanup
        careerPathwayRoleRepository.deleteAll();
        careerPathwayRepository.deleteAll();
        staffRepository.deleteAll();
        roleRepository.deleteAll();
        orgChartRepository.deleteAll();
        authorityRepository.deleteAll();

        // Setup Auth
        createAuthority(AuthorityName.CAN_MANAGE_CAREER_PATHWAY);
        createAuthority(AuthorityName.CAN_MANAGE_STAFF);

        // Setup Org & Roles
        OrgChart dept = createOrgChart("IT Dept");
        juniorDev = createRole("Junior Dev", dept);
        hrManager = createRole("HR Manager", dept);

        // Setup Pathway (Junior Dev is part of it)
        engineeringTrack = createPathway("Engineering Track", juniorDev);
        // Add HR Manager is NOT part of this track (Root is Junior, no edges added)

        // Setup Staff
        alice = createStaff("Alice", "alice@tbm.com", juniorDev); // Eligible
        bob = createStaff("Bob", "bob@tbm.com", hrManager);       // Not Eligible
    }

    // --- Scenario 1: Assign Valid Staff ---
    @Test
    @WithMockUser(username = MOCK_ADMIN_ID, authorities = {"CAN_MANAGE_STAFF", "CAN_MANAGE_CAREER_PATHWAY"})
    void assignCareerPathway_ShouldSucceed_WhenRoleMatches() throws Exception {
        AssignCareerPathwayRequestDto req = new AssignCareerPathwayRequestDto();
        req.setCareerPathwayId(engineeringTrack.getId());
        req.setStaffIds(new HashSet<>(Collections.singleton(alice.getId())));

        mockMvc.perform(post("/api/career-pathway-assignment")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").exists());

        // Verify DB
        Staff updatedAlice = staffRepository.findById(alice.getId()).orElseThrow();
        assertThat(updatedAlice.getCareerPathway().getId()).isEqualTo(engineeringTrack.getId());
    }

    // --- Scenario 2: Assign Invalid Staff (Role Mismatch) ---
    @Test
    @WithMockUser(username = MOCK_ADMIN_ID, authorities = {"CAN_MANAGE_STAFF", "CAN_MANAGE_CAREER_PATHWAY"})
    void assignCareerPathway_ShouldFail_WhenRoleNotInPathway() throws Exception {
        AssignCareerPathwayRequestDto req = new AssignCareerPathwayRequestDto();
        req.setCareerPathwayId(engineeringTrack.getId());
        req.setStaffIds(new HashSet<>(Collections.singleton(bob.getId()))); // Bob is HR Manager, not in Engineering Track

        mockMvc.perform(post("/api/career-pathway-assignment")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest()); // Expect Error
    }

    // --- Scenario 3: Update Assignment (Remove Staff) ---
    @Test
    @WithMockUser(username = MOCK_ADMIN_ID, authorities = {"CAN_MANAGE_STAFF", "CAN_MANAGE_CAREER_PATHWAY"})
    void update_ShouldRemoveUnlistedStaff() throws Exception {
        // 1. Pre-assign Alice
        alice.setCareerPathway(engineeringTrack);
        staffRepository.save(alice);

        // 2. Request with empty staff list (implies removing everyone from this pathway)
        AssignCareerPathwayRequestDto req = new AssignCareerPathwayRequestDto();
        req.setCareerPathwayId(engineeringTrack.getId());
        req.setStaffIds(Collections.emptySet());

        mockMvc.perform(put("/api/career-pathway-assignment/edit")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk());

        // 3. Verify
        Staff updatedAlice = staffRepository.findById(alice.getId()).orElseThrow();
        assertThat(updatedAlice.getCareerPathway()).isNull();
    }

    // --- Scenario 4: Delete Assignment (By Pathway ID) ---
    @Test
    @WithMockUser(username = MOCK_ADMIN_ID, authorities = {"CAN_MANAGE_STAFF", "CAN_MANAGE_CAREER_PATHWAY"})
    void delete_ShouldUnassignAllStaff() throws Exception {
        alice.setCareerPathway(engineeringTrack);
        staffRepository.save(alice);

        mockMvc.perform(delete("/api/career-pathway-assignment/delete")
                        .param("selectedCareerPathwayId", engineeringTrack.getId().toString()))
                .andExpect(status().isOk());

        Staff updatedAlice = staffRepository.findById(alice.getId()).orElseThrow();
        assertThat(updatedAlice.getCareerPathway()).isNull();
    }

    // --- Scenario 5: Import Data (Excel) ---
    @Test
    @WithMockUser(username = MOCK_ADMIN_ID, authorities = {"CAN_MANAGE_STAFF", "CAN_MANAGE_CAREER_PATHWAY"})
    void importData_ShouldAssignAndValidateRole() throws Exception {
        // Excel: "IT Dept" | "Engineering Track" | "alice@tbm.com"
        // Alice has "Junior Dev" role which IS in "Engineering Track" -> Should Pass
        byte[] excelBytes = createExcelData(new ArrayList<>(Collections.singleton(
                new String[]{"IT Dept", "Engineering Track", "alice@tbm.com"}
        )));

        MockMultipartFile file = new MockMultipartFile(
                "file", "assign.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                excelBytes
        );

        mockMvc.perform(multipart("/api/career-pathway-assignment/import")
                        .file(file))
                .andExpect(status().isOk());

        Staff updatedAlice = staffRepository.findById(alice.getId()).orElseThrow();
        assertThat(updatedAlice.getCareerPathway().getName()).isEqualTo("Engineering Track");
    }

    // --- Scenario 6: Import Invalid Data (Role Mismatch) ---
    @Test
    @WithMockUser(username = MOCK_ADMIN_ID, authorities = {"CAN_MANAGE_STAFF", "CAN_MANAGE_CAREER_PATHWAY"})
    void importData_ShouldFail_WhenRoleMismatch() throws Exception {
        // Excel: "IT Dept" | "Engineering Track" | "bob@tbm.com"
        // Bob has "HR Manager" role which is NOT in "Engineering Track" -> Should Fail
        byte[] excelBytes = createExcelData(new ArrayList<>(Collections.singleton(
                new String[]{"IT Dept", "Engineering Track", "bob@tbm.com"}
        )));

        MockMultipartFile file = new MockMultipartFile(
                "file", "assign_fail.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                excelBytes
        );

        mockMvc.perform(multipart("/api/career-pathway-assignment/import")
                        .file(file))
                .andExpect(status().isBadRequest()); // Expect validation error
    }

    // Helpers
    private Authority createAuthority(AuthorityName name) {
        Authority a = new Authority();
        a.setName(name);
        a.setLabelKey("l");
        a.setDescriptionKey("d");
        return authorityRepository.save(a);
    }

    private OrgChart createOrgChart(String name) {
        OrgChart oc = new OrgChart();
        oc.setName(name);
        oc.setType(OrgChartType.D);
        oc.setDeleted(false);
        oc.setCreatedAt(OffsetDateTime.now());
        oc.setUpdatedAt(OffsetDateTime.now());
        return orgChartRepository.save(oc);
    }

    private Role createRole(String name, OrgChart oc) {
        Role r = new Role();
        r.setName(name);
        r.setOrgChart(oc);
        r.setDeleted(false);
        r.setCreatedAt(OffsetDateTime.now());
        r.setUpdatedAt(OffsetDateTime.now());
        return roleRepository.save(r);
    }

    private CareerPathway createPathway(String name, Role root) {
        CareerPathway cp = new CareerPathway();
        cp.setName(name);
        cp.setOrgChart(root.getOrgChart());
        cp.setRootRole(root);
        cp.setDeleted(false);
        cp.setCreatedAt(OffsetDateTime.now());
        cp.setUpdatedAt(OffsetDateTime.now());
        cp.setCreatedBy(UUID.fromString(MOCK_ADMIN_ID));
        return careerPathwayRepository.save(cp);
    }

    private Staff createStaff(String name, String email, Role role) {
        Staff s = new Staff();
        s.setId(UUID.randomUUID());
        s.setName(name);
        s.setEmail(email);
        s.setRole(role);
        s.setAccountStatus(StaffAccountStatus.ACTIVE);
        s.setDeleted(false);
        s.setCreatedAt(OffsetDateTime.now());
        s.setUpdatedAt(OffsetDateTime.now());
        return staffRepository.save(s);
    }

    private byte[] createExcelData(List<String[]> rows) throws Exception {
        try (Workbook workbook = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sheet = workbook.createSheet("Sheet 1");
            Row header = sheet.createRow(0);
            String[] headers = {"Department Name", "Career Pathway Name", "Staff Email"};
            for (int i = 0; i < headers.length; i++) header.createCell(i).setCellValue(headers[i]);

            int rowIdx = 1;
            for (String[] data : rows) {
                Row row = sheet.createRow(rowIdx++);
                for (int c = 0; c < data.length; c++) row.createCell(c).setCellValue(data[c]);
            }
            workbook.write(out);
            return out.toByteArray();
        }
    }
}