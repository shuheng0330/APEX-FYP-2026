package com.tbm.careerpathlearning.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tbm.careerpathlearning.dto.*;
import com.tbm.careerpathlearning.enums.ProposalRole;
import com.tbm.careerpathlearning.enums.ProposalStatus;
import com.tbm.careerpathlearning.enums.ProposalType;
import com.tbm.careerpathlearning.model.*;
import com.tbm.careerpathlearning.service.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.MessageSource;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.*;

import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.hasSize;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(CompetencyAssignmentCollaborationController.class)
@AutoConfigureMockMvc(addFilters = false)
public class CompetencyAssignmentCollaborationControllerTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;

    // Core services used in the specific method under test
    @MockitoBean
    private CompetencyService competencyService;
    @MockitoBean
    private CompetencyProposalService competencyProposalService;
    @MockitoBean
    private RoleCompetencyProposalItemService roleCompetencyProposalItemService;
    @MockitoBean
    private TokenService tokenService; // Required for Security Config

    // Other dependencies required to load the ApplicationContext for this Controller
    @MockitoBean
    private MessageSource messageSource;
    @MockitoBean
    private ValidationService validationService;
    @MockitoBean
    private StaffService staffService;
    @MockitoBean
    private RoleService roleService;
    @MockitoBean
    private EmailService emailService;
    @MockitoBean
    private ProposalService proposalService;
    @MockitoBean
    private CompTagService compTagService;
    @MockitoBean
    private CompetencyCompTagProposalService competencyCompTagProposalService;
    @MockitoBean
    private ProposalParticipantService proposalParticipantService;
    @MockitoBean
    private RoleAuthorityService roleAuthorityService;
    @MockitoBean
    private AuthorityService authorityService;
    @MockitoBean
    private CompetencyCompTagService competencyCompTagService;
    @MockitoBean
    private JobScopeService jobScopeService;
    @MockitoBean
    private RoleCompetencyProposalService roleCompetencyProposalService;
    @MockitoBean
    private RoleJobScopeProposalService roleJobScopeProposalService;
    @MockitoBean
    private RoleCompetencyItemService roleCompetencyItemService;
    @MockitoBean
    private RoleJobScopeService roleJobScopeService;
    @MockitoBean
    private RoleCompetencyService roleCompetencyService;

    private Authentication authentication;
    private UUID userUUID;

    @BeforeEach
    void setUp() {
        userUUID = UUID.randomUUID();
        authentication = mock(Authentication.class);
        when(authentication.getName()).thenReturn(userUUID.toString());
        when(authentication.getPrincipal()).thenReturn(userUUID.toString());
        doReturn(List.of(new SimpleGrantedAuthority("CAN_MANAGE_ROLE")))
                .when(authentication).getAuthorities();
    }

    @Test
    @WithMockUser(authorities = {"CAN_MANAGE_ROLE"})
    void getCompetencyForProposing_NoParams_ShouldReturnPermanentAndParticipated() throws Exception {
        // --- 1. Data Setup ---
        CompetencyDto permComp = new CompetencyDto();
        permComp.setId(100L);
        permComp.setName("Java Core");

        ProposalParticipantId propId = new ProposalParticipantId(1L, userUUID);
        CompetencyProposalDto proposedComp = new CompetencyProposalDto();
        proposedComp.setId(propId);
        proposedComp.setName("New Cloud Skill");

        StaffDto staffDto = new StaffDto();
        staffDto.setEmail("user@tbm.com");
        proposedComp.setStaff(staffDto);

        // --- 2. Service Mocks ---
        when(competencyService.findAllByIsDeletedIsFalse())
                .thenReturn(List.of(permComp));

        when(competencyProposalService.getAllByStaffId(userUUID))
                .thenReturn(List.of(proposedComp));

        // --- 3. Execution & Verification ---
        mockMvc.perform(get("/api/competency-assignment-collaboration/creation-required-competency")
                        .principal(authentication)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))

                .andExpect(jsonPath("$[?(@.name == 'Java Core')].id.proposal").value(false))
                .andExpect(jsonPath("$[?(@.name == 'Java Core')].id.competencyId").value(100))

                // Verify Proposed Item (isProposal = true)
                .andExpect(jsonPath("$[?(@.name == 'New Cloud Skill')].id.proposal").value(true))
                .andExpect(jsonPath("$[?(@.name == 'New Cloud Skill')].proposerEmail").value("user@tbm.com"));

        verify(roleCompetencyProposalItemService, never()).findAllByRoleCompetencyProposalId(any(), any(), any());
    }

    @Test
    @WithMockUser(authorities = {"CAN_MANAGE_ROLE"})
    void getCompetencyForProposing_WithParams_ShouldIncludeConsumedProposals() throws Exception {
        // --- 1. Data Setup ---
        Long proposalId = 50L;
        Long roleId = 10L;
        UUID staffId = UUID.randomUUID();

        // Mock empty permanent list to isolate logic
        when(competencyService.findAllByIsDeletedIsFalse()).thenReturn(Collections.emptyList());

        // Mock empty user proposals to isolate logic
        when(competencyProposalService.getAllByStaffId(userUUID)).thenReturn(Collections.emptyList());

        // Setup "Consumed" Proposal (Already assigned to this role in this collaboration)
        ProposalParticipantId consumedId = new ProposalParticipantId(99L, UUID.randomUUID());

        CompetencyProposalDto consumedComp = new CompetencyProposalDto();
        consumedComp.setId(consumedId);
        consumedComp.setName("Consumed Skill");
        consumedComp.setStaff(new StaffDto());
        consumedComp.getStaff().setEmail("consumed@tbm.com");

        RoleCompetencyProposalItemDto itemDto = new RoleCompetencyProposalItemDto();
        itemDto.setCompetencyProposal(consumedComp);

        // --- 2. Service Mocks ---
        // This service call only happens when params are present
        when(roleCompetencyProposalItemService.findAllByRoleCompetencyProposalId(proposalId, roleId, staffId))
                .thenReturn(List.of(itemDto));

        // --- 3. Execution & Verification ---
        mockMvc.perform(get("/api/competency-assignment-collaboration/creation-required-competency")
                        .principal(authentication)
                        .param("proposalId", proposalId.toString())
                        .param("roleId", roleId.toString())
                        .param("staffId", staffId.toString())
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].name").value("Consumed Skill"))
                .andExpect(jsonPath("$[0].id.proposal").value(true));

        // Logic check: Verify the specific service method was called
        verify(roleCompetencyProposalItemService).findAllByRoleCompetencyProposalId(proposalId, roleId, staffId);
    }

    @Test
    @WithMockUser(authorities = {"CAN_MANAGE_ROLE"})
    void proposeCompetencyAssignment_InvalidRequest_ShouldThrowBadRequest() throws Exception {
        // Case 1: Missing Role ID
        ProposeCompetencyAssignmentRequest reqNoRole = createValidAssignmentRequest();
        reqNoRole.setRoleId(null);

        when(messageSource.getMessage(anyString(), any(), any())).thenReturn("Error Message");

        mockMvc.perform(post("/api/competency-assignment-collaboration")
                        .principal(authentication)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(reqNoRole)))
                .andExpect(status().isBadRequest());

        // Case 2: Self-Assignment (Creator is Reviewer)
        ProposeCompetencyAssignmentRequest reqSelfReview = createValidAssignmentRequest();
        reqSelfReview.setReviewerList(List.of(userUUID)); // Conflict: User cannot review own proposal

        when(messageSource.getMessage(anyString(), any(), any())).thenReturn("Error Message");

        mockMvc.perform(post("/api/competency-assignment-collaboration")
                        .principal(authentication)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(reqSelfReview)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser(authorities = {"CAN_MANAGE_ROLE"})
    void proposeCompetencyAssignment_FullFlow_ShouldCreateAllRecordsAndSendEmails() throws Exception {
        // --- 1. Data Setup ---
        Long roleId = 10L;
        UUID reviewerId = UUID.randomUUID();
        UUID proposerId = UUID.randomUUID();

        ProposeCompetencyAssignmentRequest req = createValidAssignmentRequest();
        req.setReviewerList(List.of(reviewerId));
        req.setProposerList(List.of(proposerId));

        // Add 1 Permanent Competency
        req.getCompetencyList().add(createPermCompetencyMap(100L, 5));
        // Add 1 Proposed Competency
        Long compProposalId = 50L;
        UUID compProposerId = UUID.randomUUID();
        req.getCompetencyList().add(createProposedCompetencyMap(compProposalId, compProposerId, 3));

        // Mock Objects
        RoleDto roleDto = new RoleDto();
        roleDto.setId(roleId);
        roleDto.setName("Dev");
        OrgChartDto orgDto = new OrgChartDto();
        orgDto.setName("IT Dept");
        roleDto.setOrgChart(orgDto);

        StaffDto userStaff = new StaffDto();
        userStaff.setId(userUUID);
        userStaff.setEmail("admin@tbm.com");
        StaffDto reviewerStaff = new StaffDto();
        reviewerStaff.setId(reviewerId);
        reviewerStaff.setEmail("rev@tbm.com");
        StaffDto proposerStaff = new StaffDto();
        proposerStaff.setId(proposerId);
        proposerStaff.setEmail("prop@tbm.com");

        ProposalDto createdProposal = new ProposalDto();
        createdProposal.setId(999L);

        // Mock Permanent Comp
        CompetencyDto permComp = new CompetencyDto();
        permComp.setId(100L);
        permComp.setName("Java");

        // Mock Proposed Comp
        CompetencyProposalDto propComp = new CompetencyProposalDto();
        propComp.setId(new ProposalParticipantId(compProposalId, compProposerId));
        propComp.setName("Cloud");

        // --- 2. Service Mocks ---
        when(roleService.getAllById(roleId)).thenReturn(roleDto);

        // Mock Staff Retrieval (Must return all participants including current user)
        when(staffService.findAllByIdIn(argThat(set -> set.contains(userUUID)))).thenReturn(List.of(userStaff, reviewerStaff, proposerStaff));

        // Mock Competency Retrievals
        when(competencyService.findAllByIsDeletedIsFalseAndIdIn(anySet())).thenReturn(List.of(permComp));
        when(competencyProposalService.getAllByIdIn(anySet())).thenReturn(List.of(propComp));

        // Mock Proposal Creation
        when(proposalService.createProposal(any())).thenReturn(createdProposal);

        // Mock Participant Creation (Return list to drive access grant logic)
        ProposalParticipantDto partDto = new ProposalParticipantDto();
        partDto.setStaff(proposerStaff);
        proposerStaff.setRole(new RoleDto()); // Prevent NPE in access grant
        when(proposalParticipantService.createProposalParticipants(anyList())).thenReturn(List.of(partDto));

        // Mock Access Grant
        when(authorityService.findByName(any())).thenReturn(new AuthorityDto());

        // Mock Role Competency Proposal Creation (Needed for downstream flatMaps)
        RoleCompetencyProposalDto rcpDto = new RoleCompetencyProposalDto();
        rcpDto.setId(new RoleCompetencyProposalId(999L, roleId, userUUID)); // Initiator copy
        rcpDto.setStaff(userStaff);
        rcpDto.setRole(roleDto);
        rcpDto.setProposal(createdProposal);
        when(roleCompetencyProposalService.createAll(anyList())).thenReturn(List.of(rcpDto));

        // Mock Job Scope Creation
        JobScopeDto jsDto = new JobScopeDto();
        jsDto.setId(55L);
        when(jobScopeService.createAll(anyList())).thenReturn(List.of(jsDto));

        // Mock Item Creation Returns (for email map generation)
        RoleCompetencyItemDto rciDto = new RoleCompetencyItemDto();
        rciDto.setCompetency(permComp);
        rciDto.setWeightage(5);
        when(roleCompetencyItemService.createAll(anyList())).thenReturn(List.of(rciDto));

        RoleCompetencyProposalItemDto rcpiDto = new RoleCompetencyProposalItemDto();
        rcpiDto.setCompetencyProposal(propComp);
        rcpiDto.setWeightage(3);
        when(roleCompetencyProposalItemService.createAll(anyList())).thenReturn(List.of(rcpiDto));

        // Mock Success Message
        when(messageSource.getMessage(anyString(), any(), any())).thenReturn("Success");

        // --- 3. Execution ---
        mockMvc.perform(post("/api/competency-assignment-collaboration")
                        .principal(authentication)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Success"));

        // --- 4. Verification ---
        // A. Verify Proposal Created
        verify(proposalService).createProposal(argThat(p -> p.getType() == ProposalType.ROLE_COMPETENCY));

        // B. Verify Participants (Initiator + Reviewer + Proposer = 3)
        verify(proposalParticipantService).createProposalParticipants(argThat(list -> list.size() == 3));

        // C. Verify Access Granted to Proposer Role
        verify(roleAuthorityService).createAll(anyList());

        // D. Verify Job Scopes Linked
        verify(roleJobScopeProposalService).createAll(anyList());

        // E. Verify Permanent Competency Linked with correct weightage
        verify(roleCompetencyItemService).createAll(argThat(list ->
                list.get(0).getWeightage() == 5 && list.get(0).getCompetency().getId().equals(100L)
        ));

        // F. Verify Proposed Competency Linked with correct weightage
        verify(roleCompetencyProposalItemService).createAll(argThat(list ->
                list.get(0).getWeightage() == 3 && list.get(0).getCompetencyProposal().getId().equals(propComp.getId())
        ));

        // G. Verify Emails Sent (1 Reviewer, 1 Proposer)
        // Reviewer Email
        verify(emailService).sendCompetencyAssignmentProposalReviewInvitationEmail(
                eq("rev@tbm.com"), any(), eq("IT Dept"), eq("Dev"), any(), any(), anyMap(), any(), any()
        );
        // Proposer Email
        verify(emailService).sendCompetencyAssignmentProposalProposeInvitationEmail(
                eq("prop@tbm.com"), any(), eq("IT Dept"), eq("Dev"), any(), any(), anyMap(), any(), any()
        );
    }

    @Test
    @WithMockUser(authorities = {"CAN_MANAGE_ROLE"})
    void proposeCompetencyAssignment_DuplicateNames_ShouldThrowBadRequest() throws Exception {
        // Test ensuring we can't assign "Java" (Permanent) and "Java" (Proposed) simultaneously
        ProposeCompetencyAssignmentRequest req = createValidAssignmentRequest();
        req.getCompetencyList().add(createPermCompetencyMap(1L, 1));
        req.getCompetencyList().add(createProposedCompetencyMap(2L, UUID.randomUUID(), 1));

        // Mock both having name "Java"
        CompetencyDto perm = new CompetencyDto();
        perm.setName("Java");
        when(competencyService.findAllByIsDeletedIsFalseAndIdIn(anySet())).thenReturn(List.of(perm));

        CompetencyProposalDto prop = new CompetencyProposalDto();
        prop.setName("Java");
        when(competencyProposalService.getAllByIdIn(anySet())).thenReturn(List.of(prop));

        // Mocks for basic setup
        when(roleService.getAllById(any())).thenReturn(new RoleDto());
        StaffDto userStaff = new StaffDto();
        userStaff.setId(userUUID);
        when(staffService.findAllByIdIn(any())).thenReturn(List.of(userStaff));

        when(messageSource.getMessage(anyString(), any(), any())).thenReturn("Error Message");

        mockMvc.perform(post("/api/competency-assignment-collaboration")
                        .principal(authentication)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest()); // Conflict Error (Duplicates detected)
    }

    @Test
    @WithMockUser(authorities = {"CAN_MANAGE_ROLE"})
    void proposeCompetencyAssignment_NoJobScopes_ShouldSkipJobScopeCreation() throws Exception {
        ProposeCompetencyAssignmentRequest req = createValidAssignmentRequest();
        req.setJobScopeList(Collections.emptyList()); // Empty Scopes

        // Basic Mocks to pass validation and flow
        RoleDto roleDto = new RoleDto();
        roleDto.setId(10L);
        roleDto.setOrgChart(new OrgChartDto());
        when(roleService.getAllById(any())).thenReturn(roleDto);

        StaffDto userStaff = new StaffDto();
        userStaff.setId(userUUID);
        userStaff.setRole(new RoleDto()); // Role needed for updateGrantedAccess logic
        when(staffService.findAllByIdIn(any())).thenReturn(List.of(userStaff));

        when(proposalService.createProposal(any())).thenReturn(new ProposalDto());

        // Ensure the participant list contains staff with roles to trigger the loop in updateGrantedAccess
        when(proposalParticipantService.createProposalParticipants(any())).thenReturn(List.of(
                new ProposalParticipantDto(null, null, userStaff, null, false, null, null, null, null)
        ));

        AuthorityDto authDto = new AuthorityDto();
        authDto.setId(55L);
        when(authorityService.findByName(any())).thenReturn(authDto);
        // -------------------------------------------------

        // Mock Role Proposal Return
        RoleCompetencyProposalDto rcp = new RoleCompetencyProposalDto();
        rcp.setId(new RoleCompetencyProposalId(1L, 1L, userUUID));
        rcp.setStaff(userStaff);
        rcp.setRole(roleDto);
        when(roleCompetencyProposalService.createAll(any())).thenReturn(List.of(rcp));

        when(messageSource.getMessage(anyString(), any(), any())).thenReturn("Success");

        // Execution
        mockMvc.perform(post("/api/competency-assignment-collaboration")
                        .principal(authentication)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk());

        // Verification
        verify(jobScopeService, never()).createAll(anyList());
        verify(roleJobScopeProposalService, never()).createAll(anyList());
    }

    @Test
    @WithMockUser(authorities = {"CAN_MANAGE_ROLE"})
    void competencyAssignmentProposalOverview_NoOngoingProposals_ShouldReturnEmptyList() throws Exception {
        // Setup: No ongoing proposals
        when(proposalService.getAllByType(ProposalType.ROLE_COMPETENCY)).thenReturn(Collections.emptyList());

        mockMvc.perform(get("/api/competency-assignment-collaboration/overview")
                        .principal(authentication))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(0)));
    }

    @Test
    @WithMockUser(authorities = {"CAN_MANAGE_ROLE"})
    void competencyAssignmentProposalOverview_AsReviewer_ShouldSeeAllDetails() throws Exception {
        // --- 1. Data Setup ---
        Long proposalId = 1L;
        Long roleId = 10L;
        UUID targetStaffId = UUID.randomUUID(); // The person being assigned

        // A. Proposal (Ongoing)
        ProposalDto pDto = new ProposalDto();
        pDto.setId(proposalId);
        pDto.setStatus(ProposalStatus.ONGOING);
        when(proposalService.getAllByType(ProposalType.ROLE_COMPETENCY)).thenReturn(List.of(pDto));

        // B. Participants (User is REVIEWER)
        ProposalParticipantDto meAsReviewer = createParticipant(proposalId, userUUID, ProposalRole.REVIEWER);
        ProposalParticipantDto targetUser = createParticipant(proposalId, targetStaffId, ProposalRole.PROPOSER);
        when(proposalParticipantService.getAllByProposalIdIn(anySet())).thenReturn(List.of(meAsReviewer, targetUser));

        // C. Role Competency Proposal (The main record)
        RoleCompetencyProposalDto rcpDto = createRcpDto(proposalId, roleId, targetStaffId);
        when(roleCompetencyProposalService.getAllByProposalIdIn(anySet())).thenReturn(List.of(rcpDto));

        // D. Job Scopes
        RoleJobScopeProposalDto rjsDto = new RoleJobScopeProposalDto();
        rjsDto.setId(new RoleJobScopeProposalId(proposalId, roleId, targetStaffId, 100L));
        rjsDto.setJobScope(new JobScopeDto("Scope 1", false, null, null, null, null));
        when(roleJobScopeProposalService.findAllByProposalIdIn(anySet())).thenReturn(List.of(rjsDto));

        // E. Permanent Competencies (Assigned)
        RoleCompetencyItemDto rciDto = new RoleCompetencyItemDto();
        rciDto.setId(new RoleCompetencyItemId(proposalId, roleId, targetStaffId, 200L));
        rciDto.setCompetency(new CompetencyDto());
        rciDto.getCompetency().setId(200L);
        rciDto.getCompetency().setName("Perm Comp");
        rciDto.setWeightage(5);
        when(roleCompetencyItemService.findAllByProposalIdIn(anySet())).thenReturn(List.of(rciDto));

        // F. Proposed Competencies (Assigned)
        RoleCompetencyProposalItemDto rcpiDto = new RoleCompetencyProposalItemDto();
        rcpiDto.setId(new RoleCompetencyProposalItemId(proposalId, roleId, targetStaffId, new ProposalParticipantId(50L, userUUID)));
        rcpiDto.setCompetencyProposal(new CompetencyProposalDto());
        rcpiDto.getCompetencyProposal().setName("Prop Comp");
        rcpiDto.setWeightage(3);
        when(roleCompetencyProposalItemService.findAllByProposalIdIn(anySet())).thenReturn(List.of(rcpiDto));

        // --- 2. Execution & Verification ---
        mockMvc.perform(get("/api/competency-assignment-collaboration/overview")
                        .principal(authentication))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                // Verify Structure
                .andExpect(jsonPath("$[0].isReviewer").value(true))
                .andExpect(jsonPath("$[0].totalWeightage").value(8)) // 5 + 3
                // Verify Aggregated Lists
                .andExpect(jsonPath("$[0].assignedJobScopes[0].jobScope").value("Scope 1"))
                .andExpect(jsonPath("$[0].assignedCompetencies", hasSize(2)));
    }

    @Test
    @WithMockUser(authorities = {"CAN_MANAGE_ROLE"})
    void competencyAssignmentProposalOverview_AsProposer_ShouldSeeOwnRecordOnly() throws Exception {
        // --- 1. Data Setup ---
        Long proposalId = 1L;
        Long roleId = 10L;
        UUID otherStaffId = UUID.randomUUID();

        // A. Proposal (Ongoing)
        ProposalDto pDto = new ProposalDto();
        pDto.setId(proposalId);
        pDto.setStatus(ProposalStatus.ONGOING);
        when(proposalService.getAllByType(ProposalType.ROLE_COMPETENCY)).thenReturn(List.of(pDto));

        // B. Participants (User is PROPOSER)
        ProposalParticipantDto meAsProposer = createParticipant(proposalId, userUUID, ProposalRole.PROPOSER);
        ProposalParticipantDto otherProposer = createParticipant(proposalId, otherStaffId, ProposalRole.PROPOSER);
        when(proposalParticipantService.getAllByProposalIdIn(anySet())).thenReturn(List.of(meAsProposer, otherProposer));

        // C. Role Competency Proposals (Two records: Mine and Someone Else's)
        RoleCompetencyProposalDto myRecord = createRcpDto(proposalId, roleId, userUUID);
        RoleCompetencyProposalDto otherRecord = createRcpDto(proposalId, roleId, otherStaffId);

        when(roleCompetencyProposalService.getAllByProposalIdIn(anySet())).thenReturn(List.of(myRecord, otherRecord));

        // Mock empty sub-lists for simplicity
        when(roleJobScopeProposalService.findAllByProposalIdIn(any())).thenReturn(Collections.emptyList());
        when(roleCompetencyItemService.findAllByProposalIdIn(any())).thenReturn(Collections.emptyList());
        when(roleCompetencyProposalItemService.findAllByProposalIdIn(any())).thenReturn(Collections.emptyList());

        // --- 2. Execution & Verification ---
        mockMvc.perform(get("/api/competency-assignment-collaboration/overview")
                        .principal(authentication))
                .andExpect(status().isOk())
                // Verify Filtering Logic
                .andExpect(jsonPath("$", hasSize(1))) // Should only see MY record, not the other person's
                .andExpect(jsonPath("$[0].collaboratorEmail").value("email@test.com")) // Check properties from myRecord
                .andExpect(jsonPath("$[0].isReviewer").value(false));
    }

    @Test
    @WithMockUser(authorities = {"CAN_MANAGE_ROLE"})
    void competencyAssignmentProposalOverview_MixedRoles_ShouldAggregateBoth() throws Exception {
        // Scenario: User is Reviewer on P1, Proposer on P2
        Long p1 = 1L;
        Long p2 = 2L;

        ProposalDto p1Dto = new ProposalDto();
        p1Dto.setId(p1);
        p1Dto.setStatus(ProposalStatus.ONGOING);
        ProposalDto p2Dto = new ProposalDto();
        p2Dto.setId(p2);
        p2Dto.setStatus(ProposalStatus.ONGOING);
        when(proposalService.getAllByType(any())).thenReturn(List.of(p1Dto, p2Dto));

        // Participants
        ProposalParticipantDto reviewP1 = createParticipant(p1, userUUID, ProposalRole.REVIEWER);
        ProposalParticipantDto proposeP2 = createParticipant(p2, userUUID, ProposalRole.PROPOSER);
        when(proposalParticipantService.getAllByProposalIdIn(anySet())).thenReturn(List.of(reviewP1, proposeP2));

        // Records
        RoleCompetencyProposalDto record1 = createRcpDto(p1, 10L, UUID.randomUUID()); // I review this
        RoleCompetencyProposalDto record2 = createRcpDto(p2, 20L, userUUID); // I own this
        when(roleCompetencyProposalService.getAllByProposalIdIn(anySet())).thenReturn(List.of(record1, record2));

        // Empty Sub-lists
        when(roleJobScopeProposalService.findAllByProposalIdIn(any())).thenReturn(Collections.emptyList());
        when(roleCompetencyItemService.findAllByProposalIdIn(any())).thenReturn(Collections.emptyList());
        when(roleCompetencyProposalItemService.findAllByProposalIdIn(any())).thenReturn(Collections.emptyList());

        // Execution
        mockMvc.perform(get("/api/competency-assignment-collaboration/overview")
                        .principal(authentication))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2))) // Should see both
                // We can't guarantee order without sorting, but we expect one true and one false for isReviewer
                .andExpect(jsonPath("$[*].isReviewer", containsInAnyOrder(true, false)));
    }

    @Test
    @WithMockUser(authorities = {"CAN_MANAGE_ROLE"})
    void updateCompetencyProposal_InvalidRequest_ShouldThrowBadRequest() throws Exception {
        // Case 1: Null Body
        mockMvc.perform(put("/api/competency-assignment-collaboration/edit-competency-assignment-proposal")
                        .principal(authentication)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest());

        // Case 2: Empty Competency List
        EditCompetencyAssignmentProposalRequestDto req = createEditRequest();
        req.setCompetencyList(Collections.emptyList());

        when(messageSource.getMessage(anyString(), any(), any())).thenReturn("Error Message");

        mockMvc.perform(put("/api/competency-assignment-collaboration/edit-competency-assignment-proposal")
                        .principal(authentication)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser(authorities = {"CAN_MANAGE_ROLE"})
    void updateCompetencyProposal_FullUpdateFlow_ShouldUpdateAllEntities() throws Exception {
        // --- 1. Data Setup ---
        Long proposalId = 1L;
        Long roleId = 10L;
        UUID staffId = UUID.randomUUID(); // The staff being updated
        RoleCompetencyProposalId rcpId = new RoleCompetencyProposalId(proposalId, roleId, staffId);

        EditCompetencyAssignmentProposalRequestDto req = createEditRequest();
        req.setId(rcpId);

        // Change Description
        req.setDescription("New Description");

        // Update Job Scopes: Keep JS1, Add JS2, Remove JS3
        req.setJobScopeList(List.of("JS1", "JS2"));

        // Update Competencies:
        // 1. Add Permanent Comp (ID=100)
        req.getCompetencyList().add(createPermCompetencyMap(100L, 5));
        // 2. Update Proposed Comp (ID=P1) Weightage 3 -> 4
        ProposalParticipantId ppId1 = new ProposalParticipantId(50L, UUID.randomUUID());
        req.getCompetencyList().add(createProposedCompetencyMap(ppId1.getProposalId(), ppId1.getStaffId(), 4));
        // 3. (Implicit) Remove Proposed Comp (ID=P2) - Not adding it to request

        // --- 2. Mocking Existing State (Before Update) ---

        // Role Proposal
        RoleCompetencyProposalDto existingRcp = new RoleCompetencyProposalDto();
        existingRcp.setId(rcpId);
        existingRcp.setDescription("Old Description");
        existingRcp.setStaff(new StaffDto());
        existingRcp.setRole(new RoleDto());
        existingRcp.getRole().setOrgChart(new OrgChartDto()); // Prevent NPE
        existingRcp.getRole().getOrgChart().setName("Org");
        when(roleCompetencyProposalService.getById(rcpId)).thenReturn(existingRcp);
        when(roleCompetencyProposalService.update(any(), any())).thenReturn(existingRcp);

        // Participants (For Email)
        ProposalParticipantDto participant = new ProposalParticipantDto();
        participant.setId(new ProposalParticipantId(proposalId, userUUID));
        participant.setStaff(new StaffDto());
        participant.getStaff().setId(userUUID);

        UUID otherStaffId = UUID.randomUUID();
        ProposalParticipantDto otherParticipant = new ProposalParticipantDto();
        otherParticipant.setId(new ProposalParticipantId(proposalId, otherStaffId));
        otherParticipant.setStaff(new StaffDto());
        otherParticipant.getStaff().setEmail("other@tbm.com");
        otherParticipant.getStaff().setId(otherStaffId);

        when(proposalParticipantService.getAllByProposalId(proposalId)).thenReturn(List.of(participant, otherParticipant));

        // Job Scopes
        // Mock created DTOs for input strings
        JobScopeDto js1 = new JobScopeDto();
        js1.setId(1L);
        js1.setJobScope("JS1");
        JobScopeDto js2 = new JobScopeDto();
        js2.setId(2L);
        js2.setJobScope("JS2");
        when(jobScopeService.createAll(anyList())).thenReturn(List.of(js1, js2));

        // Mock currently assigned (JS1, JS3) -> JS3 should be removed
        RoleJobScopeProposalDto rjs1 = new RoleJobScopeProposalDto();
        rjs1.setId(new RoleJobScopeProposalId(proposalId, roleId, staffId, 1L));
        RoleJobScopeProposalDto rjs3 = new RoleJobScopeProposalDto();
        rjs3.setId(new RoleJobScopeProposalId(proposalId, roleId, staffId, 3L));
        when(roleJobScopeProposalService.getByRoleCompetencyProposalId(proposalId, roleId, staffId))
                .thenReturn(List.of(rjs1, rjs3));

        // Garbage Collection Check for JS3: Not used elsewhere
        when(roleJobScopeService.findAllByJobScopeIdIn(any())).thenReturn(Collections.emptyList());
        when(roleJobScopeProposalService.findAllByJobScopeIdIn(any())).thenReturn(Collections.emptyList());

        // Permanent Competencies
        CompetencyDto comp100 = new CompetencyDto();
        comp100.setId(100L);
        comp100.setName("Comp A");
        when(competencyService.findAllByIsDeletedIsFalse()).thenReturn(List.of(comp100));

        // Currently assigned: Empty (So 100 will be Added)
        when(roleCompetencyItemService.findAllByProposalStaffId(proposalId, staffId))
                .thenReturn(Collections.emptyList()); // First call

        // Proposed Competencies
        ProposalParticipantId ppId2 = new ProposalParticipantId(60L, UUID.randomUUID());
        CompetencyProposalDto cp1 = new CompetencyProposalDto();
        cp1.setId(ppId1);
        cp1.setName("Prop A");
        CompetencyProposalDto cp2 = new CompetencyProposalDto();
        cp2.setId(ppId2);
        cp2.setName("Prop B");
        when(competencyProposalService.getAll()).thenReturn(List.of(cp1, cp2));

        // Currently assigned: P1 (Weight 3), P2 (Weight 3)
        // Result: P1 Updated to 4, P2 Removed
        RoleCompetencyProposalItemDto rcpi1 = new RoleCompetencyProposalItemDto();
        rcpi1.setId(new RoleCompetencyProposalItemId(proposalId, roleId, staffId, ppId1));
        rcpi1.setCompetencyProposal(cp1);
        rcpi1.setWeightage(3);

        RoleCompetencyProposalItemDto rcpi2 = new RoleCompetencyProposalItemDto();
        rcpi2.setId(new RoleCompetencyProposalItemId(proposalId, roleId, staffId, ppId2));
        rcpi2.setCompetencyProposal(cp2);
        rcpi2.setWeightage(3);

        // Note: The service is called once at the start of the logic block
        when(roleCompetencyProposalItemService.findAllByProposalStaffId(proposalId, staffId))
                .thenReturn(List.of(rcpi1, rcpi2));

        // --- 3. Mocking Final State (For Email & Duplicate Check) ---
        // The controller calls these again at the end. We must return the "New" state to avoid duplicate errors
        // and ensure email content is correct.
        RoleCompetencyItemDto finalRci = new RoleCompetencyItemDto();
        finalRci.setCompetency(comp100);
        finalRci.setWeightage(5);

        RoleCompetencyProposalItemDto finalRcpi = new RoleCompetencyProposalItemDto();
        finalRcpi.setCompetencyProposal(cp1);
        finalRcpi.setWeightage(4);

        // Use 'doAnswer' or specific sequence if the method is called multiple times with same args,
        // or ensure the mocks above distinguish based on flow.
        // In the controller:
        // 1. `roleCompetencyItemService.findAllByProposalStaffId` called for Diff logic
        // 2. `roleCompetencyItemService.findAllByProposalStaffId` called for Email logic
        when(roleCompetencyItemService.findAllByProposalStaffId(proposalId, staffId))
                .thenReturn(Collections.emptyList()) // 1st call (Diff)
                .thenReturn(List.of(finalRci));      // 2nd call (Email)

        when(roleCompetencyProposalItemService.findAllByProposalStaffId(proposalId, staffId))
                .thenReturn(List.of(rcpi1, rcpi2))   // 1st call (Diff)
                .thenReturn(List.of(finalRcpi));     // 2nd call (Email)

        when(messageSource.getMessage(anyString(), any(), any())).thenReturn("Success");

        // --- 4. Execution ---
        mockMvc.perform(put("/api/competency-assignment-collaboration/edit-competency-assignment-proposal")
                        .principal(authentication)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Success"));

        // --- 5. Verification ---
        // 1. Role Proposal Description Updated
        verify(roleCompetencyProposalService).update(eq(rcpId), argThat(dto -> dto.getDescription().equals("New Description")));

        // 2. Job Scope Added (JS2) and Removed (JS3)
        verify(roleJobScopeProposalService).createAll(argThat(list ->
                list.size() == 1 && list.get(0).getId().getJobScopeId().equals(2L)
        ));
        verify(roleJobScopeProposalService).deleteAllByIdIn(argThat(set ->
                set.size() == 1 && set.stream().findFirst().get().getJobScopeId().equals(3L)
        ));
        // Verify JS3 Definition Deleted (Garbage Collection)
        verify(jobScopeService).deleteAllByIdIn(argThat(set -> set.contains(3L)), eq(userUUID));

        // 3. Permanent Competency Added (100)
        verify(roleCompetencyItemService).createAll(argThat(list ->
                list.size() == 1 && list.get(0).getId().getCompetencyId().equals(100L)
        ));

        // 4. Proposed Competency Updated (P1 weight 4) and Removed (P2)
        verify(roleCompetencyProposalItemService).updateAll(anySet(), argThat(list ->
                list.get(0).getWeightage() == 4
        ));
        verify(roleCompetencyProposalItemService).deleteAllByIdIn(argThat(set ->
                set.stream().anyMatch(id -> id.getCompetencyProposalId().equals(ppId2))
        ));

        // 5. Email Sent
        verify(emailService).sendCompetencyAssignmentProposalUpdateEmail(any(), any(), any(), any(), any(), any(), anyMap(), any(), any(), any());
    }

    @Test
    @WithMockUser(authorities = {"CAN_MANAGE_ROLE"})
    void updateCompetencyProposal_DuplicateNameAfterUpdate_ShouldThrowBadRequest() throws Exception {
        // Setup: Input valid, but final state results in duplicates
        EditCompetencyAssignmentProposalRequestDto req = createEditRequest();
        req.getCompetencyList().add(createPermCompetencyMap(100L, 5));

        ProposalParticipantDto dummyPart = new ProposalParticipantDto();
        // 1. Set the ID so dto.getId() isn't null
        dummyPart.setId(new ProposalParticipantId(1L, userUUID));
        // 2. Set Staff so dto.getStaff() isn't null (accessed later in controller)
        dummyPart.setStaff(new StaffDto());
        dummyPart.getStaff().setId(userUUID);
        dummyPart.getStaff().setEmail("test@tbm.com");

        when(proposalParticipantService.getAllByProposalId(any())).thenReturn(List.of(dummyPart));

        // Basic Mocks to bypass initial logic
        // Ensure getById returns a valid object with IDs to prevent other NPEs
        RoleCompetencyProposalDto rcpDto = new RoleCompetencyProposalDto();
        rcpDto.setId(req.getId());
        rcpDto.setDescription("Original Description"); // Ensure description exists
        rcpDto.setStaff(new StaffDto());
        rcpDto.setRole(new RoleDto());
        rcpDto.getRole().setOrgChart(new OrgChartDto());

        when(roleCompetencyProposalService.getById(any())).thenReturn(rcpDto);
        when(roleCompetencyProposalService.update(any(), any())).thenReturn(rcpDto);
        when(jobScopeService.createAll(anyList())).thenReturn(Collections.emptyList());
        when(roleJobScopeProposalService.getByRoleCompetencyProposalId(any(), any(), any())).thenReturn(Collections.emptyList());
        when(competencyService.findAllByIsDeletedIsFalse()).thenReturn(List.of(new CompetencyDto()));
        when(competencyProposalService.getAll()).thenReturn(Collections.emptyList());

        // Mocks for Diff Logic (Return empty to skip DB ops)
        when(roleCompetencyItemService.findAllByProposalStaffId(any(), any()))
                .thenReturn(Collections.emptyList()) // 1st call (Diff)
                .thenReturn(List.of(createRoleCompetencyItemWithDuplicateName("Java"))); // 2nd call (Email/Validate)

        when(roleCompetencyProposalItemService.findAllByProposalStaffId(any(), any()))
                .thenReturn(Collections.emptyList()) // 1st call (Diff)
                .thenReturn(List.of(createRoleCompetencyProposalItemWithDuplicateName("Java"))); // 2nd call (Email/Validate)

        // Stub message source to avoid NPE in Exception Handler
        when(messageSource.getMessage(anyString(), any(), any())).thenReturn("Duplicate Error");

        // Execution
        mockMvc.perform(put("/api/competency-assignment-collaboration/edit-competency-assignment-proposal")
                        .principal(authentication)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest());
    }


    private RoleCompetencyProposalItemDto createRoleCompetencyProposalItemWithDuplicateName(String name) {
        RoleCompetencyProposalItemDto dto = new RoleCompetencyProposalItemDto();
        dto.setCompetencyProposal(new CompetencyProposalDto());
        dto.getCompetencyProposal().setName(name);
        return dto;
    }

    @Test
    @WithMockUser(authorities = {"CAN_MANAGE_ROLE"})
    void updateCompetencyProposal_UsedJobScope_ShouldNotDeleteDefinition() throws Exception {
        // Scenario: Removing JS3, but JS3 is used in another Permanent Role
        EditCompetencyAssignmentProposalRequestDto req = createEditRequest();
        RoleCompetencyProposalId id = new RoleCompetencyProposalId(1L, 1L, UUID.randomUUID());
        req.setId(id);
        req.setJobScopeList(Collections.emptyList()); // Remove all scopes
        req.getCompetencyList().add(createPermCompetencyMap(100L, 5)); // Add dummy comp to pass validation

        // Mock existing record
        RoleCompetencyProposalDto rcp = new RoleCompetencyProposalDto();
        rcp.setId(id);
        rcp.setDescription("D");
        rcp.setStaff(new StaffDto()); rcp.setRole(new RoleDto());

        when(roleCompetencyProposalService.getById(any())).thenReturn(rcp);
        when(roleCompetencyProposalService.update(any(), any())).thenReturn(rcp); // Mock update to prevent NPE

        ProposalParticipantDto part = new ProposalParticipantDto();
        part.setId(new ProposalParticipantId(1L, userUUID));
        when(proposalParticipantService.getAllByProposalId(any())).thenReturn(List.of(part));

        // Mock assigned JS3 (To be removed)
        RoleJobScopeProposalDto rjs3 = new RoleJobScopeProposalDto();
        rjs3.setId(new RoleJobScopeProposalId(1L, 1L, id.getStaffId(), 3L));
        when(roleJobScopeProposalService.getByRoleCompetencyProposalId(any(), any(), any())).thenReturn(List.of(rjs3));

        // Mock JS3 IS USED in permanent role
        RoleJobScopeDto roleJobScopeUsage = new RoleJobScopeDto();
        roleJobScopeUsage.setId(new RoleJobScopeId(99L, 3L));

        // 1. Mock Usage check for Permanent Roles
        when(roleJobScopeService.findAllByJobScopeIdIn(argThat(set -> set.contains(3L))))
                .thenReturn(List.of(roleJobScopeUsage)); // Returns result, so 3L is kept

        // 2. Mock Usage check for Proposed Roles (Empty to ensure logic flow completes)
        when(roleJobScopeProposalService.findAllByJobScopeIdIn(any())).thenReturn(Collections.emptyList());

        // Basic mocks for rest
        when(competencyService.findAllByIsDeletedIsFalse()).thenReturn(Collections.emptyList());
        when(competencyProposalService.getAll()).thenReturn(Collections.emptyList());
        when(messageSource.getMessage(anyString(), any(), any())).thenReturn("Success");

        // Execution
        mockMvc.perform(put("/api/competency-assignment-collaboration/edit-competency-assignment-proposal")
                        .principal(authentication)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk());

        // Verification
        // Association removed
        verify(roleJobScopeProposalService).deleteAllByIdIn(any());

        verify(jobScopeService, never()).deleteAllByIdIn(
                argThat(set -> set != null && set.contains(3L)), // Check that 3L is NOT in the deletion list
                any()
        );
    }

    @Test
    @WithMockUser(authorities = {"CAN_MANAGE_ROLE"})
    void rejectCompetencyAssignmentProposal_NullId_ShouldThrowBadRequest() throws Exception {
        mockMvc.perform(put("/api/competency-assignment-collaboration/reject-competency-assignment-proposal")
                        .principal(authentication)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser(authorities = {"CAN_MANAGE_ROLE"})
    void rejectCompetencyAssignmentProposal_PartialRejection_ShouldDeleteRecordButKeepProposalOngoing() throws Exception {
        // --- 1. Data Setup ---
        Long proposalId = 1L;
        Long roleId = 10L;
        UUID staffIdToReject = UUID.randomUUID();
        RoleCompetencyProposalId rcpId = createRcpId(proposalId, roleId, staffIdToReject);

        // --- 2. Mocks ---

        // Context & Participants
        ProposalParticipantDto updater = new ProposalParticipantDto();
        updater.setId(new ProposalParticipantId(proposalId, userUUID));
        updater.setStaff(new StaffDto()); updater.getStaff().setEmail("admin@tbm.com");

        ProposalParticipantDto rejectedUser = new ProposalParticipantDto();
        rejectedUser.setId(new ProposalParticipantId(proposalId, staffIdToReject));
        rejectedUser.setProposalRole(ProposalRole.PROPOSER);
        rejectedUser.setStaff(new StaffDto()); rejectedUser.getStaff().setRole(new RoleDto());
        rejectedUser.getStaff().getRole().setId(roleId); // Needed for Access Logic

        // Return multiple participants (Scenario: Proposal continues for others)
        when(proposalParticipantService.getAllByProposalId(proposalId))
                .thenReturn(List.of(updater, rejectedUser));

        // Mock Delete Returns (To populate email maps)
        RoleCompetencyItemDto rci = new RoleCompetencyItemDto();
        rci.setCompetency(new CompetencyDto()); rci.getCompetency().setName("Java");
        rci.setWeightage(5);
        when(roleCompetencyItemService.findAndDeleteAllByProposalStaffId(proposalId, staffIdToReject))
                .thenReturn(List.of(rci));

        when(roleCompetencyProposalItemService.findAndDeleteAllByProposalStaffId(any(), any()))
                .thenReturn(Collections.emptyList());

        JobScopeDto js = new JobScopeDto(); js.setId(99L); js.setJobScope("Scope A");
        RoleJobScopeProposalDto rjs = new RoleJobScopeProposalDto(); rjs.setJobScope(js);
        when(roleJobScopeProposalService.findAndDeleteAllByProposalStaffId(proposalId, staffIdToReject))
                .thenReturn(List.of(rjs));

        // Mock Main Record Delete
        RoleCompetencyProposalDto deletedRecord = new RoleCompetencyProposalDto();
        deletedRecord.setStaff(new StaffDto()); deletedRecord.getStaff().setEmail("rejected@tbm.com");
        deletedRecord.setRole(new RoleDto()); deletedRecord.getRole().setName("Dev");
        deletedRecord.getRole().setOrgChart(new OrgChartDto());
        when(roleCompetencyProposalService.findAndDeleteById(rcpId)).thenReturn(deletedRecord);

        // Mock Participant Delete
        when(proposalParticipantService.findAndDeleteById(any())).thenReturn(rejectedUser);

        // --- BRANCH CHECK: Remaining Proposals Exist ---
        RoleCompetencyProposalDto remainingRecord = new RoleCompetencyProposalDto();
        when(roleCompetencyProposalService.getAllByProposalId(proposalId))
                .thenReturn(List.of(remainingRecord)); // List NOT empty

        // Access GC Mocks
        AuthorityDto authDto = new AuthorityDto(); authDto.setId(55L);
        when(authorityService.findByName(any())).thenReturn(authDto);
        // Mock that the role IS used by someone else, so NO delete happens
        ProposalParticipantDto otherUsage = new ProposalParticipantDto();
        otherUsage.setStaff(new StaffDto()); otherUsage.getStaff().setRole(new RoleDto());
        otherUsage.getStaff().getRole().setId(roleId);
        when(proposalParticipantService.getByStaffRoleIdIn(argThat(set -> set.contains(roleId))))
                .thenReturn(List.of(otherUsage));

        when(messageSource.getMessage(anyString(), any(), any())).thenReturn("Success");

        // --- 3. Execution ---
        mockMvc.perform(put("/api/competency-assignment-collaboration/reject-competency-assignment-proposal")
                        .principal(authentication)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(rcpId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Success"));

        // --- 4. Verification ---
        // Verify dependencies deleted
        verify(roleCompetencyItemService).findAndDeleteAllByProposalStaffId(proposalId, staffIdToReject);
        verify(roleJobScopeProposalService).findAndDeleteAllByProposalStaffId(proposalId, staffIdToReject);

        // Verify emails sent (One to owner, loop for reviewers empty/mocked out)
        verify(emailService, times(1)).sendCompetencyAssignmentProposalRejectedEmail(
                eq("rejected@tbm.com"), any(), any(), any(), any(), anyMap(), eq("admin@tbm.com"), any()
        );

        // Verify Proposal Status NOT updated (Partial Rejection)
        verify(proposalService, never()).updateProposalStatusById(any(), any(), any());

        // Verify Access NOT revoked (because it was used by others)
        verify(roleAuthorityService, never()).deleteAllByIdIn(any());
    }

    @Test
    @WithMockUser(authorities = {"CAN_MANAGE_ROLE"})
    void rejectCompetencyAssignmentProposal_LastParticipant_ShouldRejectProposalAndRevokeAccess() throws Exception {
        // --- 1. Data Setup ---
        Long proposalId = 1L;
        Long roleId = 10L;
        UUID staffIdToReject = UUID.randomUUID();
        RoleCompetencyProposalId rcpId = createRcpId(proposalId, roleId, staffIdToReject);

        // --- 2. Mocks ---
        ProposalParticipantDto updater = new ProposalParticipantDto();
        updater.setId(new ProposalParticipantId(proposalId, userUUID));
        updater.setStaff(new StaffDto());
        updater.getStaff().setRole(new RoleDto());
        updater.getStaff().getRole().setId(roleId);

        // To verify "Other Reviewers" email loop, let's add a 3rd party reviewer
        ProposalParticipantDto thirdPartyReviewer = new ProposalParticipantDto();
        thirdPartyReviewer.setProposalRole(ProposalRole.REVIEWER);
        thirdPartyReviewer.setStaff(new StaffDto()); thirdPartyReviewer.getStaff().setEmail("reviewer@tbm.com");

        when(proposalParticipantService.getAllByProposalId(proposalId))
                .thenReturn(List.of(updater, thirdPartyReviewer));

        // Mock Deletes returning empty lists (simplification)
        when(roleCompetencyItemService.findAndDeleteAllByProposalStaffId(any(), any())).thenReturn(Collections.emptyList());
        when(roleCompetencyProposalItemService.findAndDeleteAllByProposalStaffId(any(), any())).thenReturn(Collections.emptyList());
        when(roleJobScopeProposalService.findAndDeleteAllByProposalStaffId(any(), any())).thenReturn(Collections.emptyList());

        RoleCompetencyProposalDto deletedRecord = new RoleCompetencyProposalDto();
        deletedRecord.setStaff(new StaffDto()); deletedRecord.setRole(new RoleDto());
        deletedRecord.getRole().setOrgChart(new OrgChartDto());
        when(roleCompetencyProposalService.findAndDeleteById(any())).thenReturn(deletedRecord);

        // Mock Participant Delete
        ProposalParticipantDto rejectedParticipant = new ProposalParticipantDto();
        rejectedParticipant.setStaff(new StaffDto()); rejectedParticipant.getStaff().setRole(new RoleDto());
        rejectedParticipant.getStaff().getRole().setId(roleId);
        when(proposalParticipantService.findAndDeleteById(any())).thenReturn(rejectedParticipant);

        // --- BRANCH CHECK: No Remaining Proposals ---
        when(roleCompetencyProposalService.getAllByProposalId(proposalId))
                .thenReturn(Collections.emptyList()); // Empty List = Full Rejection

        // Cleanup other participants
        when(proposalParticipantService.findAndDeleteByProposalId(proposalId))
                .thenReturn(List.of(updater)); // Return updater to extract role ID for cleanup

        // Access GC Mocks
        AuthorityDto authDto = new AuthorityDto(); authDto.setId(55L);
        when(authorityService.findByName(any())).thenReturn(authDto);
        // Mock that roles are NOT used anymore (Empty list)
        when(proposalParticipantService.getByStaffRoleIdIn(any()))
                .thenReturn(Collections.emptyList());

        when(messageSource.getMessage(anyString(), any(), any())).thenReturn("Success");

        // --- 3. Execution ---
        mockMvc.perform(put("/api/competency-assignment-collaboration/reject-competency-assignment-proposal")
                        .principal(authentication)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(rcpId)))
                .andExpect(status().isOk());

        // --- 4. Verification ---

        // Verify Proposal Status Updated to REJECTED
        verify(proposalService).updateProposalStatusById(eq(proposalId), eq(ProposalStatus.REJECTED), eq(userUUID));

        // Verify Emails (1 for owner (null email in mock) + 1 for third party reviewer)
        verify(emailService, times(2)).sendCompetencyAssignmentProposalRejectedEmail(any(), any(), any(), any(), any(), any(), any(), any());
        verify(emailService).sendCompetencyAssignmentProposalRejectedEmail(eq("reviewer@tbm.com"), any(), any(), any(), any(), any(), any(), any());

        // Verify Access Revoked (deleteAllByIdIn called)
        verify(roleAuthorityService).deleteAllByIdIn(any());
    }

    @Test
    @WithMockUser(authorities = {"CAN_MANAGE_ROLE"})
    void rejectCompetencyAssignmentProposal_JobScopeGc_ShouldDeleteDefinitionIfUnused() throws Exception {
        // Scenario: Deleting proposal deletes JobScope usage. If unused elsewhere, definition is deleted.
        Long proposalId = 1L; UUID staffId = UUID.randomUUID();
        RoleCompetencyProposalId id = createRcpId(proposalId, 10L, staffId);

        // Mocks for flow
        ProposalParticipantDto dummyPart = new ProposalParticipantDto();
        dummyPart.setId(new ProposalParticipantId(proposalId, userUUID));
        dummyPart.setStaff(new StaffDto());
        when(proposalParticipantService.getAllByProposalId(any())).thenReturn(List.of(dummyPart));

        // Mock Job Scope Deletion from Proposal
        JobScopeDto js = new JobScopeDto(); js.setId(99L);
        RoleJobScopeProposalDto rjs = new RoleJobScopeProposalDto(); rjs.setJobScope(js);
        when(roleJobScopeProposalService.findAndDeleteAllByProposalStaffId(proposalId, staffId))
                .thenReturn(List.of(rjs));

        // Mock Usage Check (GC) -> Unused
        when(roleJobScopeService.findAllByJobScopeIdIn(any())).thenReturn(Collections.emptyList());
        when(roleJobScopeProposalService.findAllByJobScopeIdIn(any())).thenReturn(Collections.emptyList());

        // Basic mocks
        RoleCompetencyProposalDto deletedRec = new RoleCompetencyProposalDto();
        deletedRec.setStaff(new StaffDto()); deletedRec.setRole(new RoleDto()); deletedRec.getRole().setOrgChart(new OrgChartDto());
        when(roleCompetencyProposalService.findAndDeleteById(any())).thenReturn(deletedRec);

        ProposalParticipantDto part = new ProposalParticipantDto();
        part.setId(new ProposalParticipantId(proposalId, staffId));
        part.setStaff(new StaffDto()); part.getStaff().setRole(new RoleDto());
        when(proposalParticipantService.findAndDeleteById(any())).thenReturn(part);
        // Ensure not empty to skip final logic
        when(roleCompetencyProposalService.getAllByProposalId(any())).thenReturn(List.of(new RoleCompetencyProposalDto()));

        when(messageSource.getMessage(anyString(), any(), any())).thenReturn("Success");

        AuthorityDto authDto = new AuthorityDto();
        authDto.setId(55L);
        when(authorityService.findByName(any())).thenReturn(authDto);

        // Execution
        mockMvc.perform(put("/api/competency-assignment-collaboration/reject-competency-assignment-proposal")
                        .principal(authentication)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(id)))
                .andExpect(status().isOk());

        // Verification
        // Verify definition deletion occurred
        verify(jobScopeService).deleteAllByIdIn(argThat(set -> set.contains(99L)), any());
    }

    @Test
    @WithMockUser(authorities = {"CAN_MANAGE_ROLE"})
    void bulkRejectCompetencyProposal_NullInput_ShouldThrowBadRequest() throws Exception {
        mockMvc.perform(put("/api/competency-assignment-collaboration/bulk-reject-competency-assignment-proposal")
                        .principal(authentication)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser(authorities = {"CAN_MANAGE_ROLE"})
    void bulkRejectCompetencyProposal_PartialRejection_ShouldDeleteItemsButKeepProposalOpen() throws Exception {
        // --- 1. Data Setup ---
        Long proposalId = 1L;
        Long roleId = 10L;
        UUID staffId1 = UUID.randomUUID();
        UUID staffId2 = UUID.randomUUID();

        // Request: Reject Staff 1, Keep Staff 2
        Set<RoleCompetencyProposalId> requestIds = Set.of(createRcpId(proposalId, roleId, staffId1));

        // --- 2. Mocks ---
        // Participants: Updater (User), Staff 1 (Owner), Staff 2 (Other Owner)
        ProposalParticipantDto updater = new ProposalParticipantDto();
        updater.setId(new ProposalParticipantId(proposalId, userUUID));
        updater.setStaff(new StaffDto()); updater.getStaff().setId(userUUID);

        ProposalParticipantDto owner1 = new ProposalParticipantDto();
        owner1.setId(new ProposalParticipantId(proposalId, staffId1));
        owner1.setStaff(new StaffDto()); owner1.getStaff().setId(staffId1); owner1.getStaff().setEmail("staff1@tbm.com");
        owner1.getStaff().setRole(new RoleDto()); owner1.getStaff().getRole().setId(roleId);

        when(proposalParticipantService.getAllByProposalIdIn(anySet()))
                .thenReturn(List.of(updater, owner1));

        // Mock Bulk Deletes (Dependencies)
        when(roleCompetencyItemService.findAndDeleteAllByProposalStaffIdIn(any(), any())).thenReturn(Collections.emptyList());
        when(roleCompetencyProposalItemService.findAndDeleteAllByProposalStaffIdIn(any(), any())).thenReturn(Collections.emptyList());
        when(roleJobScopeProposalService.findAndDeleteAllByProposalStaffIdIn(any(), any())).thenReturn(Collections.emptyList());

        // Mock Main Record Delete
        RoleCompetencyProposalDto deletedRcp = new RoleCompetencyProposalDto();
        deletedRcp.setId(createRcpId(proposalId, roleId, staffId1));
        deletedRcp.setStaff(owner1.getStaff());
        deletedRcp.setRole(new RoleDto()); deletedRcp.getRole().setOrgChart(new OrgChartDto());
        when(roleCompetencyProposalService.findAndDeleteByIdIn(anySet())).thenReturn(List.of(deletedRcp));

        // Mock Participant Delete
        when(proposalParticipantService.findAndDeleteByIdIn(anySet())).thenReturn(List.of(owner1));

        // --- BRANCH CHECK: Remaining Proposals Exist (Partial Rejection) ---
        // Mock that Staff 2's proposal still exists
        RoleCompetencyProposalDto remainingRcp = new RoleCompetencyProposalDto();
        when(roleCompetencyProposalService.getAllByProposalId(proposalId))
                .thenReturn(List.of(remainingRcp));

        // Mock Access GC (Simplification: No cleanup needed)
        when(authorityService.findByName(any())).thenReturn(new AuthorityDto());
        when(proposalParticipantService.getByStaffRoleIdIn(any())).thenReturn(List.of(owner1)); // Still used logic simulation

        when(messageSource.getMessage(anyString(), any(), any())).thenReturn("Success");

        // --- 3. Execution ---
        mockMvc.perform(put("/api/competency-assignment-collaboration/bulk-reject-competency-assignment-proposal")
                        .principal(authentication)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(requestIds)))
                .andExpect(status().isOk());

        // --- 4. Verification ---
        // Verify dependencies deleted via bulk methods
        verify(roleCompetencyItemService).findAndDeleteAllByProposalStaffIdIn(anySet(), anySet());

        // Verify Email Sent to Owner
        verify(emailService).sendCompetencyAssignmentProposalRejectedEmail(
                eq("staff1@tbm.com"), any(), any(), any(), any(), any(), any(), any()
        );

        // Verify Proposal Status NOT updated
        verify(proposalService, never()).updateProposalStatusById(any(), any(), any());
    }

    @Test
    @WithMockUser(authorities = {"CAN_MANAGE_ROLE"})
    void bulkRejectCompetencyProposal_FullRejection_ShouldCloseProposalAndRevokeAccess() throws Exception {
        // --- 1. Data Setup ---
        Long proposalId = 1L;
        Long roleId = 10L;
        UUID staffId = UUID.randomUUID();
        Set<RoleCompetencyProposalId> requestIds = Set.of(createRcpId(proposalId, roleId, staffId));

        // --- 2. Mocks ---
        ProposalParticipantDto updater = new ProposalParticipantDto();
        updater.setId(new ProposalParticipantId(proposalId, userUUID));
        updater.setStaff(new StaffDto()); updater.getStaff().setId(userUUID);

        ProposalParticipantDto owner = new ProposalParticipantDto();
        owner.setId(new ProposalParticipantId(proposalId, staffId));
        owner.setStaff(new StaffDto()); owner.getStaff().setId(staffId); owner.getStaff().setEmail("owner@tbm.com");
        owner.getStaff().setRole(new RoleDto()); owner.getStaff().getRole().setId(roleId);

        when(proposalParticipantService.getAllByProposalIdIn(anySet())).thenReturn(List.of(updater, owner));

        // Simplify deletions
        when(roleCompetencyProposalService.findAndDeleteByIdIn(anySet())).thenReturn(List.of(createMockDeletedRcp(proposalId, staffId, owner.getStaff())));
        when(proposalParticipantService.findAndDeleteByIdIn(anySet())).thenReturn(List.of(owner));
        when(roleCompetencyItemService.findAndDeleteAllByProposalStaffIdIn(any(), any())).thenReturn(Collections.emptyList());
        when(roleCompetencyProposalItemService.findAndDeleteAllByProposalStaffIdIn(any(), any())).thenReturn(Collections.emptyList());
        when(roleJobScopeProposalService.findAndDeleteAllByProposalStaffIdIn(any(), any())).thenReturn(Collections.emptyList());

        // --- BRANCH CHECK: No Remaining Proposals (Full Rejection) ---
        when(roleCompetencyProposalService.getAllByProposalId(proposalId))
                .thenReturn(Collections.emptyList()); // Empty list triggers closure

        // Mock cleanup of remaining participants (e.g., the updater/reviewer)
        ProposalParticipantDto leftoverParticipant = new ProposalParticipantDto();
        leftoverParticipant.setStaff(new StaffDto()); leftoverParticipant.getStaff().setRole(new RoleDto());
        when(proposalParticipantService.findAndDeleteByProposalId(proposalId))
                .thenReturn(List.of(leftoverParticipant));

        // Mock Access Revocation (Return empty usage to trigger delete)
        AuthorityDto auth = new AuthorityDto(); auth.setId(99L);
        when(authorityService.findByName(any())).thenReturn(auth);
        when(proposalParticipantService.getByStaffRoleIdIn(any())).thenReturn(Collections.emptyList()); // Unused

        when(messageSource.getMessage(anyString(), any(), any())).thenReturn("Success");

        // --- 3. Execution ---
        mockMvc.perform(put("/api/competency-assignment-collaboration/bulk-reject-competency-assignment-proposal")
                        .principal(authentication)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(requestIds)))
                .andExpect(status().isOk());

        // --- 4. Verification ---
        // Verify Proposal Closed
        verify(proposalService).updateProposalStatusById(eq(proposalId), eq(ProposalStatus.REJECTED), eq(userUUID));

        // Verify Access Revoked
        verify(roleAuthorityService).deleteAllByIdIn(anySet());
    }

    @Test
    @WithMockUser(authorities = {"CAN_MANAGE_ROLE"})
    void bulkRejectCompetencyProposal_JobScopeGc_ShouldDeleteDefinition() throws Exception {
        // --- Scenario: Rejecting a proposal removes its Job Scope. Since it's unused elsewhere, delete the definition. ---
        Long proposalId = 1L; UUID staffId = UUID.randomUUID();
        Set<RoleCompetencyProposalId> request = Set.of(createRcpId(proposalId, 10L, staffId));

        // Mocks: Basic Setup
        ProposalParticipantDto updater = createParticipant(proposalId, userUUID, ProposalRole.REVIEWER);
        ProposalParticipantDto owner = createParticipant(proposalId, staffId, ProposalRole.PROPOSER);
        owner.getStaff().setRole(new RoleDto(10L)); // Avoid NPE during cleanup

        when(proposalParticipantService.getAllByProposalIdIn(anySet())).thenReturn(List.of(updater, owner));

        // Mock: Job Scope Deletion Return
        JobScopeDto js = new JobScopeDto(); js.setId(99L); js.setJobScope("Unique Scope");
        RoleJobScopeProposalDto rjs = new RoleJobScopeProposalDto(); rjs.setJobScope(js);
        rjs.setId(new RoleJobScopeProposalId(proposalId, 10L, staffId, 99L));

        when(roleJobScopeProposalService.findAndDeleteAllByProposalStaffIdIn(anySet(), anySet()))
                .thenReturn(List.of(rjs));

        // Mock: Garbage Collection Check (Return EMPTY to signify unused)
        when(roleJobScopeService.findAllByJobScopeIdIn(any())).thenReturn(Collections.emptyList());
        when(roleJobScopeProposalService.findAllByJobScopeIdIn(any())).thenReturn(Collections.emptyList());

        // Standard Mocks to pass the rest of the flow
        when(roleCompetencyProposalService.findAndDeleteByIdIn(anySet())).thenReturn(List.of(createMockDeletedRcp(proposalId, staffId, owner.getStaff())));
        when(proposalParticipantService.findAndDeleteByIdIn(anySet())).thenReturn(List.of(owner));
        // Assume proposal stays open to skip that logic block
        when(roleCompetencyProposalService.getAllByProposalId(any())).thenReturn(List.of(new RoleCompetencyProposalDto()));
        when(messageSource.getMessage(anyString(), any(), any())).thenReturn("Success");

        AuthorityDto authDto = new AuthorityDto();
        authDto.setId(55L);
        when(authorityService.findByName(any())).thenReturn(authDto);

        // Execution
        mockMvc.perform(put("/api/competency-assignment-collaboration/bulk-reject-competency-assignment-proposal")
                        .principal(authentication)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk());

        // Verification: Ensure the definition deletion was called for ID 99
        verify(jobScopeService).deleteAllByIdIn(argThat(set -> set.contains(99L)), eq(userUUID));
    }

    @Test
    @WithMockUser(authorities = {"CAN_MANAGE_ROLE"})
    void bulkRejectCompetencyProposal_WithReviewers_ShouldNotifyReviewerButSkipUpdater() throws Exception {
        // --- Scenario: A 3rd party Reviewer exists. They should get an email. The Updater (also a Reviewer) should NOT. ---
        Long proposalId = 1L; UUID staffId = UUID.randomUUID();
        Set<RoleCompetencyProposalId> request = Set.of(createRcpId(proposalId, 10L, staffId));

        // Participants
        ProposalParticipantDto updater = createParticipant(proposalId, userUUID, ProposalRole.REVIEWER);
        updater.getStaff().setEmail("updater@tbm.com"); // Should NOT receive email

        ProposalParticipantDto owner = createParticipant(proposalId, staffId, ProposalRole.PROPOSER);
        owner.getStaff().setEmail("owner@tbm.com"); // Should receive email
        owner.getStaff().setRole(new RoleDto(10L));

        ProposalParticipantDto otherReviewer = createParticipant(proposalId, UUID.randomUUID(), ProposalRole.REVIEWER);
        otherReviewer.getStaff().setEmail("reviewer@tbm.com"); // Should receive email

        when(proposalParticipantService.getAllByProposalIdIn(anySet()))
                .thenReturn(List.of(updater, owner, otherReviewer));

        // Mocks for deletion (Return 1 record to trigger the email loop)
        RoleCompetencyProposalDto deletedRecord = createMockDeletedRcp(proposalId, staffId, owner.getStaff());
        when(roleCompetencyProposalService.findAndDeleteByIdIn(anySet())).thenReturn(List.of(deletedRecord));
        when(proposalParticipantService.findAndDeleteByIdIn(anySet())).thenReturn(List.of(owner));

        // Skip proposal closure logic
        when(roleCompetencyProposalService.getAllByProposalId(any())).thenReturn(List.of(new RoleCompetencyProposalDto()));
        when(messageSource.getMessage(anyString(), any(), any())).thenReturn("Success");

        AuthorityDto authDto = new AuthorityDto();
        authDto.setId(55L);
        when(authorityService.findByName(any())).thenReturn(authDto);

        // Execution
        mockMvc.perform(put("/api/competency-assignment-collaboration/bulk-reject-competency-assignment-proposal")
                        .principal(authentication)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk());

        // Verification
        // 1. Owner notified
        verify(emailService).sendCompetencyAssignmentProposalRejectedEmail(eq("owner@tbm.com"), any(), any(), any(), any(), any(), any(), any());
        // 2. Other Reviewer notified
        verify(emailService).sendCompetencyAssignmentProposalRejectedEmail(eq("reviewer@tbm.com"), any(), any(), any(), any(), any(), any(), any());
        // 3. Updater NOT notified
        verify(emailService, never()).sendCompetencyAssignmentProposalRejectedEmail(eq("updater@tbm.com"), any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    @WithMockUser(authorities = {"CAN_MANAGE_ROLE"})
    void bulkRejectCompetencyProposal_MultipleProposals_ShouldProcessBatchCorrectly() throws Exception {
        // --- Scenario: Rejecting items from Proposal A and Proposal B in one request ---
        Long p1 = 1L; UUID s1 = UUID.randomUUID();
        Long p2 = 2L; UUID s2 = UUID.randomUUID();

        Set<RoleCompetencyProposalId> request = Set.of(
                createRcpId(p1, 10L, s1),
                createRcpId(p2, 20L, s2)
        );

        // Participants
        ProposalParticipantDto updater = createParticipant(p1, userUUID, ProposalRole.REVIEWER);
        // Note: Updater ID must match userUUID for the "updatedBy" filter to work
        updater.getId().setStaffId(userUUID);

        ProposalParticipantDto owner1 = createParticipant(p1, s1, ProposalRole.PROPOSER);
        owner1.getStaff().setRole(new RoleDto(10L));

        ProposalParticipantDto owner2 = createParticipant(p2, s2, ProposalRole.PROPOSER);
        owner2.getStaff().setRole(new RoleDto(20L));

        when(proposalParticipantService.getAllByProposalIdIn(anySet()))
                .thenReturn(List.of(updater, owner1, owner2));

        // Mock Deletions (Must return list containing both deleted records to enter the loop twice)
        RoleCompetencyProposalDto rec1 = createMockDeletedRcp(p1, s1, owner1.getStaff());
        RoleCompetencyProposalDto rec2 = createMockDeletedRcp(p2, s2, owner2.getStaff());

        when(roleCompetencyProposalService.findAndDeleteByIdIn(anySet()))
                .thenReturn(List.of(rec1, rec2));

        when(proposalParticipantService.findAndDeleteByIdIn(anySet()))
                .thenReturn(List.of(owner1, owner2));

        // Mock: Both proposals become empty (Full Rejection)
        when(roleCompetencyProposalService.getAllByProposalId(any())).thenReturn(Collections.emptyList());

        // Return dummy participant for cleanup logic
        ProposalParticipantDto cleanupPart = new ProposalParticipantDto();
        cleanupPart.setStaff(new StaffDto()); cleanupPart.getStaff().setRole(new RoleDto(99L));
        when(proposalParticipantService.findAndDeleteByProposalId(any())).thenReturn(List.of(cleanupPart));

        when(messageSource.getMessage(anyString(), any(), any())).thenReturn("Success");

        AuthorityDto authDto = new AuthorityDto();
        authDto.setId(55L);
        when(authorityService.findByName(any())).thenReturn(authDto);

        // Execution
        mockMvc.perform(put("/api/competency-assignment-collaboration/bulk-reject-competency-assignment-proposal")
                        .principal(authentication)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk());

        // Verification
        // Verify updateStatus called for P1
        verify(proposalService).updateProposalStatusById(eq(p1), eq(ProposalStatus.REJECTED), eq(userUUID));
        // Verify updateStatus called for P2
        verify(proposalService).updateProposalStatusById(eq(p2), eq(ProposalStatus.REJECTED), eq(userUUID));
        // Verify loop ran twice (at least 2 emails sent)
        verify(emailService, times(2)).sendCompetencyAssignmentProposalRejectedEmail(any(), any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    @WithMockUser(authorities = {"CAN_MANAGE_ROLE"})
    void approveCompetencyAssignmentProposal_NullId_ShouldThrowBadRequest() throws Exception {
        mockMvc.perform(put("/api/competency-assignment-collaboration/approve-competency-assignment-proposal")
                        .principal(authentication)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser(authorities = {"CAN_MANAGE_ROLE"})
    void approveCompetencyAssignmentProposal_FullFlow_ShouldMigrateDataAndNotifyAll() throws Exception {
        // --- 1. Data Setup ---
        Long proposalId = 1L;
        Long roleId = 10L;
        UUID approvedStaffId = UUID.randomUUID();
        UUID rejectedStaffId = UUID.randomUUID();
        UUID reviewerId = UUID.randomUUID();

        RoleCompetencyProposalId selectedId = new RoleCompetencyProposalId(proposalId, roleId, approvedStaffId);

        // --- 2. Mocks ---

        // A. Basic Proposal & Role Info
        RoleCompetencyProposalDto selectedRecord = new RoleCompetencyProposalDto();
        selectedRecord.setId(selectedId);
        selectedRecord.setDescription("New Role Desc");

        RoleDto roleDto = new RoleDto(); roleDto.setId(roleId); roleDto.setName("Role"); roleDto.setDescription("Old Role Desc");
        roleDto.setOrgChart(new OrgChartDto());
        selectedRecord.setRole(roleDto);
        selectedRecord.setStaff(new StaffDto()); selectedRecord.getStaff().setEmail("approved@tbm.com");

        when(roleCompetencyProposalService.getById(selectedId)).thenReturn(selectedRecord);
        when(roleService.update(any(), any())).thenReturn(roleDto);

        // B. Participants
        ProposalParticipantDto approver = createParticipant(proposalId, userUUID, ProposalRole.REVIEWER);
        approver.getStaff().setRole(new RoleDto(roleId));

        ProposalParticipantDto winner = createParticipant(proposalId, approvedStaffId, ProposalRole.PROPOSER);
        winner.getStaff().setEmail("approved@tbm.com");
        winner.getStaff().setRole(new RoleDto(roleId));

        ProposalParticipantDto loser = createParticipant(proposalId, rejectedStaffId, ProposalRole.PROPOSER);
        loser.getStaff().setEmail("rejected@tbm.com");
        loser.getStaff().setRole(new RoleDto(roleId));

        ProposalParticipantDto reviewer = createParticipant(proposalId, reviewerId, ProposalRole.REVIEWER);
        reviewer.getStaff().setEmail("reviewer@tbm.com");
        reviewer.getStaff().setRole(new RoleDto(roleId));

        when(proposalParticipantService.getAllByProposalId(proposalId))
                .thenReturn(List.of(approver, winner, loser, reviewer));

        // 1. Prepare ID and DTOs
        ProposalParticipantId ppId = new ProposalParticipantId(50L, approvedStaffId);

        CompetencyProposalDto cpDto = new CompetencyProposalDto();
        cpDto.setId(ppId);
        cpDto.setName("Prop Comp");
        cpDto.setDescription("Desc");
        cpDto.setCreatedBy(approvedStaffId); // Ensure createdBy is set for mapping

        // 2. Mock Retrieval of Proposal Data
        when(competencyProposalService.findAndDeleteAllByProposalIdIn(anySet()))
                .thenReturn(List.of(cpDto));

        CompetencyDto createdComp = new CompetencyDto();
        createdComp.setId(200L);
        createdComp.setName("Prop Comp");
        createdComp.setCreatedBy(approvedStaffId);

        // !!! CHANGE: Mock createAll instead of create !!!
        when(competencyService.createAll(anyList())).thenReturn(List.of(createdComp));

        // 4. Mock Tag/Participant Services to prevent NPEs in loops
        when(competencyCompTagProposalService.findAndDeleteAllByProposalIdIn(any()))
                .thenReturn(Collections.emptyList());
        // Mock for notification inside private method
        when(proposalParticipantService.getAllByProposalIdIn(any()))
                .thenReturn(Collections.emptyList());

        when(competencyCompTagService.createAll(any())).thenReturn(Collections.emptyList());

        // C. Clean & Filter Competencies (Permanent Assignments)
        RoleCompetencyItemDto rciWinner = new RoleCompetencyItemDto();
        rciWinner.setId(new RoleCompetencyItemId(proposalId, roleId, approvedStaffId, 100L));
        rciWinner.setCompetency(new CompetencyDto()); rciWinner.getCompetency().setName("Perm Comp");
        rciWinner.setWeightage(5);

        when(roleCompetencyItemService.findAndDeleteAllByProposalId(proposalId))
                .thenReturn(List.of(rciWinner));

        // D. Clean & Filter Competencies (Proposed Assignments)
        RoleCompetencyProposalItemDto rcpiWinner = new RoleCompetencyProposalItemDto();
        RoleCompetencyProposalItemId itemId = new RoleCompetencyProposalItemId(proposalId, roleId, approvedStaffId, ppId);

        rcpiWinner.setId(itemId);
        rcpiWinner.setCompetencyProposal(cpDto); // Use same DTO instance
        rcpiWinner.setWeightage(4);

        when(roleCompetencyProposalItemService.findAndDeleteAllByProposalId(proposalId))
                .thenReturn(List.of(rcpiWinner));

        // E. Job Scopes
        JobScopeDto js = new JobScopeDto(); js.setId(1L); js.setJobScope("Scope 1");
        RoleJobScopeProposalDto rjspWinner = new RoleJobScopeProposalDto();
        rjspWinner.setId(new RoleJobScopeProposalId(proposalId, roleId, approvedStaffId, 1L));
        rjspWinner.setJobScope(js);

        when(roleJobScopeProposalService.findAndDeleteAllByProposalId(proposalId))
                .thenReturn(List.of(rjspWinner));
        when(roleJobScopeService.findAndDeleteAllByRoleId(roleId)).thenReturn(Collections.emptyList());

        // F. Role Competency Proposal Records
        RoleCompetencyProposalDto approvedRcp = selectedRecord;
        RoleCompetencyProposalDto rejectedRcp = new RoleCompetencyProposalDto();
        rejectedRcp.setId(new RoleCompetencyProposalId(proposalId, roleId, rejectedStaffId));
        rejectedRcp.setStaff(loser.getStaff());
        rejectedRcp.setRole(roleDto);
        rejectedRcp.setDescription("Rejected Desc");

        when(roleCompetencyProposalService.findAndDeleteByProposalId(proposalId))
                .thenReturn(List.of(approvedRcp, rejectedRcp));

        // G. Cleanup & Access
        when(proposalParticipantService.findAndDeleteByProposalId(proposalId)).thenReturn(List.of(winner, loser, reviewer, approver));
        when(authorityService.findByName(any())).thenReturn(new AuthorityDto());
        when(proposalParticipantService.getByStaffRoleIdIn(any())).thenReturn(Collections.emptyList());

        when(messageSource.getMessage(anyString(), any(), any())).thenReturn("Success");

        // --- 3. Execution ---
        mockMvc.perform(put("/api/competency-assignment-collaboration/approve-competency-assignment-proposal")
                        .principal(authentication)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(selectedId)))
                .andExpect(status().isOk());

        // --- 4. Verification ---
        verify(roleService).update(eq(roleId), any());
        verify(roleCompetencyService).deleteAllByRoleId(roleId);
        verify(roleCompetencyService).createAll(anyList());
        verify(proposalService).updateProposalStatusById(eq(proposalId), eq(ProposalStatus.APPROVED), eq(userUUID));

        // Check Emails
        verify(emailService).sendCompetencyAssignmentProposalApprovedEmail(eq("approved@tbm.com"), any(), any(), any(), anyList(), anyMap(), any(), any());
        verify(emailService).sendCompetencyAssignmentProposalRejectedEmail(eq("rejected@tbm.com"), any(), any(), any(), anyList(), anyMap(), any(), any());
    }

    @Test
    @WithMockUser(authorities = {"CAN_MANAGE_ROLE"})
    void approveCompetencyAssignmentProposal_NoRoleUpdate_ShouldSkipRoleServiceUpdate() throws Exception {
        // Setup: IDs match, Descriptions MATCH (No update needed)
        RoleCompetencyProposalId selectedId = new RoleCompetencyProposalId(1L, 10L, UUID.randomUUID());

        RoleCompetencyProposalDto record = new RoleCompetencyProposalDto();
        record.setId(selectedId);
        record.setDescription("Same Desc");

        RoleDto role = new RoleDto();
        role.setId(10L);
        role.setDescription("Same Desc"); // MATCH
        role.setOrgChart(new OrgChartDto());
        record.setRole(role);
        record.setStaff(new StaffDto());

        when(roleCompetencyProposalService.getById(selectedId)).thenReturn(record);
        when(proposalParticipantService.getAllByProposalId(any())).thenReturn(Collections.emptyList());

        // Return empty lists to skip loops
        when(roleCompetencyItemService.findAndDeleteAllByProposalId(any())).thenReturn(Collections.emptyList());
        when(roleCompetencyProposalItemService.findAndDeleteAllByProposalId(any())).thenReturn(Collections.emptyList());
        when(roleJobScopeProposalService.findAndDeleteAllByProposalId(any())).thenReturn(Collections.emptyList());
        when(roleJobScopeService.findAndDeleteAllByRoleId(any())).thenReturn(Collections.emptyList());
        when(roleCompetencyProposalService.findAndDeleteByProposalId(any())).thenReturn(List.of(record)); // Return self to avoid NPE
        when(proposalParticipantService.findAndDeleteByProposalId(any())).thenReturn(Collections.emptyList());

        when(messageSource.getMessage(anyString(), any(), any())).thenReturn("Success");

        // Execution
        mockMvc.perform(put("/api/competency-assignment-collaboration/approve-competency-assignment-proposal")
                        .principal(authentication)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(selectedId)))
                .andExpect(status().isOk());

        // Verification
        verify(roleService, never()).update(any(), any()); // Should NOT update Role
        verify(roleCompetencyService).deleteAllByRoleId(10L); // Should still proceed with assignment
    }

    @Test
    @WithMockUser(authorities = {"CAN_MANAGE_ROLE"})
    void approveCompetencyAssignmentProposal_WithMixedOutcomesAndTags_ShouldMigrateTagsAndNotifyRejections() throws Exception {
        // --- 1. Data Setup ---
        Long proposalId = 1L;
        Long roleId = 10L;
        UUID staffId = UUID.randomUUID();
        RoleCompetencyProposalId selectedId = new RoleCompetencyProposalId(proposalId, roleId, staffId);

        // IDs for Logic
        ProposalParticipantId approvedCpId = new ProposalParticipantId(50L, staffId);
        ProposalParticipantId rejectedCpId = new ProposalParticipantId(51L, staffId);

        // --- 2. Basic Mocks (Role, Participants, etc.) ---
        RoleCompetencyProposalDto record = new RoleCompetencyProposalDto();
        record.setId(selectedId); record.setDescription("Desc");
        record.setRole(new RoleDto(roleId)); record.setStaff(new StaffDto(staffId));
        record.getRole().setOrgChart(new OrgChartDto(100L, "Org Chart"));
        when(roleCompetencyProposalService.getById(selectedId)).thenReturn(record);
        when(roleService.update(any(), any())).thenReturn(new RoleDto(roleId));

        ProposalParticipantDto participant = new ProposalParticipantDto();
        participant.setId(new ProposalParticipantId(proposalId, staffId));
        participant.setStaff(new StaffDto(staffId));
        participant.getStaff().setEmail("owner@tbm.com");
        RoleDto roleDto = new RoleDto();
        roleDto.setId(roleId);
        participant.getStaff().setRole(roleDto);
        when(proposalParticipantService.getAllByProposalId(any())).thenReturn(List.of(participant));

        when(proposalParticipantService.getAllByProposalIdIn(anySet())).thenReturn(List.of(participant));

        // --- 3. Mock Input: Approved Item ---
        RoleCompetencyProposalItemDto approvedItem = new RoleCompetencyProposalItemDto();
        approvedItem.setId(new RoleCompetencyProposalItemId(proposalId, roleId, staffId, approvedCpId));

        CompetencyProposalDto approvedCpDto = new CompetencyProposalDto();
        approvedCpDto.setId(approvedCpId);
        approvedCpDto.setName("Java");
        approvedCpDto.setCreatedBy(staffId);
        // --- FIX 1: Set Staff for Approved DTO (Good practice) ---
        approvedCpDto.setStaff(participant.getStaff());

        approvedItem.setCompetencyProposal(approvedCpDto);
        approvedItem.setWeightage(3);

        when(roleCompetencyProposalItemService.findAndDeleteAllByProposalId(proposalId))
                .thenReturn(List.of(approvedItem));

        // --- 4. Mock Private Method Logic: Competency Proposals ---

        CompetencyProposalDto rejectedCpDto = new CompetencyProposalDto();
        rejectedCpDto.setId(rejectedCpId);
        rejectedCpDto.setName("Python");
        rejectedCpDto.setDescription("Rejected Desc");

        // --- FIX 2: Set Staff for Rejected DTO (Prevents NPE in email logic) ---
        rejectedCpDto.setStaff(participant.getStaff());

        when(competencyProposalService.findAndDeleteAllByProposalIdIn(anySet()))
                .thenReturn(List.of(approvedCpDto, rejectedCpDto));

        // --- 5. Mock Private Method Logic: Tags ---
        CompetencyCompTagProposalDto tag1 = new CompetencyCompTagProposalDto();
        tag1.setId(new CompetencyCompTagProposalId(approvedCpId.getProposalId(), approvedCpId.getStaffId(), 100L));
        tag1.setCompTag(new CompTagDto(100L, "Backend", false));

        CompetencyCompTagProposalDto tag2 = new CompetencyCompTagProposalDto();
        tag2.setId(new CompetencyCompTagProposalId(rejectedCpId.getProposalId(), rejectedCpId.getStaffId(), 101L));
        tag2.setCompTag(new CompTagDto(101L, "Data", false));

        when(competencyCompTagProposalService.findAndDeleteAllByProposalIdIn(anySet()))
                .thenReturn(List.of(tag1, tag2));

        // --- 6. Mock Creation of Permanent Records ---
        CompetencyDto createdComp = new CompetencyDto();
        createdComp.setId(900L); createdComp.setName("Java"); createdComp.setCreatedBy(staffId);
        when(competencyService.createAll(anyList())).thenReturn(List.of(createdComp));

        // Cleanup Mocks
        when(roleCompetencyItemService.findAndDeleteAllByProposalId(any())).thenReturn(Collections.emptyList());
        when(roleJobScopeProposalService.findAndDeleteAllByProposalId(any())).thenReturn(Collections.emptyList());
        when(roleJobScopeService.findAndDeleteAllByRoleId(any())).thenReturn(Collections.emptyList());
        when(roleCompetencyProposalService.findAndDeleteByProposalId(any())).thenReturn(List.of(record));
        when(proposalParticipantService.findAndDeleteByProposalId(any())).thenReturn(List.of(participant));
        when(authorityService.findByName(any())).thenReturn(new AuthorityDto());
        when(proposalParticipantService.getByStaffRoleIdIn(any())).thenReturn(Collections.emptyList());

        when(messageSource.getMessage(anyString(), any(), any())).thenReturn("Success");

        // --- 7. Execution ---
        mockMvc.perform(put("/api/competency-assignment-collaboration/approve-competency-assignment-proposal")
                        .principal(authentication)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(selectedId)))
                .andExpect(status().isOk());

        // --- 8. Verification ---
        verify(competencyCompTagService).createAll(argThat(list ->
                list.size() == 1 &&
                        list.get(0).getCompetency().getId().equals(900L) &&
                        list.get(0).getCompTag().getTag().equals("Backend")
        ));

        verify(emailService).sendCompetencyAssignmentProposalApprovedEmail(
                eq("owner@tbm.com"), any(), any(), any(), anyList(), anyMap(), any(), any()
        );

        verify(emailService).sendCompetencyProposalRejectedEmail(
                eq("owner@tbm.com"),
                eq("Python"),
                eq("Rejected Desc"),
                argThat(tags -> tags.contains("Data")),
                any(),
                any()
        );
    }

    @Test
    @WithMockUser(authorities = {"CAN_MANAGE_ROLE"})
    void bulkApproveCompetencyProposal_NullInput_ShouldThrowBadRequest() throws Exception {
        mockMvc.perform(put("/api/competency-assignment-collaboration/bulk-approve-competency-assignment-proposal")
                        .principal(authentication)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser(authorities = {"CAN_MANAGE_ROLE"})
    void bulkApproveCompetencyProposal_DuplicateProposalIds_ShouldThrowBadRequest() throws Exception {
        // Scenario: Input contains two requests belonging to the SAME Proposal ID (e.g., different staff in same proposal)
        // The controller logic explicitly forbids duplicate Proposal IDs in the input list.
        Long proposalId = 1L;
        RoleCompetencyProposalId id1 = createRcpId(proposalId, 10L, UUID.randomUUID());
        RoleCompetencyProposalId id2 = createRcpId(proposalId, 11L, UUID.randomUUID()); // Same Proposal ID

        Set<RoleCompetencyProposalId> request = Set.of(id1, id2);

        // Mock message source to avoid NPE in exception handler
        when(messageSource.getMessage(anyString(), any(), any())).thenReturn("Conflict Error");

        mockMvc.perform(put("/api/competency-assignment-collaboration/bulk-approve-competency-assignment-proposal")
                        .principal(authentication)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser(authorities = {"CAN_MANAGE_ROLE"})
    void bulkApproveCompetencyProposal_DuplicateRoleIds_ShouldThrowBadRequest() throws Exception {
        // Scenario: Input contains two requests affecting the SAME Role ID (e.g., from different proposals)
        Long roleId = 10L;
        RoleCompetencyProposalId id1 = createRcpId(1L, roleId, UUID.randomUUID());
        RoleCompetencyProposalId id2 = createRcpId(2L, roleId, UUID.randomUUID()); // Same Role ID

        Set<RoleCompetencyProposalId> request = Set.of(id1, id2);

        when(messageSource.getMessage(anyString(), any(), any())).thenReturn("Conflict Error");

        mockMvc.perform(put("/api/competency-assignment-collaboration/bulk-approve-competency-assignment-proposal")
                        .principal(authentication)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser(authorities = {"CAN_MANAGE_ROLE"})
    void bulkApproveCompetencyProposal_Success_ShouldUpdateMultipleRolesAndMigrateData() throws Exception {
        // --- 1. Data Setup ---
        // Two distinct requests
        Long p1 = 1L; Long r1 = 10L; UUID s1 = UUID.randomUUID();
        Long p2 = 2L; Long r2 = 20L; UUID s2 = UUID.randomUUID();

        RoleCompetencyProposalId id1 = createRcpId(p1, r1, s1);
        RoleCompetencyProposalId id2 = createRcpId(p2, r2, s2);
        Set<RoleCompetencyProposalId> request = Set.of(id1, id2);

        // --- 2. Mocks ---

        // A. Participants (User + Owners)
        ProposalParticipantDto updater = createParticipant(p1, userUUID, ProposalRole.REVIEWER);
        // Ensure updater ID matches userUUID for the filter logic
        updater.getId().setStaffId(userUUID);

        ProposalParticipantDto owner1 = createParticipant(p1, s1, ProposalRole.PROPOSER);
        owner1.getStaff().setEmail("owner1@tbm.com");
        owner1.getStaff().setRole(new RoleDto(r1)); // Set Role for cleanup logic

        ProposalParticipantDto owner2 = createParticipant(p2, s2, ProposalRole.PROPOSER);
        owner2.getStaff().setEmail("owner2@tbm.com");
        owner2.getStaff().setRole(new RoleDto(r2)); // Set Role for cleanup logic

        when(proposalParticipantService.getAllByProposalIdIn(anySet()))
                .thenReturn(List.of(updater, owner1, owner2));

        // B. Fetch Items (Permanent & Proposed)
        // Simulating items for P1 (Approved)
        RoleCompetencyItemDto rci1 = new RoleCompetencyItemDto();
        rci1.setId(new RoleCompetencyItemId(p1, r1, s1, 100L));
        rci1.setCompetency(new CompetencyDto()); rci1.getCompetency().setName("Perm1");

        // Simulating items for P2 (Approved)
        RoleCompetencyProposalItemDto rcpi2 = new RoleCompetencyProposalItemDto();
        // **Critical**: Fully populate IDs to avoid NPE in streams
        ProposalParticipantId ppId2 = new ProposalParticipantId(50L, s2);
        rcpi2.setId(new RoleCompetencyProposalItemId(p2, r2, s2, ppId2));

        CompetencyProposalDto cpDto = new CompetencyProposalDto();
        cpDto.setId(ppId2); cpDto.setName("Prop2"); cpDto.setCreatedBy(s2);
        rcpi2.setCompetencyProposal(cpDto);

        when(roleCompetencyItemService.findAllByProposalIdIn(anySet())).thenReturn(List.of(rci1));
        when(roleCompetencyProposalItemService.findAllByProposalIdIn(anySet())).thenReturn(List.of(rcpi2));

        // Job Scopes
        JobScopeDto js = new JobScopeDto(); js.setId(1L);
        RoleJobScopeProposalDto rjs = new RoleJobScopeProposalDto();
        rjs.setId(new RoleJobScopeProposalId(p1, r1, s1, 1L));
        rjs.setJobScope(js);
        when(roleJobScopeProposalService.findAllByProposalIdIn(anySet())).thenReturn(List.of(rjs));

        // C. Update Role Logic
        RoleDto role1 = new RoleDto(); role1.setId(r1); role1.setOrgChart(new OrgChartDto());
        RoleDto role2 = new RoleDto(); role2.setId(r2); role2.setOrgChart(new OrgChartDto());
        when(roleService.getAllByIsDeletedIsFalseAndIdIn(anySet())).thenReturn(List.of(role1, role2));
        when(roleService.updateAll(anySet(), anyList())).thenReturn(List.of(role1, role2));

        // D. Role Proposal Records (for Descriptions)
        RoleCompetencyProposalDto rcp1 = new RoleCompetencyProposalDto(); rcp1.setId(id1); rcp1.setDescription("Desc1"); rcp1.setRole(role1);
        RoleCompetencyProposalDto rcp2 = new RoleCompetencyProposalDto(); rcp2.setId(id2); rcp2.setDescription("Desc2"); rcp2.setRole(role2);
        when(roleCompetencyProposalService.getAllByProposalIdIn(anySet())).thenReturn(List.of(rcp1, rcp2));

        // E. Internal Private Method Mocks (approveCompetencyFromProposal)
        // 1. Mock finding the proposal data
        when(competencyProposalService.findAndDeleteAllByProposalIdIn(anySet())).thenReturn(List.of(cpDto));
        // 2. Mock creating the permanent record (singular create not used, assume createAll)
        CompetencyDto createdComp = new CompetencyDto();
        createdComp.setId(999L); createdComp.setName("Prop2");
        when(competencyService.createAll(anyList())).thenReturn(List.of(createdComp));
        // 3. Mock empty tags/notifications inside private method
        when(competencyCompTagProposalService.findAndDeleteAllByProposalIdIn(anySet())).thenReturn(Collections.emptyList());
        when(competencyCompTagService.createAll(any())).thenReturn(Collections.emptyList());

        // F. Cleanup Mocks
        when(proposalParticipantService.findAndDeleteByProposalIdIn(anySet())).thenReturn(List.of(owner1, owner2));
        when(authorityService.findByName(any())).thenReturn(new AuthorityDto());
        when(proposalParticipantService.getByStaffRoleIdIn(any())).thenReturn(Collections.emptyList());

        when(messageSource.getMessage(anyString(), any(), any())).thenReturn("Success");

        // --- 3. Execution ---
        mockMvc.perform(put("/api/competency-assignment-collaboration/bulk-approve-competency-assignment-proposal")
                        .principal(authentication)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk());

        // --- 4. Verification ---
        // Verify Roles Updated
        verify(roleService).updateAll(argThat(ids -> ids.containsAll(Set.of(r1, r2))), anyList());

        // Verify Competencies Created
        verify(roleCompetencyService).createAll(anyList());

        // Verify Proposal Status
        verify(proposalService).updateProposalStatusByIdIn(argThat(ids -> ids.containsAll(Set.of(p1, p2))), eq(ProposalStatus.APPROVED), eq(userUUID));

        // Verify Emails (Two owners approved)
        verify(emailService, times(2)).sendCompetencyAssignmentProposalApprovedEmail(any(), any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    @WithMockUser(authorities = {"CAN_MANAGE_ROLE"})
    void bulkApproveCompetencyProposal_ImplicitRejection_ShouldSeparateApprovedAndRejectedItems() throws Exception {
        // Scenario: Proposal P1 has Staff A and Staff B.
        // Request only selects Staff A. Staff B should be treated as Rejected.

        Long p1 = 1L; Long r1 = 10L;
        UUID staffA = UUID.randomUUID();
        UUID staffB = UUID.randomUUID();

        // Request ONLY Staff A
        Set<RoleCompetencyProposalId> request = Set.of(createRcpId(p1, r1, staffA));

        // --- Mocks ---
        ProposalParticipantDto updater = createParticipant(p1, userUUID, ProposalRole.REVIEWER);
        updater.getId().setStaffId(userUUID);
        ProposalParticipantDto partA = createParticipant(p1, staffA, ProposalRole.PROPOSER);
        partA.getStaff().setEmail("staffA@tbm.com"); partA.getStaff().setRole(new RoleDto(r1));
        ProposalParticipantDto partB = createParticipant(p1, staffB, ProposalRole.PROPOSER);
        partB.getStaff().setEmail("staffB@tbm.com"); partB.getStaff().setRole(new RoleDto(r1));

        when(proposalParticipantService.getAllByProposalIdIn(anySet())).thenReturn(List.of(updater, partA, partB));

        // Mock Items: Both A and B have items
        // Item A (Selected -> Approved)
        RoleCompetencyItemDto itemA = new RoleCompetencyItemDto();
        itemA.setId(new RoleCompetencyItemId(p1, r1, staffA, 100L));
        itemA.setCompetency(new CompetencyDto()); itemA.getCompetency().setName("CompA");

        // Item B (Not Selected -> Rejected)
        RoleCompetencyItemDto itemB = new RoleCompetencyItemDto();
        itemB.setId(new RoleCompetencyItemId(p1, r1, staffB, 200L));
        itemB.setCompetency(new CompetencyDto()); itemB.getCompetency().setName("CompB");

        // The service returns ALL items for the proposal ID
        when(roleCompetencyItemService.findAllByProposalIdIn(anySet())).thenReturn(List.of(itemA, itemB));

        // Empty mocks for other types
        when(roleCompetencyProposalItemService.findAllByProposalIdIn(anySet())).thenReturn(Collections.emptyList());
        when(roleJobScopeProposalService.findAllByProposalIdIn(anySet())).thenReturn(Collections.emptyList());

        // Mock Role Data
        RoleDto role = new RoleDto(); role.setId(r1); role.setOrgChart(new OrgChartDto());
        when(roleService.getAllByIsDeletedIsFalseAndIdIn(anySet())).thenReturn(List.of(role));
        when(roleService.updateAll(anySet(), anyList())).thenReturn(List.of(role));

        // Mock Records (A is approved, B is implicitly rejected)
        RoleCompetencyProposalDto recordA = new RoleCompetencyProposalDto(); recordA.setId(createRcpId(p1, r1, staffA)); recordA.setDescription("DescA"); recordA.setRole(role);
        RoleCompetencyProposalDto recordB = new RoleCompetencyProposalDto(); recordB.setId(createRcpId(p1, r1, staffB)); recordB.setDescription("DescB"); recordB.setRole(role);
        when(roleCompetencyProposalService.getAllByProposalIdIn(anySet())).thenReturn(List.of(recordA, recordB));

        // Private method mocks (Logic for proposed items - return empty to focus on rejection logic)
        when(competencyProposalService.findAndDeleteAllByProposalIdIn(anySet())).thenReturn(Collections.emptyList());
        when(competencyService.createAll(anyList())).thenReturn(Collections.emptyList());

        // Cleanup
        when(proposalParticipantService.findAndDeleteByProposalIdIn(anySet())).thenReturn(List.of(partA, partB));
        when(authorityService.findByName(any())).thenReturn(new AuthorityDto());
        when(proposalParticipantService.getByStaffRoleIdIn(any())).thenReturn(Collections.emptyList());

        when(messageSource.getMessage(anyString(), any(), any())).thenReturn("Success");

        // --- Execution ---
        mockMvc.perform(put("/api/competency-assignment-collaboration/bulk-approve-competency-assignment-proposal")
                        .principal(authentication)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk());

        // --- Verification ---
        // 1. Verify Role Update (Only based on A's description)
        verify(roleService).updateAll(anySet(), argThat(list -> list.size() == 1));

        // 2. Verify Assignment (Only Item A should be created)
        verify(roleCompetencyService).createAll(argThat(list ->
                list.stream().allMatch(dto -> dto.getCompetency().getName().equals("CompA"))
        ));

        // 3. Verify Emails
        // A gets Approved Email
        verify(emailService).sendCompetencyAssignmentProposalApprovedEmail(
                eq("staffA@tbm.com"), any(), any(), any(), any(), any(), any(), any()
        );

        // B gets Email (The controller calls the 'ApprovedEmail' method for rejected records too in the provided code)
        // We verify the interaction happens for Staff B
        verify(emailService).sendCompetencyAssignmentProposalApprovedEmail(
                eq("staffB@tbm.com"), any(), any(), any(), any(), any(), any(), any()
        );
    }

    // --- Helper Methods for Data Setup ---
    private ProposeCompetencyAssignmentRequest createValidAssignmentRequest() {
        ProposeCompetencyAssignmentRequest req = new ProposeCompetencyAssignmentRequest();
        req.setOrgChartId(1L);
        req.setRoleId(10L);
        req.setDescription("Assigning skills to role");
        req.setReviewerList(new ArrayList<>());
        req.setProposerList(new ArrayList<>());
        req.setJobScopeList(new ArrayList<>(List.of("Scope 1"))); // Mutable list
        req.setCompetencyList(new ArrayList<>());
        return req;
    }

    private ProposeCompetencyAssignmentMap createPermCompetencyMap(Long id, int weight) {
        ProposeCompetencyAssignmentMap map = new ProposeCompetencyAssignmentMap();
        CompetencyAssignmentCreationRequiredDataId mapId = new CompetencyAssignmentCreationRequiredDataId(id, null, false);
        map.setId(mapId);
        map.setWeightage(weight);
        return map;
    }

    private ProposeCompetencyAssignmentMap createProposedCompetencyMap(Long proposalId, UUID staffId, int weight) {
        ProposeCompetencyAssignmentMap map = new ProposeCompetencyAssignmentMap();
        ProposalParticipantId ppId = new ProposalParticipantId(proposalId, staffId);
        CompetencyAssignmentCreationRequiredDataId mapId = new CompetencyAssignmentCreationRequiredDataId(null, ppId, true);
        map.setId(mapId);
        map.setWeightage(weight);
        return map;
    }

    private RoleCompetencyProposalDto createRcpDto(Long proposalId, Long roleId, UUID staffId) {
        RoleCompetencyProposalDto dto = new RoleCompetencyProposalDto();
        dto.setId(new RoleCompetencyProposalId(proposalId, roleId, staffId));
        dto.setDescription("Desc");

        RoleDto role = new RoleDto();
        role.setId(roleId);
        role.setName("RoleName");
        OrgChartDto org = new OrgChartDto();
        org.setId(1L);
        org.setName("OrgName");
        role.setOrgChart(org);
        dto.setRole(role);

        StaffDto staff = new StaffDto();
        staff.setId(staffId);
        staff.setName("StaffName");
        staff.setEmail("email@test.com");
        dto.setStaff(staff);

        dto.setUpdatedBy(staffId);
        return dto;
    }

    private ProposalParticipantDto createParticipant(Long propId, UUID staffId, ProposalRole role) {
        ProposalParticipantDto dto = new ProposalParticipantDto();
        dto.setId(new ProposalParticipantId(propId, staffId));
        dto.setProposalRole(role);
        StaffDto s = new StaffDto();
        s.setId(staffId);
        s.setName("StaffName");
        dto.setStaff(s);
        return dto;
    }

    private EditCompetencyAssignmentProposalRequestDto createEditRequest() {
        EditCompetencyAssignmentProposalRequestDto req = new EditCompetencyAssignmentProposalRequestDto();
        req.setId(new RoleCompetencyProposalId(1L, 10L, UUID.randomUUID()));
        req.setDescription("Updated Description");
        req.setJobScopeList(new ArrayList<>());
        req.setCompetencyList(new ArrayList<>());
        return req;
    }

    private RoleCompetencyItemDto createRoleCompetencyItemWithDuplicateName(String name) {
        RoleCompetencyItemDto dto = new RoleCompetencyItemDto();
        dto.setCompetency(new CompetencyDto());
        dto.getCompetency().setName(name);
        return dto;
    }

    private RoleCompetencyProposalId createRcpId(Long pId, Long rId, UUID sId) {
        return new RoleCompetencyProposalId(pId, rId, sId);
    }

    private RoleCompetencyProposalDto createMockDeletedRcp(Long pId, UUID sId, StaffDto staff) {
        RoleCompetencyProposalDto dto = new RoleCompetencyProposalDto();
        dto.setId(new RoleCompetencyProposalId(pId, 10L, sId));
        dto.setStaff(staff);
        dto.setRole(new RoleDto());
        dto.getRole().setOrgChart(new OrgChartDto());
        return dto;
    }
}