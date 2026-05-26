package com.tbm.careerpathlearning.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tbm.careerpathlearning.dto.*;
import com.tbm.careerpathlearning.enums.AuthorityName;
import com.tbm.careerpathlearning.model.RoleJobScopeId;
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
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(controllers = RoleController.class)
@AutoConfigureMockMvc(addFilters = false)
class RoleControllerTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private RoleService roleService;
    @MockitoBean
    private RoleJobScopeService roleJobScopeService;
    @MockitoBean
    private JobScopeService jobScopeService;
    @MockitoBean
    private OrgChartService orgChartService;
    @MockitoBean
    private AuthorityService authorityService;
    @MockitoBean
    private RoleAuthorityService roleAuthorityService;
    @MockitoBean
    private RoleCompetencyService roleCompetencyService;
    @MockitoBean
    private ValidationService validationService;
    @MockitoBean
    private MessageSource messageSource;
    @MockitoBean
    private TokenService tokenService;

    private Authentication authentication;
    private UUID userUUID;
    private RoleDto mockRole;
    private OrgChartDto mockOrg;

    @BeforeEach
    void setUp() {
        userUUID = UUID.randomUUID();
        authentication = mock(Authentication.class);
        when(authentication.getName()).thenReturn(userUUID.toString());
        when(authentication.getPrincipal()).thenReturn(userUUID.toString());

        mockOrg = new OrgChartDto();
        mockOrg.setId(100L);
        mockOrg.setName("IT Dept");

        mockRole = new RoleDto();
        mockRole.setId(1L);
        mockRole.setName("Dev");
        mockRole.setOrgChart(mockOrg);
        mockRole.setVisible(true);

        lenient().when(messageSource.getMessage(any(), any(), any(Locale.class))).thenReturn("Mock Message");
    }

    // --- Get All Roles ---
    @Test
    void getAllRoles_ShouldReturnList() throws Exception {
        when(roleService.getAllByDeletedIsFalse()).thenReturn(List.of(mockRole));

        mockMvc.perform(get("/api/role").principal(authentication))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.size()").value(1))
                .andExpect(jsonPath("$[0].name").value("Dev"));
    }

    @Test
    void getAllRoleJobScopeMap_ShouldReturnGroupedMap() throws Exception {
        // Data: Role 1 has Scope A & Scope B
        RoleJobScopeDto rjs1 = new RoleJobScopeDto();
        rjs1.setId(new RoleJobScopeId(1L, 10L));
        JobScopeDto js1 = new JobScopeDto();
        js1.setJobScope("Scope A");
        rjs1.setJobScope(js1);

        RoleJobScopeDto rjs2 = new RoleJobScopeDto();
        rjs2.setId(new RoleJobScopeId(1L, 20L));
        JobScopeDto js2 = new JobScopeDto();
        js2.setJobScope("Scope B");
        rjs2.setJobScope(js2);

        when(roleJobScopeService.findAll()).thenReturn(List.of(rjs1, rjs2));

        mockMvc.perform(get("/api/role/jobScope-map").principal(authentication))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].roleId").value(1))
                .andExpect(jsonPath("$[0].assignedJobScopes.length()").value(2));
    }

    // --- Get Overview ---
    @Test
    void getRoleOverview_ShouldReturnDetails() throws Exception {
        RoleJobScopeDto rjs = new RoleJobScopeDto();
        rjs.setRole(mockRole);
        JobScopeDto js = new JobScopeDto();
        js.setJobScope("Coding");
        rjs.setJobScope(js);

        when(roleService.getAllByDeletedIsFalse()).thenReturn(List.of(mockRole));
        when(roleJobScopeService.findAll()).thenReturn(List.of(rjs));

        mockMvc.perform(get("/api/role/overview").principal(authentication))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].roleName").value("Dev"))
                .andExpect(jsonPath("$[0].assignedJobScopes[0].jobScope").value("Coding"));
    }

    // --- Toggle Visibility ---
    @Test
    void toggleVisibility_ShouldUpdateRole() throws Exception {
        ToggleRoleVisibilityRequest req = new ToggleRoleVisibilityRequest();
        req.setRoleId(1L);
        req.setVisibility(false);

        when(roleService.getAllById(1L)).thenReturn(mockRole);

        mockMvc.perform(put("/api/role/toggle-visibility")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req))
                        .principal(authentication))
                .andExpect(status().isOk());

        verify(roleService).update(eq(1L), any(RoleDto.class));
    }

    @Test
    void toggleVisibility_ShouldFail_WhenRoleNotFound() throws Exception {
        ToggleRoleVisibilityRequest req = new ToggleRoleVisibilityRequest();
        req.setRoleId(999L); // Non-existent ID
        req.setVisibility(true);

        when(roleService.getAllById(999L)).thenReturn(null);

        mockMvc.perform(put("/api/role/toggle-visibility")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req))
                        .principal(authentication))
                .andExpect(status().isInternalServerError()); // DataAccessException maps to 500 usually, or 404 depending on config
    }

    // --- Create Role ---
    @Test
    void createRole_ShouldCreateRoleAndJobScopes() throws Exception {
        CreateRoleRequestDto req = new CreateRoleRequestDto();
        req.setRoleName("New Role");
        req.setOrgChartId(100L);
        req.setDescription("Desc");
        req.setJobScopeList(List.of("Scope 1"));

        when(orgChartService.getByById(100L)).thenReturn(mockOrg);
        when(roleService.create(any(RoleDto.class))).thenReturn(mockRole);

        AuthorityDto auth = new AuthorityDto();
        auth.setId(1L);
        when(authorityService.findByName(AuthorityName.ROLE_USER)).thenReturn(auth);

        JobScopeDto createdJs = new JobScopeDto();
        createdJs.setId(50L);
        when(jobScopeService.createAll(anyList())).thenReturn(List.of(createdJs));

        mockMvc.perform(post("/api/role")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req))
                        .principal(authentication))
                .andExpect(status().isOk());

        verify(roleService).create(any(RoleDto.class));
        verify(roleAuthorityService).create(any(RoleAuthorityDto.class));
        verify(roleJobScopeService).createAll(anyList());
    }

    @Test
    void createRole_ShouldFail_WhenBodyInvalid() throws Exception {
        CreateRoleRequestDto req = new CreateRoleRequestDto(); // Missing fields

        mockMvc.perform(post("/api/role")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req))
                        .principal(authentication))
                .andExpect(status().isBadRequest());
    }

    @Test
    void createRole_ShouldSucceed_WhenJobScopeListIsNull() throws Exception {
        CreateRoleRequestDto req = new CreateRoleRequestDto();
        req.setRoleName("New Role");
        req.setOrgChartId(100L);
        req.setJobScopeList(null); // NULL List

        when(orgChartService.getByById(100L)).thenReturn(mockOrg);
        when(roleService.create(any(RoleDto.class))).thenReturn(mockRole);
        when(authorityService.findByName(any())).thenReturn(new AuthorityDto());

        mockMvc.perform(post("/api/role")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req))
                        .principal(authentication))
                .andExpect(status().isOk());

        verify(roleJobScopeService, never()).createAll(anyList());
    }

    // --- Edit Role ---
    @Test
    void updateRole_ShouldUpdateDetailsAndJobScopes() throws Exception {
        EditRoleRequestDto req = new EditRoleRequestDto();
        req.setRoleId(1L);
        req.setRoleName("Updated Role");
        req.setOrgChartId(100L);
        req.setJobScopeList(List.of("New Scope")); // Adding new scope

        when(orgChartService.getByById(100L)).thenReturn(mockOrg);
        when(roleService.update(eq(1L), any(RoleDto.class))).thenReturn(mockRole);

        // Mock existing scopes (Empty, so "New Scope" is added)
        when(roleJobScopeService.findAllByRoleId(1L)).thenReturn(Collections.emptyList());

        JobScopeDto jsDto = new JobScopeDto();
        jsDto.setId(55L);
        when(jobScopeService.createAll(anyList())).thenReturn(List.of(jsDto));

        mockMvc.perform(put("/api/role/edit-role")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req))
                        .principal(authentication))
                .andExpect(status().isOk());

        verify(roleJobScopeService).createAll(anyList()); // Verify addition
    }

    @Test
    void updateRole_ShouldHandleScopeRemovalAndCleanup() throws Exception {
        // Scenario: Role currently has "Scope A" & "Scope B".
        // Update request only keeps "Scope A".
        // "Scope B" should be removed. If "Scope B" is not used by others, it should be deleted.

        EditRoleRequestDto req = new EditRoleRequestDto();
        req.setRoleId(1L);
        req.setRoleName("Dev");
        req.setOrgChartId(100L);
        req.setJobScopeList(List.of("Scope A")); // Scope B is missing (Removed)

        // Mock existing Role
        when(orgChartService.getByById(100L)).thenReturn(mockOrg);
        when(roleService.update(eq(1L), any(RoleDto.class))).thenReturn(mockRole);

        // Mock Existing Scopes in DB (A & B)
        JobScopeDto jsA = new JobScopeDto();
        jsA.setId(10L);
        jsA.setJobScope("Scope A");
        JobScopeDto jsB = new JobScopeDto();
        jsB.setId(20L);
        jsB.setJobScope("Scope B");

        RoleJobScopeDto rjsA = new RoleJobScopeDto();
        rjsA.setId(new RoleJobScopeId(1L, 10L));
        rjsA.setJobScope(jsA);
        RoleJobScopeDto rjsB = new RoleJobScopeDto();
        rjsB.setId(new RoleJobScopeId(1L, 20L));
        rjsB.setJobScope(jsB);

        when(roleJobScopeService.findAllByRoleId(1L)).thenReturn(List.of(rjsA, rjsB));

        // Mock creation of "Scope A" (returns existing ID)
        when(jobScopeService.createAll(anyList())).thenReturn(List.of(jsA));

        // Mock Check for "Scope B" usage (Not used by others)
        when(roleJobScopeService.findJobScopesUsedByOtherRoles(Set.of(20L), 1L)).thenReturn(Collections.emptyList());

        mockMvc.perform(put("/api/role/edit-role")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req))
                        .principal(authentication))
                .andExpect(status().isOk());

        // Verify Removal
        verify(roleJobScopeService).deleteByRoleIdAndJobScopeIdIn(eq(1L), argThat(ids -> ids.contains(20L)));
        // Verify Hard Delete of Scope B (since it's unused)
        verify(jobScopeService).deleteAllByIdIn(argThat(ids -> ids.contains(20L)), any(UUID.class));
    }

    @Test
    void updateRole_ShouldFail_WhenBodyInvalid() throws Exception {
        EditRoleRequestDto req = new EditRoleRequestDto();
        // Missing RoleID and Name

        mockMvc.perform(put("/api/role/edit-role")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req))
                        .principal(authentication))
                .andExpect(status().isBadRequest());
    }

    @Test
    void updateRole_ShouldSucceed_WhenJobScopeListIsNull() throws Exception {
        EditRoleRequestDto req = new EditRoleRequestDto();
        req.setRoleId(1L);
        req.setRoleName("Dev");
        req.setOrgChartId(100L);
        req.setJobScopeList(null); // NULL

        when(orgChartService.getByById(100L)).thenReturn(mockOrg);
        when(roleService.update(anyLong(), any())).thenReturn(mockRole);

        // Mock existing scopes to ensure no crash
        when(roleJobScopeService.findAllByRoleId(1L)).thenReturn(Collections.emptyList());

        mockMvc.perform(put("/api/role/edit-role")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req))
                        .principal(authentication))
                .andExpect(status().isOk());
    }

    // --- Delete Role ---
    @Test
    void deleteRole_ShouldDeleteDependenciesAndRole() throws Exception {
        when(roleJobScopeService.findAllByRoleId(1L)).thenReturn(Collections.emptyList());

        mockMvc.perform(delete("/api/role/delete")
                        .param("roleId", "1")
                        .principal(authentication))
                .andExpect(status().isOk());

        verify(roleAuthorityService).deleteAllByRoleId(1L);
        verify(roleService).delete(eq(1L), any(UUID.class));
    }

    @Test
    void deleteRole_ShouldFail_WhenIdMissing() throws Exception {
        mockMvc.perform(delete("/api/role/delete")
                        // No param provided
                        .principal(authentication))
                .andExpect(status().isBadRequest());
    }

    @Test
    void deleteRole_ShouldDeleteDependencies_WhenLinksExist() throws Exception {
        // Mock: Role has assigned JobScopes
        RoleJobScopeDto rjs = new RoleJobScopeDto();
        rjs.setId(new RoleJobScopeId(1L, 100L));
        when(roleJobScopeService.findAllByRoleId(1L)).thenReturn(List.of(rjs));

        // Mock: Scope 100 is NOT used by anyone else
        when(roleJobScopeService.findJobScopesUsedByOtherRoles(anySet(), eq(1L)))
                .thenReturn(Collections.emptyList());

        mockMvc.perform(delete("/api/role/delete")
                        .param("roleId", "1")
                        .principal(authentication))
                .andExpect(status().isOk());

        // Verify: Scope 100 is deleted because it became an orphan
        verify(jobScopeService).deleteAllByIdIn(argThat(ids -> ids.contains(100L)), any(UUID.class));
        verify(roleService).delete(eq(1L), any(UUID.class));
    }

    // --- Bulk Delete ---
    @Test
    void bulkDeleteRole_ShouldDeleteMultiple() throws Exception {
        mockMvc.perform(delete("/api/role/bulk-delete")
                        .param("roleIds", "1,2")
                        .principal(authentication))
                .andExpect(status().isOk());

        verify(roleService).deleteAllByRoleIdIn(anySet(), any(UUID.class));
    }

    @Test
    void bulkDeleteRole_ShouldFail_WhenListEmpty() throws Exception {
        mockMvc.perform(delete("/api/role/bulk-delete")
                        // No param provided implies null/empty list
                        .principal(authentication))
                .andExpect(status().isBadRequest());
    }

    @Test
    void bulkDeleteRole_ShouldDeleteDependencies_WhenLinksExist() throws Exception {
        // Mock: Roles have assigned JobScopes
        RoleJobScopeDto rjs1 = new RoleJobScopeDto();
        rjs1.setId(new RoleJobScopeId(1L, 100L));
        RoleJobScopeDto rjs2 = new RoleJobScopeDto();
        rjs2.setId(new RoleJobScopeId(2L, 101L));

        when(roleJobScopeService.findAllByRoleIdIn(anySet())).thenReturn(List.of(rjs1, rjs2));

        // Mock: Some scopes are still used by other roles, some are not
        // Scope 100 is still used, Scope 101 is NOT used
        RoleJobScopeDto stillUsedRjs = new RoleJobScopeDto();
        stillUsedRjs.setId(new RoleJobScopeId(3L, 100L));
        when(roleJobScopeService.findJobScopesUsedByRoleIdNotIn(anySet(), anySet()))
                .thenReturn(List.of(stillUsedRjs));

        mockMvc.perform(delete("/api/role/bulk-delete")
                        .param("roleIds", "1,2")
                        .principal(authentication))
                .andExpect(status().isOk());

        // Verify: RoleJobScopes deleted
        verify(roleJobScopeService).deleteAllByIdIn(anySet());

        // Verify: Only unused JobScope (101L) is hard deleted. 100L should NOT be deleted.
        verify(jobScopeService).deleteAllByIdIn(argThat(ids ->
                ids.contains(101L) && !ids.contains(100L)
        ), any(UUID.class));

        // Verify: Authorities and Competencies cleaned up
        verify(roleAuthorityService).deleteAllByRoleIdIn(anySet());
        verify(roleCompetencyService).deleteAllByRoleIdIn(anySet());
        verify(roleService).deleteAllByRoleIdIn(anySet(), any(UUID.class));
    }

    // --- Export ---
    @Test
    void export_ShouldReturnExcel() throws Exception {
        when(roleService.getAllByDeletedIsFalse()).thenReturn(List.of(mockRole));
        when(roleJobScopeService.findAll()).thenReturn(Collections.emptyList());

        mockMvc.perform(get("/api/role/export").principal(authentication))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=Exported_Role_Overview_Data.xlsx"));
    }

    @Test
    void export_ShouldReturn500_WhenServiceFails() throws Exception {
        when(roleService.getAllByDeletedIsFalse()).thenThrow(new RuntimeException("DB Error"));

        mockMvc.perform(get("/api/role/export").principal(authentication))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.message").value("Mock Message"));
    }

    // --- Import (Happy Path) ---
    @Test
    void import_ShouldProcessValidExcel() throws Exception {
        // 1. Excel Input Data
        List<String[]> data = new ArrayList<>(Collections.singleton(new String[]{"IT Dept", "New Role", "Desc", "Scope 1", "Yes", "", "", ""}));
        byte[] excelBytes = createExcelFromData(data);
        MockMultipartFile file = new MockMultipartFile("file", "import.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", excelBytes);

        // 2. Mocks
        when(orgChartService.findAllByIsDeletedIsFalse()).thenReturn(List.of(mockOrg)); // mockOrg is "IT Dept"
        when(roleService.getAllByDeletedIsFalse()).thenReturn(Collections.emptyList());

        when(validationService.isNullOrBlank(any())).thenAnswer(inv -> {
            String s = inv.getArgument(0);
            return s == null || s.trim().isEmpty();
        });

        RoleDto matchingRole = new RoleDto();
        matchingRole.setId(1L);
        matchingRole.setName("New Role");
        matchingRole.setOrgChart(mockOrg);

        when(roleService.createAndUpdateAll(anyList())).thenReturn(List.of(matchingRole));

        AuthorityDto auth = new AuthorityDto();
        auth.setId(1L);
        when(authorityService.findByName(AuthorityName.ROLE_USER)).thenReturn(auth);

        JobScopeDto js = new JobScopeDto();
        js.setId(10L);
        js.setJobScope("Scope 1");
        when(jobScopeService.createAll(anyList())).thenReturn(List.of(js));

        mockMvc.perform(multipart("/api/role/import").file(file).principal(authentication))
                .andExpect(status().isOk());

        verify(roleService).createAndUpdateAll(anyList());
        verify(roleAuthorityService).createAll(anyList());
        verify(roleJobScopeService).createAll(anyList());
    }

    @Test
    void import_ShouldHandleRenameAndReassignDepartment() throws Exception {
        // Row: Dept="IT", Role="Dev", NewDept="HR", NewRole="HR Lead"
        List<String[]> data = new ArrayList<>(Collections.singleton(new String[]{"IT Dept", "Dev", "", "", "", "HR Dept", "HR Lead", ""}));
        byte[] excelBytes = createExcelFromData(data);
        MockMultipartFile file = new MockMultipartFile("file", "import.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", excelBytes);

        // Mock Departments (IT and HR exist)
        OrgChartDto itDept = new OrgChartDto();
        itDept.setId(100L);
        itDept.setName("IT Dept");
        OrgChartDto hrDept = new OrgChartDto();
        hrDept.setId(200L);
        hrDept.setName("HR Dept");
        when(orgChartService.findAllByIsDeletedIsFalse()).thenReturn(List.of(itDept, hrDept));

        // Mock Existing Role "Dev" in "IT Dept"
        RoleDto existingRole = new RoleDto();
        existingRole.setId(1L);
        existingRole.setName("Dev");
        existingRole.setOrgChart(itDept);
        when(roleService.getAllByDeletedIsFalse()).thenReturn(List.of(existingRole));

        // Mock Validation
        when(validationService.isNullOrBlank(any())).thenAnswer(inv -> {
            String s = inv.getArgument(0);
            return s == null || s.trim().isEmpty();
        });

        // Mock Update Return (Must match expected map key in controller)
        RoleDto updatedRole = new RoleDto();
        updatedRole.setId(1L);
        updatedRole.setName("HR Lead");
        updatedRole.setOrgChart(hrDept);
        when(roleService.createAndUpdateAll(anyList())).thenReturn(List.of(updatedRole));

        mockMvc.perform(multipart("/api/role/import").file(file).principal(authentication))
                .andExpect(status().isOk());

        // Verify Update called with new Name and new OrgChart
        verify(roleService).createAndUpdateAll(argThat(list -> {
            RoleDto dto = (RoleDto) list.get(0);
            return dto.getName().equals("HR Lead") && dto.getOrgChart().getId().equals(200L);
        }));
    }

    @Test
    void import_ShouldHandle_NumericAndBooleanCells() throws Exception {
        // We need to manually construct a workbook to inject Numeric/Boolean types
        byte[] excelBytes;
        try (Workbook workbook = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sheet = workbook.createSheet("Sheet 1");
            Row header = sheet.createRow(0);
            // ... (fill headers as usual) ...
            String[] headers = {"Department Name", "Role Name", "Role Description", "Job Scopes",
                    "Visibility", "New Department Name", "New Role Name", "To Be Deleted"};
            for (int i = 0; i < headers.length; i++) header.createCell(i).setCellValue(headers[i]);

            Row row = sheet.createRow(1);
            // Col 0: String "IT Dept"
            row.createCell(0).setCellValue("IT Dept");
            // Col 1: Numeric Role Name (e.g. 123) -> Should trigger NUMERIC branch
            row.createCell(1).setCellValue(123);
            // Col 2: String Desc
            row.createCell(2).setCellValue("Desc");
            // Col 3: Empty Scopes
            row.createCell(3).setCellValue("");
            // Col 4: Boolean Visibility (TRUE) -> Should trigger BOOLEAN branch (imports as "true")
            row.createCell(4).setCellValue(true);
            // Others empty
            for (int i = 5; i <= 7; i++) row.createCell(i).setCellValue("");

            workbook.write(out);
            excelBytes = out.toByteArray();
        }

        MockMultipartFile file = new MockMultipartFile("file", "types.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", excelBytes);

        // Mock dependencies to allow the import to proceed far enough to read cells
        OrgChartDto org = new OrgChartDto();
        org.setName("IT Dept");
        when(orgChartService.findAllByIsDeletedIsFalse()).thenReturn(List.of(org));
        when(roleService.getAllByDeletedIsFalse()).thenReturn(Collections.emptyList());
        when(validationService.isNullOrBlank(any())).thenAnswer(inv -> {
            String s = inv.getArgument(0);
            return s == null || s.trim().isEmpty();
        });

        RoleDto numericRole = new RoleDto();
        numericRole.setId(1L);
        numericRole.setName("123");
        numericRole.setOrgChart(org);

        // We expect it to try and create a role named "123"
        when(roleService.createAndUpdateAll(anyList())).thenReturn(List.of(numericRole));

        AuthorityDto auth = new AuthorityDto(); auth.setId(1L);
        when(authorityService.findByName(any())).thenReturn(auth);
        when(jobScopeService.createAll(anyList())).thenReturn(Collections.emptyList());

        mockMvc.perform(multipart("/api/role/import").file(file).principal(authentication))
                .andExpect(status().isOk());

        // No Exception means it successfully read "123" and "true"
    }

    // --- Import (Sad Path) ---
    @Test
    void import_ShouldFail_WhenDeptNotFound() throws Exception {
        List<String[]> data = new ArrayList<>(Collections.singleton(new String[]{"Missing Dept", "Role", "Desc", "Scope", "Yes", "", "", ""}));
        byte[] excelBytes = createExcelFromData(data);
        MockMultipartFile file = new MockMultipartFile("file", "import.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", excelBytes);

        when(orgChartService.findAllByIsDeletedIsFalse()).thenReturn(Collections.emptyList()); // Empty DB
        when(validationService.isNullOrBlank(any())).thenReturn(false);

        mockMvc.perform(multipart("/api/role/import").file(file).principal(authentication))
                .andExpect(status().isBadRequest());
    }

    @Test
    void import_ShouldFail_WhenActionConflict_DeleteAndRename() throws Exception {
        // Conflict: "To Be Deleted" = Yes AND "New Role Name" is provided
        List<String[]> data = new ArrayList<>(Collections.singleton(new String[]{"IT Dept", "Dev", "", "", "", "", "New Name", "Yes"}));
        byte[] excelBytes = createExcelFromData(data);
        MockMultipartFile file = new MockMultipartFile("file", "import.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", excelBytes);

        // Basic Setup to get past initial checks
        when(orgChartService.findAllByIsDeletedIsFalse()).thenReturn(List.of(mockOrg));
        when(roleService.getAllByDeletedIsFalse()).thenReturn(List.of(mockRole));
        when(validationService.isNullOrBlank(any())).thenAnswer(inv -> {
            String s = inv.getArgument(0);
            return s == null || s.trim().isEmpty();
        });

        mockMvc.perform(multipart("/api/role/import").file(file).principal(authentication))
                .andExpect(status().isBadRequest()); // IMPORT_ACTION_CONFLICT_ERR_MSG_CODE
    }

    @Test
    void import_ShouldFail_WhenInvalidFileType() throws Exception {
        MockMultipartFile txtFile = new MockMultipartFile("file", "test.txt", "text/plain", "content".getBytes());
        mockMvc.perform(multipart("/api/role/import").file(txtFile).principal(authentication))
                .andExpect(status().isBadRequest());
    }

    @Test
    void import_ShouldFail_WhenDuplicateRowInFile() throws Exception {
        // Two rows defining "Dev" role in "IT Dept"
        List<String[]> data = List.of(
                new String[]{"IT Dept", "Dev", "", "", "", "", "", ""},
                new String[]{"IT Dept", "Dev", "", "", "", "", "", ""}
        );
        byte[] excelBytes = createExcelFromData(data);
        MockMultipartFile file = new MockMultipartFile("file", "import.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", excelBytes);

        when(orgChartService.findAllByIsDeletedIsFalse()).thenReturn(List.of(mockOrg));
        when(validationService.isNullOrBlank(any())).thenAnswer(inv -> {
            String s = inv.getArgument(0);
            return s == null || s.trim().isEmpty();
        });

        mockMvc.perform(multipart("/api/role/import").file(file).principal(authentication))
                .andExpect(status().isBadRequest()); // IMPORT_DUPLICATE_ENTRY_ERR_MSG_CODE
    }

    @Test
    void import_ShouldFail_WhenFileTooLarge() throws Exception {
        // Create a large byte array > 5MB
        byte[] largeContent = new byte[(5 * 1024 * 1024) + 10];
        MockMultipartFile largeFile = new MockMultipartFile("file", "large.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", largeContent);

        mockMvc.perform(multipart("/api/role/import").file(largeFile).principal(authentication))
                .andExpect(status().isBadRequest()); // IMPORT_FILE_TOO_LARGE_ERR_MSG_CODE
    }

    @Test
    void import_ShouldFail_WhenNewDepartmentNotFound() throws Exception {
        // Row: Dept="IT", NewDept="Missing Dept"
        List<String[]> data = new ArrayList<>(Collections.singleton(new String[]{"IT Dept", "Dev", "", "", "", "Missing Dept", "", ""}));
        byte[] excelBytes = createExcelFromData(data);
        MockMultipartFile file = new MockMultipartFile("file", "import.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", excelBytes);

        // IT exists, Missing Dept does not
        OrgChartDto itDept = new OrgChartDto();
        itDept.setName("IT Dept");
        when(orgChartService.findAllByIsDeletedIsFalse()).thenReturn(List.of(itDept));
        when(validationService.isNullOrBlank(any())).thenAnswer(inv -> {
            String s = inv.getArgument(0);
            return s == null || s.trim().isEmpty();
        });

        mockMvc.perform(multipart("/api/role/import").file(file).principal(authentication))
                .andExpect(status().isBadRequest()); // IMPORT_DATA_NOT_FOUND_ERR_MSG_CODE
    }

    @Test
    void import_ShouldFail_WhenRenameConflictsWithExistingRole() throws Exception {
        // Scenario: Rename "Dev" -> "QA", but "QA" already exists in "IT Dept"
        List<String[]> data = new ArrayList<>(Collections.singleton(new String[]{"IT Dept", "Dev", "", "", "", "", "QA", ""}));
        byte[] excelBytes = createExcelFromData(data);
        MockMultipartFile file = new MockMultipartFile("file", "import.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", excelBytes);

        OrgChartDto itDept = new OrgChartDto();
        itDept.setId(100L);
        itDept.setName("IT Dept");
        when(orgChartService.findAllByIsDeletedIsFalse()).thenReturn(List.of(itDept));

        // Mock Roles: Dev exists, QA exists
        RoleDto devRole = new RoleDto();
        devRole.setName("Dev");
        devRole.setOrgChart(itDept);
        RoleDto qaRole = new RoleDto();
        qaRole.setName("QA");
        qaRole.setOrgChart(itDept);

        when(roleService.getAllByDeletedIsFalse()).thenReturn(List.of(devRole, qaRole));
        when(validationService.isNullOrBlank(any())).thenAnswer(inv -> {
            String s = inv.getArgument(0);
            return s == null || s.trim().isEmpty();
        });

        mockMvc.perform(multipart("/api/role/import").file(file).principal(authentication))
                .andExpect(status().isBadRequest()); // IMPORT_UNIQUE_NAME_ERR_MSG_CODE
    }

    @Test
    void import_ShouldFail_WhenJobScopeTooLong() throws Exception {
        // Construct Excel with a very long job scope
        String longScope = "A".repeat(1001);
        List<String[]> data = new ArrayList<>(Collections.singleton(new String[]{"IT Dept", "Dev", "Desc", longScope, "Yes", "", "", ""}));
        byte[] excelBytes = createExcelFromData(data);
        MockMultipartFile file = new MockMultipartFile("file", "import.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", excelBytes);

        // Basic Setup
        when(orgChartService.findAllByIsDeletedIsFalse()).thenReturn(List.of(mockOrg));
        when(roleService.getAllByDeletedIsFalse()).thenReturn(List.of(mockRole));
        when(validationService.isNullOrBlank(any())).thenReturn(false);

        mockMvc.perform(multipart("/api/role/import").file(file).principal(authentication))
                .andExpect(status().isBadRequest());
    }

    // --- Helper for Excel ---
    private byte[] createExcelFromData(List<String[]> rowsData) throws IOException {
        try (Workbook workbook = new XSSFWorkbook();
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sheet = workbook.createSheet("Sheet 1");

            Row header = sheet.createRow(0);
            String[] headers = {"Department Name", "Role Name", "Role Description", "Job Scopes",
                    "Visibility", "New Department Name", "New Role Name", "To Be Deleted"};
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