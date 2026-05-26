package com.tbm.careerpathlearning.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tbm.careerpathlearning.dto.OrgChartDto;
import com.tbm.careerpathlearning.enums.AuthorityName;
import com.tbm.careerpathlearning.enums.OrgChartType;
import com.tbm.careerpathlearning.model.Authority;
import com.tbm.careerpathlearning.model.OrgChart;
import com.tbm.careerpathlearning.repository.AuthorityRepository;
import com.tbm.careerpathlearning.repository.OrgChartRepository;
import com.tbm.careerpathlearning.repository.ParentChildNodeRepository;
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
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
public class OrgChartIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private OrgChartRepository orgChartRepository;

    @Autowired
    private ParentChildNodeRepository parentChildNodeRepository;

    @Autowired
    private AuthorityRepository authorityRepository;

    private static final String MOCK_USER_ID = "d371028b-cfd7-4315-bcd5-3790e5eac036";

    @BeforeEach
    void setUp() {
        parentChildNodeRepository.deleteAll();
        orgChartRepository.deleteAll();
        authorityRepository.deleteAll(); // Clean up first

        // SEED REQUIRED AUTHORITY
        Authority auth = new Authority();
        auth.setName(AuthorityName.ROLE_USER);
        auth.setLabelKey("label");
        auth.setDescriptionKey("desc");
        authorityRepository.save(auth);
    }

    // --- Scenario 1: Get All Org Charts (Empty & Populated) ---
    @Test
    @WithMockUser(authorities = "CAN_MANAGE_ORG_CHART")
    void getAllOrgChart_ShouldReturnList() throws Exception {
        // 1. Empty state
        mockMvc.perform(get("/api/orgChart"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.size()").value(0));

        // 2. Populate data
        createOrgChartInDb("IT Department", OrgChartType.D);
        createOrgChartInDb("HR Department", OrgChartType.D);

        // 3. Verify list
        mockMvc.perform(get("/api/orgChart"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.size()").value(2))
                .andExpect(jsonPath("$[0].name").exists());
    }

    // --- Scenario 2: Import Org Chart (Valid Excel) ---
    @Test
    @WithMockUser(username = MOCK_USER_ID, authorities = "CAN_MANAGE_ORG_CHART")
    void importOrgChart_ShouldCreateNodes_WhenFileIsValid() throws Exception {
        // Create valid Excel: IT Dept (D) -> Software Eng (P)
        byte[] excelBytes = createExcelData(List.of(
                new String[]{"D", "IT Dept", "", "", ""},          // Root
                new String[]{"P", "Software Engineer", "IT Dept", "", ""} // Child
        ));

        MockMultipartFile file = new MockMultipartFile(
                "file", "org.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                excelBytes
        );

        mockMvc.perform(multipart("/api/orgChart/import")
                        .file(file))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").exists());

        // Verify DB
        List<OrgChart> nodes = orgChartRepository.findAll();
        assertThat(nodes).hasSize(2);
        assertThat(nodes).anyMatch(n -> n.getName().equals("IT Dept") && n.getType() == OrgChartType.D);
        assertThat(nodes).anyMatch(n -> n.getName().equals("Software Engineer") && n.getType() == OrgChartType.P);
    }

    // --- Scenario 3: Import Org Chart (Cycle Detection) ---
    @Test
    @WithMockUser(username = MOCK_USER_ID, authorities = "CAN_MANAGE_ORG_CHART")
    void importOrgChart_ShouldFail_WhenCycleDetected() throws Exception {
        // Create Cyclic Excel: A -> B -> A
        byte[] excelBytes = createExcelData(List.of(
                new String[]{"D", "Dept A", "Dept B", "", ""}, // A depends on B
                new String[]{"D", "Dept B", "Dept A", "", ""}  // B depends on A
        ));

        MockMultipartFile file = new MockMultipartFile(
                "file", "cyclic.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                excelBytes
        );

        mockMvc.perform(multipart("/api/orgChart/import")
                        .file(file))
                .andExpect(status().isBadRequest()); // Expect cycle error
    }

    // --- Scenario 4: Export Org Chart ---
    @Test
    @WithMockUser(authorities = "CAN_MANAGE_ORG_CHART")
    void exportOrgChart_ShouldReturnExcelFile() throws Exception {
        // Populate DB
        createOrgChartInDb("Finance", OrgChartType.D);

        mockMvc.perform(get("/api/orgChart/export"))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=Exported_Organizational_Chart_Data.xlsx"))
                .andExpect(content().contentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"));
    }

    // --- Scenario 5: Show Org Chart (Graph Structure) ---
    @Test
    @WithMockUser(authorities = "ROLE_USER")
    void showOrgChart_ShouldReturnHierarchy() throws Exception {
        // Manually link nodes in DB if needed, or rely on services if available.
        // For Level 1 simple test, we verify the endpoint doesn't crash with empty data.
        mockMvc.perform(get("/api/orgChart/show-org-chart"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray());
    }

    // --- Helper to create DB data ---
    private OrgChart createOrgChartInDb(String name, OrgChartType type) {
        OrgChart node = new OrgChart();
        node.setName(name);
        node.setType(type);
        node.setRoot(true); // Simplify for test
        node.setDeleted(false);
        node.setCreatedAt(OffsetDateTime.now());
        node.setUpdatedAt(OffsetDateTime.now());
        return orgChartRepository.save(node);
    }

    // --- Helper to create Excel bytes ---
    private byte[] createExcelData(List<String[]> rows) throws Exception {
        try (Workbook workbook = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sheet = workbook.createSheet("Sheet 1");

            // Header
            Row header = sheet.createRow(0);
            String[] headers = {"Node Type", "Node Name", "Parent Node Name", "Rename To", "To Be Deleted"};
            for (int i = 0; i < headers.length; i++) header.createCell(i).setCellValue(headers[i]);

            // Data
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