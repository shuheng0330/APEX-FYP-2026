package com.tbm.careerpathlearning.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tbm.careerpathlearning.dto.*;
import com.tbm.careerpathlearning.enums.AuthorityName;
import com.tbm.careerpathlearning.enums.RelationType;
import com.tbm.careerpathlearning.enums.StaffAccountStatus;
import com.tbm.careerpathlearning.exception.DataAccessException;
import com.tbm.careerpathlearning.model.CareerPathwayRoleId;
import com.tbm.careerpathlearning.model.RoleAuthorityId;
import com.tbm.careerpathlearning.service.*;
import org.apache.poi.ss.usermodel.Cell;
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
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(controllers = StaffController.class)
@AutoConfigureMockMvc(addFilters = false)
@TestPropertySource(properties = {
        "file.upload-dir=src/test/resources/temp-uploads"
})
class StaffControllerTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private StaffService staffService;
    @MockitoBean
    private RoleService roleService;
    @MockitoBean
    private MessageSource messageSource;
    @MockitoBean
    private EmailService emailService;
    @MockitoBean
    private ValidationService validationService;
    @MockitoBean
    private CareerPathwayService careerPathwayService;
    @MockitoBean
    private AuthorityService authorityService;
    @MockitoBean
    private RoleAuthorityService roleAuthorityService;
    @MockitoBean
    private StaffLoginAuditService staffLoginAuditService;
    @MockitoBean
    private CareerPathwayRoleService careerPathwayRoleService;
    @MockitoBean
    private StaffProfileService staffProfileService;
    @MockitoBean
    private StaffSelfDeclaredSkillService staffSelfDeclaredSkillService;
    @MockitoBean
    private StaffCertService staffCertService;
    @MockitoBean
    private ParentChildNodeService parentChildNodeService;
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
    private CareerPathwayRoleDto mockRelation;
    private StaffDto staff1;
    private StaffDto staff2;

    @BeforeEach
    void setUp() {
        authentication = mock(Authentication.class);
        when(authentication.getName()).thenReturn(USER_UUID.toString());
        when(authentication.getPrincipal()).thenReturn(USER_UUID.toString());
        // Default authority for overview tests
        doReturn(List.of(new SimpleGrantedAuthority(AuthorityName.CAN_MANAGE_STAFF.getAuthorityName())))
                .when(authentication).getAuthorities();

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

        mockRelation =  new CareerPathwayRoleDto();
        mockRelation.setId(new CareerPathwayRoleId(1L, 100L, 200L));
        mockRelation.setCareerPathway(mockPathway);
        mockRelation.setParentRole(roleRoot);
        mockRelation.setChildRole(roleChild);

        // Setup Staff
        staff1 = new StaffDto();
        staff1.setId(STAFF_UUID_1);
        staff1.setEmail("dev@test.com");
        staff1.setName("John Dev");
        staff1.setRole(roleChild);
        staff1.setCareerPathway(mockPathway);
        staff1.setAccountStatus(StaffAccountStatus.ACTIVE);

        staff2 = new StaffDto();
        staff2.setId(STAFF_UUID_2);
        staff2.setEmail("mgr@test.com");
        staff2.setName("Jane Mgr");
        staff2.setRole(roleRoot);
        staff2.setAccountStatus(StaffAccountStatus.ACTIVE);

        lenient().when(messageSource.getMessage(any(), any(), any(Locale.class))).thenReturn("Mock Message");

        lenient().when(validationService.isNullOrBlank(any())).thenAnswer(inv -> {
            String s = inv.getArgument(0);
            return s == null || s.trim().isEmpty();
        });
    }

    // --- GET Endpoint Tests ---

    @Test
    void getAllStaffs_ShouldReturnList() throws Exception {
        when(staffService.findAllByIsDeletedIsFalse()).thenReturn(List.of(staff1, staff2));

        mockMvc.perform(get("/api/staff").principal(authentication))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.size()").value(2))
                .andExpect(jsonPath("$[0].email").value("dev@test.com"));
    }

    @Test
    void getStaffById_ShouldReturnDto() throws Exception {
        when(staffService.findById(STAFF_UUID_1)).thenReturn(staff1);

        mockMvc.perform(get("/api/staff/{id}", STAFF_UUID_1).principal(authentication))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(STAFF_UUID_1.toString()));
    }

    @Test
    void getDirectDownLine_ShouldReturnList() throws Exception {
        when(staffService.findAllByIsDeletedIsFalseAndManagerId(STAFF_UUID_2)).thenReturn(List.of(staff1));

        mockMvc.perform(get("/api/staff/direct-down-line/{id}", STAFF_UUID_2).principal(authentication))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(STAFF_UUID_1.toString()));
    }

    @Test
    void getStaffByAuthority_ShouldReturnList() throws Exception {
        String authName = AuthorityName.CAN_MANAGE_STAFF.getAuthorityName();
        AuthorityDto authDto = new AuthorityDto();
        authDto.setId(5L);
        authDto.setName(AuthorityName.CAN_MANAGE_STAFF);

        RoleAuthorityDto raDto = new RoleAuthorityDto();
        raDto.setId(new RoleAuthorityId(100L, 5L)); // Manager role has authority

        when(authorityService.findAllByNameIn(anySet())).thenReturn(List.of(authDto));
        when(roleAuthorityService.getAll()).thenReturn(List.of(raDto));
        when(staffService.findAllByRoleIdIn(Set.of(100L))).thenReturn(List.of(staff2)); // Only manager

        mockMvc.perform(get("/api/staff/by-authority-name")
                        .param("authorityNames", authName)
                        .principal(authentication))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].email").value("mgr@test.com"));
    }

    @Test
    void getStaffOverview_AdminAccess_ShouldReturnAll() throws Exception {
        // Admin user
        doReturn(List.of(new SimpleGrantedAuthority(AuthorityName.CAN_MANAGE_STAFF.getAuthorityName())))
                .when(authentication).getAuthorities();

        when(staffService.findAllByIsDeletedIsFalse()).thenReturn(List.of(staff1, staff2));

        mockMvc.perform(get("/api/staff/overview").principal(authentication))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.size()").value(2));
    }

    @Test
    void getStaffOverview_UserAccess_ShouldReturnSubordinates() throws Exception {
        // Regular user (Manager)
        doReturn(List.of(new SimpleGrantedAuthority(AuthorityName.ROLE_USER.getAuthorityName())))
                .when(authentication).getAuthorities();
        when(authentication.getName()).thenReturn(STAFF_UUID_2.toString()); // Logged in as Manager

        // Mock Hierarchy: Staff 1 reports to Staff 2
        staff1.setManager(staff2);

        when(staffService.findAllByIsDeletedIsFalse()).thenReturn(List.of(staff1, staff2));
        when(parentChildNodeService.findAllByRelationType(RelationType.ORG_CHART)).thenReturn(Collections.emptyList());

        mockMvc.perform(get("/api/staff/overview").principal(authentication))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.size()").value(2)); // Self + Direct Report
    }

    @Test
    void getStaffOverview_UserAccess_ShouldFilterByDescendants() throws Exception {
        // Setup: User has ROLE_USER but NOT CAN_MANAGE_STAFF
        doReturn(List.of(new SimpleGrantedAuthority(AuthorityName.ROLE_USER.getAuthorityName())))
                .when(authentication).getAuthorities();
        when(authentication.getName()).thenReturn(STAFF_UUID_2.toString()); // Logged in as Manager

        // Mock Hierarchy: Staff 1 reports to Staff 2
        staff1.setManager(staff2);

        // Mock that Manager (Staff 2) is in a parent org (e.g. ID 10)
        // Staff 1 is in same org.
        // We need to ensure the logic enters the "limited access" branch
        when(staffService.findAllByIsDeletedIsFalse()).thenReturn(List.of(staff1, staff2));

        // Use a real or mock ParentChildNodeService response if needed,
        // but getAllSubordinates logic relies on the manager map built from the list.

        mockMvc.perform(get("/api/staff/overview").principal(authentication))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.size()").value(2)); // Should see Self + Direct Report
    }

    // --- CREATE / REGISTER Tests ---

    @Test
    void registerAccount_ShouldCreateStaff_WhenValid() throws Exception {
        RegisterAccountRequest req = new RegisterAccountRequest();
        req.setEmail("new@test.com");
        req.setName("New User");
        req.setRoleId(100L); // Manager Role (valid for no pathway)

        when(validationService.isNullOrBlank(any())).thenReturn(false);
        when(staffService.findByIsDeletedIsFalseAndEmail("new@test.com")).thenReturn(Optional.empty());
        when(roleService.getAllById(100L)).thenReturn(roleRoot);

        // Mock creation returns
        StaffDto created = new StaffDto();
        created.setId(UUID.randomUUID());
        created.setEmail("new@test.com");
        when(staffService.create(any(StaffDto.class))).thenReturn(created);

        mockMvc.perform(post("/api/staff/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req))
                        .principal(authentication))
                .andExpect(status().isOk());

        verify(staffService).create(any(StaffDto.class));
        verify(emailService).sendAccountRegisteredEmail(eq("new@test.com"), any());
    }

    @Test
    void registerAccount_ShouldFail_WhenRoleNotInPathway() throws Exception {
        RegisterAccountRequest req = new RegisterAccountRequest();
        req.setEmail("new@test.com");
        req.setRoleId(999L); // Random Role
        req.setCareerPathwayId(1L); // IT Pathway

        when(validationService.isNullOrBlank(any())).thenReturn(false);
        when(staffService.findByIsDeletedIsFalseAndEmail("new@test.com")).thenReturn(Optional.empty());

        // Pathway Config
        when(careerPathwayService.getById(1L)).thenReturn(mockPathway);
        CareerPathwayRoleDto rel = new CareerPathwayRoleDto();
        rel.setId(new CareerPathwayRoleId(1L, 100L, 200L));
        when(careerPathwayRoleService.getAllByCareerPathwayId(1L)).thenReturn(List.of(rel));

        mockMvc.perform(post("/api/staff/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req))
                        .principal(authentication))
                .andExpect(status().isBadRequest()); // Role 999 not in pathway [100, 200]
    }

    @Test
    void registerAccount_ShouldFail_WhenEmailExists() throws Exception {
        RegisterAccountRequest req = new RegisterAccountRequest();
        req.setEmail("dev@test.com"); // Already exists in system

        when(validationService.isNullOrBlank(any())).thenReturn(false);
        // Service returns existing staff
        when(staffService.findByIsDeletedIsFalseAndEmail("dev@test.com")).thenReturn(Optional.of(staff1));

        mockMvc.perform(post("/api/staff/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req))
                        .principal(authentication))
                .andExpect(status().isBadRequest());
    }

    @Test
    void registerAccount_ShouldFail_WhenPathwayProvidedWithoutRole() throws Exception {
        RegisterAccountRequest req = new RegisterAccountRequest();
        req.setEmail("new@test.com");
        req.setCareerPathwayId(1L);
        req.setRoleId(null); // Missing Role

        when(validationService.isNullOrBlank(any())).thenReturn(false);
        when(staffService.findByIsDeletedIsFalseAndEmail(anyString())).thenReturn(Optional.empty());

        mockMvc.perform(post("/api/staff/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req))
                        .principal(authentication))
                .andExpect(status().isBadRequest());
    }

    @Test
    void registerAccount_ShouldAssignManager_WhenValid() throws Exception {
        RegisterAccountRequest req = new RegisterAccountRequest();
        req.setEmail("subordinate@test.com");
        req.setManagerId(STAFF_UUID_2); // Assign Manager (Staff 2)

        when(validationService.isNullOrBlank(any())).thenReturn(false);
        when(staffService.findByIsDeletedIsFalseAndEmail(anyString())).thenReturn(Optional.empty());

        // Mock finding the manager
        when(staffService.findById(STAFF_UUID_2)).thenReturn(staff2);

        // Mock creation
        StaffDto created = new StaffDto(); created.setId(UUID.randomUUID());
        when(staffService.create(any(StaffDto.class))).thenReturn(created);

        mockMvc.perform(post("/api/staff/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req))
                        .principal(authentication))
                .andExpect(status().isOk());

        // Verify manager was set on the DTO passed to create
        verify(staffService).create(argThat(dto ->
                dto.getManager() != null && dto.getManager().getId().equals(STAFF_UUID_2)
        ));
    }

    // --- UPDATE Tests ---

    @Test
    void updateStaff_ShouldUpdateFields() throws Exception {
        EditStaffRequestDto req = new EditStaffRequestDto();
        req.setStaffId(STAFF_UUID_1);
        req.setEmail("updated@test.com");
        req.setName("Updated Name");
        req.setAccountStatus(true);

        when(staffService.findById(STAFF_UUID_1)).thenReturn(staff1);
        when(validationService.isNullOrBlank(any())).thenReturn(false);

        mockMvc.perform(put("/api/staff/edit-staff")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req))
                        .principal(authentication))
                .andExpect(status().isOk());

        verify(staffService).update(eq(STAFF_UUID_1), any(StaffDto.class));
        // Verify email changed trigger
        verify(emailService).sendAccountRegisteredEmail(eq("updated@test.com"), any());
    }

    @Test
    void updateStaff_ShouldRemoveManager_WhenIdIsNull() throws Exception {
        // Setup: Staff 1 currently has a manager
        staff1.setManager(staff2);

        EditStaffRequestDto req = new EditStaffRequestDto();
        req.setStaffId(STAFF_UUID_1);
        req.setEmail("dev@test.com");
        req.setManagerId(null); // Remove Manager

        when(staffService.findById(STAFF_UUID_1)).thenReturn(staff1);
        when(validationService.isNullOrBlank(any())).thenReturn(false);

        mockMvc.perform(put("/api/staff/edit-staff")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req))
                        .principal(authentication))
                .andExpect(status().isOk());

        // Verify manager is null in update call
        verify(staffService).update(eq(STAFF_UUID_1), argThat(dto -> dto.getManager() == null));
    }

    @Test
    void toggleAccountStatus_ShouldUpdateStatus() throws Exception {
        ToggleAccountStatusRequest req = new ToggleAccountStatusRequest();
        req.setStaffId(STAFF_UUID_1);
        req.setAccountStatus(false); // Deactivate

        when(staffService.findById(STAFF_UUID_1)).thenReturn(staff1);

        mockMvc.perform(put("/api/staff/toggle-account-status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req))
                        .principal(authentication))
                .andExpect(status().isOk());

        verify(staffService).update(eq(STAFF_UUID_1), argThat(dto -> dto.getAccountStatus() == StaffAccountStatus.INACTIVE));
    }

    @Test
    void toggleAccountStatus_ShouldFail_WhenRequestIsInvalid() throws Exception {
        ToggleAccountStatusRequest req = new ToggleAccountStatusRequest();
        // Missing StaffId inside the request object
        req.setAccountStatus(true);

        mockMvc.perform(put("/api/staff/toggle-account-status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req))
                        .principal(authentication))
                .andExpect(status().isBadRequest());
    }

    @Test
    void toggleAccountStatus_ShouldThrowException_WhenStaffNotFound() throws Exception {
        ToggleAccountStatusRequest req = new ToggleAccountStatusRequest();
        req.setStaffId(STAFF_UUID_1);
        req.setAccountStatus(true);

        // Mock service returning null (Staff not found)
        when(staffService.findById(STAFF_UUID_1)).thenReturn(null);

        mockMvc.perform(put("/api/staff/toggle-account-status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req))
                        .principal(authentication))
                // Verify the controller threw the expected custom DataAccessException
                .andExpect(result -> assertInstanceOf(DataAccessException.class, result.getResolvedException()));
    }

    @Test
    void toggleAccountStatus_ShouldReactivateAndResetAudit_WhenStatusIsTrue() throws Exception {
        // Setup Request: Activate Account
        ToggleAccountStatusRequest req = new ToggleAccountStatusRequest();
        req.setStaffId(STAFF_UUID_1);
        req.setAccountStatus(true);

        // Setup Audit Data (Simulate existing failures)
        StaffLoginAuditDto auditDto = new StaffLoginAuditDto();
        auditDto.setLoginFailedAttempts(5);
        auditDto.setForgotPasswordAttempts(3);

        when(staffService.findById(STAFF_UUID_1)).thenReturn(staff1);
        when(staffLoginAuditService.findStaffLoginAuditById(STAFF_UUID_1)).thenReturn(Optional.of(auditDto));

        mockMvc.perform(put("/api/staff/toggle-account-status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req))
                        .principal(authentication))
                .andExpect(status().isOk());

        // Verify 1: Staff status updated to ACTIVE
        verify(staffService).update(eq(STAFF_UUID_1), argThat(dto ->
                dto.getAccountStatus() == StaffAccountStatus.ACTIVE
        ));

        // Verify 2: Login Audit counts reset to 0
        verify(staffLoginAuditService).updateStaffLoginAudit(eq(STAFF_UUID_1), argThat(dto ->
                dto.getLoginFailedAttempts() == 0 && dto.getForgotPasswordAttempts() == 0
        ));
    }

    // --- DELETE Tests ---

    @Test
    void delete_ShouldRemoveStaffAndAssets() throws Exception {
        mockMvc.perform(delete("/api/staff/delete")
                        .param("staffId", STAFF_UUID_1.toString())
                        .principal(authentication))
                .andExpect(status().isOk());

        verify(staffProfileService).delete(STAFF_UUID_1);
        verify(staffService).delete(eq(STAFF_UUID_1), any());
    }

    @Test
    void bulkDelete_ShouldRemoveMultiple() throws Exception {
        String ids = STAFF_UUID_1 + "," + STAFF_UUID_2;

        mockMvc.perform(delete("/api/staff/bulk-delete")
                        .param("staffIds", ids)
                        .principal(authentication))
                .andExpect(status().isOk());

        verify(staffService).deleteAllById(anySet(), any());
    }

    // --- IMPORT Tests ---

    @Test
    void import_ShouldCreateNewStaff_WhenValid() throws Exception {
        // "New Email", "New Name", "Dept", "Role", "Path", "Mgr", "Status", "NewEmail", "Del"
        List<String[]> data = new ArrayList<>(Collections.singleton(
                new String[]{
                        "new@test.com", "New User", "IT Dept", "Developer", "Software Engineering", "", "Active", "", ""
                }
        ));
        byte[] excelBytes = createExcelFromData(data);
        MockMultipartFile file = new MockMultipartFile("file", "import.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", excelBytes);

        // Mocks for lookup
        when(orgChartService.findAllByIsDeletedIsFalse()).thenReturn(List.of(mockOrg));
        when(roleService.getAllByDeletedIsFalse()).thenReturn(List.of(roleRoot, roleChild));
        when(careerPathwayService.getAllByIsDeletedIsFalse()).thenReturn(List.of(mockPathway));

        // Mock Relations for Role Check
        CareerPathwayRoleDto rel = new CareerPathwayRoleDto();
        rel.setId(new CareerPathwayRoleId(1L, 100L, 200L));
        rel.setParentRole(roleRoot);
        rel.setChildRole(roleChild);
        rel.setCareerPathway(mockPathway);
        when(careerPathwayRoleService.getAll()).thenReturn(List.of(rel));

        when(staffService.updateAll(anyList())).thenAnswer(inv -> inv.getArgument(0));

        mockMvc.perform(multipart("/api/staff/import").file(file).principal(authentication))
                .andExpect(status().isOk());

        verify(staffService).updateAll(argThat(list ->
                list.get(0).getEmail().equals("new@test.com") && list.get(0).getName().equals("New User")
        ));
    }

    @Test
    void import_ShouldFail_WhenRoleNotInPathway() throws Exception {
        // Developer (200) is in path. Manager (100) is root.
        // Let's create a role "HR" (300) in "IT Dept" but NOT in the "Software Engineering" pathway.
        RoleDto hrRole = new RoleDto();
        hrRole.setId(300L);
        hrRole.setName("HR");
        hrRole.setOrgChart(mockOrg);

        List<String[]> data = new ArrayList<>(Collections.singleton(
                new String[]{
                        "hr@test.com", "HR User", "IT Dept", "HR", "Software Engineering", "", "Active", "", ""
                }
        ));

        byte[] excelBytes = createExcelFromData(data);
        MockMultipartFile file = new MockMultipartFile("file", "import.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", excelBytes);

        when(orgChartService.findAllByIsDeletedIsFalse()).thenReturn(List.of(mockOrg));
        when(roleService.getAllByDeletedIsFalse()).thenReturn(List.of(roleRoot, roleChild, hrRole));
        when(careerPathwayService.getAllByIsDeletedIsFalse()).thenReturn(List.of(mockPathway));

        // Relation only contains 100->200. HR (300) is missing.
        CareerPathwayRoleDto rel = new CareerPathwayRoleDto();
        rel.setId(new CareerPathwayRoleId(1L, 100L, 200L));
        rel.setParentRole(roleRoot);
        rel.setChildRole(roleChild);
        rel.setCareerPathway(mockPathway);
        when(careerPathwayRoleService.getAll()).thenReturn(List.of(rel));

        mockMvc.perform(multipart("/api/staff/import").file(file).principal(authentication))
                .andExpect(status().isBadRequest()); // IMPORT_INVALID_DATA_ERR_MSG_CODE (Role not in path)
    }

    @Test
    void import_ShouldFail_WhenDuplicateEmailsInFile() throws Exception {
        // Two rows with same email
        List<String[]> data = new ArrayList<>();
        data.add(new String[]{"dup@test.com", "User 1", "IT Dept", "Developer", "", "", "", "", ""});
        data.add(new String[]{"dup@test.com", "User 2", "IT Dept", "Manager", "", "", "", "", ""});

        byte[] excelBytes = createExcelFromData(data);
        MockMultipartFile file = new MockMultipartFile("file", "import.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", excelBytes);

        // Basic Mocks
        when(orgChartService.findAllByIsDeletedIsFalse()).thenReturn(List.of(mockOrg));
        when(roleService.getAllByDeletedIsFalse()).thenReturn(List.of(roleRoot, roleChild));

        mockMvc.perform(multipart("/api/staff/import").file(file).principal(authentication))
                .andExpect(status().isBadRequest()); // IMPORT_DUPLICATE_ENTRY_ERR_MSG_CODE
    }

    @Test
    void import_ShouldUpdateExistingStaff_WhenValid() throws Exception {
        // Scenario: "dev@test.com" exists. Update Name to "Updated Dev" and Role to "Manager".
        List<String[]> data = new ArrayList<>(Collections.singleton(
                new String[]{
                        "dev@test.com", "Updated Dev", "IT Dept", "Manager", "Software Engineering", "", "Active", "", ""
                }
        ));
        byte[] excelBytes = createExcelFromData(data);
        MockMultipartFile file = new MockMultipartFile("file", "import.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", excelBytes);

        // 1. Mock Existing Data
        when(orgChartService.findAllByIsDeletedIsFalse()).thenReturn(List.of(mockOrg));
        when(roleService.getAllByDeletedIsFalse()).thenReturn(List.of(roleRoot, roleChild));
        when(careerPathwayService.getAllByIsDeletedIsFalse()).thenReturn(List.of(mockPathway));

        // Mock Relations (Manager Role is valid in IT Dept and Pathway)
        CareerPathwayRoleDto rel = new CareerPathwayRoleDto();
        rel.setId(new CareerPathwayRoleId(1L, 100L, 200L));
        rel.setParentRole(roleRoot); rel.setChildRole(roleChild); rel.setCareerPathway(mockPathway);
        when(careerPathwayRoleService.getAll()).thenReturn(List.of(rel));

        // 2. IMPORTANT: Return existing staff so Controller treats this as an UPDATE
        when(staffService.findAllByIsDeletedIsFalse()).thenReturn(List.of(staff1));

        // 3. Mock Update Response
        when(staffService.updateAll(anyList())).thenAnswer(inv -> inv.getArgument(0));

        mockMvc.perform(multipart("/api/staff/import").file(file).principal(authentication))
                .andExpect(status().isOk());

        verify(staffService).updateAll(argThat(list ->
                list.size() == 1 &&
                        list.get(0).getEmail().equals("dev@test.com") &&
                        list.get(0).getName().equals("Updated Dev") &&
                        list.get(0).getRole().getId().equals(100L) // Changed to Manager
        ));
    }

    @Test
    void import_ShouldSoftDelete_WhenFlagged() throws Exception {
        List<String[]> data = new ArrayList<>(Collections.singleton(
                new String[]{ "dev@test.com", "John Dev", "IT Dept", "Developer", "", "", "", "", "Yes" }
        ));
        byte[] excelBytes = createExcelFromData(data);
        MockMultipartFile file = new MockMultipartFile("file", "import.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", excelBytes);

        when(validationService.isNullOrBlank(any())).thenAnswer(inv -> {
            String s = inv.getArgument(0);
            return s == null || s.trim().isEmpty();
        });

        // Existing record must exist to be deleted
        when(orgChartService.findAllByIsDeletedIsFalse()).thenReturn(List.of(mockOrg));
        when(roleService.getAllByDeletedIsFalse()).thenReturn(List.of(roleRoot, roleChild));
        when(careerPathwayService.getAllByIsDeletedIsFalse()).thenReturn(List.of(mockPathway));
        when(staffService.findAllByIsDeletedIsFalse()).thenReturn(List.of(staff1, staff2));

        mockMvc.perform(multipart("/api/staff/import").file(file).principal(authentication))
                .andExpect(status().isOk());

        // Verify the DTO passed to service has deleted=true
        verify(staffService).updateAll(argThat(list ->
                list.get(0).isDeleted()
        ));

        // Verify delete was called with THIS staff's ID
        verify(staffProfileService).deleteAllById(argThat(ids -> ids.contains(STAFF_UUID_1)));
        verify(staffSelfDeclaredSkillService).deleteAllByStaffIdIn(argThat(ids -> ids.contains(STAFF_UUID_1)));
        verify(staffCertService).deleteAllByStaffIdIn(argThat(ids -> ids.contains(STAFF_UUID_1)));
    }

    @Test
    void import_ShouldFail_WhenRenamingAndDeletingSimultaneously() throws Exception {
        // Try to delete AND provide a new email at the same time
        List<String[]> data = new ArrayList<>(Collections.singleton(
                new String[]{ "dev@test.com", "Dev", "IT Dept", "Developer", "", "", "", "new@test.com", "Yes" }
        ));
        byte[] excelBytes = createExcelFromData(data);
        MockMultipartFile file = new MockMultipartFile("file", "import.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", excelBytes);

        // Mocks to pass initial checks
        when(orgChartService.findAllByIsDeletedIsFalse()).thenReturn(List.of(mockOrg));
        when(roleService.getAllByDeletedIsFalse()).thenReturn(List.of(roleChild));
        when(staffService.findAllByIsDeletedIsFalse()).thenReturn(List.of(staff1));

        mockMvc.perform(multipart("/api/staff/import").file(file).principal(authentication))
                .andExpect(status().isBadRequest()); // Conflict Error
    }

    @Test
    void import_ShouldFail_WhenDepartmentNotFound() throws Exception {
        List<String[]> data = new ArrayList<>(Collections.singleton(
                new String[]{ "new@test.com", "User", "Ghost Dept", "Role", "", "", "", "", "" }
        ));
        byte[] excelBytes = createExcelFromData(data);
        MockMultipartFile file = new MockMultipartFile("file", "import.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", excelBytes);

        when(orgChartService.findAllByIsDeletedIsFalse()).thenReturn(List.of(mockOrg)); // Only "IT Dept" exists

        mockMvc.perform(multipart("/api/staff/import").file(file).principal(authentication))
                .andExpect(status().isBadRequest()); // IMPORT_DATA_NOT_FOUND_ERR_MSG_CODE
    }

    @Test
    void import_ShouldFail_WhenRoleDoesNotBelongToDepartment() throws Exception {
        // "HR Manager" role does not exist in "IT Dept"
        RoleDto hrRole = new RoleDto(); hrRole.setName("HR Manager");
        OrgChartDto hrDept = new OrgChartDto(); hrDept.setId(20L); hrDept.setName("HR Dept");
        hrRole.setOrgChart(hrDept);

        List<String[]> data = new ArrayList<>(Collections.singleton(
                new String[]{ "new@test.com", "User", "IT Dept", "HR Manager", "", "", "", "", "" }
        ));
        byte[] excelBytes = createExcelFromData(data);
        MockMultipartFile file = new MockMultipartFile("file", "import.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", excelBytes);

        when(orgChartService.findAllByIsDeletedIsFalse()).thenReturn(List.of(mockOrg, hrDept));
        when(roleService.getAllByDeletedIsFalse()).thenReturn(List.of(roleRoot, hrRole));

        mockMvc.perform(multipart("/api/staff/import").file(file).principal(authentication))
                .andExpect(status().isBadRequest()); // IMPORT_DATA_NOT_FOUND_ERR_MSG_CODE (Role in wrong dept)
    }

    @Test
    void import_ShouldRenameEmail_WhenValid() throws Exception {
        // "dev@test.com" changes to "dev_updated@test.com"
        List<String[]> data = new ArrayList<>(Collections.singleton(
                new String[]{ "dev@test.com", "Dev", "IT Dept", "Developer", "", "", "", "dev_updated@test.com", "" }
        ));
        byte[] excelBytes = createExcelFromData(data);
        MockMultipartFile file = new MockMultipartFile("file", "import.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", excelBytes);

        // Setup existing
        when(staffService.findAllByIsDeletedIsFalse()).thenReturn(List.of(staff1));
        when(orgChartService.findAllByIsDeletedIsFalse()).thenReturn(List.of(mockOrg));
        when(roleService.getAllByDeletedIsFalse()).thenReturn(List.of(roleChild));

        // Mock update
        when(staffService.updateAll(anyList())).thenAnswer(inv -> inv.getArgument(0));

        mockMvc.perform(multipart("/api/staff/import").file(file).principal(authentication))
                .andExpect(status().isOk());

        verify(staffService).updateAll(argThat(list ->
                list.get(0).getId().equals(STAFF_UUID_1) &&
                        list.get(0).getEmail().equals("dev_updated@test.com")
        ));
    }

    @Test
    void import_ShouldCreateNewStaff_WithInactiveStatus() throws Exception {
        // Register new user with "Inactive" status
        List<String[]> data = new ArrayList<>(Collections.singleton(
                new String[]{ "inactive@test.com", "Inactive User", "IT Dept", "Developer", "Software Engineering", "", "Inactive", "", "" }
        ));
        byte[] excelBytes = createExcelFromData(data);
        MockMultipartFile file = new MockMultipartFile("file", "import.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", excelBytes);

        // Mocks for validation
        when(orgChartService.findAllByIsDeletedIsFalse()).thenReturn(List.of(mockOrg));
        when(roleService.getAllByDeletedIsFalse()).thenReturn(List.of(roleChild));
        when(careerPathwayService.getAllByIsDeletedIsFalse()).thenReturn(List.of(mockPathway));

        // Role Relation
        CareerPathwayRoleDto rel = new CareerPathwayRoleDto();
        rel.setId(new CareerPathwayRoleId(1L, 100L, 200L));
        rel.setParentRole(roleRoot); rel.setChildRole(roleChild); rel.setCareerPathway(mockPathway);
        when(careerPathwayRoleService.getAll()).thenReturn(List.of(rel));

        // Stub updateAll to return what was passed (captured)
        when(staffService.updateAll(anyList())).thenAnswer(inv -> inv.getArgument(0));

        mockMvc.perform(multipart("/api/staff/import").file(file).principal(authentication))
                .andExpect(status().isOk());

        // Verify status was set to INACTIVE
        verify(staffService).updateAll(argThat(list ->
                list.get(0).getEmail().equals("inactive@test.com") &&
                        list.get(0).getAccountStatus() == StaffAccountStatus.INACTIVE
        ));
    }

    @Test
    void import_ShouldFail_WhenManagerEmailNotFound() throws Exception {
        // Try to assign a manager email that doesn't exist
        List<String[]> data = new ArrayList<>(Collections.singleton(
                new String[]{ "new@test.com", "User", "IT Dept", "Developer", "Software Engineering", "ghost@test.com", "Active", "", "" }
        ));
        byte[] excelBytes = createExcelFromData(data);
        MockMultipartFile file = new MockMultipartFile("file", "import.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", excelBytes);

        // Mocks
        when(orgChartService.findAllByIsDeletedIsFalse()).thenReturn(List.of(mockOrg));
        when(roleService.getAllByDeletedIsFalse()).thenReturn(List.of(roleChild));
        when(careerPathwayService.getAllByIsDeletedIsFalse()).thenReturn(List.of(mockPathway));

        // Mock empty staff list (so ghost@test.com is definitely missing)
        when(staffService.findAllByIsDeletedIsFalse()).thenReturn(Collections.emptyList());

        mockMvc.perform(multipart("/api/staff/import").file(file).principal(authentication))
                .andExpect(status().isBadRequest()); // IMPORT_DATA_NOT_FOUND_ERR_MSG_CODE (Manager not found)
    }

    @Test
    void export_ShouldReturnExcelFile_WithCorrectData() throws Exception {
        // 1. Setup Mock Data
        // staff1 is "John Dev" (from setUp)
        // Create a second staff with null optional fields to test null handling
        StaffDto minimalStaff = new StaffDto();
        minimalStaff.setId(UUID.randomUUID());
        minimalStaff.setEmail("min@test.com");
        minimalStaff.setName("Minimal User");
        minimalStaff.setAccountStatus(StaffAccountStatus.INACTIVE); // Inactive status
        // Role, Pathway, Manager are null

        when(staffService.findAllByIsDeletedIsFalse()).thenReturn(List.of(staff1, minimalStaff));

        // 2. Perform Request
        var result = mockMvc.perform(get("/api/staff/export")
                        .principal(authentication))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=Exported_Staff_Account_Data.xlsx"))
                .andExpect(header().string(HttpHeaders.CONTENT_TYPE, "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .andReturn();

        // 3. Verify Excel Content
        byte[] content = result.getResponse().getContentAsByteArray();
        try (Workbook workbook = new XSSFWorkbook(new ByteArrayInputStream(content))) {
            Sheet sheet = workbook.getSheetAt(0);
            assertNotNull(sheet, "Sheet should exist");

            // Verify Header Row (Row 0)
            Row header = sheet.getRow(0);
            assertEquals("Staff Email", header.getCell(0).getStringCellValue());
            assertEquals("To Be Deleted", header.getCell(8).getStringCellValue());

            // Verify Data Row 1 (staff1 - Full Data)
            Row row1 = sheet.getRow(1);
            assertEquals(staff1.getEmail(), row1.getCell(0).getStringCellValue());
            assertEquals(staff1.getName(), row1.getCell(1).getStringCellValue());
            assertEquals(staff1.getRole().getOrgChart().getName(), row1.getCell(2).getStringCellValue()); // Dept
            assertEquals(staff1.getRole().getName(), row1.getCell(3).getStringCellValue()); // Role
            assertEquals(staff1.getCareerPathway().getName(), row1.getCell(4).getStringCellValue()); // Pathway
            // Manager email check if setup (staff1 didn't have manager in setUp, assuming null or check logic)

            // Account Status check: Active -> Null/Empty in logic
            // Logic: Objects.equals(ACTIVE) ? null : INACTIVE
            // staff1 is ACTIVE -> Expect Cell is Blank or Null
            Cell statusCell1 = row1.getCell(6);
            assertTrue(statusCell1 == null || statusCell1.getStringCellValue().isEmpty(), "Active status should result in empty cell");

            // Verify Data Row 2 (minimalStaff - Nulls & Inactive)
            Row row2 = sheet.getRow(2);
            assertEquals("min@test.com", row2.getCell(0).getStringCellValue());
            // Null Role -> Dept & Role Name cells should be empty/null (depending on implementation default)
            // Controller Logic: dto.getRole() == null ? null : ...
            // So cell value is null/blank.

            // Account Status check: Inactive -> "Inactive"
            assertEquals("Inactive", row2.getCell(6).getStringCellValue());
        }
    }

    // --- Helper ---

    private byte[] createExcelFromData(List<String[]> rowsData) throws IOException {
        try (Workbook workbook = new XSSFWorkbook();
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sheet = workbook.createSheet("Sheet 1");

            Row header = sheet.createRow(0);
            String[] headers = {
                    "Staff Email", "Staff Name", "Department Name", "Role Name",
                    "Career Pathway Name", "Direct Manager Email", "Account Status",
                    "New Staff Email", "To Be Deleted"
            };
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