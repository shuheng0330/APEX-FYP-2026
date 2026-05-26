package com.tbm.careerpathlearning.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tbm.careerpathlearning.dto.*;
import com.tbm.careerpathlearning.enums.AuthorityName;
import com.tbm.careerpathlearning.enums.OrgChartType;
import com.tbm.careerpathlearning.enums.RelationType;
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
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.core.Authentication;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.*;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(controllers = OrgChartController.class)
@AutoConfigureMockMvc(addFilters = false)
class OrgChartControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private OrgChartService orgChartService;
    @MockitoBean
    private ParentChildNodeService parentChildNodeService;
    @MockitoBean
    private RoleService roleService;
    @MockitoBean
    private StaffService staffService;
    @MockitoBean
    private StaffProfileService staffProfileService;
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
    private OrgChartDto mockDept;

    @BeforeEach
    void setUp() {
        userUUID = UUID.randomUUID();
        authentication = mock(Authentication.class);
        when(authentication.getName()).thenReturn(userUUID.toString());
        when(authentication.getPrincipal()).thenReturn(userUUID.toString());

        mockDept = new OrgChartDto();
        mockDept.setId(1L);
        mockDept.setName("IT Dept");
        mockDept.setType(OrgChartType.D);
        mockDept.setDeleted(false);

        lenient().when(messageSource.getMessage(any(), any(), any(Locale.class))).thenReturn("Mock Error");
    }

    // --- 1. Get All Org Chart ---

    @Test
    void getAllOrgChart_ShouldReturnList() throws Exception {
        when(orgChartService.findAllByIsDeletedIsFalse()).thenReturn(List.of(mockDept));

        mockMvc.perform(get("/api/orgChart")
                        .principal(authentication))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.size()").value(1));
    }

    // --- 2. Show Org Chart ---

    @Test
    void showOrgChart_ShouldBuildHierarchy() throws Exception {
        OrgChartDto child = new OrgChartDto();
        child.setId(2L);
        child.setName("Dev Team");
        child.setType(OrgChartType.D);

        ParentChildNodeDto relation = new ParentChildNodeDto();
        relation.setParentId(1L);
        relation.setChildId(2L);

        when(orgChartService.findAllByIsDeletedIsFalse()).thenReturn(List.of(mockDept, child));
        when(parentChildNodeService.findAllByRelationType(RelationType.ORG_CHART)).thenReturn(List.of(relation));
        when(roleService.getAllByDeletedIsFalse()).thenReturn(Collections.emptyList());
        when(staffService.findAllByIsDeletedIsFalse()).thenReturn(Collections.emptyList());

        mockMvc.perform(get("/api/orgChart/show-org-chart")
                        .principal(authentication))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(1))
                .andExpect(jsonPath("$[0].children[0].id").value(2));
    }

    @Test
    void showOrgChart_ShouldPopulateDetailedNodes() throws Exception {
        // 1. Dept Node
        OrgChartDto dept = new OrgChartDto();
        dept.setId(1L);
        dept.setName("IT");
        dept.setType(OrgChartType.D);

        // 2. Person Node (Linked to Dept)
        OrgChartDto personNode = new OrgChartDto();
        personNode.setId(2L);
        personNode.setName("Manager");
        personNode.setType(OrgChartType.P);

        // 3. Relationships
        ParentChildNodeDto rel = new ParentChildNodeDto();
        rel.setParentId(1L);
        rel.setChildId(2L); // IT -> Manager

        // 4. Role Linked to Person Node
        RoleDto role = new RoleDto();
        role.setId(10L);
        role.setName("Manager Role");
        OrgChartDto roleOrgLink = new OrgChartDto();
        roleOrgLink.setId(2L); // Link to Person Node ID
        role.setOrgChart(roleOrgLink);

        // 5. Staff Linked to Role
        UUID staffID = UUID.randomUUID();
        StaffDto staff = new StaffDto();
        staff.setId(staffID);
        staff.setName("John Doe");
        staff.setEmail("john@test.com");
        staff.setRole(role);

        // 6. Staff Profile
        StaffProfileDto profile = new StaffProfileDto();
        profile.setStaffId(staffID);
        profile.setProfilePicturePath("/img/pic.png");

        // Mocks
        when(orgChartService.findAllByIsDeletedIsFalse()).thenReturn(List.of(dept, personNode));
        when(parentChildNodeService.findAllByRelationType(RelationType.ORG_CHART)).thenReturn(List.of(rel));
        when(roleService.getAllByDeletedIsFalse()).thenReturn(List.of(role));
        when(staffService.findAllByIsDeletedIsFalse()).thenReturn(List.of(staff));
        when(staffProfileService.findById(staffID)).thenReturn(profile);

        mockMvc.perform(get("/api/orgChart/show-org-chart").principal(authentication))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name").value("IT"))
                .andExpect(jsonPath("$[0].data.type").value("D"))
                .andExpect(jsonPath("$[0].children[0].name").value("Manager"))
                .andExpect(jsonPath("$[0].children[0].data.type").value("P"))
                .andExpect(jsonPath("$[0].children[0].data.personNode.staffName").value("John Doe"))
                .andExpect(jsonPath("$[0].children[0].data.personNode.roleName").value("Manager Role"))
                .andExpect(jsonPath("$[0].children[0].data.personNode.profileUrl").value("/img/pic.png"));
    }

    // --- 3. Get Departments ---

    @Test
    void getDepartmentList_ShouldReturnList() throws Exception {
        when(orgChartService.getDepartments()).thenReturn(List.of(mockDept));

        mockMvc.perform(get("/api/orgChart/get-departments")
                        .principal(authentication))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].type").value("D"));
    }

    // --- 4. Export Org Chart ---

    @Test
    void exportOrgChartData_ShouldReturnExcelFile() throws Exception {
        when(orgChartService.findAllByIsDeletedIsFalse()).thenReturn(List.of(mockDept));
        when(parentChildNodeService.findAllByRelationType(RelationType.ORG_CHART)).thenReturn(Collections.emptyList());

        mockMvc.perform(get("/api/orgChart/export")
                        .principal(authentication))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=Exported_Organizational_Chart_Data.xlsx"));
    }

    @Test
    void exportOrgChartData_ShouldReturn500_WhenServiceFails() throws Exception {
        when(orgChartService.findAllByIsDeletedIsFalse()).thenThrow(new RuntimeException("DB Error"));

        mockMvc.perform(get("/api/orgChart/export").principal(authentication))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.message").value("Mock Error"));
    }

    // --- 5. Import Org Chart (Sad Paths) ---

    @Test
    void importOrgChartData_ShouldFail_WhenFileIsEmpty() throws Exception {
        MockMultipartFile emptyFile = new MockMultipartFile("file", "test.xlsx", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", new byte[0]);

        mockMvc.perform(multipart("/api/orgChart/import")
                        .file(emptyFile)
                        .principal(authentication))
                .andExpect(status().isBadRequest());
    }

    @Test
    void importOrgChartData_ShouldFail_WhenInvalidContentType() throws Exception {
        MockMultipartFile txtFile = new MockMultipartFile("file", "test.txt", "text/plain", "content".getBytes());

        mockMvc.perform(multipart("/api/orgChart/import")
                        .file(txtFile)
                        .principal(authentication))
                .andExpect(status().isBadRequest());
    }

    // --- 6. Import Org Chart (Happy Path) ---

    @Test
    void importOrgChartData_ShouldProcessValidExcel() throws Exception {
        byte[] excelBytes = createValidExcelFile();
        MockMultipartFile validFile = new MockMultipartFile(
                "file",
                "import.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                excelBytes
        );

        when(orgChartService.findAllByIsDeletedIsFalse()).thenReturn(Collections.emptyList());

        // Make validation logic functional instead of hardcoding 'false'
        when(validationService.isNullOrBlank(any())).thenAnswer(invocation -> {
            String arg = invocation.getArgument(0);
            return arg == null || arg.trim().isEmpty();
        });

        when(orgChartService.createAndUpdateAll(anyList())).thenReturn(List.of(mockDept));
        AuthorityDto mockAuth = new AuthorityDto();
        mockAuth.setId(1L);
        when(authorityService.findByName(AuthorityName.ROLE_USER)).thenReturn(mockAuth);

        mockMvc.perform(multipart("/api/orgChart/import")
                        .file(validFile)
                        .principal(authentication))
                .andExpect(status().isOk());
    }

    @Test
    void importOrgChartData_ShouldFail_WhenHeadersAreInvalid() throws Exception {
        byte[] excelBytes;
        try (Workbook workbook = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sheet = workbook.createSheet();
            Row row = sheet.createRow(0);
            row.createCell(0).setCellValue("Wrong Header"); // Invalid Header
            workbook.write(out);
            excelBytes = out.toByteArray();
        }

        MockMultipartFile file = new MockMultipartFile("file", "test.xlsx", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", excelBytes);

        mockMvc.perform(multipart("/api/orgChart/import").file(file).principal(authentication))
                .andExpect(status().isBadRequest());
    }

    @Test
    void importOrgChartData_ShouldFail_WhenSelfReferenceExists() throws Exception {
        // Node A reports Parent as A
        List<String[]> data = new ArrayList<>(Collections.singleton(new String[]{"D", "Dept A", "Dept A", "", ""}));
        MockMultipartFile file = new MockMultipartFile("file", "import.xlsx", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", createExcelFromData(data));

        // Mock validation
        when(orgChartService.findAllByIsDeletedIsFalse()).thenReturn(Collections.emptyList());
        when(validationService.isNullOrBlank(any())).thenAnswer(inv -> {
            String s = inv.getArgument(0);
            return s == null || s.trim().isEmpty();
        });

        mockMvc.perform(multipart("/api/orgChart/import").file(file).principal(authentication))
                .andExpect(status().isBadRequest()); // IMPORT_RECURSIVE_REFERENCE_ERR_MSG_CODE
    }

    @Test
    void importOrgChartData_ShouldFail_WhenCycleDetected() throws Exception {
        // A -> B, B -> A
        List<String[]> data = List.of(
                new String[]{"D", "Dept A", "", "", ""},   // A is Root (initially)
                new String[]{"D", "Dept B", "Dept A", "", ""}, // B is child of A
                // Wait, to test cycle properly in "validateNoCycles", we need input structure:
                // Let's try: A -> B, B -> C, C -> A
                new String[]{"D", "Dept A", "Dept C", "", ""},
                new String[]{"D", "Dept B", "Dept A", "", ""},
                new String[]{"D", "Dept C", "Dept B", "", ""}
        );

        MockMultipartFile file = new MockMultipartFile("file", "import.xlsx", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", createExcelFromData(data));

        when(orgChartService.findAllByIsDeletedIsFalse()).thenReturn(Collections.emptyList());
        when(validationService.isNullOrBlank(any())).thenAnswer(inv -> {
            String s = inv.getArgument(0);
            return s == null || s.trim().isEmpty();
        });

        mockMvc.perform(multipart("/api/orgChart/import").file(file).principal(authentication))
                .andExpect(status().isBadRequest()); // IMPORT_INVALID_HIERARCHY_ERR_MSG_CODE
    }

    @Test
    void importOrgChartData_ShouldFail_WhenActionConflict_DeleteAndRename() throws Exception {
        // Rename AND Delete set
        List<String[]> data = new ArrayList<>(Collections.singleton(new String[]{"D", "Dept A", "", "New Name", "Yes"}));
        MockMultipartFile file = new MockMultipartFile("file", "import.xlsx", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", createExcelFromData(data));

        when(validationService.isNullOrBlank(any())).thenAnswer(inv -> {
            String s = inv.getArgument(0);
            return s == null || s.trim().isEmpty();
        });

        mockMvc.perform(multipart("/api/orgChart/import").file(file).principal(authentication))
                .andExpect(status().isBadRequest()); // IMPORT_ACTION_CONFLICT_ERR_MSG_CODE
    }

    @Test
    void importOrgChartData_ShouldFail_WhenTypeChangeOnExistingRecord() throws Exception {
        // DB has Dept A as 'D', Input tries to set it as 'P'
        OrgChartDto existing = new OrgChartDto();
        existing.setId(1L);
        existing.setName("Dept A");
        existing.setType(OrgChartType.D); // Type D

        List<String[]> data = new ArrayList<>(Collections.singleton(new String[]{"P", "Dept A", "", "", ""})); // Input Type P
        MockMultipartFile file = new MockMultipartFile("file", "import.xlsx", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", createExcelFromData(data));

        when(orgChartService.findAllByIsDeletedIsFalse()).thenReturn(List.of(existing));
        when(validationService.isNullOrBlank(any())).thenAnswer(inv -> {
            String s = inv.getArgument(0);
            return s == null || s.trim().isEmpty();
        });

        mockMvc.perform(multipart("/api/orgChart/import").file(file).principal(authentication))
                .andExpect(status().isBadRequest()); // IMPORT_ORG_CHART_TYPE_CHANGE_ERR_MSG_CODE
    }

    @Test
    void importOrgChartData_ShouldHandle_Renames_And_RoleCreation() throws Exception {
        // Scenario:
        // 1. Existing "Dept A" -> Rename to "Dept Z"
        // 2. New "Manager" (Type P) -> Child of "Dept Z"

        OrgChartDto existing = new OrgChartDto();
        existing.setId(1L);
        existing.setName("Dept A");
        existing.setType(OrgChartType.D);
        existing.setRoot(true);

        List<String[]> data = List.of(
                new String[]{"D", "Dept A", "", "Dept Z", ""}, // Rename Existing
                new String[]{"P", "Manager", "Dept Z", "", ""} // New Person under Renamed Node
        );
        MockMultipartFile file = new MockMultipartFile("file", "import.xlsx", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", createExcelFromData(data));

        when(orgChartService.findAllByIsDeletedIsFalse()).thenReturn(List.of(existing));
        when(validationService.isNullOrBlank(any())).thenAnswer(inv -> {
            String s = inv.getArgument(0);
            return s == null || s.trim().isEmpty();
        });

        // Mock Returns
        when(orgChartService.createAndUpdateAll(anyList())).thenAnswer(inv -> {
            List<OrgChartDto> list = inv.getArgument(0);
            // Simulate that Dept A became Dept Z
            list.forEach(dto -> {
                if (dto.getName().equals("Dept Z")) dto.setId(1L);
                if (dto.getName().equals("Manager")) dto.setId(2L);
            });
            return list;
        });

        AuthorityDto auth = new AuthorityDto();
        auth.setId(99L);
        when(authorityService.findByName(AuthorityName.ROLE_USER)).thenReturn(auth);

        // Mock Role creation
        RoleDto newRole = new RoleDto();
        newRole.setId(10L);
        newRole.setName("Manager");
        when(roleService.createAll(anyList())).thenReturn(List.of(newRole));

        mockMvc.perform(multipart("/api/orgChart/import").file(file).principal(authentication))
                .andExpect(status().isOk());

        // Verifications
        verify(orgChartService).createAndUpdateAll(argThat(list ->
                list.stream().anyMatch(dto -> dto.getName().equals("Dept Z")) // Verify Rename logic
        ));
        verify(roleService).createAll(anyList()); // Verify Role creation for Type P
        verify(parentChildNodeService).createALl(anyList(), eq(RelationType.ORG_CHART)); // Verify Graph Linkage
    }

    @Test
    void importOrgChartData_ShouldHandle_Deletion_Of_NodesAndRoles() throws Exception {
        // Scenario: Delete "Dept A" (D) and "Manager" (P)
        OrgChartDto dept = new OrgChartDto();
        dept.setId(1L);
        dept.setName("Dept A");
        dept.setType(OrgChartType.D);
        OrgChartDto person = new OrgChartDto();
        person.setId(2L);
        person.setName("Manager");
        person.setType(OrgChartType.P);

        List<String[]> data = List.of(
                new String[]{"D", "Dept A", "", "", "Yes"},
                new String[]{"P", "Manager", "Dept A", "", "Yes"}
        );
        MockMultipartFile file = new MockMultipartFile("file", "import.xlsx", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", createExcelFromData(data));

        when(orgChartService.findAllByIsDeletedIsFalse()).thenReturn(List.of(dept, person));
        // Mock Validation to return true/false correctly
        when(validationService.isNullOrBlank(any())).thenAnswer(inv -> {
            String s = inv.getArgument(0);
            return s == null || s.trim().isEmpty();
        });

        // Mock Role Deletion Return
        RoleDto roleDto = new RoleDto();
        roleDto.setId(100L);
        when(roleService.findAndDeleteAllByOrgChartIdIn(anySet(), any(), any())).thenReturn(List.of(roleDto));

        mockMvc.perform(multipart("/api/orgChart/import").file(file).principal(authentication))
                .andExpect(status().isOk());

        // Verify Delete Calls
        // 1. Verify Nodes are deleted
        verify(orgChartService).deleteAllByIdIn(argThat(set -> set.contains(1L) && set.contains(2L)), any(), any());
        // 2. Verify Roles are removed (for Type P)
        verify(roleService).findAndDeleteAllByOrgChartIdIn(argThat(set -> set.contains(2L)), any(), any());
        // 3. Verify Cleanup of Role Authorities/Competencies
        verify(roleAuthorityService).deleteAllByRoleIdIn(argThat(set -> set.contains(100L)));
    }

    @Test
    void importOrgChartData_ShouldFail_When_NodeIsDefinedAs_BothRootAndChild() throws Exception {
        // Row 1: "Dept A" is Root (No Parent)
        // Row 2: "Dept A" is Child (Parent is "Dept B")
        List<String[]> data = List.of(
                new String[]{"D", "Dept A", "", "", ""},
                new String[]{"D", "Dept B", "", "", ""},
                new String[]{"D", "Dept A", "Dept B", "", ""}
        );
        MockMultipartFile file = new MockMultipartFile("file", "import.xlsx", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", createExcelFromData(data));

        when(orgChartService.findAllByIsDeletedIsFalse()).thenReturn(Collections.emptyList());
        when(validationService.isNullOrBlank(any())).thenAnswer(inv -> {
            String s = inv.getArgument(0);
            return s == null || s.trim().isEmpty();
        });

        mockMvc.perform(multipart("/api/orgChart/import").file(file).principal(authentication))
                .andExpect(status().isBadRequest()); // IMPORT_PARENT_NAME_CONFLICT_ERR_MSG_CODE
    }

    @Test
    void importOrgChartData_ShouldFail_When_ParentReference_IsMissingInMap() throws Exception {
        // "Dept B" refers to parent "Dept A", but "Dept A" is NOT in the DB and NOT in the Excel file.
        List<String[]> data = new ArrayList<>(Collections.singleton(new String[]{"D", "Dept B", "Dept A", "", ""}));
        MockMultipartFile file = new MockMultipartFile("file", "import.xlsx", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", createExcelFromData(data));

        when(orgChartService.findAllByIsDeletedIsFalse()).thenReturn(Collections.emptyList());
        when(validationService.isNullOrBlank(any())).thenAnswer(inv -> {
            String s = inv.getArgument(0);
            return s == null || s.trim().isEmpty();
        });

        mockMvc.perform(multipart("/api/orgChart/import").file(file).principal(authentication))
                .andExpect(status().isBadRequest()); // IMPORT_PARENT_NODE_NULL_ERR_MSG_CODE
    }

    private byte[] createValidExcelFile() throws IOException {
        try (Workbook workbook = new XSSFWorkbook();
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sheet = workbook.createSheet("Sheet 1");
            Row header = sheet.createRow(0);
            header.createCell(0).setCellValue("Node Type");
            header.createCell(1).setCellValue("Node Name");
            header.createCell(2).setCellValue("Parent Node Name");
            header.createCell(3).setCellValue("Rename To");
            header.createCell(4).setCellValue("To Be Deleted");

            Row row1 = sheet.createRow(1);
            row1.createCell(0).setCellValue("D");
            row1.createCell(1).setCellValue("Root Dept");
            row1.createCell(2).setCellValue("");
            row1.createCell(3).setCellValue("");
            row1.createCell(4).setCellValue("");

            workbook.write(out);
            return out.toByteArray();
        }
    }

    private byte[] createExcelFromData(List<String[]> rowsData) throws IOException {
        try (Workbook workbook = new XSSFWorkbook();
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sheet = workbook.createSheet("Sheet 1");

            // Header
            Row header = sheet.createRow(0);
            String[] headers = {"Node Type", "Node Name", "Parent Node Name", "Rename To", "To Be Deleted"};
            for (int i = 0; i < headers.length; i++) {
                header.createCell(i).setCellValue(headers[i]);
            }

            // Data
            int rowIdx = 1;
            for (String[] rowData : rowsData) {
                Row row = sheet.createRow(rowIdx++);
                for (int col = 0; col < rowData.length; col++) {
                    if (rowData[col] != null) {
                        row.createCell(col).setCellValue(rowData[col]);
                    }
                }
            }

            workbook.write(out);
            return out.toByteArray();
        }
    }
}