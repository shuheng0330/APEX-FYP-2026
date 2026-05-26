package com.tbm.careerpathlearning.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tbm.careerpathlearning.dto.CreateRoleRequestDto;
import com.tbm.careerpathlearning.dto.EditRoleRequestDto;
import com.tbm.careerpathlearning.dto.ToggleRoleVisibilityRequest;
import com.tbm.careerpathlearning.enums.AuthorityName;
import com.tbm.careerpathlearning.enums.OrgChartType;
import com.tbm.careerpathlearning.model.Authority;
import com.tbm.careerpathlearning.model.JobScope;
import com.tbm.careerpathlearning.model.OrgChart;
import com.tbm.careerpathlearning.model.Role;
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
public class RoleIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private OrgChartRepository orgChartRepository;

    @Autowired
    private JobScopeRepository jobScopeRepository;

    @Autowired
    private RoleJobScopeRepository roleJobScopeRepository;

    @Autowired
    private AuthorityRepository authorityRepository;

    @Autowired
    private RoleAuthorityRepository roleAuthorityRepository;

    private static final String MOCK_USER_ID = "d371028b-cfd7-4315-bcd5-3790e5eac036";
    private OrgChart testOrgChart;

    @BeforeEach
    void setUp() {
        // Clean up
        roleAuthorityRepository.deleteAll();
        roleJobScopeRepository.deleteAll();
        roleRepository.deleteAll();
        jobScopeRepository.deleteAll();
        orgChartRepository.deleteAll();
        authorityRepository.deleteAll();

        // 1. Setup Dependencies (Level 1)
        Authority auth = new Authority();
        auth.setName(AuthorityName.ROLE_USER);
        auth.setLabelKey("label");
        auth.setDescriptionKey("desc");
        authorityRepository.save(auth);

        testOrgChart = new OrgChart();
        testOrgChart.setName("IT Dept");
        testOrgChart.setType(OrgChartType.D);
        testOrgChart.setRoot(true);
        testOrgChart.setDeleted(false);
        testOrgChart.setCreatedAt(OffsetDateTime.now());
        testOrgChart.setUpdatedAt(OffsetDateTime.now());
        testOrgChart = orgChartRepository.save(testOrgChart);
    }

    // --- Scenario 1: Create Role ---
    @Test
    @WithMockUser(username = MOCK_USER_ID, authorities = "CAN_MANAGE_ROLE")
    void createRole_ShouldSaveRoleAndJobScopes() throws Exception {
        CreateRoleRequestDto req = new CreateRoleRequestDto();
        req.setOrgChartId(testOrgChart.getId());
        req.setRoleName("Java Developer");
        req.setDescription("Backend Dev");
        req.setVisibility(true);
        req.setJobScopeList(List.of("Coding", "Testing"));

        mockMvc.perform(post("/api/role")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.createdRole.name").value("Java Developer"));

        // Verify DB
        List<Role> roles = roleRepository.findAll();
        assertThat(roles).hasSize(1);
        Role savedRole = roles.get(0);
        assertThat(savedRole.getName()).isEqualTo("Java Developer");

        // Verify Job Scopes
        assertThat(roleJobScopeRepository.findAllByRole_Id(savedRole.getId())).hasSize(2);

        // Verify Authorities (Auto-assigned ROLE_USER)
        assertThat(roleAuthorityRepository.findByRoleId(savedRole.getId())).hasSize(1);
    }

    // --- Scenario 2: Get All Roles ---
    @Test
    @WithMockUser(authorities = "CAN_MANAGE_ROLE")
    void getAllRoles_ShouldReturnList() throws Exception {
        createRoleInDb("Senior Dev");

        mockMvc.perform(get("/api/role"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name").value("Senior Dev"));
    }

    // --- Scenario 3: Update Role (Add/Remove Job Scopes) ---
    @Test
    @WithMockUser(username = MOCK_USER_ID, authorities = "CAN_MANAGE_ROLE")
    void updateRole_ShouldModifyJobScopes() throws Exception {
        // 1. Create Role with existing scope
        Role role = createRoleInDb("Dev");
        JobScope jsOld = createJobScopeInDb("Old Scope");
        linkRoleJobScope(role, jsOld);

        // 2. Update Request (Remove "Old Scope", Add "New Scope")
        EditRoleRequestDto req = new EditRoleRequestDto();
        req.setRoleId(role.getId());
        req.setOrgChartId(testOrgChart.getId());
        req.setRoleName("Dev Updated");
        req.setVisibility(true);
        req.setJobScopeList(List.of("New Scope"));

        mockMvc.perform(put("/api/role/edit-role")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk());

        // 3. Verify
        assertThat(roleJobScopeRepository.findAllByRole_Id(role.getId())).hasSize(1); // Only 1 linked
        // Check if "Old Scope" was soft-deleted
        assertThat(jobScopeRepository.findByJobScope("Old Scope")
                .filter(js -> !js.isDeleted()) // Ensure we check the deleted flag if findByJobScope returns deleted ones
        ).isEmpty();
        // Check if "New Scope" created
        assertThat(jobScopeRepository.findByJobScope("New Scope")).isPresent();
    }

    // --- Scenario 4: Delete Role ---
    @Test
    @WithMockUser(username = MOCK_USER_ID, authorities = "CAN_MANAGE_ROLE")
    void deleteRole_ShouldMarkAsDeleted() throws Exception {
        Role role = createRoleInDb("To Delete");

        mockMvc.perform(delete("/api/role/delete")
                        .param("roleId", role.getId().toString()))
                .andExpect(status().isOk());

        Role deletedRole = roleRepository.findById(role.getId()).orElseThrow();
        assertThat(deletedRole.isDeleted()).isTrue();
    }

    // --- Scenario 5: Toggle Visibility ---
    @Test
    @WithMockUser(username = MOCK_USER_ID, authorities = "CAN_MANAGE_ROLE")
    void toggleVisibility_ShouldUpdateFlag() throws Exception {
        Role role = createRoleInDb("Hidden Role");
        role.setVisible(false);
        roleRepository.save(role);

        ToggleRoleVisibilityRequest req = new ToggleRoleVisibilityRequest();
        req.setRoleId(role.getId());
        req.setVisibility(true);

        mockMvc.perform(put("/api/role/toggle-visibility")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk());

        Role updated = roleRepository.findById(role.getId()).orElseThrow();
        assertThat(updated.isVisible()).isTrue();
    }

    // --- Scenario 6: Import Roles (Excel) ---
    @Test
    @WithMockUser(username = MOCK_USER_ID, authorities = "CAN_MANAGE_ROLE")
    void importData_ShouldCreateRoles() throws Exception {
        // Create Excel: "IT Dept" | "SysAdmin" | "Desc" | "Scope1; Scope2" | "Yes" | "" | "" | ""
        byte[] excelBytes = createExcelData(new ArrayList<>(Collections.singleton(
                new String[]{"IT Dept", "SysAdmin", "Maintain Servers", "Linux;Network", "Yes", "", "", ""}
        )));

        MockMultipartFile file = new MockMultipartFile(
                "file", "roles.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                excelBytes
        );

        mockMvc.perform(multipart("/api/role/import")
                        .file(file))
                .andExpect(status().isOk());

        // Verify DB
        assertThat(roleRepository.findAll()).hasSize(1);
        Role imported = roleRepository.findAll().get(0);
        assertThat(imported.getName()).isEqualTo("SysAdmin");

        // Verify Job Scopes created
        assertThat(roleJobScopeRepository.findAllByRole_Id(imported.getId())).hasSize(2);
    }

    // --- Scenario 7: Export Roles ---
    @Test
    @WithMockUser(authorities = "CAN_MANAGE_ROLE")
    void exportRoleOverviewData_ShouldReturnExcel() throws Exception {
        createRoleInDb("Manager");

        mockMvc.perform(get("/api/role/export"))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=Exported_Role_Overview_Data.xlsx"))
                .andExpect(content().contentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"));
    }

    // Helpers
    private Role createRoleInDb(String name) {
        Role role = new Role();
        role.setName(name);
        role.setOrgChart(testOrgChart);
        role.setVisible(true);
        role.setDeleted(false);
        role.setCreatedAt(OffsetDateTime.now());
        role.setUpdatedAt(OffsetDateTime.now());
        role.setCreatedBy(UUID.fromString(MOCK_USER_ID));
        role.setUpdatedBy(UUID.fromString(MOCK_USER_ID));
        return roleRepository.save(role);
    }

    private JobScope createJobScopeInDb(String name) {
        JobScope js = new JobScope();
        js.setJobScope(name);
        js.setDeleted(false);
        js.setCreatedAt(OffsetDateTime.now());
        js.setUpdatedAt(OffsetDateTime.now());
        js.setCreatedBy(UUID.fromString(MOCK_USER_ID));
        js.setUpdatedBy(UUID.fromString(MOCK_USER_ID));
        return jobScopeRepository.save(js);
    }

    private void linkRoleJobScope(Role role, JobScope jobScope) {
        com.tbm.careerpathlearning.model.RoleJobScope rjs = new com.tbm.careerpathlearning.model.RoleJobScope();
        rjs.setId(new com.tbm.careerpathlearning.model.RoleJobScopeId(role.getId(), jobScope.getId()));
        rjs.setRole(role);
        rjs.setJobScope(jobScope);
        rjs.setCreatedAt(OffsetDateTime.now());
        rjs.setUpdatedAt(OffsetDateTime.now());
        rjs.setCreatedBy(UUID.fromString(MOCK_USER_ID));
        rjs.setUpdatedBy(UUID.fromString(MOCK_USER_ID));
        roleJobScopeRepository.save(rjs);
    }

    private byte[] createExcelData(List<String[]> rows) throws Exception {
        try (Workbook workbook = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sheet = workbook.createSheet("Sheet 1");

            Row header = sheet.createRow(0);
            String[] headers = {"Department Name", "Role Name", "Role Description", "Job Scopes", "Visibility", "New Department Name", "New Role Name", "To Be Deleted"};
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