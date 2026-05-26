package com.tbm.careerpathlearning.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tbm.careerpathlearning.dto.CompetencyAssignmentDto;
import com.tbm.careerpathlearning.dto.CreateRoleCompetencyRequestDto;
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
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
public class RoleCompetenciesIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private CompetencyRepository competencyRepository;

    @Autowired
    private RoleCompetencyRepository roleCompetencyRepository;

    @Autowired
    private OrgChartRepository orgChartRepository;

    @Autowired
    private JobScopeRepository jobScopeRepository;

    @Autowired
    private RoleJobScopeRepository roleJobScopeRepository;

    @Autowired
    private StaffRepository staffRepository;

    @Autowired
    private CompTagRepository compTagRepository;

    @Autowired
    private CompetencyCompTagRepository competencyCompTagRepository;

    private static final String MOCK_ADMIN_ID = "d371028b-cfd7-4315-bcd5-3790e5eac036";
    private Role testRole;
    private Competency compJava;
    private Competency compLeadership;

    @BeforeEach
    void setUp() {
        // Clean DB
        roleCompetencyRepository.deleteAll();
        roleRepository.deleteAll();
        competencyRepository.deleteAll();
        orgChartRepository.deleteAll();

        // 1. Create Org Chart (Parent of Role)
        OrgChart dept = new OrgChart();
        dept.setName("IT Dept");
        dept.setType(OrgChartType.D);
        dept.setDeleted(false);
        dept.setCreatedAt(OffsetDateTime.now());
        dept.setUpdatedAt(OffsetDateTime.now());
        orgChartRepository.save(dept);

        // 2. Create Role
        testRole = new Role();
        testRole.setName("Senior Dev");
        testRole.setOrgChart(dept);
        testRole.setDeleted(false);
        testRole.setCreatedAt(OffsetDateTime.now());
        testRole.setUpdatedAt(OffsetDateTime.now());
        testRole.setCreatedBy(UUID.fromString(MOCK_ADMIN_ID));
        testRole.setUpdatedBy(UUID.fromString(MOCK_ADMIN_ID));
        testRole = roleRepository.save(testRole);

        // 3. Create Competencies
        compJava = createCompetency("Java");
        compLeadership = createCompetency("Leadership");
    }

    // --- Scenario 1: Assign Competencies (Create) ---
    @Test
    @WithMockUser(username = MOCK_ADMIN_ID, authorities = "CAN_MANAGE_ROLE")
    void createRoleCompetency_ShouldAssignWithWeightage() throws Exception {
        CreateRoleCompetencyRequestDto req = new CreateRoleCompetencyRequestDto();
        req.setRoleId(testRole.getId());

        CompetencyAssignmentDto assign1 = new CompetencyAssignmentDto();
        assign1.setCompetencyId(compJava.getId());
        assign1.setWeightage(80);

        CompetencyAssignmentDto assign2 = new CompetencyAssignmentDto();
        assign2.setCompetencyId(compLeadership.getId());
        assign2.setWeightage(20);

        req.setCompetencyAssignment(List.of(assign1, assign2));

        mockMvc.perform(post("/api/role-competency")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").exists());

        // Verify DB
        List<RoleCompetency> assignments = roleCompetencyRepository.findByRoleId(testRole.getId());
        assertThat(assignments).hasSize(2);

        RoleCompetency javaAssign = assignments.stream()
                .filter(a -> a.getCompetency().getId().equals(compJava.getId()))
                .findFirst().orElseThrow();
        assertThat(javaAssign.getWeightage()).isEqualTo(80);
    }

    // --- Scenario 2: Get Overview ---
    @Test
    @WithMockUser(authorities = "CAN_MANAGE_ROLE")
    void getCompetencyAssignmentOverview_ShouldReturnDetails() throws Exception {
        linkCompetency(testRole, compJava, 50);

        mockMvc.perform(get("/api/role-competency/overview"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].roleName").value("Senior Dev"))
                .andExpect(jsonPath("$[0].totalWeightage").value(50));
    }

    // --- Scenario 3: Update Assignment (Modify Weightage & Remove) ---
    @Test
    @WithMockUser(username = MOCK_ADMIN_ID, authorities = "CAN_MANAGE_ROLE")
    void createRoleCompetency_ShouldUpdateExistingAndRemoveUnlisted() throws Exception {
        // 1. Setup: Role has Java (50) and Leadership (50)
        linkCompetency(testRole, compJava, 50);
        linkCompetency(testRole, compLeadership, 50);

        // 2. Request: Keep Java (Update to 90), Remove Leadership (by omitting it)
        CreateRoleCompetencyRequestDto req = new CreateRoleCompetencyRequestDto();
        req.setRoleId(testRole.getId());

        CompetencyAssignmentDto updateJava = new CompetencyAssignmentDto();
        updateJava.setCompetencyId(compJava.getId());
        updateJava.setWeightage(90); // Changed

        req.setCompetencyAssignment(List.of(updateJava));

        mockMvc.perform(post("/api/role-competency")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk());

        // 3. Verify
        List<RoleCompetency> results = roleCompetencyRepository.findByRoleId(testRole.getId());
        assertThat(results).hasSize(1);

        RoleCompetency updated = results.get(0);
        assertThat(updated.getCompetency().getName()).isEqualTo("Java");
        assertThat(updated.getWeightage()).isEqualTo(90);
    }

    // --- Scenario 4: Delete Assignment ---
    @Test
    @WithMockUser(username = MOCK_ADMIN_ID, authorities = "CAN_MANAGE_ROLE")
    void deleteCompetency_ShouldRemoveAllForRole() throws Exception {
        linkCompetency(testRole, compJava, 100);

        mockMvc.perform(delete("/api/role-competency/delete")
                        .param("roleId", testRole.getId().toString()))
                .andExpect(status().isOk());

        assertThat(roleCompetencyRepository.findByRoleId(testRole.getId())).isEmpty();
    }

    // --- Scenario 5: Import Data (Excel) ---
    @Test
    @WithMockUser(username = MOCK_ADMIN_ID, authorities = "CAN_MANAGE_ROLE")
    void importData_ShouldParseWeightageString() throws Exception {
        // Excel: "IT Dept" | "Senior Dev" | "Java > 75; Leadership > 25"
        byte[] excelBytes = createExcelData(new ArrayList<>(Collections.singleton(
                new String[]{"IT Dept", "Senior Dev", "Java > 75; Leadership > 25"}
        )));

        MockMultipartFile file = new MockMultipartFile(
                "file", "assignments.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                excelBytes
        );

        mockMvc.perform(multipart("/api/role-competency/import")
                        .file(file))
                .andExpect(status().isOk());

        // Verify
        List<RoleCompetency> assignments = roleCompetencyRepository.findByRoleId(testRole.getId());
        assertThat(assignments).hasSize(2);

        assertThat(assignments).anyMatch(a -> a.getCompetency().getName().equals("Java") && a.getWeightage() == 75);
        assertThat(assignments).anyMatch(a -> a.getCompetency().getName().equals("Leadership") && a.getWeightage() == 25);
    }

    // --- Scenario 6: Export Data ---
    @Test
    @WithMockUser(authorities = "CAN_MANAGE_ROLE")
    void exportCompetencyAssignmentData_ShouldReturnExcel() throws Exception {
        linkCompetency(testRole, compJava, 100);

        mockMvc.perform(get("/api/role-competency/export"))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=Exported_Competency_Assignment_Data.xlsx"))
                .andExpect(content().contentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"));
    }

    // --- Scenario 7: Get Role Details (Deep Fetch) ---
    @Test
    @WithMockUser(authorities = "ROLE_USER")
    void getRoleDetails_ShouldReturnAggregatedInfo() throws Exception {
        // 1. Setup Data Aggregation
        linkCompetency(testRole, compJava, 50);

        JobScope js = createJobScope("Develop Code");
        linkRoleJobScope(testRole, js);

        createStaff("Alice", testRole);

        CompTag tag = createTag("Backend");
        linkCompetencyTag(compJava, tag);

        // 2. Perform Request
        mockMvc.perform(get("/api/role-competency/role-details")
                        .param("roleId", testRole.getId().toString()))
                .andExpect(status().isOk())

                // 3. Verify Aggregated Fields
                .andExpect(jsonPath("$.roleName").value("Senior Dev"))
                .andExpect(jsonPath("$.totalWeightage").value(50))
                .andExpect(jsonPath("$.jobScopes[0].jobScope").value("Develop Code"))
                .andExpect(jsonPath("$.staffs[0].name").value("Alice"))
                .andExpect(jsonPath("$.staffs[0].password").doesNotExist()) // Security Check
                .andExpect(jsonPath("$.competencies[0].competency.name").value("Java"))
                // Check tag map: assignedCompTags['compId'] contains 'Backend'
                .andExpect(jsonPath("$.compTags['" + compJava.getId() + "'][0]").value("Backend"));
    }

    // --- Scenario 8: Get Role Details Overview (List) ---
    @Test
    @WithMockUser(authorities = "ROLE_USER")
    void getRoleDetailsOverview_ShouldReturnList() throws Exception {
        // Role 1: Senior Dev
        linkCompetency(testRole, compJava, 60);

        // Role 2: Lead Dev (New Role)
        Role leadRole = createRole("Lead Dev", testRole.getOrgChart());
        linkCompetency(leadRole, compLeadership, 40);

        mockMvc.perform(get("/api/role-competency/role-details-overview"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[?(@.roleName=='Senior Dev')].totalWeightage").value(60))
                .andExpect(jsonPath("$[?(@.roleName=='Lead Dev')].totalWeightage").value(40));
    }

    // Helpers
    private Competency createCompetency(String name) {
        Competency c = new Competency();
        c.setName(name);
        c.setDeleted(false);
        c.setCreatedAt(OffsetDateTime.now());
        c.setUpdatedAt(OffsetDateTime.now());
        c.setCreatedBy(UUID.fromString(MOCK_ADMIN_ID));
        c.setUpdatedBy(UUID.fromString(MOCK_ADMIN_ID));
        return competencyRepository.save(c);
    }

    private void linkCompetency(Role role, Competency comp, int weight) {
        RoleCompetency rc = new RoleCompetency();
        rc.setId(new RoleCompetencyId(role.getId(), comp.getId()));
        rc.setRole(role);
        rc.setCompetency(comp);
        rc.setWeightage(weight);
        rc.setCreatedAt(OffsetDateTime.now());
        rc.setUpdatedAt(OffsetDateTime.now());
        rc.setCreatedBy(UUID.fromString(MOCK_ADMIN_ID));
        rc.setUpdatedBy(UUID.fromString(MOCK_ADMIN_ID));
        roleCompetencyRepository.save(rc);
    }

    private byte[] createExcelData(List<String[]> rows) throws Exception {
        try (Workbook workbook = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sheet = workbook.createSheet("Sheet 1");

            Row header = sheet.createRow(0);
            String[] headers = {"Department Name", "Role Name", "Competency Name > Weightage"};
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

    private Role createRole(String name, OrgChart oc) {
        Role r = new Role();
        r.setName(name);
        r.setOrgChart(oc);
        r.setDeleted(false);
        r.setCreatedAt(OffsetDateTime.now());
        r.setUpdatedAt(OffsetDateTime.now());
        return roleRepository.save(r);
    }

    private JobScope createJobScope(String name) {
        JobScope js = new JobScope();
        js.setJobScope(name);
        js.setDeleted(false);
        js.setCreatedAt(OffsetDateTime.now());
        js.setUpdatedAt(OffsetDateTime.now());
        return jobScopeRepository.save(js);
    }

    private void linkRoleJobScope(Role r, JobScope js) {
        RoleJobScope rjs = new RoleJobScope();
        rjs.setId(new RoleJobScopeId(r.getId(), js.getId()));
        rjs.setRole(r);
        rjs.setJobScope(js);
        rjs.setCreatedAt(OffsetDateTime.now());
        rjs.setUpdatedAt(OffsetDateTime.now());
        roleJobScopeRepository.save(rjs);
    }

    private void createStaff(String name, Role r) {
        Staff s = new Staff();
        s.setId(UUID.randomUUID());
        s.setName(name);
        s.setEmail(name.toLowerCase() + "@test.com");
        s.setRole(r);
        s.setAccountStatus(StaffAccountStatus.ACTIVE);
        s.setDeleted(false);
        s.setCreatedAt(OffsetDateTime.now());
        s.setUpdatedAt(OffsetDateTime.now());
        staffRepository.save(s);
    }

    private CompTag createTag(String name) {
        CompTag t = new CompTag();
        t.setTag(name);
        t.setDeleted(false);
        t.setCreatedAt(OffsetDateTime.now());
        t.setUpdatedAt(OffsetDateTime.now());
        return compTagRepository.save(t);
    }

    private void linkCompetencyTag(Competency c, CompTag t) {
        CompetencyCompTag link = new CompetencyCompTag();
        link.setId(new CompetencyCompTagId(c.getId(), t.getId()));
        link.setCompetency(c);
        link.setCompTag(t);
        link.setCreatedAt(OffsetDateTime.now());
        link.setUpdatedAt(OffsetDateTime.now());
        competencyCompTagRepository.save(link);
    }
}