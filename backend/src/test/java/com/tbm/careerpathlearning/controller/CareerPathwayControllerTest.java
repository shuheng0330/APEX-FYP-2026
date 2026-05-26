package com.tbm.careerpathlearning.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tbm.careerpathlearning.dto.*;
import com.tbm.careerpathlearning.exception.BadRequestException;
import com.tbm.careerpathlearning.model.CareerPathwayRoleId;
import com.tbm.careerpathlearning.model.CareerPathwayTrackId;
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
import java.time.OffsetDateTime;
import java.util.*;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(controllers = CareerPathwayController.class)
@AutoConfigureMockMvc(addFilters = false)
class CareerPathwayControllerTest {

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
    private OrgChartService orgChartService;
    @MockitoBean
    private TrackService trackService;
    @MockitoBean
    private CareerPathwayTrackService careerPathwayTrackService;
    @MockitoBean
    private CareerPathwayRoleService careerPathwayRoleService;
    @MockitoBean
    private RoleService roleService;
    @MockitoBean
    private StaffService staffService;
    @MockitoBean
    private TokenService tokenService;

    private Authentication authentication;
    private UUID userUUID;
    private CareerPathwayDto mockPathway;
    private OrgChartDto mockOrg;
    private RoleDto mockRootRole;
    private RoleDto mockChildRole;

    @BeforeEach
    void setUp() {
        userUUID = UUID.randomUUID();
        authentication = mock(Authentication.class);
        when(authentication.getName()).thenReturn(userUUID.toString());
        when(authentication.getPrincipal()).thenReturn(userUUID.toString());

        mockOrg = new OrgChartDto();
        mockOrg.setId(10L);
        mockOrg.setName("Engineering");

        mockRootRole = new RoleDto();
        mockRootRole.setId(100L);
        mockRootRole.setName("Head of Eng");
        mockRootRole.setOrgChart(mockOrg);

        mockChildRole = new RoleDto();
        mockChildRole.setId(200L);
        mockChildRole.setName("Senior Dev");
        mockChildRole.setOrgChart(mockOrg);

        mockPathway = new CareerPathwayDto();
        mockPathway.setId(1L);
        mockPathway.setName("Software Engineering");
        mockPathway.setOrgChart(mockOrg);
        mockPathway.setRootRole(mockRootRole);

        lenient().when(messageSource.getMessage(any(), any(), any(Locale.class))).thenReturn("Mock Message");
    }

    // --- GET Endpoint Tests ---

    @Test
    void getAll_ShouldReturnList() throws Exception {
        when(careerPathwayService.getAllByIsDeletedIsFalse()).thenReturn(List.of(mockPathway));

        mockMvc.perform(get("/api/career-pathway").principal(authentication))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.size()").value(1))
                .andExpect(jsonPath("$[0].name").value("Software Engineering"));
    }

    @Test
    void overview_ShouldReturnGraphStructure() throws Exception {
        // Setup complex mock for overview graph generation
        when(careerPathwayService.getAllByIsDeletedIsFalse()).thenReturn(List.of(mockPathway));
        when(careerPathwayRoleService.getAll()).thenReturn(new ArrayList<>()); // No children for simplicity
        when(careerPathwayTrackService.findAll()).thenReturn(Collections.emptyList());

        mockMvc.perform(get("/api/career-pathway/overview").principal(authentication))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].graph.name").value("Head of Eng")); // Root role node
    }

    @Test
    void overview_ShouldBuildRecursiveGraph() throws Exception {
        // 1. Setup Role Objects (Needed for graph node generation)
        // ROOT: Use existing mock to avoid collision with pathway root
        // mockRootRole (ID 100, Name "Head of Eng") is already set in setUp()

        RoleDto midRole = new RoleDto(); midRole.setId(200L); midRole.setName("Mid");
        RoleDto leafRole = new RoleDto(); leafRole.setId(300L); leafRole.setName("Leaf");

        // 2. Setup Hierarchy Links: Root(100) -> Mid(200)
        CareerPathwayRoleDto link1 = new CareerPathwayRoleDto();
        link1.setId(new CareerPathwayRoleId(1L, 100L, 200L));
        link1.setParentRole(mockRootRole); // MUST BE SET (ID 100)
        link1.setChildRole(midRole);       // MUST BE SET (ID 200)

        // 3. Setup Hierarchy Links: Mid(200) -> Leaf(300)
        CareerPathwayRoleDto link2 = new CareerPathwayRoleDto();
        link2.setId(new CareerPathwayRoleId(1L, 200L, 300L));
        link2.setParentRole(midRole);      // MUST BE SET (ID 200)
        link2.setChildRole(leafRole);      // MUST BE SET (ID 300)

        // Return links
        List<CareerPathwayRoleDto> allLinks = new ArrayList<>(List.of(link1, link2));

        when(careerPathwayService.getAllByIsDeletedIsFalse()).thenReturn(List.of(mockPathway));
        when(careerPathwayRoleService.getAll()).thenReturn(allLinks);
        when(careerPathwayTrackService.findAll()).thenReturn(Collections.emptyList());

        mockMvc.perform(get("/api/career-pathway/overview").principal(authentication))
                .andExpect(status().isOk())
                // Verify Root (Head of Eng)
                .andExpect(jsonPath("$[0].graph.id").value(100))
                // Verify Child (Mid)
                .andExpect(jsonPath("$[0].graph.children[0].id").value(200))
                // Verify Grandchild (Leaf)
                .andExpect(jsonPath("$[0].graph.children[0].children[0].id").value(300));
    }

    @Test
    void my_ShouldReturnVisualizedGraphWithVisitedStatus() throws Exception {
        UUID staffId = UUID.randomUUID();

        // 1. Setup Staff with Role and Pathway
        StaffDto mockStaff = new StaffDto();
        mockStaff.setRole(mockChildRole); // Staff is "Senior Dev" (ID 200)
        mockStaff.setCareerPathway(mockPathway); // Pathway ID 1

        // 2. Setup Role Hierarchy: Head (100) -> Senior (200)
        CareerPathwayRoleDto link = new CareerPathwayRoleDto();
        link.setId(new CareerPathwayRoleId(1L, 100L, 200L));
        link.setParentRole(mockRootRole);
        link.setChildRole(mockChildRole);

        when(staffService.findById(staffId)).thenReturn(mockStaff);
        when(careerPathwayRoleService.getAllByCareerPathwayId(1L)).thenReturn(List.of(link));
        when(careerPathwayTrackService.findAll()).thenReturn(Collections.emptyList());

        mockMvc.perform(get("/api/career-pathway/my")
                        .param("staffId", staffId.toString())
                        .principal(authentication))
                .andExpect(status().isOk())
                // Root (Head of Eng) should be marked as visited (1) because it's a parent of current role
                .andExpect(jsonPath("$.graph.id").value(100))
                .andExpect(jsonPath("$.graph.data.hasVisited").value(1))
                // Child (Senior Dev) is the current role (0)
                .andExpect(jsonPath("$.graph.children[0].id").value(200))
                .andExpect(jsonPath("$.graph.children[0].data.hasVisited").value(0));
    }

    @Test
    void my_ShouldReturnNull_WhenStaffHasNoPathway() throws Exception {
        UUID staffId = UUID.randomUUID();
        StaffDto staffWithoutPathway = new StaffDto();
        staffWithoutPathway.setCareerPathway(null); // No pathway

        when(staffService.findById(staffId)).thenReturn(staffWithoutPathway);

        mockMvc.perform(get("/api/career-pathway/my")
                        .param("staffId", staffId.toString())
                        .principal(authentication))
                .andExpect(status().isOk())
                .andExpect(content().string("")); // Empty response
    }

    // --- Create Endpoint Tests ---

    @Test
    void createCareerPathway_ShouldCreateEntities() throws Exception {
        CreateCareerPathwayRequestDto req = new CreateCareerPathwayRequestDto();
        req.setCareerPathwayName("New Path");
        req.setOrgChartId(10L);
        req.setRootRoleId(100L);
        req.setParentChildRoleId(Map.of(100L, Set.of(200L))); // Parent 100 -> Child 200
        req.setTrack(List.of("Tech Track"));

        when(validationService.isNullOrBlank(any())).thenReturn(false);
        when(orgChartService.getByById(10L)).thenReturn(mockOrg);
        when(roleService.getAllByIsDeletedIsFalseAndIdIn(anySet())).thenReturn(List.of(mockRootRole, mockChildRole));
        when(careerPathwayService.create(any(CareerPathwayDto.class))).thenReturn(mockPathway);

        TrackDto trackDto = new TrackDto();
        trackDto.setId(5L);
        trackDto.setTrack("Tech Track");
        when(trackService.createAll(anyList())).thenReturn(List.of(trackDto));

        mockMvc.perform(post("/api/career-pathway")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req))
                        .principal(authentication))
                .andExpect(status().isOk());

        verify(careerPathwayService).create(any(CareerPathwayDto.class));
        verify(careerPathwayRoleService).createAll(anyList()); // Verify roles linked
        verify(careerPathwayTrackService).createAll(anyList()); // Verify tracks linked
    }

    @Test
    void createCareerPathway_ShouldFail_WhenGraphHasCycle() throws Exception {
        CreateCareerPathwayRequestDto req = new CreateCareerPathwayRequestDto();
        req.setCareerPathwayName("Cycle Path");
        req.setOrgChartId(10L);
        req.setRootRoleId(100L);
        // Cycle: 100 -> 200 -> 100
        req.setParentChildRoleId(Map.of(
                100L, Set.of(200L),
                200L, Set.of(100L)
        ));

        when(validationService.isNullOrBlank(any())).thenReturn(false);

        mockMvc.perform(post("/api/career-pathway")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req))
                        .principal(authentication))
                .andExpect(status().isBadRequest()); // Circular error
    }

    @Test
    void createCareerPathway_ShouldFail_WhenMultipleRootsExist() throws Exception {
        CreateCareerPathwayRequestDto req = new CreateCareerPathwayRequestDto();
        req.setCareerPathwayName("Broken Path");
        req.setOrgChartId(10L);
        req.setRootRoleId(100L);

        // Graph: 100->200 AND 300->400.
        // 100 and 300 are both roots. This is invalid for a single pathway.
        req.setParentChildRoleId(Map.of(
                100L, Set.of(200L),
                300L, Set.of(400L)
        ));

        when(validationService.isNullOrBlank(any())).thenReturn(false);

        mockMvc.perform(post("/api/career-pathway")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req))
                        .principal(authentication))
                .andExpect(status().isBadRequest()); // Should trigger MULTIPLE_ROOT error
    }

    @Test
    void createCareerPathway_ShouldFail_WhenGraphIsDisconnected() throws Exception {
        CreateCareerPathwayRequestDto req = new CreateCareerPathwayRequestDto();
        req.setCareerPathwayName("Disconnected");
        req.setOrgChartId(10L);
        req.setRootRoleId(100L); // Root is 100

        // Graph: 100 is isolated. 200 -> 300 is a separate island.
        // 100 has in-degree 0 (valid root). 200 has in-degree 0 (another root).
        // But the check "visited.size() != indegree.size()" will fail because starting BFS from 100 won't reach 200/300.
        req.setParentChildRoleId(Map.of(
                200L, Set.of(300L)
        ));

        when(validationService.isNullOrBlank(any())).thenReturn(false);

        mockMvc.perform(post("/api/career-pathway")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req))
                        .principal(authentication))
                .andExpect(status().isBadRequest());
        // Message should be CAREER_PATHWAY_DISCONNECTED_ERR_MSG_CODE or MULTIPLE_ROOT depending on exact logic flow
    }

    // --- Update Endpoint Tests ---

    @Test
    void update_ShouldUpdateAndSyncRelations() throws Exception {
        CreateCareerPathwayRequestDto req = new CreateCareerPathwayRequestDto();
        req.setCareerPathwayId(1L);
        req.setCareerPathwayName("Updated Name");
        req.setOrgChartId(10L);
        req.setRootRoleId(100L);
        req.setParentChildRoleId(Collections.emptyMap()); // Removing all children

        when(careerPathwayService.getById(1L)).thenReturn(mockPathway);
        when(roleService.getAllByIsDeletedIsFalseAndIdIn(anySet())).thenReturn(List.of(mockRootRole));
        when(careerPathwayService.update(eq(1L), any())).thenReturn(mockPathway);

        // Mock existing roles to force deletion
        CareerPathwayRoleDto existingRel = new CareerPathwayRoleDto();
        existingRel.setId(new CareerPathwayRoleId(1L, 100L, 200L));
        when(careerPathwayRoleService.getAllByCareerPathwayId(1L)).thenReturn(List.of(existingRel));

        mockMvc.perform(put("/api/career-pathway/edit")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req))
                        .principal(authentication))
                .andExpect(status().isOk());

        verify(careerPathwayRoleService).deleteAllByIdIn(anySet()); // Should delete removed relation
    }

    @Test
    void update_ShouldRemoveUnusedTracksAndNodes() throws Exception {
        CreateCareerPathwayRequestDto req = new CreateCareerPathwayRequestDto();
        req.setCareerPathwayId(1L);
        req.setCareerPathwayName("Updated");
        req.setOrgChartId(10L);
        req.setRootRoleId(100L);
        req.setTrack(Collections.emptyList()); // Empty tracks -> trigger removal
        req.setParentChildRoleId(Collections.emptyMap()); // Empty nodes -> trigger removal

        when(careerPathwayService.getById(1L)).thenReturn(mockPathway);
        when(roleService.getAllByIsDeletedIsFalseAndIdIn(anySet())).thenReturn(List.of(mockRootRole));
        when(careerPathwayService.update(any(), any())).thenReturn(mockPathway);

        // 1. Mock existing Tracks to force removal logic
        CareerPathwayTrackDto existingTrack = new CareerPathwayTrackDto();
        existingTrack.setId(new CareerPathwayTrackId(1L, 99L));
        when(careerPathwayTrackService.findAllByCareerPathwayId(1L)).thenReturn(List.of(existingTrack));

        // 2. Mock existing Nodes to force removal logic
        CareerPathwayRoleDto existingNode = new CareerPathwayRoleDto();
        existingNode.setId(new CareerPathwayRoleId(1L, 100L, 200L));
        when(careerPathwayRoleService.getAllByCareerPathwayId(1L)).thenReturn(List.of(existingNode));

        mockMvc.perform(put("/api/career-pathway/edit")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req))
                        .principal(authentication))
                .andExpect(status().isOk());

        // Verify Deletions
        verify(careerPathwayTrackService).deleteAllById(anySet());
        verify(careerPathwayRoleService).deleteAllByIdIn(anySet());
    }

    // --- Delete Endpoint Tests ---

    @Test
    void delete_ShouldRemoveAllDependencies() throws Exception {
        when(careerPathwayTrackService.findAndDeleteByCareerPathwayId(1L)).thenReturn(Collections.emptyList());

        mockMvc.perform(delete("/api/career-pathway/delete")
                        .param("selectedCareerPathwayId", "1")
                        .principal(authentication))
                .andExpect(status().isOk());

        verify(careerPathwayService).delete(eq(1L), any(), any());
        verify(careerPathwayRoleService).deleteByCareerPathwayId(1L);
    }

    @Test
    void bulkDelete_ShouldDeleteMultipleRecords() throws Exception {
        Set<Long> idsToDelete = Set.of(1L, 2L);
        when(careerPathwayTrackService.findAndDeleteByCareerPathwayIdIn(idsToDelete))
                .thenReturn(Collections.emptyList());

        mockMvc.perform(delete("/api/career-pathway/bulk-delete")
                        .param("selectedCareerPathwayIds", "1,2")
                        .principal(authentication))
                .andExpect(status().isOk());

        verify(careerPathwayService).deleteAll(eq(idsToDelete), any(), any());
        verify(careerPathwayRoleService).deleteAllByCareerPathwayIdIn(idsToDelete);
    }

    // --- Export Tests ---

    @Test
    void export_ShouldReturnExcel() throws Exception {
        when(careerPathwayService.getAllByIsDeletedIsFalse()).thenReturn(List.of(mockPathway));

        mockMvc.perform(get("/api/career-pathway/export").principal(authentication))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=Exported_Career_Pathway_Overview_Data.xlsx"));
    }

    @Test
    void export_ShouldHandleInternalError() throws Exception {
        when(careerPathwayService.getAllByIsDeletedIsFalse()).thenThrow(new RuntimeException("DB Fail"));

        mockMvc.perform(get("/api/career-pathway/export").principal(authentication))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.message").value("Mock Message"));
    }

    // --- Import Tests (Complex Scenarios) ---

    @Test
    void import_ShouldProcessValidFile() throws Exception {
        List<String[]> data = new ArrayList<>(Collections.singleton(
                new String[]{
                        "Engineering", "Software Engineering", "Desc", "Tech", "Head of Eng", "Head of Eng > Senior Dev", "", ""
                }));

        byte[] excelBytes = createExcelFromData(data);
        MockMultipartFile file = new MockMultipartFile("file", "import.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", excelBytes);

        when(validationService.isNullOrBlank(any())).thenAnswer(inv -> {
            String s = inv.getArgument(0);
            return s == null || s.trim().isEmpty();
        });

        // Mocks for lookup maps
        when(orgChartService.findAllByIsDeletedIsFalse()).thenReturn(List.of(mockOrg));
        when(roleService.getAllByDeletedIsFalse()).thenReturn(List.of(mockRootRole, mockChildRole));
        when(careerPathwayService.getAllByIsDeletedIsFalse()).thenReturn(Collections.emptyList()); // New record

        // Mocks for creation
        when(careerPathwayService.createAndUpdateAll(anyList())).thenReturn(List.of(mockPathway));
        when(trackService.createAll(anyList())).thenReturn(List.of(new TrackDto(5L, "Tech", false, null, null, null, null)));

        mockMvc.perform(multipart("/api/career-pathway/import").file(file).principal(authentication))
                .andExpect(status().isOk());

        verify(careerPathwayService).createAndUpdateAll(anyList());
        verify(careerPathwayRoleService).createAll(anyList()); // Link roles
    }

    @Test
    void import_ShouldFail_WhenRoleNotFound() throws Exception {
        // "Head of Eng > Ghost Role" -> Ghost Role does not exist
        List<String[]> data = new ArrayList<>(Collections.singleton(
                new String[]{
                        "Engineering", "Software Eng", "", "", "Head of Eng", "Head of Eng > Ghost Role", "", ""
                }
        ));

        byte[] excelBytes = createExcelFromData(data);
        MockMultipartFile file = new MockMultipartFile("file", "import.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", excelBytes);

        when(orgChartService.findAllByIsDeletedIsFalse()).thenReturn(List.of(mockOrg));
        when(roleService.getAllByDeletedIsFalse()).thenReturn(List.of(mockRootRole)); // Only Root exists

        mockMvc.perform(multipart("/api/career-pathway/import").file(file).principal(authentication))
                .andExpect(status().isBadRequest()); // Data Not Found Exception mapped to BadRequest usually
    }

    @Test
    void import_ShouldFail_WhenGraphIsDisconnected() throws Exception {
        // Root: Head. Edges: Senior > Junior. (Head is isolated)
        List<String[]> data = new ArrayList<>(Collections.singleton(
                new String[]{
                        "Engineering", "Software Eng", "", "", "Head of Eng", "Senior Dev > Junior Dev", "", ""
                }
        ));

        byte[] excelBytes = createExcelFromData(data);
        MockMultipartFile file = new MockMultipartFile("file", "import.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", excelBytes);

        RoleDto senior = new RoleDto();
        senior.setId(200L);
        senior.setName("Senior Dev");
        senior.setOrgChart(mockOrg);
        RoleDto junior = new RoleDto();
        junior.setId(300L);
        junior.setName("Junior Dev");
        junior.setOrgChart(mockOrg);

        when(orgChartService.findAllByIsDeletedIsFalse()).thenReturn(List.of(mockOrg));
        when(roleService.getAllByDeletedIsFalse()).thenReturn(List.of(mockRootRole, senior, junior));

        mockMvc.perform(multipart("/api/career-pathway/import").file(file).principal(authentication))
                .andExpect(status().isBadRequest()); // Disconnected graph error
    }

    @Test
    void import_ShouldFail_WhenDuplicateRowsInFile() throws Exception {
        // Two identical rows
        List<String[]> data = new ArrayList<>();
        String[] row = {"Engineering", "Software Engineering", "", "", "Head of Eng", "", "", ""};
        data.add(row);
        data.add(row); // Duplicate

        byte[] excelBytes = createExcelFromData(data);
        MockMultipartFile file = new MockMultipartFile("file", "import.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", excelBytes);

        // Validation stub
        when(validationService.isNullOrBlank(any())).thenAnswer(inv -> {
            String s = inv.getArgument(0);
            return s == null || s.trim().isEmpty();
        });

        mockMvc.perform(multipart("/api/career-pathway/import").file(file).principal(authentication))
                .andExpect(status().isBadRequest());
    }

    @Test
    void import_ShouldUpdateExisting_WhenNameMatches() throws Exception {
        List<String[]> data = new ArrayList<>(Collections.singleton(new String[]{
                "Engineering", "Software Engineering", "New Desc", "", "Head of Eng", "", "", ""
        }));
        byte[] excelBytes = createExcelFromData(data);
        MockMultipartFile file = new MockMultipartFile("file", "import.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", excelBytes);

        // Validation Stub
        when(validationService.isNullOrBlank(any())).thenAnswer(inv -> {
            String s = inv.getArgument(0);
            return s == null || s.trim().isEmpty();
        });

        // 1. Mock Existing Data
        when(orgChartService.findAllByIsDeletedIsFalse()).thenReturn(List.of(mockOrg));
        when(roleService.getAllByDeletedIsFalse()).thenReturn(List.of(mockRootRole));

        // 2. IMPORTANT: Return the existing pathway so Controller detects it as an UPDATE
        when(careerPathwayService.getAllByIsDeletedIsFalse()).thenReturn(List.of(mockPathway));

        // 3. Mock the Update call
        when(careerPathwayService.createAndUpdateAll(anyList())).thenReturn(List.of(mockPathway));

        mockMvc.perform(multipart("/api/career-pathway/import").file(file).principal(authentication))
                .andExpect(status().isOk());

        // Verify that createAndUpdateAll was called (which handles updates in bulk)
        // And check that the description on the captured object was updated (you can use ArgumentCaptor for strict check)
        verify(careerPathwayService).createAndUpdateAll(argThat(list ->
                list.get(0).getDescription().equals("New Desc")
        ));
    }

    @Test
    void import_ShouldSoftDelete_WhenFlaggedInFile() throws Exception {
        List<String[]> data = new ArrayList<>(Collections.singleton(
                new String[]{
                        "Engineering", "Software Engineering", "", "", "Head of Eng", "", "", "Yes"
                }
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
        when(roleService.getAllByDeletedIsFalse()).thenReturn(List.of(mockRootRole));
        when(careerPathwayService.getAllByIsDeletedIsFalse()).thenReturn(List.of(mockPathway));

        mockMvc.perform(multipart("/api/career-pathway/import").file(file).principal(authentication))
                .andExpect(status().isOk());

        // Verify the DTO passed to service has deleted=true
        verify(careerPathwayService).createAndUpdateAll(argThat(list ->
                list.get(0).isDeleted()
        ));
    }

    @Test
    void import_ShouldRename_WhenNewNameProvided() throws Exception {
        List<String[]> data = new ArrayList<>(Collections.singleton(new String[]{
                "Engineering", "Software Engineering", "", "", "Head of Eng", "", "DevOps", ""
        }));
        byte[] excelBytes = createExcelFromData(data);
        MockMultipartFile file = new MockMultipartFile("file", "import.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", excelBytes);

        when(validationService.isNullOrBlank(any())).thenAnswer(inv -> {
            String s = inv.getArgument(0);
            return s == null || s.trim().isEmpty();
        });

        // Existing record
        when(orgChartService.findAllByIsDeletedIsFalse()).thenReturn(List.of(mockOrg));
        when(roleService.getAllByDeletedIsFalse()).thenReturn(List.of(mockRootRole));
        when(careerPathwayService.getAllByIsDeletedIsFalse()).thenReturn(List.of(mockPathway));

        mockMvc.perform(multipart("/api/career-pathway/import").file(file).principal(authentication))
                .andExpect(status().isOk());

        // Verify name changed
        verify(careerPathwayService).createAndUpdateAll(argThat(list ->
                list.get(0).getName().equals("DevOps")
        ));
    }

    @Test
    void import_ShouldFail_WhenRenamingAndDeletingSimultaneously() throws Exception {
        List<String[]> data = new ArrayList<>(Collections.singleton(
                new String[]{
                        "Engineering", "Software Engineering", "", "", "Head of Eng", "", "New Name", "Yes"
                }
        ));
        byte[] excelBytes = createExcelFromData(data);
        MockMultipartFile file = new MockMultipartFile("file", "import.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", excelBytes);

        when(validationService.isNullOrBlank(any())).thenAnswer(inv -> {
            String s = inv.getArgument(0);
            return s == null || s.trim().isEmpty();
        });

        when(orgChartService.findAllByIsDeletedIsFalse()).thenReturn(List.of(mockOrg));
        when(roleService.getAllByDeletedIsFalse()).thenReturn(List.of(mockRootRole));
        when(careerPathwayService.getAllByIsDeletedIsFalse()).thenReturn(List.of(mockPathway));

        mockMvc.perform(multipart("/api/career-pathway/import").file(file).principal(authentication))
                .andExpect(status().isBadRequest()); // Action Conflict
    }

    @Test
    void import_ShouldHandle_NumericAndBooleanCells() throws Exception {
        byte[] excelBytes;
        try (Workbook workbook = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sheet = workbook.createSheet("Sheet 1");
            Row header = sheet.createRow(0);
            String[] headers = {
                    "Department Name", "Career Pathway Name", "Career Pathway Description",
                    "Tags", "Root Role Name", "Parent Role Name > Child Role Name",
                    "New Career Pathway Name", "To Be Deleted"
            };
            for (int i = 0; i < headers.length; i++) header.createCell(i).setCellValue(headers[i]);

            Row row = sheet.createRow(1);
            // 0: Dept (String)
            row.createCell(0).setCellValue("Engineering");
            // 1: Name (Numeric - e.g., "101")
            row.createCell(1).setCellValue(101);
            // 2: Desc (Boolean - e.g., "true")
            row.createCell(2).setCellValue(true);
            // 3: Tags
            row.createCell(3).setCellValue("Tag1");
            // 4: Root
            row.createCell(4).setCellValue("Head");
            // others empty...

            workbook.write(out);
            excelBytes = out.toByteArray();
        }

        MockMultipartFile file = new MockMultipartFile("file", "types.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", excelBytes);

        when(validationService.isNullOrBlank(any())).thenAnswer(inv -> {
            Object arg = inv.getArgument(0);
            return arg == null || arg.toString().trim().isEmpty();
        });

        // Mock dependencies to allow processing to proceed until we hit a logic block or finish
        // We expect it to try and process "101" as the name and "true" as description
        when(orgChartService.findAllByIsDeletedIsFalse()).thenReturn(List.of(mockOrg));
        RoleDto head = new RoleDto();
        head.setName("Head");
        head.setId(100L);
        head.setOrgChart(mockOrg);

        when(roleService.getAllByDeletedIsFalse()).thenReturn(List.of(head));

        // Return empty so it tries to create
        when(careerPathwayService.getAllByIsDeletedIsFalse()).thenReturn(Collections.emptyList());

        CareerPathwayDto createdDto = new CareerPathwayDto();
        createdDto.setName("101");
        createdDto.setDescription("true");
        createdDto.setId(1L);

        when(careerPathwayService.createAndUpdateAll(anyList())).thenReturn(List.of(createdDto));

        TrackDto tagDto = new TrackDto();
        tagDto.setId(55L);
        tagDto.setTrack("Tag1");

        when(trackService.createAll(anyList())).thenReturn(List.of(tagDto));

        mockMvc.perform(multipart("/api/career-pathway/import").file(file).principal(authentication))
                .andExpect(status().isOk());

        // Verify it converted the number 101 to string "101"
        verify(careerPathwayService).createAndUpdateAll(argThat(list ->
                list.get(0).getName().equals("101") && list.get(0).getDescription().equals("true")
        ));
    }

    @Test
    void import_ShouldFail_WhenTagIsTooLong() throws Exception {
        String longTag = "a".repeat(101);
        List<String[]> data = new ArrayList<>(Collections.singleton(new String[]{
                "Engineering", "Path", "", longTag, "Head", "", "", ""
        }));

        byte[] excelBytes = createExcelFromData(data);
        MockMultipartFile file = new MockMultipartFile("file", "import.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", excelBytes);

        when(validationService.isNullOrBlank(any())).thenReturn(false);
        // Setup deps to pass early checks
        when(orgChartService.findAllByIsDeletedIsFalse()).thenReturn(List.of(mockOrg));
        when(roleService.getAllByDeletedIsFalse()).thenReturn(List.of(mockRootRole));

        mockMvc.perform(multipart("/api/career-pathway/import").file(file).principal(authentication))
                .andExpect(status().isBadRequest());
    }

    @Test
    void import_ShouldFail_WhenParentChildFormatIsInvalid() throws Exception {
        List<String[]> data = new ArrayList<>(Collections.singleton(new String[]{
                "Engineering", "Path", "", "", "Head", "Parent Only", "", "" // Missing ">"
        }));
        byte[] excelBytes = createExcelFromData(data);
        MockMultipartFile file = new MockMultipartFile("file", "import.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", excelBytes);

        when(validationService.isNullOrBlank(any())).thenReturn(false);
        when(orgChartService.findAllByIsDeletedIsFalse()).thenReturn(List.of(mockOrg));
        when(roleService.getAllByDeletedIsFalse()).thenReturn(List.of(mockRootRole));

        mockMvc.perform(multipart("/api/career-pathway/import").file(file).principal(authentication))
                .andExpect(status().isBadRequest());
    }

    @Test
    void import_ShouldFail_WhenCycleDetectedInFile() throws Exception {
        // Cycle: A -> B -> A
        List<String[]> data = new ArrayList<>(Collections.singleton(
                new String[]{
                        "Engineering", "Path", "", "", "Head", "Head > Senior; Senior > Head", "", ""
                }
        ));

        byte[] excelBytes = createExcelFromData(data);
        MockMultipartFile file = new MockMultipartFile("file", "import.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", excelBytes);

        when(validationService.isNullOrBlank(any())).thenReturn(false);
        when(orgChartService.findAllByIsDeletedIsFalse()).thenReturn(List.of(mockOrg));

        RoleDto head = new RoleDto();
        head.setId(100L);
        head.setName("Head");
        head.setOrgChart(mockOrg);
        RoleDto senior = new RoleDto();
        senior.setId(200L);
        senior.setName("Senior");
        senior.setOrgChart(mockOrg);

        when(roleService.getAllByDeletedIsFalse()).thenReturn(List.of(head, senior));

        mockMvc.perform(multipart("/api/career-pathway/import").file(file).principal(authentication))
                .andExpect(status().isBadRequest());
        // You can assert the message contains the row number if needed
    }

    @Test
    void import_ShouldRemoveTags_WhenMissingInFile() throws Exception {
        // Excel has NO tags (Cell 3 is "")
        List<String[]> data = new ArrayList<>(Collections.singleton(new String[]{
                "Engineering", "Existing Path", "", "", "Head of Eng", "", "", ""
        }));

        byte[] excelBytes = createExcelFromData(data);
        MockMultipartFile file = new MockMultipartFile("file", "import.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", excelBytes);

        when(validationService.isNullOrBlank(any())).thenAnswer(inv -> {
            String s = inv.getArgument(0);
            return s == null || s.trim().isEmpty();
        });

        // 1. Existing Pathway
        CareerPathwayDto path = new CareerPathwayDto();
        path.setId(1L);
        path.setName("Existing Path");
        path.setOrgChart(mockOrg);
        path.setRootRole(mockRootRole);
        when(careerPathwayService.getAllByIsDeletedIsFalse()).thenReturn(List.of(path));

        // 2. Existing Tags (Tag "Old" exists in DB)
        TrackDto oldTag = new TrackDto();
        oldTag.setId(55L);
        oldTag.setTrack("Old");
        CareerPathwayTrackDto cpTrack = new CareerPathwayTrackDto();
        cpTrack.setId(new CareerPathwayTrackId(1L, 55L));
        cpTrack.setTrack(oldTag);

        // This ensures 'allAssignedTag' in the controller contains the old tag
        when(careerPathwayTrackService.findAll()).thenReturn(List.of(cpTrack));

        // 3. Mock Setup for Validation/Maps
        when(orgChartService.findAllByIsDeletedIsFalse()).thenReturn(List.of(mockOrg));
        when(roleService.getAllByDeletedIsFalse()).thenReturn(List.of(mockRootRole));

        // Mock the update call (returning the same path is fine)
        when(careerPathwayService.createAndUpdateAll(anyList())).thenReturn(List.of(path));

        // 4. Mock Tag Deletion Logic dependencies
        // The controller calls findAllByTrackIdIn to check if tags are still in use elsewhere
        when(careerPathwayTrackService.findAllByTrackIdIn(anySet())).thenReturn(Collections.emptyList());

        mockMvc.perform(multipart("/api/career-pathway/import").file(file).principal(authentication))
                .andExpect(status().isOk());

        // Verify deletion was called for the missing tag (Difference between DB "Old" and Excel "")
        verify(careerPathwayTrackService).deleteAllById(anySet());
    }

    @Test
    void import_ShouldRemoveRoleLinks_WhenMissingInFile() throws Exception {
        // Excel has NO parent-child roles
        List<String[]> data = new ArrayList<>(Collections.singleton(new String[]{
                "Engineering", "Existing Path", "", "", "Head of Eng", "", "", ""
        }));

        byte[] excelBytes = createExcelFromData(data);
        MockMultipartFile file = new MockMultipartFile("file", "import.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", excelBytes);

        when(validationService.isNullOrBlank(any())).thenAnswer(inv -> {
            String s = inv.getArgument(0);
            return s == null || s.trim().isEmpty();
        });

        CareerPathwayDto path = new CareerPathwayDto();
        path.setId(1L);
        path.setName("Existing Path");
        path.setOrgChart(mockOrg);
        path.setRootRole(mockRootRole);
        when(careerPathwayService.getAllByIsDeletedIsFalse()).thenReturn(List.of(path));

        // Existing Role Link in DB
        CareerPathwayRoleDto existingRole = new CareerPathwayRoleDto();
        existingRole.setId(new CareerPathwayRoleId(1L, 100L, 200L));
        when(careerPathwayRoleService.getAll()).thenReturn(List.of(existingRole));

        when(orgChartService.findAllByIsDeletedIsFalse()).thenReturn(List.of(mockOrg));
        when(roleService.getAllByDeletedIsFalse()).thenReturn(List.of(mockRootRole));
        when(careerPathwayService.createAndUpdateAll(anyList())).thenReturn(List.of(path));

        mockMvc.perform(multipart("/api/career-pathway/import").file(file).principal(authentication))
                .andExpect(status().isOk());

        // Verify role link deletion
        verify(careerPathwayRoleService).deleteAllByIdIn(anySet());
    }

    // --- Helpers ---

    private byte[] createExcelFromData(List<String[]> rowsData) throws IOException {
        try (Workbook workbook = new XSSFWorkbook();
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sheet = workbook.createSheet("Sheet 1");

            Row header = sheet.createRow(0);
            String[] headers = {
                    "Department Name", "Career Pathway Name", "Career Pathway Description",
                    "Tags", "Root Role Name", "Parent Role Name > Child Role Name",
                    "New Career Pathway Name", "To Be Deleted"
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