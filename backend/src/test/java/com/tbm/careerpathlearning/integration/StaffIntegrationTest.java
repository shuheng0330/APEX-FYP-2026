package com.tbm.careerpathlearning.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tbm.careerpathlearning.dto.EditStaffRequestDto;
import com.tbm.careerpathlearning.dto.RegisterAccountRequest;
import com.tbm.careerpathlearning.enums.AuthorityName;
import com.tbm.careerpathlearning.enums.OrgChartType;
import com.tbm.careerpathlearning.enums.StaffAccountStatus;
import com.tbm.careerpathlearning.model.*;
import com.tbm.careerpathlearning.repository.*;
import com.tbm.careerpathlearning.service.EmailService;
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
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayOutputStream;
import java.time.OffsetDateTime;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doNothing;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
public class StaffIntegrationTest {

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
    private AuthorityRepository authorityRepository;

    @Autowired
    private StaffProfileRepository staffProfileRepository;

    @MockitoBean
    private EmailService emailService;

    private static final String MOCK_ADMIN_ID = "d371028b-cfd7-4315-bcd5-3790e5eac036";
    private Role managerRole;
    private Role staffRole;
    private Staff manager;

    @BeforeEach
    void setUp() {
        // Cleanup
        staffProfileRepository.deleteAll();
        staffRepository.deleteAll();
        roleRepository.deleteAll();
        orgChartRepository.deleteAll();
        authorityRepository.deleteAll();

        // Setup Auth
        createAuthority(AuthorityName.CAN_MANAGE_STAFF);
        createAuthority(AuthorityName.ROLE_USER);

        // Setup Org & Roles
        OrgChart dept = createOrgChart("IT Dept");
        managerRole = createRole("Manager", dept);
        staffRole = createRole("Developer", dept);

        // Setup Existing Staff (Manager)
        manager = createStaff("Manager User", "manager@tbm.com", managerRole, null);

        // Mock Email
        doNothing().when(emailService).sendAccountRegisteredEmail(anyString(), any());
    }

    // --- Scenario 1: Register New Staff ---
    @Test
    @WithMockUser(username = MOCK_ADMIN_ID, authorities = "CAN_MANAGE_STAFF")
    void registerAccount_ShouldCreateStaffAndProfile() throws Exception {
        RegisterAccountRequest req = new RegisterAccountRequest();
        req.setEmail("newuser@tbm.com");
        req.setName("New User");
        req.setRoleId(staffRole.getId());
        req.setManagerId(manager.getId());

        mockMvc.perform(post("/api/staff/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").exists());

        // Verify DB
        Staff newStaff = staffRepository.findByIsDeletedIsFalseAndEmail("newuser@tbm.com").orElseThrow();
        assertThat(newStaff.getName()).isEqualTo("New User");
        assertThat(newStaff.getManager().getId()).isEqualTo(manager.getId());
        assertThat(newStaff.getAccountStatus()).isEqualTo(StaffAccountStatus.ACTIVE);

        // Verify Profile Created
        assertThat(staffProfileRepository.findById(newStaff.getId())).isPresent();
    }

    // --- Scenario 2: Register Duplicate Email ---
    @Test
    @WithMockUser(username = MOCK_ADMIN_ID, authorities = "CAN_MANAGE_STAFF")
    void registerAccount_ShouldFail_WhenEmailExists() throws Exception {
        RegisterAccountRequest req = new RegisterAccountRequest();
        req.setEmail("manager@tbm.com"); // Exists
        req.setName("Duplicate User");

        mockMvc.perform(post("/api/staff/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest());
    }

    // --- Scenario 3: Update Staff Details ---
    @Test
    @WithMockUser(username = MOCK_ADMIN_ID, authorities = "CAN_MANAGE_STAFF")
    void updateStaff_ShouldUpdateFields() throws Exception {
        EditStaffRequestDto req = new EditStaffRequestDto();
        req.setStaffId(manager.getId());
        req.setEmail("updated_manager@tbm.com");
        req.setName("Updated Manager");
        req.setRoleId(managerRole.getId());
        req.setAccountStatus(true);

        mockMvc.perform(put("/api/staff/edit-staff")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk());

        Staff updated = staffRepository.findById(manager.getId()).orElseThrow();
        assertThat(updated.getEmail()).isEqualTo("updated_manager@tbm.com");
        assertThat(updated.getName()).isEqualTo("Updated Manager");
    }

    // --- Scenario 4: Get Overview (Filtered by Authority) ---
    @Test
    @WithMockUser(username = MOCK_ADMIN_ID, authorities = "CAN_MANAGE_STAFF")
    void getStaffOverview_AsAdmin_ShouldReturnAll() throws Exception {
        createStaff("User 2", "user2@tbm.com", staffRole, manager);

        mockMvc.perform(get("/api/staff/overview"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2)); // Manager + User 2
    }

    // --- Scenario 5: Delete Staff (Soft Delete) ---
    @Test
    @WithMockUser(username = MOCK_ADMIN_ID, authorities = "CAN_MANAGE_STAFF")
    void delete_ShouldSoftDeleteStaff() throws Exception {
        mockMvc.perform(delete("/api/staff/delete")
                        .param("staffId", manager.getId().toString()))
                .andExpect(status().isOk());

        Staff deleted = staffRepository.findById(manager.getId()).orElseThrow();
        assertThat(deleted.isDeleted()).isTrue();
    }

    // --- Scenario 6: Import Data (Excel) ---
    @Test
    @WithMockUser(username = MOCK_ADMIN_ID, authorities = "CAN_MANAGE_STAFF")
    void importData_ShouldCreateAndUpdate() throws Exception {
        // Excel:
        // Row 1: Update Manager's Name
        // Row 2: Create New Staff
        byte[] excelBytes = createExcelData(List.of(
                new String[]{
                        "manager@tbm.com", "Manager Updated", "IT Dept", "Manager", "", "", "Active", "", ""
                },
                new String[]{
                        "imported@tbm.com", "Imported User", "IT Dept", "Developer", "", "manager@tbm.com", "Active", "", ""
                }
        ));

        MockMultipartFile file = new MockMultipartFile(
                "file", "staff.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                excelBytes
        );

        mockMvc.perform(multipart("/api/staff/import")
                        .file(file))
                .andExpect(status().isOk());

        // Verify Update
        Staff updatedManager = staffRepository.findByIsDeletedIsFalseAndEmail("manager@tbm.com").orElseThrow();
        assertThat(updatedManager.getName()).isEqualTo("Manager Updated");

        // Verify Create
        Staff importedUser = staffRepository.findByIsDeletedIsFalseAndEmail("imported@tbm.com").orElseThrow();
        assertThat(importedUser.getName()).isEqualTo("Imported User");
        assertThat(importedUser.getRole().getName()).isEqualTo("Developer");
    }

    // --- Scenario 7: Export Data ---
    @Test
    @WithMockUser(authorities = "CAN_MANAGE_STAFF")
    void exportStaffAccountData_ShouldReturnExcel() throws Exception {
        mockMvc.perform(get("/api/staff/export"))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=Exported_Staff_Account_Data.xlsx"))
                .andExpect(content().contentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"));
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

    private Staff createStaff(String name, String email, Role role, Staff manager) {
        Staff s = new Staff();
        s.setId(UUID.randomUUID());
        s.setName(name);
        s.setEmail(email);
        s.setRole(role);
        s.setManager(manager);
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
            String[] headers = {
                    "Staff Email", "Staff Name", "Department Name", "Role Name",
                    "Career Pathway Name", "Direct Manager Email", "Account Status",
                    "New Staff Email", "To Be Deleted"
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