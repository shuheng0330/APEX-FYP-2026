package com.tbm.careerpathlearning.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tbm.careerpathlearning.dto.*;
import com.tbm.careerpathlearning.model.CompetencyCompTagId;
import com.tbm.careerpathlearning.model.RoleCompetencyId;
import com.tbm.careerpathlearning.model.RoleJobScopeId;
import com.tbm.careerpathlearning.service.*;
import org.apache.poi.ss.usermodel.*;
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
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.*;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(RoleCompetenciesController.class)
@AutoConfigureMockMvc(addFilters = false) // Disable security filters for unit testing controller logic
public class RoleCompetenciesControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private CompetencyService competencyService;
    @MockitoBean
    private RoleCompetencyService roleCompetencyService;
    @MockitoBean
    private MessageSource messageSource;
    @MockitoBean
    private RoleService roleService;
    @MockitoBean
    private RoleJobScopeService roleJobScopeService;
    @MockitoBean
    private StaffService staffService;
    @MockitoBean
    private CompetencyCompTagService competencyCompTagService;
    @MockitoBean
    private OrgChartService orgChartService;
    @MockitoBean
    private ValidationService validationService;
    @MockitoBean
    private TokenService tokenService;

    private Authentication authentication;
    private RoleDto mockRole;
    private CompetencyDto mockCompetency;
    private RoleCompetencyDto mockRoleCompetency;
    private JobScopeDto mockJobScope;
    private RoleJobScopeDto mockRoleJobScope;
    private StaffDto mockStaff;
    private final UUID USER_UUID = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        authentication = mock(Authentication.class);
        when(authentication.getName()).thenReturn(USER_UUID.toString());
        when(authentication.getPrincipal()).thenReturn(USER_UUID.toString());

        OrgChartDto org = new OrgChartDto();
        org.setId(1L);
        org.setName("IT Department");
        org.setDeleted(false);

        mockRole = new RoleDto();
        mockRole.setId(10L);
        mockRole.setName("Software Engineer");
        mockRole.setOrgChart(org);
        mockRole.setDeleted(false);

        mockCompetency = new CompetencyDto();
        mockCompetency.setId(100L);
        mockCompetency.setName("Java Programming");
        mockCompetency.setDeleted(false);

        mockRoleCompetency = new RoleCompetencyDto();
        mockRoleCompetency.setId(new RoleCompetencyId(10L, 100L));
        mockRoleCompetency.setRole(mockRole);
        mockRoleCompetency.setCompetency(mockCompetency);
        mockRoleCompetency.setWeightage(Integer.parseInt("50"));

        mockJobScope = new JobScopeDto();
        mockJobScope.setId(5L);
        mockJobScope.setJobScope("Coding");
        mockRoleJobScope = new RoleJobScopeDto();
        mockRoleJobScope.setId(new RoleJobScopeId(10L, 5L));
        mockRoleJobScope.setJobScope(mockJobScope);

        mockStaff = new StaffDto();
        mockStaff.setId(UUID.randomUUID());
        mockStaff.setName("John Doe");
        mockStaff.setRole(mockRole);

        lenient().when(messageSource.getMessage(anyString(), any(), any(Locale.class))).thenReturn("Success Message");
    }

    @Test
    void getAll_ShouldReturnList() throws Exception {
        when(roleCompetencyService.findAll()).thenReturn(List.of(mockRoleCompetency));
        mockMvc.perform(get("/api/role-competency"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].weightage").value(50));
    }

    @Test
    @WithMockUser(authorities = "CAN_MANAGE_ROLE")
    void getOverview_ShouldReturnMappedData() throws Exception {
        when(roleService.getAllByDeletedIsFalse()).thenReturn(List.of(mockRole));
        when(roleCompetencyService.findAll()).thenReturn(List.of(mockRoleCompetency));
        when(competencyCompTagService.findAll()).thenReturn(Collections.emptyList());

        mockMvc.perform(get("/api/role-competency/overview"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].roleName").value("Software Engineer"))
                .andExpect(jsonPath("$[0].totalWeightage").value(50));
    }

    @Test
    void getRoleDetails_ShouldReturnFullDetails() throws Exception {
        when(roleService.getAllById(10L)).thenReturn(mockRole);
        when(roleJobScopeService.findAllByRoleId(10L)).thenReturn(List.of(mockRoleJobScope));
        when(roleCompetencyService.findAllByRoleId(10L)).thenReturn(List.of(mockRoleCompetency));
        when(staffService.findAllByRoleId(10L)).thenReturn(List.of(mockStaff));

        // Mock Tag
        CompTagDto tag = new CompTagDto(); tag.setTag("Backend");
        CompetencyCompTagDto compTag = new CompetencyCompTagDto();
        compTag.setId(new CompetencyCompTagId(100L, 1L));
        compTag.setCompTag(tag);
        when(competencyCompTagService.findAllByCompetencyIdIn(anySet())).thenReturn(List.of(compTag));

        mockMvc.perform(get("/api/role-competency/role-details").param("roleId", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.roleName").value("Software Engineer"))
                .andExpect(jsonPath("$.jobScopes[0].jobScope").value("Coding"))
                .andExpect(jsonPath("$.totalWeightage").value(50))
                .andExpect(jsonPath("$.compTags['100'][0]").value("Backend"));
    }

    @Test
    void getRoleDetails_MissingId_ShouldFail() throws Exception {
        when(messageSource.getMessage(anyString(), any(), any(Locale.class))).thenReturn("Invalid ID");

        mockMvc.perform(get("/api/role-competency/role-details").param("roleId", ""))
                .andExpect(status().isBadRequest());
    }

    @Test
    void getRoleDetailsOverview_ShouldReturnList() throws Exception {
        when(roleService.getAllByDeletedIsFalse()).thenReturn(List.of(mockRole));
        when(roleJobScopeService.findAll()).thenReturn(List.of(mockRoleJobScope));
        when(roleCompetencyService.findAll()).thenReturn(List.of(mockRoleCompetency));
        when(staffService.findAllByIsDeletedIsFalse()).thenReturn(List.of(mockStaff));
        when(competencyCompTagService.findAll()).thenReturn(Collections.emptyList());

        mockMvc.perform(get("/api/role-competency/role-details-overview"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].roleName").value("Software Engineer"))
                .andExpect(jsonPath("$[0].staffs[0].name").value("John Doe"));
    }

    @Test
    void create_ShouldSyncState_AddUpdateRemove() throws Exception {
        CreateRoleCompetencyRequestDto req = new CreateRoleCompetencyRequestDto();
        req.setRoleId(10L);
        CompetencyAssignmentDto assign = new CompetencyAssignmentDto(101L, "Python", "Desc", false, 80);
        req.setCompetencyAssignment(List.of(assign));

        CompetencyDto newCompetency = new CompetencyDto();
        newCompetency.setId(101L);
        newCompetency.setName("Python");
        newCompetency.setDescription("Desc");
        newCompetency.setDeleted(false);

        when(roleService.getAllById(10L)).thenReturn(mockRole);
        when(roleCompetencyService.findAllByRoleId(10L)).thenReturn(List.of(mockRoleCompetency));
        when(competencyService.findAllByIsDeletedIsFalseAndIdIn(Set.of(101L)))
                .thenReturn(List.of(newCompetency));

        mockMvc.perform(post("/api/role-competency")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req))
                        .principal(authentication))
                .andExpect(status().isOk());

        verify(roleCompetencyService).deleteAllByIdIn(argThat(set ->
                set.stream().anyMatch(id -> id.getCompetencyId().equals(100L))));

        verify(roleCompetencyService).createAll(argThat(list ->
                list.get(0).getId().getCompetencyId().equals(101L)));
    }

    @Test
    void create_ShouldUpdateWeightage_WhenCompetencyExistsWithDifferentWeight() throws Exception {
        CreateRoleCompetencyRequestDto req = new CreateRoleCompetencyRequestDto();
        req.setRoleId(10L);
        // ID 100 already exists with weight 50, request changes it to 75
        CompetencyAssignmentDto updateAssign = new CompetencyAssignmentDto(100L, "Java", "Desc", false, 75);
        req.setCompetencyAssignment(List.of(updateAssign));

        when(roleService.getAllById(10L)).thenReturn(mockRole);
        when(roleCompetencyService.findAllByRoleId(10L)).thenReturn(List.of(mockRoleCompetency));

        mockMvc.perform(post("/api/role-competency")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req))
                        .principal(authentication))
                .andExpect(status().isOk());

        // Verify updateAll is called because weightage changed (50 -> 75)
        verify(roleCompetencyService).updateAll(anySet(), argThat(list ->
                list.get(0).getWeightage() == 75));
        verify(roleCompetencyService, never()).deleteAllByIdIn(any());
    }

    @Test
    void create_ShouldFail_WhenRequestIsInvalid() throws Exception {
        CreateRoleCompetencyRequestDto emptyReq = new CreateRoleCompetencyRequestDto();
        // Missing roleId and competency list

        mockMvc.perform(post("/api/role-competency")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(emptyReq))
                        .principal(authentication))
                .andExpect(status().isBadRequest());
    }

    @Test
    void export_ShouldReturnExcelFile() throws Exception {
        when(roleService.getAllByDeletedIsFalse()).thenReturn(List.of(mockRole));
        when(roleCompetencyService.findAll()).thenReturn(List.of(mockRoleCompetency));

        var result = mockMvc.perform(get("/api/role-competency/export"))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CONTENT_TYPE, "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .andReturn();

        byte[] content = result.getResponse().getContentAsByteArray();
        try (Workbook wb = new XSSFWorkbook(new ByteArrayInputStream(content))) {
            Sheet sheet = wb.getSheetAt(0);
            assertEquals("IT Department", sheet.getRow(1).getCell(0).getStringCellValue());
            assertEquals("Java Programming > 50", sheet.getRow(1).getCell(2).getStringCellValue());
        }
    }

    @Test
    void export_ShouldReturnInternalServerError_WhenServiceFails() throws Exception {
        // Force an exception by making a service throw a RuntimeException
        when(roleService.getAllByDeletedIsFalse()).thenThrow(new RuntimeException("DB Down"));

        mockMvc.perform(get("/api/role-competency/export")
                        .principal(authentication))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.message").exists());
    }

    @Test
    void import_ShouldProcessFile() throws Exception {
        byte[] excelContent = createMockImportExcel();
        MockMultipartFile file = new MockMultipartFile("file", "test.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", excelContent);

        when(orgChartService.findAllByIsDeletedIsFalse()).thenReturn(List.of(mockRole.getOrgChart()));
        when(roleService.getAllByDeletedIsFalse()).thenReturn(List.of(mockRole));
        when(competencyService.findAllByIsDeletedIsFalse()).thenReturn(List.of(mockCompetency));
        when(roleCompetencyService.findAll()).thenReturn(Collections.emptyList());

        mockMvc.perform(multipart("/api/role-competency/import")
                        .file(file)
                        .principal(authentication))
                .andExpect(status().isOk());

        verify(roleCompetencyService).updateAll(anySet(), anyList());
    }

    @Test
    void import_ShouldFail_WhenWeightageFormatIsInvalid() throws Exception {
        try (Workbook wb = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sheet = wb.createSheet();
            Row header = sheet.createRow(0);
            header.createCell(0).setCellValue("Department Name");
            header.createCell(1).setCellValue("Role Name");
            header.createCell(2).setCellValue("Competency Name > Weightage");

            Row data = sheet.createRow(1);
            data.createCell(0).setCellValue("IT Department");
            data.createCell(1).setCellValue("Software Engineer");
            data.createCell(2).setCellValue("Java Programming : 100"); // Invalid delimiter ':'

            wb.write(out);
            MockMultipartFile file = new MockMultipartFile("file", "test.xlsx",
                    "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", out.toByteArray());

            when(orgChartService.findAllByIsDeletedIsFalse()).thenReturn(List.of(mockRole.getOrgChart()));
            when(roleService.getAllByDeletedIsFalse()).thenReturn(List.of(mockRole));
            when(competencyService.findAllByIsDeletedIsFalse()).thenReturn(List.of(mockCompetency));

            mockMvc.perform(multipart("/api/role-competency/import").file(file).principal(authentication))
                    .andExpect(status().isBadRequest());
        }
    }

    @Test
    void import_ShouldFail_WhenWeightageIsOutOfRange() throws Exception {
        byte[] excelContent = createExcelWithData("IT Department", "Software Engineer", "Java Programming > 1000"); // Too high
        MockMultipartFile file = new MockMultipartFile("file", "test.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", excelContent);

        // Setup mocks same as import process
        when(orgChartService.findAllByIsDeletedIsFalse()).thenReturn(List.of(mockRole.getOrgChart()));
        when(roleService.getAllByDeletedIsFalse()).thenReturn(List.of(mockRole));
        when(competencyService.findAllByIsDeletedIsFalse()).thenReturn(List.of(mockCompetency));

        mockMvc.perform(multipart("/api/role-competency/import").file(file).principal(authentication))
                .andExpect(status().isBadRequest());
    }

    @Test
    void import_ShouldFail_WhenRoleMismatchWithDepartment() throws Exception {
        // IT Department exists, and "HR Manager" role exists,
        // but "HR Manager" is NOT in "IT Department"
        RoleDto hrRole = new RoleDto();
        hrRole.setId(20L);
        hrRole.setName("HR Manager");
        OrgChartDto hrOrg = new OrgChartDto();
        hrOrg.setId(2L);
        hrOrg.setName("HR Dept");
        hrRole.setOrgChart(hrOrg);

        byte[] excelContent = createExcelWithData("IT Department", "HR Manager", "Java > 50");
        MockMultipartFile file = new MockMultipartFile("file", "test.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", excelContent);

        when(orgChartService.findAllByIsDeletedIsFalse()).thenReturn(List.of(mockRole.getOrgChart())); // Only IT Dept
        when(roleService.getAllByDeletedIsFalse()).thenReturn(List.of(mockRole, hrRole));

        mockMvc.perform(multipart("/api/role-competency/import").file(file).principal(authentication))
                .andExpect(status().isBadRequest());
    }

    @Test
    void import_ShouldFail_WhenWeightageIsNotNumeric() throws Exception {
        // "ABC" is not a valid numeric weightage
        byte[] excelContent = createExcelWithData("IT Department", "Software Engineer", "Java > ABC");
        MockMultipartFile file = new MockMultipartFile("file", "test.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", excelContent);

        // Mocks for lookup success
        when(orgChartService.findAllByIsDeletedIsFalse()).thenReturn(List.of(mockRole.getOrgChart()));
        when(roleService.getAllByDeletedIsFalse()).thenReturn(List.of(mockRole));
        when(competencyService.findAllByIsDeletedIsFalse()).thenReturn(List.of(mockCompetency));

        mockMvc.perform(multipart("/api/role-competency/import").file(file).principal(authentication))
                .andExpect(status().isBadRequest());
    }

    @Test
    void import_ShouldFail_WhenCompetencyNameNotFound() throws Exception {
        byte[] excelContent = createExcelWithData("IT Department", "Software Engineer", "Ghost Competency > 50");
        MockMultipartFile file = new MockMultipartFile("file", "test.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", excelContent);

        when(orgChartService.findAllByIsDeletedIsFalse()).thenReturn(List.of(mockRole.getOrgChart()));
        when(roleService.getAllByDeletedIsFalse()).thenReturn(List.of(mockRole));
        when(competencyService.findAllByIsDeletedIsFalse()).thenReturn(Collections.emptyList()); // Competency missing

        mockMvc.perform(multipart("/api/role-competency/import").file(file).principal(authentication))
                .andExpect(status().isBadRequest());
    }

    @Test
    void import_ShouldDeleteOmittedCompetencies() throws Exception {
        // Excel only has Java
        byte[] excelContent = createExcelWithData("IT Department", "Software Engineer", "Java Programming > 50");
        MockMultipartFile file = new MockMultipartFile("file", "test.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", excelContent);

        // DB currently has Java (ID 100) AND Python (ID 102)
        RoleCompetencyDto pythonAssign = new RoleCompetencyDto();
        pythonAssign.setId(new RoleCompetencyId(10L, 102L));
        pythonAssign.setWeightage(30);

        when(orgChartService.findAllByIsDeletedIsFalse()).thenReturn(List.of(mockRole.getOrgChart()));
        when(roleService.getAllByDeletedIsFalse()).thenReturn(List.of(mockRole));
        when(competencyService.findAllByIsDeletedIsFalse()).thenReturn(List.of(mockCompetency));
        when(roleCompetencyService.findAll()).thenReturn(List.of(mockRoleCompetency, pythonAssign));

        mockMvc.perform(multipart("/api/role-competency/import").file(file).principal(authentication))
                .andExpect(status().isOk());

        // Verify Python (ID 102) is deleted because it was missing from Excel
        verify(roleCompetencyService).deleteAllByIdIn(argThat(set ->
                set.stream().anyMatch(id -> id.getCompetencyId().equals(102L))));
    }

    @Test
    void import_ShouldNotUpdate_WhenWeightageIsIdentical() throws Exception {
        // Excel has Java > 50
        byte[] excelContent = createExcelWithData("IT Department", "Software Engineer", "Java Programming > 50");
        MockMultipartFile file = new MockMultipartFile("file", "test.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", excelContent);

        // DB also has Java > 50
        when(orgChartService.findAllByIsDeletedIsFalse()).thenReturn(List.of(mockRole.getOrgChart()));
        when(roleService.getAllByDeletedIsFalse()).thenReturn(List.of(mockRole));
        when(competencyService.findAllByIsDeletedIsFalse()).thenReturn(List.of(mockCompetency));
        when(roleCompetencyService.findAll()).thenReturn(List.of(mockRoleCompetency));

        mockMvc.perform(multipart("/api/role-competency/import").file(file).principal(authentication))
                .andExpect(status().isOk());

        // Verify updateAll was NEVER called because weightage 50 == 50
        verify(roleCompetencyService, never()).updateAll(anySet(), anyList());
    }


    @Test
    void deleteByRole_ShouldCallService() throws Exception {
        mockMvc.perform(delete("/api/role-competency/delete").param("roleId", "10"))
                .andExpect(status().isOk());
        verify(roleCompetencyService).deleteAllByRoleId(10L);
    }

    @Test
    void bulkDelete_ShouldFail_WhenListIsEmpty() throws Exception {
        mockMvc.perform(delete("/api/role-competency/bulk-delete")
                        .param("roleIds", "") // Empty list
                        .principal(authentication))
                .andExpect(status().isBadRequest());
    }

    // Helper for quick excel generation
    private byte[] createExcelWithData(String dept, String role, String comp) throws IOException {
        try (Workbook wb = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sheet = wb.createSheet();
            Row header = sheet.createRow(0);
            header.createCell(0).setCellValue("Department Name");
            header.createCell(1).setCellValue("Role Name");
            header.createCell(2).setCellValue("Competency Name > Weightage");

            Row data = sheet.createRow(1);
            data.createCell(0).setCellValue(dept);
            data.createCell(1).setCellValue(role);
            data.createCell(2).setCellValue(comp);
            wb.write(out);
            return out.toByteArray();
        }
    }

    private byte[] createMockImportExcel() throws IOException {
        try (Workbook wb = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sheet = wb.createSheet();
            Row header = sheet.createRow(0);
            header.createCell(0).setCellValue("Department Name");
            header.createCell(1).setCellValue("Role Name");
            header.createCell(2).setCellValue("Competency Name > Weightage");

            Row data = sheet.createRow(1);
            data.createCell(0).setCellValue("IT Department");
            data.createCell(1).setCellValue("Software Engineer");
            data.createCell(2).setCellValue("Java Programming > 75");

            wb.write(out);
            return out.toByteArray();
        }
    }
}