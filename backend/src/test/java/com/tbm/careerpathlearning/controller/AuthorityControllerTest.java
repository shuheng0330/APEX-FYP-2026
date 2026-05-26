package com.tbm.careerpathlearning.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tbm.careerpathlearning.dto.*;
import com.tbm.careerpathlearning.enums.AuthorityName;
import com.tbm.careerpathlearning.exception.DataAccessException;
import com.tbm.careerpathlearning.model.RoleAuthorityId;
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
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(controllers = AuthorityController.class)
@AutoConfigureMockMvc(addFilters = false)
class AuthorityControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private RoleService roleService;
    @MockitoBean
    private AuthorityService authorityService;
    @MockitoBean
    private RoleAuthorityService roleAuthorityService;
    @MockitoBean
    private MessageSource messageSource;
    @MockitoBean
    private OrgChartService orgChartService;
    @MockitoBean
    private ValidationService validationService;
    @MockitoBean
    private TokenService tokenService; // Required for Security Config

    private Authentication authentication;
    private final UUID USER_UUID = UUID.randomUUID();

    // Mock Data
    private OrgChartDto mockOrg;
    private RoleDto roleDto;
    private AuthorityDto authView;
    private AuthorityDto authManage;
    private TranslatedAuthorityDto translatedAuth;
    private RoleAuthorityDto roleAuthDto;

    @BeforeEach
    void setUp() {
        authentication = new UsernamePasswordAuthenticationToken(
                USER_UUID.toString(), "password",
                List.of(new SimpleGrantedAuthority(AuthorityName.CAN_MANAGE_ACCESS_CONTROL.getAuthorityName()))
        );

        // Org & Role
        mockOrg = new OrgChartDto();
        mockOrg.setId(10L);
        mockOrg.setName("IT Dept");
        roleDto = new RoleDto();
        roleDto.setId(100L);
        roleDto.setName("Admin");
        roleDto.setOrgChart(mockOrg);

        // Authorities
        authView = new AuthorityDto();
        authView.setId(1L);
        authView.setName(AuthorityName.CAN_VIEW_ACCESS_CONTROL);
        authManage = new AuthorityDto();
        authManage.setId(2L);
        authManage.setName(AuthorityName.CAN_MANAGE_ACCESS_CONTROL);

        translatedAuth = new TranslatedAuthorityDto();
        translatedAuth.setId(1L);
        translatedAuth.setName(AuthorityName.CAN_VIEW_ACCESS_CONTROL);
        translatedAuth.setLabel("View Access");

        // Role Authority
        roleAuthDto = new RoleAuthorityDto();
        roleAuthDto.setId(new RoleAuthorityId(100L, 1L));
        roleAuthDto.setRole(roleDto);
        roleAuthDto.setAuthority(authView);

        // Common Mocks
        lenient().when(messageSource.getMessage(any(), any(), any(Locale.class))).thenReturn("Mock Message");
        lenient().when(validationService.isNullOrBlank(any())).thenAnswer(inv -> {
            String s = inv.getArgument(0);
            return s == null || s.trim().isEmpty();
        });
    }

    // --- GET Overview Tests ---

    @Test
    void getOverview_ShouldReturnData() throws Exception {
        when(authorityService.getAllTranslatedAuthorities()).thenReturn(List.of(translatedAuth));
        when(roleService.getAllByDeletedIsFalse()).thenReturn(List.of(roleDto));
        when(roleAuthorityService.getRoleAuthorityMapByRoleId(100L)).thenReturn(Map.of(1L, true));

        mockMvc.perform(get("/api/auth/access-control-overview").principal(authentication))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.permissions[0].id").value(1))
                .andExpect(jsonPath("$.departments[0].roles[0].roleId").value(100));
    }

    // --- GRANT ACCESS Tests ---

    @Test
    void grantAccess_ShouldCreateEntries() throws Exception {
        GrantAccessDto req = new GrantAccessDto();
        req.setOrgChartId(10L);
        req.setRoleId(100L);
        req.setSelectedAuthorities(List.of(1L, 2L)); // Add 1 and 2

        when(roleService.getAllById(100L)).thenReturn(roleDto);
        when(authorityService.findAll()).thenReturn(List.of(authView, authManage));
        // Current: Empty
        when(roleAuthorityService.getAllByRoleId(100L)).thenReturn(Collections.emptyList());

        mockMvc.perform(post("/api/auth/grant-access")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req))
                        .principal(authentication))
                .andExpect(status().isOk());

        verify(roleAuthorityService).createAll(argThat(list -> list.size() == 2));
    }

    @Test
    void grantAccess_ShouldFail_WhenOrgChartMismatch() throws Exception {
        GrantAccessDto req = new GrantAccessDto();
        req.setOrgChartId(999L); // Wrong Org ID
        req.setRoleId(100L);
        req.setSelectedAuthorities(List.of(1L));

        when(roleService.getAllById(100L)).thenReturn(roleDto); // Returns Role in Org 10

        mockMvc.perform(post("/api/auth/grant-access")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req))
                        .principal(authentication))
                .andExpect(status().isInternalServerError())
                // NOTE: Controller throws custom DataAccessException.
                // If not mapped, check what result matcher expects.
                // Usually custom exceptions map to 4xx or 5xx.
                .andExpect(result -> assertTrue(result.getResolvedException() instanceof DataAccessException));
    }

    @Test
    void grantAccess_ShouldFail_WhenRequestInvalid() throws Exception {
        GrantAccessDto req = new GrantAccessDto(); // Missing IDs and list

        mockMvc.perform(post("/api/auth/grant-access")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req))
                        .principal(authentication))
                .andExpect(status().isBadRequest());
    }

    @Test
    void grantAccess_ShouldDoNothing_WhenAuthoritiesAlreadyExist() throws Exception {
        GrantAccessDto req = new GrantAccessDto();
        req.setOrgChartId(10L);
        req.setRoleId(100L);
        req.setSelectedAuthorities(List.of(1L)); // Requesting Authority 1

        when(roleService.getAllById(100L)).thenReturn(roleDto);

        // Mock that Role 100 ALREADY has Authority 1
        RoleAuthorityDto existing = new RoleAuthorityDto();
        existing.setId(new RoleAuthorityId(100L, 1L));
        when(roleAuthorityService.getAllByRoleId(100L)).thenReturn(List.of(existing));

        // Perform request
        mockMvc.perform(post("/api/auth/grant-access")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req))
                        .principal(authentication))
                .andExpect(status().isOk());

        // Verify createAll was NEVER called because toAdd was empty
        verify(roleAuthorityService, never()).createAll(any());
    }

    // --- EDIT ACCESS Tests ---

    @Test
    void editAccess_ShouldAddAndRemove() throws Exception {
        GrantAccessDto req = new GrantAccessDto();
        req.setOrgChartId(10L);
        req.setRoleId(100L);
        req.setSelectedAuthorities(List.of(2L)); // Want only 2 (Manage)

        when(roleService.getAllById(100L)).thenReturn(roleDto);
        // Current: Have 1 (View)
        when(roleAuthorityService.getAllByRoleId(100L)).thenReturn(List.of(roleAuthDto));
        when(authorityService.findById(2L)).thenReturn(authManage);

        mockMvc.perform(put("/api/auth/edit-granted-access")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req))
                        .principal(authentication))
                .andExpect(status().isOk());

        // Should create ID 2
        verify(roleAuthorityService).createAll(argThat(list -> list.size() == 1 && list.get(0).getAuthority().getId().equals(2L)));
        // Should delete ID 1
        verify(roleAuthorityService).deleteByRoleIdAndAuthorityIdIn(eq(100L), argThat(set -> set.contains(1L)));
    }

    @Test
    void editAccess_ShouldReturnOk_WhenNoChanges() throws Exception {
        GrantAccessDto req = new GrantAccessDto();
        req.setOrgChartId(10L);
        req.setRoleId(100L);
        req.setSelectedAuthorities(List.of(1L)); // Input: 1

        when(roleService.getAllById(100L)).thenReturn(roleDto);
        // Existed: 1
        RoleAuthorityDto existing = new RoleAuthorityDto();
        existing.setId(new RoleAuthorityId(100L, 1L));
        existing.setAuthority(authView); // ID 1
        when(roleAuthorityService.getAllByRoleId(100L)).thenReturn(List.of(existing));

        mockMvc.perform(put("/api/auth/edit-granted-access")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req))
                        .principal(authentication))
                .andExpect(status().isOk());

        // Verify NO create or delete called
        verify(roleAuthorityService, never()).createAll(any());
        verify(roleAuthorityService, never()).deleteByRoleIdAndAuthorityIdIn(anyLong(), anySet());
    }

    @Test
    void editAccess_ShouldRemove_WhenInputIsEmpty() throws Exception {
        GrantAccessDto req = new GrantAccessDto();
        req.setOrgChartId(10L);
        req.setRoleId(100L);
        req.setSelectedAuthorities(Collections.emptyList()); // Requesting NO permissions

        when(roleService.getAllById(100L)).thenReturn(roleDto);

        // Existing has Authority 1
        RoleAuthorityDto existing = new RoleAuthorityDto();
        existing.setId(new RoleAuthorityId(100L, 1L));
        existing.setAuthority(authView);
        when(roleAuthorityService.getAllByRoleId(100L)).thenReturn(List.of(existing));

        mockMvc.perform(put("/api/auth/edit-granted-access")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req))
                        .principal(authentication))
                .andExpect(status().isOk());

        // Verify create not called
        verify(roleAuthorityService, never()).createAll(any());
        // Verify delete IS called for ID 1
        verify(roleAuthorityService).deleteByRoleIdAndAuthorityIdIn(eq(100L), argThat(ids -> ids.contains(1L)));
    }

    // --- DELETE Tests ---

    @Test
    void delete_ShouldRemoveAllForRole() throws Exception {
        when(roleService.getAllById(100L)).thenReturn(roleDto);
        when(roleAuthorityService.getAllByRoleId(100L)).thenReturn(List.of(roleAuthDto));

        mockMvc.perform(delete("/api/auth/delete")
                        .param("orgChartId", "10")
                        .param("roleId", "100")
                        .principal(authentication))
                .andExpect(status().isOk());

        verify(roleAuthorityService).deleteAllByIdIn(anySet());
    }

    @Test
    void delete_ShouldFail_WhenNoAuthoritiesExist() throws Exception {
        when(roleService.getAllById(100L)).thenReturn(roleDto);
        when(roleAuthorityService.getAllByRoleId(100L)).thenReturn(Collections.emptyList()); // Empty

        mockMvc.perform(delete("/api/auth/delete")
                        .param("orgChartId", "10")
                        .param("roleId", "100")
                        .principal(authentication))
                .andExpect(result -> assertTrue(result.getResolvedException() instanceof DataAccessException));
    }

    @Test
    void delete_ShouldFail_WhenOrgChartMismatch() throws Exception {
        when(roleService.getAllById(100L)).thenReturn(roleDto); // Role is in Org 10

        mockMvc.perform(delete("/api/auth/delete")
                        .param("orgChartId", "999") // Wrong Org ID
                        .param("roleId", "100")
                        .principal(authentication))
                .andExpect(status().isInternalServerError()) // DataAccessException
                .andExpect(result -> assertTrue(result.getResolvedException() instanceof DataAccessException));
    }

    @Test
    void bulkDelete_ShouldRemoveMultiple() throws Exception {
        List<Long> roleIds = List.of(100L);
        when(roleService.getAllByIsDeletedIsFalseAndIdIn(anySet())).thenReturn(List.of(roleDto));
        when(roleAuthorityService.getAllByRoleIdIn(anySet())).thenReturn(List.of(roleAuthDto));

        mockMvc.perform(delete("/api/auth/bulk-delete")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(roleIds))
                        .principal(authentication))
                .andExpect(status().isOk());

        verify(roleAuthorityService).deleteAllByRoleIdIn(anySet());
    }

    @Test
    void bulkDelete_ShouldFail_WhenRoleNotFound() throws Exception {
        List<Long> roleIds = List.of(100L, 200L); // Requesting 2 roles

        // Mock returns only 1 role (Role 200 missing)
        when(roleService.getAllByIsDeletedIsFalseAndIdIn(anySet())).thenReturn(List.of(roleDto));

        mockMvc.perform(delete("/api/auth/bulk-delete")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(roleIds))
                        .principal(authentication))
                .andExpect(result -> assertTrue(result.getResolvedException() instanceof DataAccessException));
    }

    @Test
    void bulkDelete_ShouldFail_WhenNoAuthoritiesToDisconnect() throws Exception {
        List<Long> roleIds = List.of(100L);
        when(roleService.getAllByIsDeletedIsFalseAndIdIn(anySet())).thenReturn(List.of(roleDto));

        // Mock that the selected role has NO authorities assigned
        when(roleAuthorityService.getAllByRoleIdIn(anySet())).thenReturn(Collections.emptyList());

        mockMvc.perform(delete("/api/auth/bulk-delete")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(roleIds))
                        .principal(authentication))
                .andExpect(status().isInternalServerError()) // DataAccessException
                .andExpect(result -> assertTrue(result.getResolvedException() instanceof DataAccessException));
    }

    // --- EXPORT Tests ---

    @Test
    void export_ShouldReturnExcel() throws Exception {
        // Setup Authority Enum Mocks for iteration in Export
        // We mocked authorityService.getAll() -> returns list. Controller uses it to map headers?
        // Actually Controller manually maps columns 2-16 to specific ENUMS.
        // We need to ensure roleAuthorityService.getAll() returns data to fill cells "Yes".

        when(roleService.getAllByDeletedIsFalse()).thenReturn(List.of(roleDto));
        when(roleAuthorityService.getAll()).thenReturn(List.of(roleAuthDto)); // Has CAN_VIEW_ACCESS_CONTROL

        var result = mockMvc.perform(get("/api/auth/export")
                        .principal(authentication))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=Exported_Access_Control_Data.xlsx"))
                .andReturn();

        byte[] content = result.getResponse().getContentAsByteArray();
        try (Workbook wb = new XSSFWorkbook(new ByteArrayInputStream(content))) {
            Sheet sheet = wb.getSheetAt(0);
            Row row = sheet.getRow(1); // Data row
            // Col 0: Dept ("IT Dept")
            assertEquals("IT Dept", row.getCell(0).getStringCellValue());
            // Col 1: Role ("Admin")
            assertEquals("Admin", row.getCell(1).getStringCellValue());
            // Col 3: VIEW_ACCESS_CONTROL column. Should be "Yes" because roleAuthDto has it.
            assertEquals("Yes", row.getCell(3).getStringCellValue());
            // Col 4: MANAGE_ACCESS_CONTROL. Should be null/empty.
            assertEquals("", row.getCell(4) == null ? "" : row.getCell(4).getStringCellValue());
        }
    }

    // --- IMPORT Tests ---

    @Test
    void import_ShouldGrantAndRevokeAccess() throws Exception {
        // Scenario: Input says "Admin" has "Manage Access" (YES) but NOT "View Access".
        // Current DB: "Admin" has "View Access".
        // Expected: Add "Manage", Remove "View".

        // 1. Setup Excel Data
        List<String> headers = new ArrayList<>(Arrays.asList(
                "Department Name", "Role Name", "User", "View Access Control", "Manage Access Control",
                "View Staff Account", "Manage Staff Account", "View Invisible Role", "Manage Role",
                "Manage Competency", "Propose Role Competencies", "Manage Career Pathway", "Manage Org Chart",
                "Manage Training", "Assign Training", "Manage Learning Material", "Manage Evaluation", "Manage Evaluation Cycle"
        ));

        // Data Row: Dept, Role, User(No), View(No), Manage(YES), others...
        List<String> rowData = new ArrayList<>(Arrays.asList(
                "IT Dept", "Admin", "", "", "Yes", "", "", "", "", "", "", "", "", "", "", "", "", ""
        ));

        byte[] excelBytes = createExcel(headers, List.of(rowData));
        MockMultipartFile file = new MockMultipartFile("file", "import.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", excelBytes);

        // 2. Mocks
        when(orgChartService.findAllByIsDeletedIsFalse()).thenReturn(List.of(mockOrg));
        when(roleService.getAllByDeletedIsFalse()).thenReturn(List.of(roleDto));

        // Mock current authorities: Admin has "View" (ID 1)
        when(roleAuthorityService.getAll()).thenReturn(List.of(roleAuthDto));

        // Mock Authority Lookup Maps
        when(authorityService.findAll()).thenReturn(List.of(authView, authManage));

        // 3. Perform
        mockMvc.perform(multipart("/api/auth/import").file(file).principal(authentication))
                .andExpect(status().isOk());

        // 4. Verify
        // Should create "Manage" (ID 2)
        verify(roleAuthorityService).createAll(argThat(list ->
                list.size() == 1 && list.get(0).getAuthority().getName().equals(AuthorityName.CAN_MANAGE_ACCESS_CONTROL)
        ));

        // Should delete "View" (ID 1)
        // Controller collects deletions into 'roleAuthorityIdToDelete' set and calls deleteAllByIdIn
        verify(roleAuthorityService).deleteAllByIdIn(argThat(ids ->
                ids.size() == 1 && ids.iterator().next().getAuthorityId().equals(1L)
        ));
    }

    @Test
    void import_ShouldFail_WhenDepartmentNotFound() throws Exception {
        List<String> headers = List.of("Department Name", "Role Name", "User", "View Access Control", "Manage Access Control", "View Staff Account", "Manage Staff Account", "View Invisible Role", "Manage Role", "Manage Competency", "Propose Role Competencies", "Manage Career Pathway", "Manage Org Chart", "Manage Training", "Assign Training", "Manage Learning Material", "Manage Evaluation", "Manage Evaluation Cycle");
        List<String> rowData = new ArrayList<>(Arrays.asList("Ghost Dept", "Admin", "", "", "", "", "", "", "", "", "", "", "", "", "", "", "", ""));

        byte[] excelBytes = createExcel(headers, List.of(rowData));
        MockMultipartFile file = new MockMultipartFile("file", "import.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", excelBytes);

        when(orgChartService.findAllByIsDeletedIsFalse()).thenReturn(List.of(mockOrg)); // Only IT Dept

        mockMvc.perform(multipart("/api/auth/import").file(file).principal(authentication))
                .andExpect(status().isBadRequest());
    }

    @Test
    void import_ShouldFail_WhenRoleNotInDepartment() throws Exception {
        // "HR" Role does not exist in "IT Dept"
        List<String> headers = getValidHeaders();
        List<String> rowData = new ArrayList<>(Collections.nCopies(17, ""));
        rowData.set(0, "IT Dept");
        rowData.set(1, "HR Manager"); // Invalid Role for IT Dept

        byte[] excelBytes = createExcel(headers, List.of(rowData));
        MockMultipartFile file = new MockMultipartFile("file", "import.xlsx", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", excelBytes);

        // Mock Org and Roles setup (Only Admin in IT Dept)
        when(orgChartService.findAllByIsDeletedIsFalse()).thenReturn(List.of(mockOrg));
        when(roleService.getAllByDeletedIsFalse()).thenReturn(List.of(roleDto));

        mockMvc.perform(multipart("/api/auth/import").file(file).principal(authentication))
                .andExpect(status().isBadRequest());
    }

    @Test
    void import_ShouldFail_WhenDuplicateEntries() throws Exception {
        List<String> headers = getValidHeaders();
        List<String> row1 = new ArrayList<>(Collections.nCopies(17, ""));
        row1.set(0, "IT Dept");
        row1.set(1, "Admin");

        // Duplicate row
        List<List<String>> rows = List.of(row1, row1);

        byte[] excelBytes = createExcel(headers, rows);
        MockMultipartFile file = new MockMultipartFile("file", "import.xlsx", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", excelBytes);

        when(orgChartService.findAllByIsDeletedIsFalse()).thenReturn(List.of(mockOrg));
        when(roleService.getAllByDeletedIsFalse()).thenReturn(List.of(roleDto));

        mockMvc.perform(multipart("/api/auth/import").file(file).principal(authentication))
                .andExpect(status().isBadRequest());
    }

    @Test
    void import_ShouldFail_WhenRoleNameIsEmpty() throws Exception {
        // Setup: Empty Role Name in Column 1
        List<String> headers = getValidHeaders();
        List<String> rowData = new ArrayList<>(Collections.nCopies(17, ""));
        rowData.set(0, "IT Dept");
        rowData.set(1, ""); // Empty Role Name

        byte[] excelBytes = createExcel(headers, List.of(rowData));
        MockMultipartFile file = new MockMultipartFile("file", "import.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", excelBytes);

        mockMvc.perform(multipart("/api/auth/import").file(file).principal(authentication))
                .andExpect(status().isBadRequest());
    }

    @Test
    void import_ShouldParseMultiplePermissionFlags() throws Exception {
        // Setup Headers
        List<String> headers = getValidHeaders();

        // Setup Row Data: Enable "View Staff" (Col 5) and "Manage Role" (Col 8)
        List<String> rowData = new ArrayList<>(Collections.nCopies(17, ""));
        rowData.set(0, "IT Dept");
        rowData.set(1, "Admin");
        rowData.set(5, "Yes"); // VIEW_STAFF
        rowData.set(8, "Yes"); // MANAGE_ROLE

        byte[] excelBytes = createExcel(headers, List.of(rowData));
        MockMultipartFile file = new MockMultipartFile("file", "import.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", excelBytes);

        // Mocks
        when(orgChartService.findAllByIsDeletedIsFalse()).thenReturn(List.of(mockOrg));
        when(roleService.getAllByDeletedIsFalse()).thenReturn(List.of(roleDto));

        // Mock Authorities needed for lookup
        AuthorityDto authStaff = new AuthorityDto();
        authStaff.setId(10L);
        authStaff.setName(AuthorityName.CAN_VIEW_STAFF);
        AuthorityDto authRole = new AuthorityDto();
        authRole.setId(11L);
        authRole.setName(AuthorityName.CAN_MANAGE_ROLE);
        when(authorityService.findAll()).thenReturn(List.of(authStaff, authRole));

        mockMvc.perform(multipart("/api/auth/import").file(file).principal(authentication))
                .andExpect(status().isOk());

        // Verify that 2 new authorities were created
        verify(roleAuthorityService).createAll(argThat(list ->
                list.size() == 2 &&
                        list.stream().anyMatch(dto -> dto.getAuthority().getName().equals(AuthorityName.CAN_VIEW_STAFF)) &&
                        list.stream().anyMatch(dto -> dto.getAuthority().getName().equals(AuthorityName.CAN_MANAGE_ROLE))
        ));
    }

    @Test
    void import_ShouldFail_WhenFileFormatIsInvalid() throws Exception {
        // Test 1: Invalid Extension
        MockMultipartFile invalidExt = new MockMultipartFile("file", "test.txt",
                "text/plain", "content".getBytes());

        mockMvc.perform(multipart("/api/auth/import").file(invalidExt).principal(authentication))
                .andExpect(status().isBadRequest());

        // Test 2: Invalid Content Type but valid extension
        MockMultipartFile invalidType = new MockMultipartFile("file", "test.xlsx",
                "application/pdf", "content".getBytes());

        mockMvc.perform(multipart("/api/auth/import").file(invalidType).principal(authentication))
                .andExpect(status().isBadRequest());
    }

    @Test
    void import_ShouldFail_WhenRoleDoesNotBelongToDepartment() throws Exception {
        List<String> headers = getValidHeaders();
        // Valid Dept "IT Dept", but Role "Janitor" exists in DB but not in this Dept
        List<String> rowData = new ArrayList<>(Collections.nCopies(17, ""));
        rowData.set(0, "IT Dept");
        rowData.set(1, "Janitor");

        byte[] excelBytes = createExcel(headers, List.of(rowData));
        MockMultipartFile file = new MockMultipartFile("file", "import.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", excelBytes);

        // IT Dept exists, but its Set of roles does NOT contain "janitor"
        when(orgChartService.findAllByIsDeletedIsFalse()).thenReturn(List.of(mockOrg));
        when(roleService.getAllByDeletedIsFalse()).thenReturn(List.of(roleDto)); // roleDto is "Admin"

        mockMvc.perform(multipart("/api/auth/import").file(file).principal(authentication))
                .andExpect(status().isBadRequest());
    }

    @Test
    void import_ShouldParseAllPermissionColumnsCorrectly() throws Exception {
        List<String> headers = getValidHeaders();
        List<String> rowData = new ArrayList<>(Collections.nCopies(17, "Yes")); // All "Yes"
        rowData.set(0, "IT Dept");
        rowData.set(1, "Admin");

        byte[] excelBytes = createExcel(headers, List.of(rowData));
        MockMultipartFile file = new MockMultipartFile("file", "import.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", excelBytes);

        when(orgChartService.findAllByIsDeletedIsFalse()).thenReturn(List.of(mockOrg));
        when(roleService.getAllByDeletedIsFalse()).thenReturn(List.of(roleDto));
        when(authorityService.findAll()).thenReturn(getAllMockAuthorities());

        mockMvc.perform(multipart("/api/auth/import").file(file).principal(authentication))
                .andExpect(status().isOk());

        // Verify createAll was called with 17 authorities (assuming role had 0 before)
        verify(roleAuthorityService).createAll(argThat(list -> list.size() == 15));
    }

    @Test
    void import_ShouldHandleSimultaneousAddAndRemove() throws Exception {
        // Current DB state: Admin has [USER_ROLE, VIEW_STAFF]
        // Excel Input: Admin marked "Yes" for [USER_ROLE, MANAGE_ROLE]
        // Result: Remove VIEW_STAFF, Add MANAGE_ROLE, keep USER_ROLE (no action)

        List<String> headers = getValidHeaders();
        List<String> rowData = new ArrayList<>(Collections.nCopies(17, ""));
        rowData.set(0, "IT Dept");
        rowData.set(1, "Admin");
        rowData.set(2, "Yes"); // ROLE_USER (Keep)
        rowData.set(8, "Yes"); // CAN_MANAGE_ROLE (Add)
        // VIEW_STAFF (Col 5) is left empty (Remove)

        byte[] excelBytes = createExcel(headers, List.of(rowData));
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "import.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                excelBytes
        );

        // Current Authorities in DB
        RoleAuthorityDto existing1 = new RoleAuthorityDto();
        existing1.setId(new RoleAuthorityId(100L, 1L)); // ROLE_USER
        RoleAuthorityDto existing2 = new RoleAuthorityDto();
        existing2.setId(new RoleAuthorityId(100L, 2L)); // CAN_VIEW_STAFF

        when(roleAuthorityService.getAll()).thenReturn(List.of(existing1, existing2));
        when(orgChartService.findAllByIsDeletedIsFalse()).thenReturn(List.of(mockOrg));
        when(roleService.getAllByDeletedIsFalse()).thenReturn(List.of(roleDto));
        when(authorityService.findAll()).thenReturn(List.of(
                createAuth(1L, AuthorityName.ROLE_USER),
                createAuth(2L, AuthorityName.CAN_VIEW_STAFF),
                createAuth(3L, AuthorityName.CAN_MANAGE_ROLE)
        ));

        mockMvc.perform(multipart("/api/auth/import").file(file).principal(authentication))
                .andExpect(status().isOk());

        // Verify: createAll called for ID 3, deleteAllByIdIn called for ID 2
        verify(roleAuthorityService).createAll(argThat(list ->
                list.size() == 1 && list.get(0).getId().getAuthorityId().equals(3L)));
        verify(roleAuthorityService).deleteAllByIdIn(argThat(set ->
                set.size() == 1 && set.iterator().next().getAuthorityId().equals(2L)));
    }

    // Helper for headers

    private AuthorityDto createAuth(Long id, AuthorityName name) {
        AuthorityDto d = new AuthorityDto();
        d.setId(id);
        d.setName(name);
        return d;
    }

    private List<AuthorityDto> getAllMockAuthorities() {
        return Arrays.stream(AuthorityName.values()).map(name -> {
            AuthorityDto dto = new AuthorityDto();
            dto.setId((long) name.ordinal());
            dto.setName(name);
            return dto;
        }).collect(Collectors.toList());
    }

    private List<String> getValidHeaders() {
        return new ArrayList<>(Arrays.asList(
                "Department Name", "Role Name", "User", "View Access Control", "Manage Access Control",
                "View Staff Account", "Manage Staff Account", "View Invisible Role", "Manage Role",
                "Manage Competency", "Propose Role Competencies", "Manage Career Pathway", "Manage Org Chart",
                "Manage Training", "Assign Training", "Manage Learning Material", "Manage Evaluation", "Manage Evaluation Cycle"
        ));
    }

    private byte[] createExcel(List<String> headers, List<List<String>> data) throws IOException {
        try (Workbook wb = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sheet = wb.createSheet();
            Row headerRow = sheet.createRow(0);
            for (int i = 0; i < headers.size(); i++) headerRow.createCell(i).setCellValue(headers.get(i));

            int rowIdx = 1;
            for (List<String> rowData : data) {
                Row row = sheet.createRow(rowIdx++);
                for (int i = 0; i < rowData.size(); i++) row.createCell(i).setCellValue(rowData.get(i));
            }
            wb.write(out);
            return out.toByteArray();
        }
    }
}