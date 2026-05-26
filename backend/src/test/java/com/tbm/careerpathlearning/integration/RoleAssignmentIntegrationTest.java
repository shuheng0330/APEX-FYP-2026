package com.tbm.careerpathlearning.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tbm.careerpathlearning.dto.UpdateRoleAssignmentRequestDto;
import com.tbm.careerpathlearning.enums.AuthorityName;
import com.tbm.careerpathlearning.enums.OrgChartType;
import com.tbm.careerpathlearning.enums.StaffAccountStatus;
import com.tbm.careerpathlearning.model.Authority;
import com.tbm.careerpathlearning.model.OrgChart;
import com.tbm.careerpathlearning.model.Role;
import com.tbm.careerpathlearning.model.Staff;
import com.tbm.careerpathlearning.repository.AuthorityRepository;
import com.tbm.careerpathlearning.repository.OrgChartRepository;
import com.tbm.careerpathlearning.repository.RoleRepository;
import com.tbm.careerpathlearning.repository.StaffRepository;
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
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
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
public class RoleAssignmentIntegrationTest {

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

    private static final String MOCK_ADMIN_ID = "d371028b-cfd7-4315-bcd5-3790e5eac036";
    private OrgChart itDept;
    private Role devRole;
    private Staff staffAlice;
    private Staff staffBob;

    @BeforeEach
    void setUp() {
        // Clean DB
        staffRepository.deleteAll();
        roleRepository.deleteAll();
        orgChartRepository.deleteAll();
        authorityRepository.deleteAll();

        // 1. Setup Auth (Required for Role Creation logic if invoked)
        Authority auth = new Authority();
        auth.setName(AuthorityName.ROLE_USER);
        auth.setLabelKey("label");
        auth.setDescriptionKey("desc");
        authorityRepository.save(auth);

        // 2. Setup Org Chart
        itDept = new OrgChart();
        itDept.setName("IT Dept");
        itDept.setType(OrgChartType.D);
        itDept.setRoot(true);
        itDept.setDeleted(false);
        itDept.setCreatedAt(OffsetDateTime.now());
        itDept.setUpdatedAt(OffsetDateTime.now());
        itDept = orgChartRepository.save(itDept);

        // 3. Setup Role
        devRole = new Role();
        devRole.setName("Developer");
        devRole.setOrgChart(itDept);
        devRole.setVisible(true);
        devRole.setDeleted(false);
        devRole.setCreatedAt(OffsetDateTime.now());
        devRole.setUpdatedAt(OffsetDateTime.now());
        devRole = roleRepository.save(devRole);

        // 4. Setup Staff (Initially No Role)
        staffAlice = createStaff("Alice", "alice@tbm.com");
        staffBob = createStaff("Bob", "bob@tbm.com");

        // 5. Setup Security Context
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(
                        MOCK_ADMIN_ID, "password", List.of(new SimpleGrantedAuthority("CAN_MANAGE_ROLE"))
                )
        );
    }

    // --- Scenario 1: Assign Role (Create) ---
    @Test
    void createRoleAssignment_ShouldLinkStaffToRole() throws Exception {
        UpdateRoleAssignmentRequestDto req = new UpdateRoleAssignmentRequestDto();
        req.setRoleId(devRole.getId());
        req.setStaffIds(List.of(staffAlice.getId()));

        mockMvc.perform(post("/api/role/assignment")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").exists());

        // Verify DB
        Staff updatedAlice = staffRepository.findById(staffAlice.getId()).orElseThrow();
        assertThat(updatedAlice.getRole()).isNotNull();
        assertThat(updatedAlice.getRole().getId()).isEqualTo(devRole.getId());
    }

    // --- Scenario 2: Edit Assignment (Swap Staff) ---
    @Test
    void updateRoleAssignment_ShouldAddAndRemoveStaff() throws Exception {
        // 1. Pre-condition: Alice has the role
        staffAlice.setRole(devRole);
        staffRepository.saveAndFlush(staffAlice);

        // 2. Request: Assign to Bob only (Implicitly remove Alice)
        UpdateRoleAssignmentRequestDto req = new UpdateRoleAssignmentRequestDto();
        req.setRoleId(devRole.getId());
        req.setStaffIds(List.of(staffBob.getId())); // Alice is missing from this list

        mockMvc.perform(put("/api/role/assignment/edit-role-assignment")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk());

        // 3. Verify DB
        Staff updatedAlice = staffRepository.findById(staffAlice.getId()).orElseThrow();
        Staff updatedBob = staffRepository.findById(staffBob.getId()).orElseThrow();

        assertThat(updatedAlice.getRole()).isNull(); // Removed
        assertThat(updatedBob.getRole().getId()).isEqualTo(devRole.getId()); // Added
    }

    // --- Scenario 3: Delete Assignment (By Role ID) ---
    @Test
    void deleteRole_ShouldUnassignAllStaff() throws Exception {
        // 1. Assign both
        staffAlice.setRole(devRole);
        staffBob.setRole(devRole);
        staffRepository.saveAll(List.of(staffAlice, staffBob));

        // 2. Delete Assignment Action
        mockMvc.perform(delete("/api/role/assignment/delete")
                        .param("roleId", devRole.getId().toString()))
                .andExpect(status().isOk());

        // 3. Verify DB
        List<Staff> staffList = staffRepository.findAll();
        assertThat(staffList).allMatch(s -> s.getRole() == null);
    }

    // --- Scenario 4: Get Overview ---
    @Test
    void getRoleAssignmentOverview_ShouldReturnDetails() throws Exception {
        staffAlice.setRole(devRole);
        staffRepository.save(staffAlice);

        mockMvc.perform(get("/api/role/assignment/overview"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].roleName").value("Developer"))
                .andExpect(jsonPath("$[0].staffList[0].email").value("alice@tbm.com"));
    }

    // --- Scenario 5: Import Data (Excel) ---
    @Test
    void importData_ShouldAssignRolesByEmail() throws Exception {
        // Excel: IT Dept | Developer | alice@tbm.com; bob@tbm.com
        byte[] excelBytes = createExcelData(new ArrayList<>(Collections.singleton(
                new String[]{"IT Dept", "Developer", "alice@tbm.com; bob@tbm.com"}
        )));

        MockMultipartFile file = new MockMultipartFile(
                "file", "assignments.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                excelBytes
        );

        mockMvc.perform(multipart("/api/role/assignment/import")
                        .file(file))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").exists());

        // Verify DB
        Staff uAlice = staffRepository.findById(staffAlice.getId()).orElseThrow();
        Staff uBob = staffRepository.findById(staffBob.getId()).orElseThrow();

        assertThat(uAlice.getRole().getName()).isEqualTo("Developer");
        assertThat(uBob.getRole().getName()).isEqualTo("Developer");
    }

    // --- Scenario 6: Export Data ---
    @Test
    void exportRoleAssignmentData_ShouldReturnExcel() throws Exception {
        staffAlice.setRole(devRole);
        staffRepository.save(staffAlice);

        mockMvc.perform(get("/api/role/assignment/export"))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=Exported_Role_Assignment_Data.xlsx"))
                .andExpect(content().contentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"));
    }

    // Helpers
    private Staff createStaff(String name, String email) {
        Staff s = new Staff();
        // Generate ID explicitly for consistent testing if needed, though DB gen is fine here
        s.setId(UUID.randomUUID());
        s.setName(name);
        s.setEmail(email);
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
            // Matches controller expectations exactly
            String[] headers = {"Department Name", "Role Name", "Staff Email"};
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