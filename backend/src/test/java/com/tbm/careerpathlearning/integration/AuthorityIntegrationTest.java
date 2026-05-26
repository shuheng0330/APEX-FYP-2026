package com.tbm.careerpathlearning.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tbm.careerpathlearning.dto.GrantAccessDto;
import com.tbm.careerpathlearning.enums.AuthorityName;
import com.tbm.careerpathlearning.enums.OrgChartType;
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
public class AuthorityIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private OrgChartRepository orgChartRepository;

    @Autowired
    private AuthorityRepository authorityRepository;

    @Autowired
    private RoleAuthorityRepository roleAuthorityRepository;

    private static final String MOCK_ADMIN_ID = "d371028b-cfd7-4315-bcd5-3790e5eac036";
    private Role adminRole;
    private Authority authViewStaff;
    private Authority authManageStaff;

    @BeforeEach
    void setUp() {
        // Cleanup
        roleAuthorityRepository.deleteAll();
        roleRepository.deleteAll();
        orgChartRepository.deleteAll();
        authorityRepository.deleteAll();

        // Setup Authorities
        createAuthority(AuthorityName.CAN_MANAGE_ACCESS_CONTROL);
        createAuthority(AuthorityName.CAN_VIEW_ACCESS_CONTROL);
        authViewStaff = createAuthority(AuthorityName.CAN_VIEW_STAFF);
        authManageStaff = createAuthority(AuthorityName.CAN_MANAGE_STAFF);
        // ... create others as needed for import test ...
        createAuthority(AuthorityName.ROLE_USER); // Needed for import

        // Setup Org & Role
        OrgChart dept = createOrgChart("IT Dept");
        adminRole = createRole("Admin", dept);
    }

    // --- Scenario 1: Grant Access (Add Permissions) ---
    @Test
    @WithMockUser(username = MOCK_ADMIN_ID, authorities = "CAN_MANAGE_ACCESS_CONTROL")
    void grantAccess_ShouldAddPermissions() throws Exception {
        GrantAccessDto req = new GrantAccessDto();
        req.setOrgChartId(adminRole.getOrgChart().getId());
        req.setRoleId(adminRole.getId());
        req.setSelectedAuthorities(List.of(authViewStaff.getId(), authManageStaff.getId()));

        mockMvc.perform(post("/api/auth/grant-access")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").exists());

        // Verify DB
        List<RoleAuthority> permissions = roleAuthorityRepository.findByRoleId(adminRole.getId());
        assertThat(permissions).hasSize(2);
        assertThat(permissions).extracting(ra -> ra.getAuthority().getName())
                .containsExactlyInAnyOrder(AuthorityName.CAN_VIEW_STAFF, AuthorityName.CAN_MANAGE_STAFF);
    }

    // --- Scenario 2: Edit Access (Add & Remove) ---
    @Test
    @WithMockUser(username = MOCK_ADMIN_ID, authorities = "CAN_MANAGE_ACCESS_CONTROL")
    void editGrantedAccess_ShouldUpdatePermissions() throws Exception {
        // 1. Pre-assign VIEW_STAFF
        linkAuthority(adminRole, authViewStaff);

        // 2. Request: Remove VIEW_STAFF, Add MANAGE_STAFF
        GrantAccessDto req = new GrantAccessDto();
        req.setOrgChartId(adminRole.getOrgChart().getId());
        req.setRoleId(adminRole.getId());
        req.setSelectedAuthorities(List.of(authManageStaff.getId()));

        mockMvc.perform(put("/api/auth/edit-granted-access")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk());

        // Verify
        List<RoleAuthority> permissions = roleAuthorityRepository.findByRoleId(adminRole.getId());
        assertThat(permissions).hasSize(1);
        assertThat(permissions.get(0).getAuthority().getName()).isEqualTo(AuthorityName.CAN_MANAGE_STAFF);
    }

    // --- Scenario 3: Get Overview ---
    @Test
    @WithMockUser(authorities = "CAN_VIEW_ACCESS_CONTROL")
    void getStaffAccessControlOverview_ShouldReturnMatrix() throws Exception {
        linkAuthority(adminRole, authViewStaff);

        mockMvc.perform(get("/api/auth/access-control-overview"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.departments[0].roles[0].roleName").value("Admin"))
                .andExpect(jsonPath("$.departments[0].roles[0].authorityMap['" + authViewStaff.getId() + "']").value(true));
    }

    // --- Scenario 4: Delete Access (Revoke All) ---
    @Test
    @WithMockUser(username = MOCK_ADMIN_ID, authorities = "CAN_MANAGE_ACCESS_CONTROL")
    void deleteRoleAuthority_ShouldRevokeAll() throws Exception {
        linkAuthority(adminRole, authViewStaff);

        mockMvc.perform(delete("/api/auth/delete")
                        .param("orgChartId", adminRole.getOrgChart().getId().toString())
                        .param("roleId", adminRole.getId().toString()))
                .andExpect(status().isOk());

        assertThat(roleAuthorityRepository.findByRoleId(adminRole.getId())).isEmpty();
    }

    // --- Scenario 5: Import Data (Excel) ---
    @Test
    @WithMockUser(username = MOCK_ADMIN_ID, authorities = "CAN_MANAGE_ACCESS_CONTROL")
    void importData_ShouldMapPermissions() throws Exception {
        // Excel: "IT Dept" | "Admin" | "Yes" (User) | ... | "Yes" (View Staff) | ...
        // We need to construct a row with ~18 columns based on the controller's logic
        String[] rowData = new String[18];
        Arrays.fill(rowData, ""); // Default blank
        rowData[0] = "IT Dept";
        rowData[1] = "Admin";
        rowData[2] = "Yes"; // User
        rowData[5] = "Yes"; // View Staff

        byte[] excelBytes = createExcelData(new ArrayList<>(Collections.singleton(rowData)));

        MockMultipartFile file = new MockMultipartFile(
                "file", "auth.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                excelBytes
        );

        mockMvc.perform(multipart("/api/auth/import")
                        .file(file))
                .andExpect(status().isOk());

        // Verify
        List<RoleAuthority> perms = roleAuthorityRepository.findByRoleId(adminRole.getId());
        assertThat(perms).hasSize(2); // User + View Staff
        assertThat(perms).extracting(p -> p.getAuthority().getName())
                .contains(AuthorityName.ROLE_USER, AuthorityName.CAN_VIEW_STAFF);
    }

    // --- Scenario 6: Export Data ---
    @Test
    @WithMockUser(authorities = "CAN_MANAGE_ACCESS_CONTROL")
    void exportRoleAssignmentData_ShouldReturnExcel() throws Exception {
        linkAuthority(adminRole, authViewStaff);

        mockMvc.perform(get("/api/auth/export"))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=Exported_Access_Control_Data.xlsx"))
                .andExpect(content().contentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"));
    }

    // Helpers
    private Authority createAuthority(AuthorityName name) {
        Authority a = new Authority();
        a.setName(name);
        a.setLabelKey("label");
        a.setDescriptionKey("desc");
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

    private void linkAuthority(Role r, Authority a) {
        RoleAuthority ra = new RoleAuthority();
        ra.setId(new RoleAuthorityId(r.getId(), a.getId()));
        ra.setRole(r);
        ra.setAuthority(a);
        ra.setCreatedAt(OffsetDateTime.now());
        ra.setUpdatedAt(OffsetDateTime.now());
        ra.setCreatedBy(UUID.fromString(MOCK_ADMIN_ID));
        roleAuthorityRepository.save(ra);
    }

    private byte[] createExcelData(List<String[]> rows) throws Exception {
        try (Workbook workbook = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sheet = workbook.createSheet("Sheet 1");
            Row header = sheet.createRow(0);

            // Replicate header from controller logic (approx 18 cols)
            String[] headers = {
                    "Department Name", "Role Name", "User", "View Access Control", "Manage Access Control",
                    "View Staff Account", "Manage Staff Account", "View Invisible Role", "Manage Role",
                    "Manage Competency", "Propose Role Competencies", "Manage Career Pathway", "Manage Org Chart",
                    "Manage Training", "Assign Training", "Manage Learning Material", "Manage Evaluation", "Manage Evaluation Cycle"
            };
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