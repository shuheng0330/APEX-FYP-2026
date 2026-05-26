package com.tbm.careerpathlearning.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tbm.careerpathlearning.dto.*;
import com.tbm.careerpathlearning.exception.BadRequestException;
import com.tbm.careerpathlearning.model.CareerPathwayRoleId;
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
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(controllers = CareerPathwayAssignmentController.class)
@AutoConfigureMockMvc(addFilters = false)
class CareerPathwayAssignmentControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private CareerPathwayService careerPathwayService;
    @MockitoBean
    private ValidationService validationService;
    @MockitoBean
    private MessageSource messageSource;
    @MockitoBean
    private StaffService staffService;
    @MockitoBean
    private TrackService trackService;
    @MockitoBean
    private CareerPathwayTrackService careerPathwayTrackService;
    @MockitoBean
    private CareerPathwayRoleService careerPathwayRoleService;
    @MockitoBean
    private OrgChartService orgChartService;
    @MockitoBean
    private TokenService tokenService; // Required for Security Config

    private Authentication authentication;
    private final UUID USER_UUID = UUID.randomUUID();
    private final UUID STAFF_UUID_1 = UUID.randomUUID();
    private final UUID STAFF_UUID_2 = UUID.randomUUID();

    private OrgChartDto mockOrg;
    private RoleDto roleRoot;
    private RoleDto roleChild;
    private CareerPathwayDto mockPathway;
    private StaffDto staff1;
    private StaffDto staff2;

    @BeforeEach
    void setUp() {
        authentication = mock(Authentication.class);
        when(authentication.getName()).thenReturn(USER_UUID.toString());
        when(authentication.getPrincipal()).thenReturn(USER_UUID.toString());

        // Setup Org and Roles
        mockOrg = new OrgChartDto();
        mockOrg.setId(10L);
        mockOrg.setName("IT Dept");

        roleRoot = new RoleDto();
        roleRoot.setId(100L);
        roleRoot.setName("Manager");
        roleRoot.setOrgChart(mockOrg);
        roleChild = new RoleDto();
        roleChild.setId(200L);
        roleChild.setName("Developer");
        roleChild.setOrgChart(mockOrg);

        // Setup Pathway
        mockPathway = new CareerPathwayDto();
        mockPathway.setId(1L);
        mockPathway.setName("Software Engineering");
        mockPathway.setOrgChart(mockOrg);
        mockPathway.setRootRole(roleRoot);

        // Setup Staff
        staff1 = new StaffDto();
        staff1.setId(STAFF_UUID_1);
        staff1.setEmail("dev@test.com");
        staff1.setRole(roleChild); // Compatible role
        // NOTE: Defaulting to Assigned for GET tests, but will override for Assign tests
        staff1.setCareerPathway(mockPathway);

        staff2 = new StaffDto();
        staff2.setId(STAFF_UUID_2);
        staff2.setEmail("mgr@test.com");
        staff2.setRole(roleRoot); // Compatible role
        staff2.setCareerPathway(null);

        lenient().when(messageSource.getMessage(any(), any(), any(Locale.class))).thenReturn("Mock Message");

        lenient().when(validationService.isNullOrBlank(any())).thenAnswer(inv -> {
            String s = inv.getArgument(0);
            return s == null || s.trim().isEmpty();
        });
    }

    // --- GET /overview ---

    @Test
    void overview_ShouldReturnAssignments() throws Exception {
        when(careerPathwayService.getAllByIsDeletedIsFalse()).thenReturn(List.of(mockPathway));
        when(staffService.findAllByIsDeletedIsFalse()).thenReturn(List.of(staff1));

        mockMvc.perform(get("/api/career-pathway-assignment/overview").principal(authentication))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].careerPathwayName").value("Software Engineering"))
                .andExpect(jsonPath("$[0].staffs[0].email").value("dev@test.com"));
    }

    // --- POST / (Assign) ---

    @Test
    void assign_ShouldSuccess_WhenRoleCompatible() throws Exception {
        AssignCareerPathwayRequestDto req = new AssignCareerPathwayRequestDto();
        req.setCareerPathwayId(1L);
        req.setStaffIds(new HashSet<>(Collections.singleton(STAFF_UUID_1)));

        // Setup: Staff 1 has NO pathway initially
        staff1.setCareerPathway(null);

        when(careerPathwayService.getById(1L)).thenReturn(mockPathway);

        // Relation: Root -> Child
        CareerPathwayRoleDto relation = new CareerPathwayRoleDto();
        relation.setParentRole(roleRoot);
        relation.setChildRole(roleChild);
        when(careerPathwayRoleService.getAllByCareerPathwayId(1L)).thenReturn(List.of(relation));

        when(staffService.findAllByIdIn(anySet())).thenReturn(List.of(staff1));

        mockMvc.perform(post("/api/career-pathway-assignment")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req))
                        .principal(authentication))
                .andExpect(status().isOk());

        verify(staffService).updateCareerPathwayByIdIn(anySet(), eq(mockPathway), any(), any());
    }

    @Test
    void assign_ShouldFail_WhenRoleNotCompatible() throws Exception {
        AssignCareerPathwayRequestDto req = new AssignCareerPathwayRequestDto();
        req.setCareerPathwayId(1L);
        req.setStaffIds(new HashSet<>(Collections.singleton(STAFF_UUID_1)));

        // Staff has incompatible role
        RoleDto unknownRole = new RoleDto();
        unknownRole.setId(999L);
        staff1.setRole(unknownRole);

        when(careerPathwayService.getById(1L)).thenReturn(mockPathway);
        CareerPathwayRoleDto relation = new CareerPathwayRoleDto();
        relation.setParentRole(roleRoot);
        relation.setChildRole(roleChild);
        when(careerPathwayRoleService.getAllByCareerPathwayId(1L)).thenReturn(List.of(relation));

        when(staffService.findAllByIdIn(anySet())).thenReturn(List.of(staff1));

        mockMvc.perform(post("/api/career-pathway-assignment")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req))
                        .principal(authentication))
                .andExpect(status().isBadRequest());
    }

    @Test
    void assign_ShouldFail_WhenRequestIsInvalid() throws Exception {
        AssignCareerPathwayRequestDto req = new AssignCareerPathwayRequestDto();
        // Missing IDs

        mockMvc.perform(post("/api/career-pathway-assignment")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req))
                        .principal(authentication))
                .andExpect(status().isBadRequest());
    }

    // --- PUT /edit (Update) ---

    @Test
    void update_ShouldHandleAddAndRemove() throws Exception {
        AssignCareerPathwayRequestDto req = new AssignCareerPathwayRequestDto();
        req.setCareerPathwayId(1L);
        // Input only Staff 2. Expected: Add Staff 2, Remove Staff 1.
        req.setStaffIds(new HashSet<>(Collections.singleton(STAFF_UUID_2)));

        when(careerPathwayService.getById(1L)).thenReturn(mockPathway);

        CareerPathwayRoleDto relation = new CareerPathwayRoleDto();
        relation.setParentRole(roleRoot);
        relation.setChildRole(roleChild);
        when(careerPathwayRoleService.getAllByCareerPathwayId(1L)).thenReturn(List.of(relation));

        when(staffService.findAllByIdIn(new HashSet<>(Collections.singleton(STAFF_UUID_2)))).thenReturn(List.of(staff2));

        // Existing Assignment: Staff 1
        when(staffService.findAllByCareerPathwayId(1L)).thenReturn(List.of(staff1));

        mockMvc.perform(put("/api/career-pathway-assignment/edit")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req))
                        .principal(authentication))
                .andExpect(status().isOk());

        verify(staffService).updateCareerPathwayByIdIn(
                argThat(ids -> ids.contains(STAFF_UUID_2)), eq(mockPathway), any(), any());

        verify(staffService).updateCareerPathwayByIdIn(
                argThat(ids -> ids.contains(STAFF_UUID_1)), eq(null), any(), any());
    }

    @Test
    void update_ShouldDoNothing_WhenNoChangesRequested() throws Exception {
        AssignCareerPathwayRequestDto req = new AssignCareerPathwayRequestDto();
        req.setCareerPathwayId(1L);
        req.setStaffIds(new HashSet<>(Collections.singleton(STAFF_UUID_1))); // Input: Staff 1

        // Current State: Staff 1 is ALREADY assigned
        when(careerPathwayService.getById(1L)).thenReturn(mockPathway);
        when(staffService.findAllByIdIn(anySet())).thenReturn(List.of(staff1));
        when(staffService.findAllByCareerPathwayId(1L)).thenReturn(List.of(staff1));

        // Mock role validation to pass
        CareerPathwayRoleDto relation = new CareerPathwayRoleDto();
        relation.setParentRole(roleRoot); relation.setChildRole(roleChild);
        when(careerPathwayRoleService.getAllByCareerPathwayId(1L)).thenReturn(List.of(relation));

        mockMvc.perform(put("/api/career-pathway-assignment/edit")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req))
                        .principal(authentication))
                .andExpect(status().isOk());

        // Verify NO updates called because ToAdd and ToRemove sets are empty
        verify(staffService, never()).updateCareerPathwayByIdIn(anySet(), any(), any(), any());
    }

    // --- DELETE /delete ---

    @Test
    void delete_ShouldUnassignAllStaff() throws Exception {
        when(staffService.findAllByCareerPathwayId(1L)).thenReturn(List.of(staff1, staff2));

        mockMvc.perform(delete("/api/career-pathway-assignment/delete")
                        .param("selectedCareerPathwayId", "1")
                        .principal(authentication))
                .andExpect(status().isOk());

        verify(staffService).updateCareerPathwayByIdIn(anySet(), eq(null), any(), any());
    }

    @Test
    void bulkDelete_ShouldUnassignMultiplePathways() throws Exception {
        Set<Long> pathwayIds = Set.of(1L, 2L);
        StaffDto s1 = new StaffDto(); s1.setId(UUID.randomUUID());
        StaffDto s2 = new StaffDto(); s2.setId(UUID.randomUUID());

        when(staffService.findAllByCareerPathwayIdIn(pathwayIds)).thenReturn(List.of(s1, s2));

        mockMvc.perform(delete("/api/career-pathway-assignment/bulk-delete")
                        .param("selectedCareerPathwayIds", "1,2")
                        .principal(authentication))
                .andExpect(status().isOk());

        verify(staffService).updateCareerPathwayByIdIn(
                argThat(ids -> ids.size() == 2),
                eq(null), // Confirm unassignment
                any(),
                any()
        );
    }

    // --- GET /export ---

    @Test
    void export_ShouldReturnExcelFile() throws Exception {
        when(careerPathwayService.getAllByIsDeletedIsFalse()).thenReturn(List.of(mockPathway));
        when(staffService.findAllByIsDeletedIsFalse()).thenReturn(List.of(staff1));

        mockMvc.perform(get("/api/career-pathway-assignment/export").principal(authentication))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=Exported_Career_Pathway_Assignment_Data.xlsx"));
    }

    // --- POST /import ---

    @Test
    void import_ShouldAssignStaff_WhenValid() throws Exception {
        // Headers: Dept Name, Pathway Name, Staff Email
        List<String[]> data = new ArrayList<>(Collections.singleton(
                new String[]{
                        "IT Dept", "Software Engineering", "dev@test.com"
                }));

        byte[] excelBytes = createExcelFromData(data);
        MockMultipartFile file = new MockMultipartFile("file", "import.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", excelBytes);

        // FIX: Ensure staff1 is NOT assigned yet, so controller detects a change
        staff1.setCareerPathway(null);

        when(orgChartService.findAllByIsDeletedIsFalse()).thenReturn(List.of(mockOrg));
        when(careerPathwayService.getAllByIsDeletedIsFalse()).thenReturn(List.of(mockPathway));
        when(staffService.findAllByIsDeletedIsFalse()).thenReturn(List.of(staff1));

        // Mock Pathway Relationships
        CareerPathwayRoleDto relation = new CareerPathwayRoleDto();
        relation.setId(new CareerPathwayRoleId(1L, 100L, 200L));
        relation.setParentRole(roleRoot);
        relation.setChildRole(roleChild);
        when(careerPathwayRoleService.getAll()).thenReturn(List.of(relation));

        mockMvc.perform(multipart("/api/career-pathway-assignment/import").file(file).principal(authentication))
                .andExpect(status().isOk());

        // Verify updateAll is called to assign pathway
        verify(staffService).updateAll(argThat(list ->
                !list.isEmpty() && list.get(0).getCareerPathway().getId().equals(1L)
        ));
    }

    @Test
    void import_ShouldUnassign_WhenUserReplacedInFile() throws Exception {
        // Scenario: File contains "mgr@test.com". "dev@test.com" is currently assigned but missing from file.
        // Result: Mgr added, Dev removed.
        List<String[]> data = new ArrayList<>(Collections.singleton(
                new String[]{"IT Dept", "Software Engineering", "mgr@test.com"}
        ));

        byte[] excelBytes = createExcelFromData(data);
        MockMultipartFile file = new MockMultipartFile("file", "import.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", excelBytes);

        when(orgChartService.findAllByIsDeletedIsFalse()).thenReturn(List.of(mockOrg));
        when(careerPathwayService.getAllByIsDeletedIsFalse()).thenReturn(List.of(mockPathway));

        // Staff 1 is assigned (should be removed). Staff 2 is null (should be assigned).
        staff1.setCareerPathway(mockPathway);
        staff2.setCareerPathway(null);
        when(staffService.findAllByIsDeletedIsFalse()).thenReturn(List.of(staff1, staff2));

        CareerPathwayRoleDto relation = new CareerPathwayRoleDto();
        relation.setId(new CareerPathwayRoleId(1L, 100L, 200L));
        relation.setParentRole(roleRoot);
        relation.setChildRole(roleChild);
        when(careerPathwayRoleService.getAll()).thenReturn(List.of(relation));

        mockMvc.perform(multipart("/api/career-pathway-assignment/import").file(file).principal(authentication))
                .andExpect(status().isOk());

        // Verify updateAll is called for BOTH users
        verify(staffService).updateAll(argThat(list -> {
            boolean staff1Removed = list.stream().anyMatch(s -> s.getId().equals(STAFF_UUID_1) && s.getCareerPathway() == null);
            boolean staff2Added = list.stream().anyMatch(s -> s.getId().equals(STAFF_UUID_2) && s.getCareerPathway().getId().equals(1L));
            return staff1Removed && staff2Added;
        }));
    }

    @Test
    void import_ShouldFail_WhenPathwayNotFound() throws Exception {
        List<String[]> data = new ArrayList<>(Collections.singleton(new String[]{"IT Dept", "Ghost Pathway", "dev@test.com"}));
        byte[] excelBytes = createExcelFromData(data);
        MockMultipartFile file = new MockMultipartFile("file", "import.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", excelBytes);

        when(orgChartService.findAllByIsDeletedIsFalse()).thenReturn(List.of(mockOrg));
        when(careerPathwayService.getAllByIsDeletedIsFalse()).thenReturn(List.of(mockPathway));
        when(staffService.findAllByIsDeletedIsFalse()).thenReturn(List.of(staff1));

        mockMvc.perform(multipart("/api/career-pathway-assignment/import").file(file).principal(authentication))
                .andExpect(status().isBadRequest());
    }

    @Test
    void import_ShouldFail_WhenDuplicateEmailsInSameRow() throws Exception {
        // "dev@test.com" appears twice in the same cell
        List<String[]> data = new ArrayList<>(Collections.singleton(
                new String[]{
                        "IT Dept", "Software Engineering", "dev@test.com; dev@test.com"
                }));

        byte[] excelBytes = createExcelFromData(data);
        MockMultipartFile file = new MockMultipartFile("file", "import.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", excelBytes);

        // Mocks needed to pass early checks
        when(orgChartService.findAllByIsDeletedIsFalse()).thenReturn(List.of(mockOrg));
        when(careerPathwayService.getAllByIsDeletedIsFalse()).thenReturn(List.of(mockPathway));
        when(staffService.findAllByIsDeletedIsFalse()).thenReturn(List.of(staff1)); // Staff exists

        mockMvc.perform(multipart("/api/career-pathway-assignment/import").file(file).principal(authentication))
                .andExpect(status().isBadRequest()); // IMPORT_DUPLICATE_ENTRY_ERR_MSG_CODE
    }

    @Test
    void import_ShouldFail_WhenDuplicateRowDefinition() throws Exception {
        // Two rows defining assignments for the SAME Pathway
        List<String[]> data = new ArrayList<>();
        data.add(new String[]{"IT Dept", "Software Engineering", "dev@test.com"});
        data.add(new String[]{"IT Dept", "Software Engineering", "mgr@test.com"}); // Duplicate definition

        byte[] excelBytes = createExcelFromData(data);
        MockMultipartFile file = new MockMultipartFile("file", "import.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", excelBytes);

        when(orgChartService.findAllByIsDeletedIsFalse()).thenReturn(List.of(mockOrg));
        when(careerPathwayService.getAllByIsDeletedIsFalse()).thenReturn(List.of(mockPathway));
        when(staffService.findAllByIsDeletedIsFalse()).thenReturn(List.of(staff1, staff2));

        mockMvc.perform(multipart("/api/career-pathway-assignment/import").file(file).principal(authentication))
                .andExpect(status().isBadRequest()); // IMPORT_DUPLICATE_ENTRY_ERR_MSG_CODE
    }

    @Test
    void import_ShouldFail_WhenStaffEmailNotFound() throws Exception {
        List<String[]> data = new ArrayList<>(Collections.singleton(
                new String[]{
                        "IT Dept", "Software Engineering", "ghost@test.com"
                }));

        byte[] excelBytes = createExcelFromData(data);
        MockMultipartFile file = new MockMultipartFile("file", "import.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", excelBytes);

        // Mocks (Staff list does NOT contain ghost@test.com)
        when(orgChartService.findAllByIsDeletedIsFalse()).thenReturn(List.of(mockOrg));
        when(careerPathwayService.getAllByIsDeletedIsFalse()).thenReturn(List.of(mockPathway));
        when(staffService.findAllByIsDeletedIsFalse()).thenReturn(List.of(staff1));

        mockMvc.perform(multipart("/api/career-pathway-assignment/import").file(file).principal(authentication))
                .andExpect(status().isBadRequest()); // IMPORT_INVALID_DATA_ERR_MSG_CODE
    }

    @Test
    void import_ShouldFail_WhenDepartmentNotFound() throws Exception {
        List<String[]> data = new ArrayList<>(Collections.singleton(
                new String[]{
                        "Unknown Dept", "Software Engineering", "dev@test.com"
                }));

        byte[] excelBytes = createExcelFromData(data);
        MockMultipartFile file = new MockMultipartFile("file", "import.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", excelBytes);

        when(orgChartService.findAllByIsDeletedIsFalse()).thenReturn(List.of(mockOrg)); // Only "IT Dept" exists
        when(careerPathwayService.getAllByIsDeletedIsFalse()).thenReturn(List.of(mockPathway));

        mockMvc.perform(multipart("/api/career-pathway-assignment/import").file(file).principal(authentication))
                .andExpect(status().isBadRequest()); // IMPORT_DATA_NOT_FOUND_ERR_MSG_CODE
    }

    @Test
    void import_ShouldHandle_NumericCells() throws Exception {
        // Manually create Excel with numeric cell
        byte[] excelBytes;
        try (Workbook workbook = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sheet = workbook.createSheet("Sheet 1");
            Row header = sheet.createRow(0);
            header.createCell(0).setCellValue("Department Name");
            header.createCell(1).setCellValue("Career Pathway Name");
            header.createCell(2).setCellValue("Staff Email");

            Row row = sheet.createRow(1);
            row.createCell(0).setCellValue(101); // Numeric Dept Name ("101")
            row.createCell(1).setCellValue(true); // Boolean Pathway Name ("true")
            row.createCell(2).setCellValue("dev@test.com");

            workbook.write(out);
            excelBytes = out.toByteArray();
        }

        MockMultipartFile file = new MockMultipartFile("file", "types.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", excelBytes);

        // Mocks must match the converted string values "101" and "true"
        OrgChartDto numOrg = new OrgChartDto(); numOrg.setId(99L); numOrg.setName("101");
        CareerPathwayDto boolPath = new CareerPathwayDto(); boolPath.setId(88L); boolPath.setName("true"); boolPath.setOrgChart(numOrg);
        boolPath.setRootRole(roleRoot); // Ensure root role is present

        when(orgChartService.findAllByIsDeletedIsFalse()).thenReturn(List.of(numOrg));
        when(careerPathwayService.getAllByIsDeletedIsFalse()).thenReturn(List.of(boolPath));
        when(staffService.findAllByIsDeletedIsFalse()).thenReturn(List.of(staff1));

        // Mock Roles for compatibility check
        CareerPathwayRoleDto relation = new CareerPathwayRoleDto();
        relation.setId(new CareerPathwayRoleId(88L, 100L, 200L));
        relation.setParentRole(roleRoot); relation.setChildRole(roleChild);
        when(careerPathwayRoleService.getAll()).thenReturn(List.of(relation));

        mockMvc.perform(multipart("/api/career-pathway-assignment/import").file(file).principal(authentication))
                .andExpect(status().isOk());

        // Verify update happened
        verify(staffService).updateAll(anyList());
    }

    @Test
    void import_ShouldFail_WhenStaffHasNoRole() throws Exception {
        // Staff "new@test.com" exists but has NO role assigned
        StaffDto rolelessStaff = new StaffDto();
        rolelessStaff.setId(UUID.randomUUID());
        rolelessStaff.setEmail("new@test.com");
        rolelessStaff.setRole(null); // No Role

        List<String[]> data = new ArrayList<>(Collections.singleton(new String[]{ "IT Dept", "Software Engineering", "new@test.com" }));
        byte[] excelBytes = createExcelFromData(data);
        MockMultipartFile file = new MockMultipartFile("file", "import.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", excelBytes);

        when(orgChartService.findAllByIsDeletedIsFalse()).thenReturn(List.of(mockOrg));
        when(careerPathwayService.getAllByIsDeletedIsFalse()).thenReturn(List.of(mockPathway));
        when(staffService.findAllByIsDeletedIsFalse()).thenReturn(List.of(rolelessStaff));

        // Mock Roles
        CareerPathwayRoleDto relation = new CareerPathwayRoleDto();
        relation.setId(new CareerPathwayRoleId(1L, 100L, 200L));
        relation.setParentRole(roleRoot); relation.setChildRole(roleChild);
        when(careerPathwayRoleService.getAll()).thenReturn(List.of(relation));

        mockMvc.perform(multipart("/api/career-pathway-assignment/import").file(file).principal(authentication))
                .andExpect(status().isBadRequest()); // IMPORT_CAREER_PATHWAY_OUTSIDE_ERR_MSG_CODE
    }

    @Test
    void import_ShouldFail_WhenStaffRoleIsNotInPathway() throws Exception {
        // Staff has "HR Role" (ID 999), but Pathway is IT (IDs 100, 200)
        RoleDto hrRole = new RoleDto(); hrRole.setId(999L);
        StaffDto hrStaff = new StaffDto();
        hrStaff.setId(UUID.randomUUID());
        hrStaff.setEmail("hr@test.com");
        hrStaff.setRole(hrRole);

        List<String[]> data = new ArrayList<>(Collections.singleton(new String[]{ "IT Dept", "Software Engineering", "hr@test.com" }));
        byte[] excelBytes = createExcelFromData(data);
        MockMultipartFile file = new MockMultipartFile("file", "import.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", excelBytes);

        when(orgChartService.findAllByIsDeletedIsFalse()).thenReturn(List.of(mockOrg));
        when(careerPathwayService.getAllByIsDeletedIsFalse()).thenReturn(List.of(mockPathway));
        when(staffService.findAllByIsDeletedIsFalse()).thenReturn(List.of(hrStaff));

        CareerPathwayRoleDto relation = new CareerPathwayRoleDto();
        relation.setId(new CareerPathwayRoleId(1L, 100L, 200L));
        relation.setParentRole(roleRoot); relation.setChildRole(roleChild);
        when(careerPathwayRoleService.getAll()).thenReturn(List.of(relation));

        mockMvc.perform(multipart("/api/career-pathway-assignment/import").file(file).principal(authentication))
                .andExpect(status().isBadRequest()); // IMPORT_CAREER_PATHWAY_OUTSIDE_ERR_MSG_CODE
    }

    private byte[] createExcelFromData(List<String[]> rowsData) throws IOException {
        try (Workbook workbook = new XSSFWorkbook();
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sheet = workbook.createSheet("Sheet 1");
            Row header = sheet.createRow(0);
            String[] headers = {"Department Name", "Career Pathway Name", "Staff Email"};
            for (int i = 0; i < headers.length; i++) header.createCell(i).setCellValue(headers[i]);

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