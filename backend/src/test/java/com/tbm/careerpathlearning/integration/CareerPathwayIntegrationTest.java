package com.tbm.careerpathlearning.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tbm.careerpathlearning.dto.CreateCareerPathwayRequestDto;
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
public class CareerPathwayIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private CareerPathwayRepository careerPathwayRepository;

    @Autowired
    private CareerPathwayRoleRepository careerPathwayRoleRepository;

    @Autowired
    private OrgChartRepository orgChartRepository;

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private TrackRepository trackRepository;

    @Autowired
    private AuthorityRepository authorityRepository;

    @Autowired
    private StaffRepository staffRepository;

    private static final String MOCK_ADMIN_ID = "d371028b-cfd7-4315-bcd5-3790e5eac036";
    private OrgChart dept;
    private Role juniorDev;
    private Role seniorDev;
    private Role leadDev;

    @BeforeEach
    void setUp() {
        // Cleanup
        careerPathwayRoleRepository.deleteAll();
        careerPathwayRepository.deleteAll();
        trackRepository.deleteAll();
        roleRepository.deleteAll();
        orgChartRepository.deleteAll();
        authorityRepository.deleteAll();

        // Setup Auth
        createAuthority(AuthorityName.CAN_MANAGE_CAREER_PATHWAY);

        // Setup Org
        dept = createOrgChart("IT Dept");

        // Setup Roles (Nodes in the pathway)
        juniorDev = createRole("Junior Dev", dept);
        seniorDev = createRole("Senior Dev", dept);
        leadDev = createRole("Lead Dev", dept);
    }

    // --- Scenario 1: Create Career Pathway (Valid Tree) ---
    @Test
    @WithMockUser(username = MOCK_ADMIN_ID, authorities = "CAN_MANAGE_CAREER_PATHWAY")
    void createCareerPathway_ShouldBuildGraph() throws Exception {
        // Graph: Junior -> Senior -> Lead
        Map<Long, Set<Long>> graph = new HashMap<>();
        graph.put(juniorDev.getId(), Set.of(seniorDev.getId()));
        graph.put(seniorDev.getId(), Set.of(leadDev.getId()));

        CreateCareerPathwayRequestDto req = new CreateCareerPathwayRequestDto();
        req.setCareerPathwayName("Engineering Track");
        req.setOrgChartId(dept.getId());
        req.setRootRoleId(juniorDev.getId());
        req.setParentChildRoleId(graph);
        req.setTrack(List.of("Technical"));

        mockMvc.perform(post("/api/career-pathway")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").exists());

        // Verify DB
        List<CareerPathway> pathways = careerPathwayRepository.findAll();
        assertThat(pathways).hasSize(1);
        CareerPathway cp = pathways.get(0);
        assertThat(cp.getName()).isEqualTo("Engineering Track");

        // Verify Edges
        List<CareerPathwayRole> edges = careerPathwayRoleRepository.findByCareerPathwayId(cp.getId());
        assertThat(edges).hasSize(2); // Junior->Senior, Senior->Lead
    }

    // --- Scenario 2: Create Invalid Pathway (Cycle) ---
    @Test
    @WithMockUser(username = MOCK_ADMIN_ID, authorities = "CAN_MANAGE_CAREER_PATHWAY")
    void createCareerPathway_ShouldFail_WhenCycleExists() throws Exception {
        // Cycle: Junior -> Senior -> Junior
        Map<Long, Set<Long>> graph = new HashMap<>();
        graph.put(juniorDev.getId(), Set.of(seniorDev.getId()));
        graph.put(seniorDev.getId(), Set.of(juniorDev.getId()));

        CreateCareerPathwayRequestDto req = new CreateCareerPathwayRequestDto();
        req.setCareerPathwayName("Cyclic Track");
        req.setOrgChartId(dept.getId());
        req.setRootRoleId(juniorDev.getId());
        req.setParentChildRoleId(graph);

        mockMvc.perform(post("/api/career-pathway")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest()); // Expect Graph Validation Error
    }

    // --- Scenario 3: Get Overview (Graph Structure) ---
    @Test
    @WithMockUser(authorities = "CAN_MANAGE_CAREER_PATHWAY")
    void overview_ShouldReturnNestedJSON() throws Exception {
        // Setup: A -> B
        createSimplePathway("Dev Track", juniorDev, seniorDev);

        mockMvc.perform(get("/api/career-pathway/overview"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].graph.name").value("Junior Dev")) // Root
                .andExpect(jsonPath("$[0].graph.children[0].name").value("Senior Dev")); // Child
    }

    // --- Scenario 4: Delete Pathway ---
    @Test
    @WithMockUser(username = MOCK_ADMIN_ID, authorities = "CAN_MANAGE_CAREER_PATHWAY")
    void delete_ShouldRemovePathwayAndEdges() throws Exception {
        CareerPathway cp = createSimplePathway("To Delete", juniorDev, seniorDev);

        mockMvc.perform(delete("/api/career-pathway/delete")
                        .param("selectedCareerPathwayId", cp.getId().toString()))
                .andExpect(status().isOk());

        // Verify Soft Delete of CP
        CareerPathway deleted = careerPathwayRepository.findById(cp.getId()).orElseThrow();
        assertThat(deleted.isDeleted()).isTrue();

        // Verify Hard Delete of Edges (Controller logic: careerPathwayRoleService.deleteByCareerPathwayId)
        assertThat(careerPathwayRoleRepository.findByCareerPathwayId(cp.getId())).isEmpty();
    }

    // --- Scenario 5: Import Data (Excel) ---
    @Test
    @WithMockUser(username = MOCK_ADMIN_ID, authorities = "CAN_MANAGE_CAREER_PATHWAY")
    void importData_ShouldParseGraphString() throws Exception {
        // Excel: "IT Dept" | "Manager Track" | "Desc" | "Mgt" | "Senior Dev" | "Senior Dev > Lead Dev; Lead Dev > CTO" | "" | ""
        // Note: Creating "CTO" role dynamically or ensure it exists?
        // Controller validates roles exist. Let's create CTO.
        Role cto = createRole("CTO", dept);

        String nodesString = "Senior Dev > Lead Dev; Lead Dev > CTO";

        byte[] excelBytes = createExcelData(new ArrayList<>(Collections.singleton(
                new String[]{"IT Dept", "Manager Track", "Desc", "Mgt", "Senior Dev", nodesString, "", ""}
        )));

        MockMultipartFile file = new MockMultipartFile(
                "file", "pathway.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                excelBytes
        );

        mockMvc.perform(multipart("/api/career-pathway/import")
                        .file(file))
                .andExpect(status().isOk());

        // Verify
        CareerPathway cp = careerPathwayRepository.findAll().stream()
                .filter(p -> p.getName().equals("Manager Track"))
                .findFirst().orElseThrow();

        List<CareerPathwayRole> edges = careerPathwayRoleRepository.findByCareerPathwayId(cp.getId());
        assertThat(edges).hasSize(2); // Senior->Lead, Lead->CTO
    }

    // --- Scenario 6: Export Data ---
    @Test
    @WithMockUser(authorities = "CAN_MANAGE_CAREER_PATHWAY")
    void exportCareerPathwayOverviewData_ShouldReturnExcel() throws Exception {
        createSimplePathway("Test Export", juniorDev, seniorDev);

        mockMvc.perform(get("/api/career-pathway/export"))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=Exported_Career_Pathway_Overview_Data.xlsx"))
                .andExpect(content().contentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"));
    }

    @Test
    @WithMockUser(username = MOCK_ADMIN_ID, authorities = "ROLE_USER")
    void my_ShouldReturnVisualizedGraph_WithVisitedStatus() throws Exception {
        // 1. Create a 3-level Pathway: Junior -> Senior -> Lead
        CareerPathway cp = createSimplePathway("Eng Track", juniorDev, seniorDev);

        // Manually add the 2nd edge (Senior -> Lead)
        CareerPathwayRole edge2 = new CareerPathwayRole();
        edge2.setId(new CareerPathwayRoleId(cp.getId(), seniorDev.getId(), leadDev.getId()));
        edge2.setCareerPathway(cp);
        edge2.setParentRole(seniorDev);
        edge2.setChildRole(leadDev);
        edge2.setCreatedAt(OffsetDateTime.now());
        edge2.setUpdatedAt(OffsetDateTime.now());
        edge2.setCreatedBy(UUID.fromString(MOCK_ADMIN_ID));
        careerPathwayRoleRepository.save(edge2);

        // 2. Create a Staff member assigned to the MIDDLE role (Senior Dev)
        Staff john = new Staff();
        john.setId(UUID.randomUUID());
        john.setName("John Doe");
        john.setEmail("john@example.com");
        john.setRole(seniorDev);
        john.setCareerPathway(cp);
        john.setAccountStatus(StaffAccountStatus.ACTIVE);
        john.setDeleted(false);
        john.setCreatedAt(OffsetDateTime.now());
        john.setUpdatedAt(OffsetDateTime.now());
        john = staffRepository.save(john);

        // 3. Execute
        mockMvc.perform(get("/api/career-pathway/my")
                        .param("staffId", john.getId().toString()))
                .andExpect(status().isOk())

                // 4. Verify Root Node (Junior Dev) -> Visited (1)
                // Logic: It is a parent of the current role
                .andExpect(jsonPath("$.graph.name").value("Junior Dev"))
                .andExpect(jsonPath("$.graph.data.hasVisited").value(1))

                // 5. Verify Child Node (Senior Dev) -> Current (0)
                // Logic: It matches the staff's current role ID
                .andExpect(jsonPath("$.graph.children[0].name").value("Senior Dev"))
                .andExpect(jsonPath("$.graph.children[0].data.hasVisited").value(0))

                // 6. Verify Grandchild Node (Lead Dev) -> Future (-1)
                // Logic: Neither current nor a parent
                .andExpect(jsonPath("$.graph.children[0].children[0].name").value("Lead Dev"))
                .andExpect(jsonPath("$.graph.children[0].children[0].data.hasVisited").value(-1));
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

    private CareerPathway createSimplePathway(String name, Role root, Role child) {
        CareerPathway cp = new CareerPathway();
        cp.setName(name);
        cp.setOrgChart(root.getOrgChart());
        cp.setRootRole(root);
        cp.setDeleted(false);
        cp.setCreatedAt(OffsetDateTime.now());
        cp.setUpdatedAt(OffsetDateTime.now());
        cp.setCreatedBy(UUID.fromString(MOCK_ADMIN_ID));
        cp = careerPathwayRepository.save(cp);

        CareerPathwayRole edge = new CareerPathwayRole();
        edge.setId(new CareerPathwayRoleId(cp.getId(), root.getId(), child.getId()));
        edge.setCareerPathway(cp);
        edge.setParentRole(root);
        edge.setChildRole(child);
        edge.setCreatedAt(OffsetDateTime.now());
        edge.setUpdatedAt(OffsetDateTime.now());
        edge.setCreatedBy(UUID.fromString(MOCK_ADMIN_ID));
        careerPathwayRoleRepository.save(edge);

        return cp;
    }

    private byte[] createExcelData(List<String[]> rows) throws Exception {
        try (Workbook workbook = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sheet = workbook.createSheet("Sheet 1");

            Row header = sheet.createRow(0);
            String[] headers = {"Department Name", "Career Pathway Name", "Career Pathway Description", "Tags", "Root Role Name", "Parent Role Name > Child Role Name", "New Career Pathway Name", "To Be Deleted"};
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