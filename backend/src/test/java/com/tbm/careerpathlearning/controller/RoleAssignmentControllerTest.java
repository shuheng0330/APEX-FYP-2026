package com.tbm.careerpathlearning.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tbm.careerpathlearning.dto.*;
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
import static org.mockito.ArgumentMatchers.anySet;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(controllers = RoleAssignmentController.class)
@AutoConfigureMockMvc(addFilters = false)
class RoleAssignmentControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private RoleService roleService;

    @MockitoBean
    private StaffService staffService;

    @MockitoBean
    private OrgChartService orgChartService;

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
    private StaffDto mockStaff;

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
        mockRole.setName("Developer");
        mockRole.setOrgChart(mockOrg);

        mockStaff = new StaffDto();
        mockStaff.setId(UUID.randomUUID());
        mockStaff.setName("John Doe");
        mockStaff.setEmail("john@test.com");
        mockStaff.setRole(mockRole);

        lenient().when(messageSource.getMessage(any(), any(), any(Locale.class))).thenReturn("Mock Message");
    }

    // --- Overview Tests ---

    @Test
    void getRoleAssignmentOverview_ShouldReturnList() throws Exception {
        when(roleService.getAll()).thenReturn(List.of(mockRole));
        when(staffService.findAllByIsDeletedIsFalse()).thenReturn(List.of(mockStaff));

        mockMvc.perform(get("/api/role/assignment/overview").principal(authentication))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.size()").value(1))
                .andExpect(jsonPath("$[0].roleName").value("Developer"))
                .andExpect(jsonPath("$[0].staffList[0].email").value("john@test.com"));
    }

    @Test
    void getRoleAssignmentOverview_ShouldFilterDeletedRolesWithoutStaff() throws Exception {
        // Scenario 1: Active Role, No Staff -> SHOW
        RoleDto roleActiveNoStaff = new RoleDto(); roleActiveNoStaff.setId(1L);
        roleActiveNoStaff.setDeleted(false); roleActiveNoStaff.setOrgChart(mockOrg);

        // Scenario 2: Deleted Role, Has Staff -> SHOW (Historical record)
        RoleDto roleDeletedWithStaff = new RoleDto(); roleDeletedWithStaff.setId(2L);
        roleDeletedWithStaff.setDeleted(true); roleDeletedWithStaff.setOrgChart(mockOrg);

        // Scenario 3: Deleted Role, No Staff -> HIDE (Cleanup)
        RoleDto roleDeletedNoStaff = new RoleDto(); roleDeletedNoStaff.setId(3L);
        roleDeletedNoStaff.setDeleted(true); roleDeletedNoStaff.setOrgChart(mockOrg);

        when(roleService.getAll()).thenReturn(List.of(roleActiveNoStaff, roleDeletedWithStaff, roleDeletedNoStaff));

        // Mock Staff: Only assign to role 2
        StaffDto staff = new StaffDto(); staff.setId(UUID.randomUUID()); staff.setRole(roleDeletedWithStaff);
        when(staffService.findAllByIsDeletedIsFalse()).thenReturn(List.of(staff));

        mockMvc.perform(get("/api/role/assignment/overview").principal(authentication))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2)) // Only 2 should be returned
                .andExpect(jsonPath("$[?(@.roleId == 1)]").exists())
                .andExpect(jsonPath("$[?(@.roleId == 2)]").exists())
                .andExpect(jsonPath("$[?(@.roleId == 3)]").doesNotExist());
    }

    // --- Create Assignment Tests ---

    @Test
    void createRoleAssignment_ShouldCallUpdate() throws Exception {
        UpdateRoleAssignmentRequestDto req = new UpdateRoleAssignmentRequestDto();
        req.setRoleId(1L);
        req.setStaffIds(List.of(mockStaff.getId()));

        when(roleService.getAllById(1L)).thenReturn(mockRole);

        mockMvc.perform(post("/api/role/assignment")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req))
                        .principal(authentication))
                .andExpect(status().isOk());

        verify(staffService).updateRoleByStaffIdIn(anySet(), eq(mockRole), any(UUID.class));
    }

    @Test
    void createRoleAssignment_ShouldFail_WhenBodyInvalid() throws Exception {
        UpdateRoleAssignmentRequestDto req = new UpdateRoleAssignmentRequestDto();
        // Missing RoleID

        mockMvc.perform(post("/api/role/assignment")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req))
                        .principal(authentication))
                .andExpect(status().isBadRequest());
    }

    @Test
    void createRoleAssignment_ShouldFail_WhenRoleIdMissing() throws Exception {
        UpdateRoleAssignmentRequestDto req = new UpdateRoleAssignmentRequestDto();
        req.setRoleId(null); // Missing Role ID

        mockMvc.perform(post("/api/role/assignment")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req))
                        .principal(authentication))
                .andExpect(status().isBadRequest());
    }

    // --- Update/Edit Tests ---

    @Test
    void updateRoleAssignment_ShouldHandleAddAndRemove() throws Exception {
        // Scenario:
        // Existing: Staff A
        // Incoming Request: Staff B
        // Result: Remove A, Add B

        UUID existingStaffId = UUID.randomUUID();
        UUID newStaffId = UUID.randomUUID();

        StaffDto existingStaff = new StaffDto();
        existingStaff.setId(existingStaffId);

        UpdateRoleAssignmentRequestDto req = new UpdateRoleAssignmentRequestDto();
        req.setRoleId(1L);
        req.setStaffIds(List.of(newStaffId)); // Only sending B

        when(roleService.getAllById(1L)).thenReturn(mockRole);
        when(staffService.findAllByRoleId(1L)).thenReturn(List.of(existingStaff)); // DB has A

        mockMvc.perform(put("/api/role/assignment/edit-role-assignment")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req))
                        .principal(authentication))
                .andExpect(status().isOk());

        // Verify Remove logic (A is removed, so role becomes null)
        verify(staffService).updateRoleByStaffIdIn(argThat(ids -> ids.contains(existingStaffId)), isNull(), any(UUID.class));

        // Verify Add logic (B is added, role is set)
        verify(staffService).updateRoleByStaffIdIn(argThat(ids -> ids.contains(newStaffId)), eq(mockRole), any(UUID.class));
    }

    @Test
    void updateRoleAssignment_ShouldFail_WhenRoleIdMissing() throws Exception {
        UpdateRoleAssignmentRequestDto req = new UpdateRoleAssignmentRequestDto();
        req.setRoleId(null); // Missing Role ID

        mockMvc.perform(put("/api/role/assignment/edit-role-assignment")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req))
                        .principal(authentication))
                .andExpect(status().isBadRequest());
    }

    // --- Delete Tests ---

    @Test
    void deleteRole_ShouldUnassignStaff() throws Exception {
        when(staffService.findAllByRoleId(1L)).thenReturn(List.of(mockStaff));

        mockMvc.perform(delete("/api/role/assignment/delete")
                        .param("roleId", "1")
                        .principal(authentication))
                .andExpect(status().isOk());

        verify(staffService).updateRoleByStaffIdIn(anySet(), isNull(), any(UUID.class));
    }

    @Test
    void deleteRole_ShouldFail_WhenRoleIdMissing() throws Exception {
        mockMvc.perform(delete("/api/role/assignment/delete")
                        // No param provided
                        .principal(authentication))
                .andExpect(status().isBadRequest());
    }

    @Test
    void bulkDeleteRole_ShouldUnassignMultiple() throws Exception {
        when(staffService.findAllByRoleIdIn(anySet())).thenReturn(List.of(mockStaff));

        mockMvc.perform(delete("/api/role/assignment/bulk-delete")
                        .param("roleIds", "1,2")
                        .principal(authentication))
                .andExpect(status().isOk());

        verify(staffService).updateRoleByStaffIdIn(anySet(), isNull(), any(UUID.class));
    }

    @Test
    void bulkDeleteRole_ShouldFail_WhenListEmpty() throws Exception {
        mockMvc.perform(delete("/api/role/assignment/bulk-delete")
                        // No param provided implies null/empty list
                        .principal(authentication))
                .andExpect(status().isBadRequest());
    }

    // --- Export Tests ---

    @Test
    void export_ShouldReturnExcel() throws Exception {
        when(roleService.getAllByDeletedIsFalse()).thenReturn(List.of(mockRole));
        when(staffService.findAllByIsDeletedIsFalse()).thenReturn(List.of(mockStaff));

        mockMvc.perform(get("/api/role/assignment/export").principal(authentication))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=Exported_Role_Assignment_Data.xlsx"))
                .andExpect(content().contentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"));
    }

    // --- Import Tests (Complex) ---

    @Test
    void import_ShouldProcessValidFile() throws Exception {
        // 1. Prepare Data
        List<String[]> data = new ArrayList<>(Collections.singleton(
                new String[]{"IT Dept", "Developer", "john@test.com; jane@test.com"}
        ));

        byte[] excelBytes = createExcelFromData(data);
        MockMultipartFile file = new MockMultipartFile("file", "import.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", excelBytes);

        // 2. Mock DB Lookups
        when(orgChartService.findAllByIsDeletedIsFalse()).thenReturn(List.of(mockOrg));
        when(roleService.getAllByDeletedIsFalse()).thenReturn(List.of(mockRole));

        StaffDto staff1 = new StaffDto();
        staff1.setId(UUID.randomUUID());
        staff1.setEmail("john@test.com");
        StaffDto staff2 = new StaffDto();
        staff2.setId(UUID.randomUUID());
        staff2.setEmail("jane@test.com");
        when(staffService.findAllByIsDeletedIsFalse()).thenReturn(List.of(staff1, staff2));

        // 3. Mock Validation
        when(validationService.isNullOrBlank(any())).thenAnswer(inv -> {
            String s = inv.getArgument(0);
            return s == null || s.trim().isEmpty();
        });

        // 4. Perform
        mockMvc.perform(multipart("/api/role/assignment/import")
                        .file(file)
                        .principal(authentication))
                .andExpect(status().isOk());

        // 5. Verify Update
        verify(staffService).updateAll(anyList());
    }

    @Test
    void import_ShouldFail_WhenHeadersInvalid() throws Exception {
        // Invalid headers
        byte[] excelBytes = createExcelWithHeaders(new String[]{"Wrong", "Header", "Columns"});
        MockMultipartFile file = new MockMultipartFile("file", "import.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", excelBytes);

        // Need mocks to pass init phase
        when(staffService.findAllByIsDeletedIsFalse()).thenReturn(Collections.emptyList());

        mockMvc.perform(multipart("/api/role/assignment/import").file(file).principal(authentication))
                .andExpect(status().isBadRequest());
    }

    @Test
    void import_ShouldFail_WhenRoleNotFound() throws Exception {
        List<String[]> data = new ArrayList<>(Collections.singleton(
                new String[]{"IT Dept", "NonExistentRole", "john@test.com"}
        ));

        byte[] excelBytes = createExcelFromData(data);
        MockMultipartFile file = new MockMultipartFile("file", "import.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", excelBytes);

        when(orgChartService.findAllByIsDeletedIsFalse()).thenReturn(List.of(mockOrg));
        // Mock role service returns empty, so map build works but check inside loop fails
        when(roleService.getAllByDeletedIsFalse()).thenReturn(List.of(mockRole));
        when(staffService.findAllByIsDeletedIsFalse()).thenReturn(List.of(mockStaff));
        when(validationService.isNullOrBlank(any())).thenReturn(false);

        mockMvc.perform(multipart("/api/role/assignment/import").file(file).principal(authentication))
                .andExpect(status().isBadRequest()); // IMPORT_DATA_NOT_FOUND_ERR_MSG_CODE
    }

    @Test
    void import_ShouldFail_WhenDuplicateEntryInFile() throws Exception {
        List<String[]> data = List.of(
                new String[]{"IT Dept", "Developer", "john@test.com"},
                new String[]{"IT Dept", "Developer", "jane@test.com"} // Duplicate row for Dept+Role
        );
        byte[] excelBytes = createExcelFromData(data);
        MockMultipartFile file = new MockMultipartFile("file", "import.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", excelBytes);

        when(orgChartService.findAllByIsDeletedIsFalse()).thenReturn(List.of(mockOrg));
        when(roleService.getAllByDeletedIsFalse()).thenReturn(List.of(mockRole));
        when(staffService.findAllByIsDeletedIsFalse()).thenReturn(List.of(mockStaff));
        when(validationService.isNullOrBlank(any())).thenReturn(false);

        mockMvc.perform(multipart("/api/role/assignment/import").file(file).principal(authentication))
                .andExpect(status().isBadRequest()); // IMPORT_DUPLICATE_ENTRY_ERR_MSG_CODE
    }

    @Test
    void import_ShouldFail_WhenStaffEmailNotFound() throws Exception {
        List<String[]> data = new ArrayList<>(Collections.singleton(
                new String[]{"IT Dept", "Developer", "unknown@test.com"}
        ));

        byte[] excelBytes = createExcelFromData(data);
        MockMultipartFile file = new MockMultipartFile("file", "import.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", excelBytes);

        when(staffService.findAllByIsDeletedIsFalse()).thenReturn(Collections.emptyList()); // No staff in DB
        when(validationService.isNullOrBlank(any())).thenReturn(false);

        mockMvc.perform(multipart("/api/role/assignment/import").file(file).principal(authentication))
                .andExpect(status().isBadRequest());
    }

    @Test
    void import_ShouldFail_WhenFileTooLarge() throws Exception {
        // Create a large byte array > 5MB
        byte[] largeContent = new byte[(5 * 1024 * 1024) + 10];
        MockMultipartFile largeFile = new MockMultipartFile("file", "large.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", largeContent);

        mockMvc.perform(multipart("/api/role/assignment/import").file(largeFile).principal(authentication))
                .andExpect(status().isBadRequest()); // IMPORT_FILE_TOO_LARGE_ERR_MSG_CODE
    }

    @Test
    void import_ShouldFail_WhenInvalidFileType() throws Exception {
        MockMultipartFile txtFile = new MockMultipartFile("file", "test.txt", "text/plain", "content".getBytes());

        mockMvc.perform(multipart("/api/role/assignment/import").file(txtFile).principal(authentication))
                .andExpect(status().isBadRequest()); // IMPORT_FILE_INVALID_ERR_MSG_CODE
    }

    @Test
    void import_ShouldFail_WhenDepartmentNotFound() throws Exception {
        List<String[]> data = new ArrayList<>(Collections.singleton(
                new String[]{"Missing Dept", "Developer", "john@test.com"}
        ));

        byte[] excelBytes = createExcelFromData(data);
        MockMultipartFile file = new MockMultipartFile("file", "import.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", excelBytes);

        // Dept lookup returns empty
        when(orgChartService.findAllByIsDeletedIsFalse()).thenReturn(Collections.emptyList());
        when(validationService.isNullOrBlank(any())).thenReturn(false);

        mockMvc.perform(multipart("/api/role/assignment/import").file(file).principal(authentication))
                .andExpect(status().isBadRequest()); // IMPORT_DATA_NOT_FOUND_ERR_MSG_CODE
    }

    @Test
    void import_ShouldFail_WhenDuplicateEmailInSameRow() throws Exception {
        // "john@test.com" appears twice in the same cell
        List<String[]> data = new ArrayList<>(Collections.singleton(
                new String[]{"IT Dept", "Developer", "john@test.com; john@test.com"}
        ));

        byte[] excelBytes = createExcelFromData(data);
        MockMultipartFile file = new MockMultipartFile("file", "import.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", excelBytes);

        // Basic Setup
        when(orgChartService.findAllByIsDeletedIsFalse()).thenReturn(List.of(mockOrg));
        when(staffService.findAllByIsDeletedIsFalse()).thenReturn(List.of(mockStaff)); // Staff exists

        // Smart Mock for validation
        when(validationService.isNullOrBlank(any())).thenAnswer(inv -> {
            String s = inv.getArgument(0);
            return s == null || s.trim().isEmpty();
        });

        mockMvc.perform(multipart("/api/role/assignment/import").file(file).principal(authentication))
                .andExpect(status().isBadRequest()); // IMPORT_DUPLICATE_ENTRY_ERR_MSG_CODE
    }

    @Test
    void import_ShouldHandle_NumericAndBooleanCells() throws Exception {
        // Manually construct Excel with non-string cells
        byte[] excelBytes;
        try (Workbook workbook = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sheet = workbook.createSheet("Sheet 1");
            Row header = sheet.createRow(0);
            String[] headers = {"Department Name", "Role Name", "Staff Email"};
            for (int i = 0; i < headers.length; i++) header.createCell(i).setCellValue(headers[i]);

            Row row = sheet.createRow(1);
            row.createCell(0).setCellValue("IT Dept");
            // Numeric Role Name "123"
            row.createCell(1).setCellValue(123);
            // Boolean Email "true" (Just to test the parser, even if invalid email)
            row.createCell(2).setCellValue(true);

            workbook.write(out);
            excelBytes = out.toByteArray();
        }

        MockMultipartFile file = new MockMultipartFile("file", "types.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", excelBytes);

        // Mocks
        when(orgChartService.findAllByIsDeletedIsFalse()).thenReturn(List.of(mockOrg));

        // Return a role that matches "123"
        RoleDto numericRole = new RoleDto();
        numericRole.setName("123");
        numericRole.setOrgChart(mockOrg);
        when(roleService.getAllByDeletedIsFalse()).thenReturn(List.of(numericRole));

        // Use smart validation mock to let "true" pass initial check (it will fail email lookup later, which is fine)
        when(validationService.isNullOrBlank(any())).thenAnswer(inv -> {
            String s = inv.getArgument(0);
            return s == null || s.trim().isEmpty();
        });

        // We expect Bad Request eventually because "true" is not a valid email in our mock map,
        // BUT the test passes if we get past the cell reading logic without a ClassCastException.
        // Or we can mock the email lookup to accept "true" if we want 200 OK.

        // Let's expect BadRequest (Email not found), which confirms the parser worked and returned "true"
        mockMvc.perform(multipart("/api/role/assignment/import").file(file).principal(authentication))
                .andExpect(status().isBadRequest());
    }

    @Test
    void import_ShouldSkipUpdate_WhenRoleNotChanging() throws Exception {
        // Scenario: John is ALREADY assigned to "Developer". File says "Developer" -> "john@test.com".
        // Expectation: logic detects no change, so staffService.updateAll is NOT called.

        List<String[]> data = new ArrayList<>(Collections.singleton(new String[]{"IT Dept", "Developer", "john@test.com"}));
        byte[] excelBytes = createExcelFromData(data);
        MockMultipartFile file = new MockMultipartFile("file", "import.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", excelBytes);

        // Mock DB: John is already in "Developer"
        when(orgChartService.findAllByIsDeletedIsFalse()).thenReturn(List.of(mockOrg));
        when(roleService.getAllByDeletedIsFalse()).thenReturn(List.of(mockRole));

        StaffDto john = new StaffDto();
        john.setId(UUID.randomUUID());
        john.setEmail("john@test.com");
        john.setRole(mockRole); // <--- Already Assigned

        when(staffService.findAllByIsDeletedIsFalse()).thenReturn(List.of(john));
        when(validationService.isNullOrBlank(any())).thenAnswer(inv -> inv.getArgument(0) == null || ((String)inv.getArgument(0)).isBlank());

        mockMvc.perform(multipart("/api/role/assignment/import").file(file).principal(authentication))
                .andExpect(status().isOk());

        // Verify: updateAll is NEVER called because no change was detected
        verify(staffService, never()).updateAll(anyList());
    }

    @Test
    void import_ShouldUnassignStaff_WhenMissingFromFile() throws Exception {
        // Scenario: John is currently "Developer".
        // Import file for "Developer" only lists "jane@test.com".
        // Result: John should be removed from "Developer".

        List<String[]> data = new ArrayList<>(Collections.singleton(new String[]{"IT Dept", "Developer", "jane@test.com"}));
        byte[] excelBytes = createExcelFromData(data);
        MockMultipartFile file = new MockMultipartFile("file", "import.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", excelBytes);

        when(orgChartService.findAllByIsDeletedIsFalse()).thenReturn(List.of(mockOrg));
        when(roleService.getAllByDeletedIsFalse()).thenReturn(List.of(mockRole));

        StaffDto john = new StaffDto(); john.setId(UUID.randomUUID()); john.setEmail("john@test.com"); john.setRole(mockRole);
        StaffDto jane = new StaffDto(); jane.setId(UUID.randomUUID()); jane.setEmail("jane@test.com"); jane.setRole(null);

        when(staffService.findAllByIsDeletedIsFalse()).thenReturn(List.of(john, jane));
        when(validationService.isNullOrBlank(any())).thenAnswer(inv -> inv.getArgument(0) == null || ((String)inv.getArgument(0)).isBlank());

        mockMvc.perform(multipart("/api/role/assignment/import").file(file).principal(authentication))
                .andExpect(status().isOk());

        // Verify: John is updated to have role = null
        verify(staffService).updateAll(argThat(list -> {
            boolean johnUnassigned = list.stream().anyMatch(s -> s.getEmail().equals("john@test.com") && s.getRole() == null);
            boolean janeAssigned = list.stream().anyMatch(s -> s.getEmail().equals("jane@test.com") && s.getRole().getId().equals(1L));
            return johnUnassigned && janeAssigned;
        }));
    }

    @Test
    void import_ShouldFail_WhenEmailTooLong() throws Exception {
        String longEmail = "a".repeat(250) + "@test.com"; // > 255 chars
        List<String[]> data = new ArrayList<>(Collections.singleton(new String[]{"IT Dept", "Developer", longEmail}));
        byte[] excelBytes = createExcelFromData(data);
        MockMultipartFile file = new MockMultipartFile("file", "import.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", excelBytes);

        // Basic setup to reach validation logic
        when(orgChartService.findAllByIsDeletedIsFalse()).thenReturn(List.of(mockOrg));
        when(roleService.getAllByDeletedIsFalse()).thenReturn(List.of(mockRole));
        when(staffService.findAllByIsDeletedIsFalse()).thenReturn(Collections.emptyList());
        when(validationService.isNullOrBlank(any())).thenReturn(false);

        mockMvc.perform(multipart("/api/role/assignment/import").file(file).principal(authentication))
                .andExpect(status().isBadRequest()); // IMPORT_INVALID_DATA_ERR_MSG_CODE
    }

    @Test
    void import_ShouldHandle_NullCells() throws Exception {
        // Manually create a row where the 3rd cell (Email) is explicitly null (never created)
        byte[] excelBytes;
        try (Workbook workbook = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sheet = workbook.createSheet("Sheet 1");
            Row header = sheet.createRow(0);
            String[] headers = {"Department Name", "Role Name", "Staff Email"};
            for (int i = 0; i < headers.length; i++) header.createCell(i).setCellValue(headers[i]);

            Row row = sheet.createRow(1);
            row.createCell(0).setCellValue("IT Dept");
            row.createCell(1).setCellValue("Developer");
            // Cell 2 is SKIPPED (Null)

            workbook.write(out);
            excelBytes = out.toByteArray();
        }

        MockMultipartFile file = new MockMultipartFile("file", "nullcell.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", excelBytes);

        when(orgChartService.findAllByIsDeletedIsFalse()).thenReturn(List.of(mockOrg));
        when(roleService.getAllByDeletedIsFalse()).thenReturn(List.of(mockRole));

        // Mock to allow "null" emails to result in empty list (skipping processing)
        when(validationService.isNullOrBlank(any())).thenAnswer(inv -> {
            String s = inv.getArgument(0);
            return s == null || s.trim().isEmpty();
        });

        mockMvc.perform(multipart("/api/role/assignment/import").file(file).principal(authentication))
                .andExpect(status().isOk());
    }

    // --- Helpers ---

    private byte[] createExcelFromData(List<String[]> rowsData) throws IOException {
        String[] headers = {"Department Name", "Role Name", "Staff Email"};
        return createExcel(headers, rowsData);
    }

    private byte[] createExcelWithHeaders(String[] headers) throws IOException {
        return createExcel(headers, Collections.emptyList());
    }

    private byte[] createExcel(String[] headers, List<String[]> rowsData) throws IOException {
        try (Workbook workbook = new XSSFWorkbook();
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sheet = workbook.createSheet("Sheet 1");

            Row header = sheet.createRow(0);
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