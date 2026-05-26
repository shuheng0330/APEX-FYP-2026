package com.tbm.careerpathlearning.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tbm.careerpathlearning.dto.*;
import com.tbm.careerpathlearning.model.CompetencyCompTagId;
import com.tbm.careerpathlearning.service.*;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.MessageSource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.core.Authentication;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.*;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(controllers = CompetencyController.class)
@AutoConfigureMockMvc(addFilters = false)
class CompetencyControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private CompetencyService competencyService;

    @MockitoBean
    private CompetencyCompTagService competencyCompTagService;

    @MockitoBean
    private CompTagService compTagService;

    @MockitoBean
    private ValidationService validationService;

    @MockitoBean
    private MessageSource messageSource;

    @MockitoBean
    private TokenService tokenService;

    private Authentication authentication;
    private UUID userUUID;
    private CompetencyDto mockCompetency;
    private CompTagDto mockTag;

    @BeforeEach
    void setUp() {
        userUUID = UUID.randomUUID();
        authentication = mock(Authentication.class);
        when(authentication.getName()).thenReturn(userUUID.toString());
        when(authentication.getPrincipal()).thenReturn(userUUID.toString());

        mockCompetency = new CompetencyDto();
        mockCompetency.setId(1L);
        mockCompetency.setName("Java");
        mockCompetency.setDescription("Core Java");

        mockTag = new CompTagDto();
        mockTag.setId(100L);
        mockTag.setTag("Backend");

        lenient().when(messageSource.getMessage(any(), any(), any(Locale.class))).thenReturn("Mock Message");
    }

    @Test
    void getAllCompetencies_ShouldReturnList() throws Exception {
        when(competencyService.findAllByIsDeletedIsFalse()).thenReturn(List.of(mockCompetency));

        mockMvc.perform(get("/api/competency"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.size()").value(1))
                .andExpect(jsonPath("$[0].name").value("Java"));
    }

    // --- GET Overview ---

    @Test
    void getCompetencyOverview_ShouldReturnList() throws Exception {
        CompetencyCompTagDto cct = new CompetencyCompTagDto();
        cct.setCompetency(mockCompetency);
        cct.setCompTag(mockTag);

        when(competencyService.findAllByIsDeletedIsFalse()).thenReturn(List.of(mockCompetency));
        when(competencyCompTagService.findAll()).thenReturn(List.of(cct));

        mockMvc.perform(get("/api/competency/overview").principal(authentication))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].competencyName").value("Java"))
                .andExpect(jsonPath("$[0].assignedCompTags[0].tag").value("Backend"));
    }

    // --- Create ---

    @Test
    void createCompetency_ShouldCreateCompetencyAndTags() throws Exception {
        CreateCompetencyRequestDto req = new CreateCompetencyRequestDto();
        req.setCompetencyName("Java");
        req.setCompetencyDescription("Desc");
        req.setCompTagList(List.of("Backend"));

        when(validationService.isNullOrBlank(any())).thenReturn(false);
        when(competencyService.create(any(CompetencyDto.class))).thenReturn(mockCompetency);
        when(compTagService.createAll(anyList())).thenReturn(List.of(mockTag));

        mockMvc.perform(post("/api/competency")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req))
                        .principal(authentication))
                .andExpect(status().isOk());

        verify(competencyService).create(any(CompetencyDto.class));
        verify(compTagService).createAll(anyList());
        verify(competencyCompTagService).createAll(anyList());
    }

    @Test
    void createCompetency_ShouldFail_WhenNameMissing() throws Exception {
        CreateCompetencyRequestDto req = new CreateCompetencyRequestDto();
        // Name is null

        when(validationService.isNullOrBlank(null)).thenReturn(true);

        mockMvc.perform(post("/api/competency")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req))
                        .principal(authentication))
                .andExpect(status().isBadRequest());
    }

    // --- Edit ---

    @Test
    void updateCompetency_ShouldUpdateAndManageTags() throws Exception {
        EditCompetencyRequestDto req = new EditCompetencyRequestDto();
        req.setCompetencyId(1L);
        req.setCompetencyName("Java Advanced");
        req.setCompTagList(List.of("New Tag"));

        // Mock update
        when(competencyService.update(eq(1L), any(CompetencyDto.class))).thenReturn(mockCompetency);

        // Mock existing tags (Empty -> "New Tag" will be added)
        when(competencyCompTagService.findAllByCompetencyId(1L)).thenReturn(Collections.emptyList());

        // Mock tag creation
        CompTagDto newTag = new CompTagDto(); newTag.setId(200L); newTag.setTag("New Tag");
        when(compTagService.createAll(anyList())).thenReturn(List.of(newTag));

        mockMvc.perform(put("/api/competency/edit-competency")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req))
                        .principal(authentication))
                .andExpect(status().isOk());

        verify(competencyCompTagService).createAll(anyList()); // Should add new tag link
    }

    @Test
    void updateCompetency_ShouldRemoveUnusedTags() throws Exception {
        // Scenario: Existing tag "Old" is removed. It is NOT used by others.
        EditCompetencyRequestDto req = new EditCompetencyRequestDto();
        req.setCompetencyId(1L);
        req.setCompetencyName("Java");
        req.setCompTagList(Collections.emptyList()); // Removing all tags

        // Mock existing tag "Old" (ID 100)
        CompetencyCompTagDto existingLink = new CompetencyCompTagDto();
        existingLink.setId(new CompetencyCompTagId(1L, 100L));
        when(competencyCompTagService.findAllByCompetencyId(1L)).thenReturn(List.of(existingLink));

        // Mock update
        when(competencyService.update(eq(1L), any())).thenReturn(mockCompetency);

        // Mock check for other usage (Returns empty -> unused)
        when(competencyCompTagService.findAllUsedByOther(Set.of(100L), 1L)).thenReturn(Collections.emptyList());

        mockMvc.perform(put("/api/competency/edit-competency")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req))
                        .principal(authentication))
                .andExpect(status().isOk());

        verify(competencyCompTagService).deleteByCompetencyIdAndCompTagIdIn(eq(1L), anySet());
        verify(compTagService).deleteAllByIdIn(argThat(ids -> ids.contains(100L)), any(UUID.class));
    }

    // --- Delete ---

    @Test
    void deleteCompetency_ShouldCleanupTags() throws Exception {
        // Mock existing tags
        CompetencyCompTagDto existingLink = new CompetencyCompTagDto();
        existingLink.setId(new CompetencyCompTagId(1L, 100L));
        when(competencyCompTagService.findAllByCompetencyId(1L)).thenReturn(List.of(existingLink));

        // Mock usage check (Unused)
        when(competencyCompTagService.findAllUsedByOther(Set.of(100L), 1L)).thenReturn(Collections.emptyList());

        mockMvc.perform(delete("/api/competency/delete")
                        .param("competencyId", "1")
                        .principal(authentication))
                .andExpect(status().isOk());

        verify(competencyCompTagService).deleteAllByIdIn(anySet());
        verify(compTagService).deleteAllByIdIn(anySet(), any(UUID.class));
        verify(competencyService).delete(eq(1L), any(UUID.class));
    }

    @Test
    void bulkDeleteCompetency_ShouldCleanupMultiple() throws Exception {
        mockMvc.perform(delete("/api/competency/bulk-delete")
                        .param("competencyIds", "1,2")
                        .principal(authentication))
                .andExpect(status().isOk());

        verify(competencyService).deleteAllByIdIn(anySet(), any(UUID.class));
    }

    @Test
    void bulkDeleteCompetency_ShouldCleanupDependenciesAndOrphanTags() throws Exception {
        List<Long> idsToDelete = List.of(1L, 2L);

        // Mock: Competencies 1 & 2 use Tag A (100L) and Tag B (200L)
        CompetencyCompTagDto link1 = new CompetencyCompTagDto();
        link1.setId(new CompetencyCompTagId(1L, 100L));
        CompetencyCompTagDto link2 = new CompetencyCompTagDto();
        link2.setId(new CompetencyCompTagId(2L, 200L));

        when(competencyCompTagService.findAllByCompetencyIdIn(anySet()))
                .thenReturn(List.of(link1, link2));

        // Mock: Tag A (100L) is NOT used by anyone else (Orphan -> Delete)
        // Mock: Tag B (200L) IS used by Competency 3 (Not Orphan -> Keep)
        CompetencyCompTagDto link3 = new CompetencyCompTagDto();
        link3.setId(new CompetencyCompTagId(3L, 200L)); // Used by ID 3

        when(competencyCompTagService.findAllUsedByOthers(anySet(), anySet()))
                .thenReturn(List.of(link3)); // Only Tag B is returned here

        mockMvc.perform(delete("/api/competency/bulk-delete")
                        .param("competencyIds", "1,2")
                        .principal(authentication))
                .andExpect(status().isOk());

        // Verify: Links deleted
        verify(competencyCompTagService).deleteAllByIdIn(anySet());

        // Verify: Only Orphan Tag A (100L) is hard deleted. Tag B is kept.
        verify(compTagService).deleteAllByIdIn(argThat(set ->
                set.contains(100L) && !set.contains(200L)
        ), any(UUID.class));

        // Verify: Competencies deleted
        verify(competencyService).deleteAllByIdIn(anySet(), any(UUID.class));
    }

    // --- Export ---

    @Test
    void export_ShouldReturnExcel() throws Exception {
        when(competencyService.findAllByIsDeletedIsFalse()).thenReturn(List.of(mockCompetency));
        when(competencyCompTagService.findAll()).thenReturn(Collections.emptyList());

        mockMvc.perform(get("/api/competency/export").principal(authentication))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=Exported_Competency_Overview_Data.xlsx"));
    }

    @Test
    void export_ShouldReturn500_WhenServiceFails() throws Exception {
        when(competencyService.findAllByIsDeletedIsFalse()).thenThrow(new RuntimeException("DB Error"));

        mockMvc.perform(get("/api/competency/export").principal(authentication))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.message").value("Mock Message"));
    }

    // --- Import (Complex Scenarios) ---

    @Test
    void import_ShouldProcessValidFile() throws Exception {
        List<String[]> data = new ArrayList<>(Collections.singleton(new String[]{"Java", "Desc", "Tag1", "", ""}));
        byte[] excelBytes = createExcelFromData(data);
        MockMultipartFile file = new MockMultipartFile("file", "import.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", excelBytes);

        // Mocks
        when(competencyService.findAllByIsDeletedIsFalse()).thenReturn(Collections.emptyList()); // New record
        when(validationService.isNullOrBlank(any())).thenAnswer(inv -> {
            String s = inv.getArgument(0);
            return s == null || s.trim().isEmpty();
        });

        // Mock creation return
        CompetencyDto createdComp = new CompetencyDto(); createdComp.setId(1L); createdComp.setName("Java");
        when(competencyService.createAndUpdateAll(anyList())).thenReturn(List.of(createdComp));

        // Mock tag creation
        CompTagDto createdTag = new CompTagDto(); createdTag.setId(100L); createdTag.setTag("Tag1");
        when(compTagService.createAll(anyList())).thenReturn(List.of(createdTag));

        mockMvc.perform(multipart("/api/competency/import").file(file).principal(authentication))
                .andExpect(status().isOk());

        verify(competencyService).createAndUpdateAll(anyList());
        verify(compTagService).createAll(anyList());
        verify(competencyCompTagService).createAll(anyList());
    }

    @Test
    void import_ShouldFail_WhenActionConflict() throws Exception {
        List<String[]> data = new ArrayList<>(Collections.singleton(new String[]{"Java", "Desc", "Tag1", "NewName", "Yes"}));
        byte[] excelBytes = createExcelFromData(data);
        MockMultipartFile file = new MockMultipartFile("file", "import.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", excelBytes);

        when(validationService.isNullOrBlank(any())).thenAnswer(inv -> {
            String s = inv.getArgument(0);
            return s == null || s.trim().isEmpty();
        });

        mockMvc.perform(multipart("/api/competency/import").file(file).principal(authentication))
                .andExpect(status().isBadRequest()); // IMPORT_ACTION_CONFLICT_ERR_MSG_CODE
    }

    @Test
    void import_ShouldFail_WhenRenameConflict() throws Exception {
        List<String[]> data = new ArrayList<>(Collections.singleton(new String[]{"Java", "", "", "Python", ""}));
        byte[] excelBytes = createExcelFromData(data);
        MockMultipartFile file = new MockMultipartFile("file", "import.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", excelBytes);

        // DB has "Java" and "Python"
        CompetencyDto java = new CompetencyDto(); java.setName("Java");
        CompetencyDto python = new CompetencyDto(); python.setName("Python");
        when(competencyService.findAllByIsDeletedIsFalse()).thenReturn(List.of(java, python));

        when(validationService.isNullOrBlank(any())).thenAnswer(inv -> {
            String s = inv.getArgument(0);
            return s == null || s.trim().isEmpty();
        });

        mockMvc.perform(multipart("/api/competency/import").file(file).principal(authentication))
                .andExpect(status().isBadRequest()); // IMPORT_UNIQUE_NAME_ERR_MSG_CODE
    }

    @Test
    void import_ShouldFail_WhenDuplicateInFile() throws Exception {
        // Two rows for "Java"
        List<String[]> data = List.of(
                new String[]{"Java", "", "", "", ""},
                new String[]{"Java", "", "", "", ""}
        );
        byte[] excelBytes = createExcelFromData(data);
        MockMultipartFile file = new MockMultipartFile("file", "import.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", excelBytes);

        when(validationService.isNullOrBlank(any())).thenAnswer(inv -> {
            String s = inv.getArgument(0);
            return s == null || s.trim().isEmpty();
        });

        mockMvc.perform(multipart("/api/competency/import").file(file).principal(authentication))
                .andExpect(status().isBadRequest()); // IMPORT_DUPLICATE_ENTRY_ERR_MSG_CODE
    }

    @Test
    void import_ShouldUpdateExistingCompetencyAndTags() throws Exception {
        List<String[]> data = new ArrayList<>(Collections.singleton(new String[]{"Java", "New Desc", "Tag2", "", ""}));
        byte[] excelBytes = createExcelFromData(data);
        MockMultipartFile file = new MockMultipartFile("file", "import.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", excelBytes);

        // Mock DB: "Java" exists
        when(competencyService.findAllByIsDeletedIsFalse()).thenReturn(List.of(mockCompetency));

        // Mock DB: "Java" currently has "Tag1"
        CompetencyCompTagDto existingLink = new CompetencyCompTagDto();
        existingLink.setId(new CompetencyCompTagId(1L, 100L));
        CompTagDto tag1 = new CompTagDto(); tag1.setId(100L); tag1.setTag("Tag1");
        existingLink.setCompTag(tag1);
        existingLink.setCompetency(mockCompetency);

        when(competencyCompTagService.findAll()).thenReturn(List.of(existingLink));

        when(validationService.isNullOrBlank(any())).thenAnswer(inv -> {
            String s = inv.getArgument(0);
            return s == null || s.trim().isEmpty();
        });

        // Mock Update return (must match logic to prevent NPE in final map construction)
        when(competencyService.createAndUpdateAll(anyList())).thenReturn(List.of(mockCompetency));

        // Mock Tag Creation for "Tag2"
        CompTagDto tag2 = new CompTagDto(); tag2.setId(200L); tag2.setTag("Tag2");
        when(compTagService.createAll(anyList())).thenReturn(List.of(tag2));

        mockMvc.perform(multipart("/api/competency/import").file(file).principal(authentication))
                .andExpect(status().isOk());

        // Verify: "Tag1" (100L) is removed (since it's not in file)
        verify(competencyCompTagService).deleteAllByIdIn(argThat(set ->
                set.stream().anyMatch(id -> id.getCompTagId().equals(100L))
        ));

        // Verify: "Tag2" is linked
        verify(competencyCompTagService).createAll(argThat(list ->
                list.stream().anyMatch(dto -> dto.getCompTag().getTag().equals("Tag2"))
        ));
    }

    @Test
    void import_ShouldSoftDelete_WhenFlaggedInFile() throws Exception {
        List<String[]> data = new ArrayList<>(Collections.singleton(new String[]{"Java", "", "", "", "Yes"}));
        byte[] excelBytes = createExcelFromData(data);
        MockMultipartFile file = new MockMultipartFile("file", "import.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", excelBytes);

        // Mock DB: "Java" exists
        when(competencyService.findAllByIsDeletedIsFalse()).thenReturn(List.of(mockCompetency));
        when(validationService.isNullOrBlank(any())).thenAnswer(inv -> {
            String s = inv.getArgument(0);
            return s == null || s.trim().isEmpty();
        });

        // Verify the service receives a list with "Java" set to deleted=true
        when(competencyService.createAndUpdateAll(anyList())).thenAnswer(inv -> {
            List<CompetencyDto> list = inv.getArgument(0);
            if (list.get(0).isDeleted()) return list;
            throw new RuntimeException("Expected deleted=true");
        });

        mockMvc.perform(multipart("/api/competency/import").file(file).principal(authentication))
                .andExpect(status().isOk());

        verify(competencyService).createAndUpdateAll(anyList());
    }

    @Test
    void import_ShouldFail_WhenDescriptionTooLong() throws Exception {
        String longDesc = "a".repeat(1001);
        List<String[]> data = new ArrayList<>(Collections.singleton(new String[]{"Java", longDesc, "", "", ""}));
        byte[] excelBytes = createExcelFromData(data);
        MockMultipartFile file = new MockMultipartFile("file", "import.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", excelBytes);

        when(validationService.isNullOrBlank(any())).thenReturn(false);

        mockMvc.perform(multipart("/api/competency/import").file(file).principal(authentication))
                .andExpect(status().isBadRequest());
    }

    @Test
    void import_ShouldHandle_NumericAndBooleanCells() throws Exception {
        byte[] excelBytes;
        try (Workbook workbook = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sheet = workbook.createSheet("Sheet 1");
            Row header = sheet.createRow(0);
            String[] headers = {"Competency Name", "Competency Description", "Tags", "New Competency Name", "To Be Deleted"};
            for (int i = 0; i < headers.length; i++) header.createCell(i).setCellValue(headers[i]);

            Row row = sheet.createRow(1);
            // Numeric Name: 123
            row.createCell(0).setCellValue(123);
            // Boolean Description: true (just to test parser)
            row.createCell(1).setCellValue(true);
            // Empty tags
            row.createCell(2).setCellValue("");
            // others empty...

            workbook.write(out);
            excelBytes = out.toByteArray();
        }

        MockMultipartFile file = new MockMultipartFile("file", "types.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", excelBytes);

        // Setup so it processes the row
        when(competencyService.findAllByIsDeletedIsFalse()).thenReturn(Collections.emptyList());
        when(validationService.isNullOrBlank(any())).thenAnswer(inv -> {
            String s = inv.getArgument(0);
            return s == null || s.trim().isEmpty();
        });

        // Expect success (123 converted to "123", true to "true")
        when(competencyService.createAndUpdateAll(anyList())).thenReturn(Collections.emptyList());

        mockMvc.perform(multipart("/api/competency/import").file(file).principal(authentication))
                .andExpect(status().isOk());
    }

    // --- Helpers ---

    private byte[] createExcelFromData(List<String[]> rowsData) throws IOException {
        try (Workbook workbook = new XSSFWorkbook();
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sheet = workbook.createSheet("Sheet 1");

            Row header = sheet.createRow(0);
            String[] headers = {"Competency Name", "Competency Description", "Tags", "New Competency Name", "To Be Deleted"};
            for (int i = 0; i < headers.length; i++) {
                header.createCell(i).setCellValue(headers[i]);
            }

            int rowIdx = 1;
            for (String[] rowData : rowsData) {
                Row row = sheet.createRow(rowIdx++);
                for (int col = 0; col < rowData.length; col++) {
                    if (rowData[col] != null) row.createCell(col).setCellValue(rowData[col]);
                }
            }
            workbook.write(out);
            return out.toByteArray();
        }
    }
}