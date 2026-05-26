package com.tbm.careerpathlearning.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tbm.careerpathlearning.dto.CreateCompetencyRequestDto;
import com.tbm.careerpathlearning.dto.EditCompetencyRequestDto;
import com.tbm.careerpathlearning.model.CompTag;
import com.tbm.careerpathlearning.model.Competency;
import com.tbm.careerpathlearning.model.CompetencyCompTag;
import com.tbm.careerpathlearning.model.CompetencyCompTagId;
import com.tbm.careerpathlearning.repository.CompTagRepository;
import com.tbm.careerpathlearning.repository.CompetencyCompTagRepository;
import com.tbm.careerpathlearning.repository.CompetencyRepository;
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
public class CompetencyIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private CompetencyRepository competencyRepository;

    @Autowired
    private CompTagRepository compTagRepository;

    @Autowired
    private CompetencyCompTagRepository competencyCompTagRepository;

    private static final String MOCK_ADMIN_ID = "d371028b-cfd7-4315-bcd5-3790e5eac036";

    @BeforeEach
    void setUp() {
        competencyCompTagRepository.deleteAll();
        competencyRepository.deleteAll();
        compTagRepository.deleteAll();
    }

    // --- Scenario 1: Create Competency ---
    @Test
    @WithMockUser(username = MOCK_ADMIN_ID, authorities = "CAN_MANAGE_COMPETENCY")
    void createCompetency_ShouldSaveRecordAndTags() throws Exception {
        CreateCompetencyRequestDto req = new CreateCompetencyRequestDto();
        req.setCompetencyName("Java Programming");
        req.setCompetencyDescription("Core Java Skills");
        req.setCompTagList(List.of("Backend", "Language"));

        mockMvc.perform(post("/api/competency")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").exists());

        // Verify DB
        List<Competency> list = competencyRepository.findAll();
        assertThat(list).hasSize(1);
        Competency saved = list.get(0);
        assertThat(saved.getName()).isEqualTo("Java Programming");

        // Verify Tags
        assertThat(competencyCompTagRepository.findAllByCompetency_Id(saved.getId())).hasSize(2);
        assertThat(compTagRepository.findByTag("Backend")).isPresent();
    }

    // --- Scenario 2: Get Overview ---
    @Test
    @WithMockUser(authorities = "CAN_MANAGE_COMPETENCY")
    void getCompetencyOverview_ShouldReturnList() throws Exception {
        createCompetencyInDb("Leadership");

        mockMvc.perform(get("/api/competency/overview"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].competencyName").value("Leadership"));
    }

    // --- Scenario 3: Update Competency (Modify Tags) ---
    @Test
    @WithMockUser(username = MOCK_ADMIN_ID, authorities = "CAN_MANAGE_COMPETENCY")
    void updateCompetency_ShouldModifyTags() throws Exception {
        // 1. Setup
        Competency competency = createCompetencyInDb("Communication");
        CompTag oldTag = createTagInDb("Verbal");
        linkCompetencyTag(competency, oldTag);

        // 2. Update (Remove "Verbal", Add "Written")
        EditCompetencyRequestDto req = new EditCompetencyRequestDto();
        req.setCompetencyId(competency.getId());
        req.setCompetencyName("Advanced Communication"); // Rename
        req.setCompetencyDescription("Updated Desc");
        req.setCompTagList(List.of("Written"));

        mockMvc.perform(put("/api/competency/edit-competency")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk());

        // 3. Verify
        Competency updated = competencyRepository.findById(competency.getId()).orElseThrow();
        assertThat(updated.getName()).isEqualTo("Advanced Communication");

        assertThat(competencyCompTagRepository.findAllByCompetency_Id(competency.getId())).hasSize(1);

        // Verify orphaned tag cleanup logic (if applicable in your controller)
        // Controller calls compTagService.deleteAllByIdIn(toDeleteCompTag)
        assertThat(compTagRepository.findByTag("Verbal")
                .filter(tag -> !tag.getDeleted()))
                .isEmpty(); // Should be deleted
        assertThat(compTagRepository.findByTag("Written")).isPresent();
    }

    // --- Scenario 4: Delete Competency ---
    @Test
    @WithMockUser(username = MOCK_ADMIN_ID, authorities = "CAN_MANAGE_COMPETENCY")
    void deleteCompetency_ShouldSoftDelete() throws Exception {
        Competency competency = createCompetencyInDb("To Delete");

        mockMvc.perform(delete("/api/competency/delete")
                        .param("competencyId", competency.getId().toString()))
                .andExpect(status().isOk());

        Competency deleted = competencyRepository.findById(competency.getId()).orElseThrow();
        assertThat(deleted.isDeleted()).isTrue();
    }

    // --- Scenario 5: Import Data (Excel) ---
    @Test
    @WithMockUser(username = MOCK_ADMIN_ID, authorities = "CAN_MANAGE_COMPETENCY")
    void importData_ShouldCreateRecords() throws Exception {
        // Excel: "SQL" | "Querying" | "Database;Tech" | "" | ""
        byte[] excelBytes = createExcelData(new ArrayList<>(Collections.singleton(
                new String[]{"SQL", "Querying", "Database;Tech", "", ""}
        )));

        MockMultipartFile file = new MockMultipartFile(
                "file", "competency.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                excelBytes
        );

        mockMvc.perform(multipart("/api/competency/import")
                        .file(file))
                .andExpect(status().isOk());

        // Verify
        assertThat(competencyRepository.findAll()).hasSize(1);
        Competency imported = competencyRepository.findAll().get(0);
        assertThat(imported.getName()).isEqualTo("SQL");
        assertThat(competencyCompTagRepository.findAllByCompetency_Id(imported.getId())).hasSize(2);
    }

    // --- Scenario 6: Export Data ---
    @Test
    @WithMockUser(authorities = "CAN_MANAGE_COMPETENCY")
    void exportCompetencyOverviewData_ShouldReturnExcel() throws Exception {
        createCompetencyInDb("Teamwork");

        mockMvc.perform(get("/api/competency/export"))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=Exported_Competency_Overview_Data.xlsx"))
                .andExpect(content().contentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"));
    }

    // Helpers
    private Competency createCompetencyInDb(String name) {
        Competency c = new Competency();
        c.setName(name);
        c.setDeleted(false);
        c.setCreatedAt(OffsetDateTime.now());
        c.setUpdatedAt(OffsetDateTime.now());
        c.setCreatedBy(UUID.fromString(MOCK_ADMIN_ID));
        c.setUpdatedBy(UUID.fromString(MOCK_ADMIN_ID));
        return competencyRepository.save(c);
    }

    private CompTag createTagInDb(String name) {
        CompTag t = new CompTag();
        t.setTag(name);
        t.setDeleted(false);
        t.setCreatedAt(OffsetDateTime.now());
        t.setUpdatedAt(OffsetDateTime.now());
        t.setCreatedBy(UUID.fromString(MOCK_ADMIN_ID));
        t.setUpdatedBy(UUID.fromString(MOCK_ADMIN_ID));
        return compTagRepository.save(t);
    }

    private void linkCompetencyTag(Competency c, CompTag t) {
        CompetencyCompTag link = new CompetencyCompTag();
        link.setId(new CompetencyCompTagId(c.getId(), t.getId()));
        link.setCompetency(c);
        link.setCompTag(t);
        link.setCreatedAt(OffsetDateTime.now());
        link.setUpdatedAt(OffsetDateTime.now());
        link.setCreatedBy(UUID.fromString(MOCK_ADMIN_ID));
        link.setUpdatedBy(UUID.fromString(MOCK_ADMIN_ID));
        competencyCompTagRepository.save(link);
    }

    private byte[] createExcelData(List<String[]> rows) throws Exception {
        try (Workbook workbook = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sheet = workbook.createSheet("Sheet 1");

            Row header = sheet.createRow(0);
            String[] headers = {"Competency Name", "Competency Description", "Tags", "New Competency Name", "To Be Deleted"};
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