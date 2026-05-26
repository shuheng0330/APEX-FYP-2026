package com.tbm.careerpathlearning.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tbm.careerpathlearning.dto.*;
import com.tbm.careerpathlearning.enums.*;
import com.tbm.careerpathlearning.model.CompetencyCompTagId;
import com.tbm.careerpathlearning.model.CompetencyCompTagProposalId;
import com.tbm.careerpathlearning.model.ProposalParticipantId;
import com.tbm.careerpathlearning.model.RoleAuthorityId;
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

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(CompetencyDefinitionCollaborationController.class)
@AutoConfigureMockMvc(addFilters = false)
public class CompetencyDefinitionCollaborationControllerTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;

    @MockitoBean private TokenService tokenService;
    @MockitoBean private StaffService staffService;
    @MockitoBean private MessageSource messageSource;
    @MockitoBean private EmailService emailService;
    @MockitoBean private ValidationService validationService;
    @MockitoBean private ProposalService proposalService;
    @MockitoBean private CompetencyService competencyService;
    @MockitoBean private CompetencyProposalService competencyProposalService;
    @MockitoBean private CompTagService compTagService;
    @MockitoBean private CompetencyCompTagProposalService competencyCompTagProposalService;
    @MockitoBean private ProposalParticipantService proposalParticipantService;
    @MockitoBean private RoleAuthorityService roleAuthorityService;
    @MockitoBean private AuthorityService authorityService;
    @MockitoBean private CompetencyCompTagService competencyCompTagService;

    private Authentication authentication;
    private UUID userUUID;
    private ProposalParticipantId mockParticipantId;

    @BeforeEach
    void setUp() {
        userUUID = UUID.randomUUID();
        mockParticipantId = new ProposalParticipantId(1L, userUUID);

        authentication = mock(Authentication.class);
        when(authentication.getName()).thenReturn(userUUID.toString());
        when(authentication.getPrincipal()).thenReturn(userUUID.toString());

        lenient().when(messageSource.getMessage(anyString(), any(), any())).thenReturn("Success");
    }

    @Test
    @WithMockUser(authorities = {"CAN_MANAGE_COMPETENCY"})
    void competencyProposalOverview_ShouldReturnOverviewList() throws Exception {
        // --- 1. Mock Data Setup ---
        Long proposalId = 1L;
        ProposalParticipantId participantId = new ProposalParticipantId(proposalId, userUUID);

        // Staff DTO for the user
        StaffDto staffDto = new StaffDto();
        staffDto.setId(userUUID);
        staffDto.setName("Test User");
        staffDto.setEmail("test@tbm.com");

        // Proposal Participant DTO (User is a REVIEWER in this case)
        ProposalParticipantDto participantDto = new ProposalParticipantDto();
        participantDto.setId(participantId);
        participantDto.setProposalRole(ProposalRole.REVIEWER);
        participantDto.setStaff(staffDto);

        // General Proposal DTO (Must be ONGOING and COMPETENCY type)
        ProposalDto proposalDto = new ProposalDto();
        proposalDto.setId(proposalId);
        proposalDto.setStatus(ProposalStatus.ONGOING);
        proposalDto.setType(ProposalType.COMPETENCY);

        // Competency Proposal DTO
        CompetencyProposalDto competencyProposalDto = new CompetencyProposalDto();
        competencyProposalDto.setId(participantId);
        competencyProposalDto.setName("Java Programming");
        competencyProposalDto.setDescription("Advanced Java skills");
        competencyProposalDto.setStaff(staffDto); // Collaborator info
        competencyProposalDto.setUpdatedBy(userUUID);

        // Comp Tag Setup
        Long tagId = 100L;
        CompTagDto tagDto = new CompTagDto();
        tagDto.setId(tagId);
        tagDto.setTag("Backend");

        CompetencyCompTagProposalDto tagProposalDto = new CompetencyCompTagProposalDto();
        tagProposalDto.setId(new CompetencyCompTagProposalId(proposalId, userUUID, tagId));
        tagProposalDto.setCompTag(tagDto);
        tagProposalDto.setProposal(proposalDto);

        // --- 2. Mocking Service Calls ---
        when(proposalParticipantService.getAllProposalParticipants())
                .thenReturn(List.of(participantDto));

        when(proposalService.getAllProposalsByIdIn(anySet()))
                .thenReturn(List.of(proposalDto));

        when(competencyProposalService.getAllByProposalIdIn(anySet()))
                .thenReturn(List.of(competencyProposalDto));

        when(competencyCompTagProposalService.findAllByProposalIdIn(anySet()))
                .thenReturn(List.of(tagProposalDto));

        when(compTagService.findAllByIsDeletedIsFalseAndIdIn(anySet()))
                .thenReturn(List.of(tagDto));

        // --- 3. Execution & Verification ---
        mockMvc.perform(get("/api/competency-definition-collaboration/overview")
                        .principal(authentication)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].competencyName").value("Java Programming"))
                .andExpect(jsonPath("$[0].reviewer").value(true))
                .andExpect(jsonPath("$[0].assignedCompTags[0].tag").value("Backend"))
                .andExpect(jsonPath("$[0].collaboratorName").value("Test User"));

        // Verify interactions
        verify(proposalParticipantService).getAllProposalParticipants();
        verify(proposalService).getAllProposalsByIdIn(anySet());
    }

    @Test
    @WithMockUser(authorities = {"CAN_MANAGE_COMPETENCY"})
    void proposeCompetency_AdminWithFullParticipants_ShouldSucceed() throws Exception {
        // --- 1. Data Setup ---
        ProposeCompetencyRequest req = createValidRequest();
        UUID reviewerId = UUID.randomUUID();
        UUID proposerId = UUID.randomUUID();
        req.setReviewerList(List.of(reviewerId));
        req.setProposerList(List.of(proposerId));

        StaffDto reviewerDto = new StaffDto();
        reviewerDto.setId(reviewerId);
        reviewerDto.setEmail("reviewer@tbm.com");

        StaffDto proposerDto = new StaffDto();
        proposerDto.setId(proposerId);
        proposerDto.setEmail("proposer@tbm.com");

        // Mock Role for proposer
        RoleDto roleDto = new RoleDto();
        roleDto.setId(1L);
        proposerDto.setRole(roleDto);

        StaffDto currentUserDto = new StaffDto(); currentUserDto.setId(userUUID);

        ProposalDto savedProposal = new ProposalDto(); savedProposal.setId(1L);

        CompTagDto tagDto = new CompTagDto(); tagDto.setId(10L); tagDto.setTag("Cloud");

        CompetencyProposalDto cpDto = new CompetencyProposalDto();
        cpDto.setId(new ProposalParticipantId(1L, userUUID));
        cpDto.setStaff(currentUserDto);

        AuthorityDto authorityDto = new AuthorityDto();
        authorityDto.setId(500L); // Any ID works
        authorityDto.setName(AuthorityName.CAN_PROPOSE_ROLE_COMPETENCIES);

        // --- 2. Service Mocking ---

        // Mocking the security context authority retrieval
        doReturn(List.of(new SimpleGrantedAuthority("CAN_MANAGE_COMPETENCY")))
                .when(authentication).getAuthorities();

        when(authorityService.findByName(AuthorityName.CAN_PROPOSE_ROLE_COMPETENCIES))
                .thenReturn(authorityDto);

        when(validationService.isNullOrBlank(any())).thenReturn(false);

        when(staffService.findAllByIdIn(argThat(set -> set != null && set.contains(reviewerId))))
                .thenReturn(List.of(reviewerDto));

        when(staffService.findAllByIdIn(argThat(set -> set != null && set.contains(proposerId))))
                .thenReturn(List.of(proposerDto));

        when(staffService.findById(userUUID)).thenReturn(currentUserDto);

        when(proposalService.createProposal(any())).thenReturn(savedProposal);
        when(compTagService.createAll(anyList())).thenReturn(List.of(tagDto));
        when(competencyProposalService.createCompetencyProposals(anyList())).thenReturn(List.of(cpDto));

        // --- 3. Execution ---
        mockMvc.perform(post("/api/competency-definition-collaboration")
                        .principal(authentication)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Success"));

        // --- 4. Verification ---
        verify(emailService).sendCompetencyProposalReviewInvitationEmail(eq("reviewer@tbm.com"), any(), any(), any(), any(), any(), any());
        verify(emailService).sendCompetencyProposalProposeInvitationEmail(eq("proposer@tbm.com"), any(), any(), any(), any(), any(), any());

        verify(roleAuthorityService).createAll(any());

        verify(proposalParticipantService).createProposalParticipants(argThat(list -> list != null && list.size() == 3));
    }

    @Test
    @WithMockUser(authorities = {"CAN_MANAGE_COMPETENCY"})
    void proposeCompetency_NullName_ShouldThrowBadRequest() throws Exception {
        ProposeCompetencyRequest req = createValidRequest();
        req.setCompetencyName(null);

        when(validationService.isNullOrBlank(null)).thenReturn(true);

        mockMvc.perform(post("/api/competency-definition-collaboration")
                        .principal(authentication)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest());

        verify(proposalService, never()).createProposal(any());
    }

    @Test
    @WithMockUser(authorities = {"CAN_PROPOSE_ROLE_COMPETENCIES"}) // Non-Admin Authority
    void proposeCompetency_NonAdminNoReviewer_ShouldThrowBadRequest() throws Exception {
        ProposeCompetencyRequest req = createValidRequest();
        req.setReviewerList(Collections.emptyList()); // Empty reviewers

        when(validationService.isNullOrBlank(any())).thenReturn(false);

        mockMvc.perform(post("/api/competency-definition-collaboration")
                        .principal(authentication)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest());

        // Ensure we didn't proceed to DB ops
        verify(competencyService, never()).checkRedundancyByName(any(), any());
    }

    @Test
    @WithMockUser(authorities = {"CAN_PROPOSE_ROLE_COMPETENCIES"})
    void proposeCompetency_NonAdminWithProposers_ShouldThrowForbidden() throws Exception {
        ProposeCompetencyRequest req = createValidRequest();
        req.setReviewerList(List.of(UUID.randomUUID())); // Reviewer present (valid)
        req.setProposerList(List.of(UUID.randomUUID())); // But Proposer present (invalid for non-admin)

        when(validationService.isNullOrBlank(any())).thenReturn(false);

        mockMvc.perform(post("/api/competency-definition-collaboration")
                        .principal(authentication)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isForbidden()); // 403 Forbidden
    }

    @Test
    @WithMockUser(authorities = {"CAN_MANAGE_COMPETENCY"})
    void proposeCompetency_ReviewerSameAsProposer_ShouldThrowBadRequest() throws Exception {
        ProposeCompetencyRequest req = createValidRequest();
        UUID dualRoleUser = UUID.randomUUID();

        doReturn(List.of(new SimpleGrantedAuthority("CAN_MANAGE_COMPETENCY")))
                .when(authentication).getAuthorities();

        req.setReviewerList(List.of(dualRoleUser));
        req.setProposerList(List.of(dualRoleUser)); // Same ID in both lists

        when(validationService.isNullOrBlank(any())).thenReturn(false);
        when(staffService.findAllByIdIn(any())).thenReturn(List.of(new StaffDto())); // Mock return to avoid null pointer before logic

        mockMvc.perform(post("/api/competency-definition-collaboration")
                        .principal(authentication)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser(authorities = {"CAN_MANAGE_COMPETENCY"})
    void proposeCompetency_UserIsReviewer_ShouldThrowBadRequest() throws Exception {
        ProposeCompetencyRequest req = createValidRequest();
        req.setReviewerList(List.of(userUUID)); // User tries to review their own proposal

        when(validationService.isNullOrBlank(any())).thenReturn(false);
        when(staffService.findAllByIdIn(any())).thenReturn(List.of(new StaffDto()));

        mockMvc.perform(post("/api/competency-definition-collaboration")
                        .principal(authentication)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser(authorities = {"CAN_MANAGE_COMPETENCY"})
    void updateCompetencyProposal_InvalidRequest_ShouldThrowBadRequest() throws Exception {
        // Case 1: Null Request Body
        mockMvc.perform(put("/api/competency-definition-collaboration/edit-competency-proposal")
                        .principal(authentication)
                        .contentType(MediaType.APPLICATION_JSON)) // Empty body
                .andExpect(status().isBadRequest());

        // Case 2: Missing Proposal ID
        EditCompetencyProposalRequestDto reqNoId = createEditRequest();
        reqNoId.setProposalParticipantId(null);

        mockMvc.perform(put("/api/competency-definition-collaboration/edit-competency-proposal")
                        .principal(authentication)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(reqNoId)))
                .andExpect(status().isBadRequest());

        // Case 3: Empty Name (Mocking ValidationService)
        EditCompetencyProposalRequestDto reqNoName = createEditRequest();
        reqNoName.setCompetencyName("");

        when(validationService.isNullOrBlank("")).thenReturn(true); // Mock validation failure

        mockMvc.perform(put("/api/competency-definition-collaboration/edit-competency-proposal")
                        .principal(authentication)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(reqNoName)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser(authorities = {"CAN_MANAGE_COMPETENCY"})
    void updateCompetencyProposal_UpdateDetailsAndSyncTags_ShouldSuccess() throws Exception {
        // --- 1. Data Setup ---
        UUID staffId = UUID.randomUUID();
        Long proposalId = 1L;

        EditCompetencyProposalRequestDto req = createEditRequest();
        req.setProposalParticipantId(new ProposalParticipantId(proposalId, staffId));
        req.setCompTagList(List.of("NewTag")); // We request ONLY "NewTag"

        // Mock Existing State: Currently has "OldTag"
        CompetencyProposalDto existingDto = new CompetencyProposalDto();
        existingDto.setId(req.getProposalParticipantId());
        existingDto.setName("Old Name");

        // Proposal DTO (needed for email/return)
        ProposalDto proposalDto = new ProposalDto();
        proposalDto.setId(proposalId);
        existingDto.setProposal(proposalDto);

        StaffDto staffDto = new StaffDto();
        staffDto.setId(staffId);
        staffDto.setEmail("owner@tbm.com");
        existingDto.setStaff(staffDto);

        // Mock Existing Tags in DB ("OldTag" id=100)
        Long oldTagId = 100L;
        CompetencyCompTagProposalDto oldTagPropDto = new CompetencyCompTagProposalDto();
        oldTagPropDto.setId(new CompetencyCompTagProposalId(proposalId, staffId, oldTagId));

        // Mock New Tag creation return ("NewTag" id=200)
        CompTagDto newTagDto = new CompTagDto();
        newTagDto.setId(200L);
        newTagDto.setTag("NewTag");

        // --- 2. Service Mocking ---
        when(validationService.isNullOrBlank(anyString())).thenReturn(false);

        // Return existing proposal
        when(competencyProposalService.getById(req.getProposalParticipantId())).thenReturn(existingDto);

        // Return update result
        when(competencyProposalService.updateCompetencyProposal(any(), any())).thenReturn(existingDto);

        // Return existing tags (OldTag)
        when(competencyCompTagProposalService.getByProposalStaffId(proposalId, staffId))
                .thenReturn(List.of(oldTagPropDto));

        // Create requested tags (NewTag)
        when(compTagService.createAll(anyList())).thenReturn(List.of(newTagDto));

        // --- Mocks for Tag Cleanup Logic ---
        // 1. Tag 100 is removed from this proposal
        // 2. Is Tag 100 used in other Competencies? NO.
        when(competencyCompTagService.findAllByCompTagIdIn(argThat(s -> s != null && s.contains(oldTagId))))
                .thenReturn(Collections.emptyList());

        // 3. Is Tag 100 used in other Proposals? NO.
        when(competencyCompTagProposalService.getByCompTagIdIn(argThat(s -> s != null && s.contains(oldTagId))))
                .thenReturn(Collections.emptyList());

        // Mock Recipients (Owner + Updater)
        ProposalParticipantDto ownerPart = new ProposalParticipantDto();
        ownerPart.setId(new ProposalParticipantId(proposalId, staffId));
        ownerPart.setStaff(staffDto); // Owner

        ProposalParticipantDto updaterPart = new ProposalParticipantDto();
        updaterPart.setId(new ProposalParticipantId(proposalId, userUUID));
        updaterPart.setStaff(new StaffDto()); // Updater (User performing action)
        updaterPart.setProposalRole(ProposalRole.REVIEWER);

        when(proposalParticipantService.getAllByProposalId(proposalId))
                .thenReturn(List.of(ownerPart, updaterPart));

        // --- 3. Execution ---
        mockMvc.perform(put("/api/competency-definition-collaboration/edit-competency-proposal")
                        .principal(authentication)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Success"));

        // --- 4. Verification ---
        // 1. Verify Name Redundancy Check
        verify(competencyService).checkRedundancyByName(null, "New Name");

        // 2. Verify Update Call
        verify(competencyProposalService).updateCompetencyProposal(eq(req.getProposalParticipantId()), any());

        // 3. Verify NEW Tag Association Created
        verify(competencyCompTagProposalService).createCompetencyCompTagProposals(argThat(list ->
                list.size() == 1 && list.get(0).getCompTag().getId().equals(200L)
        ));

        // 4. Verify OLD Tag Association Removed
        verify(competencyCompTagProposalService).deleteByProposalStaffIdAndCompTagIdIn(eq(proposalId), eq(staffId), argThat(set -> set.contains(oldTagId)));

        // 5. Verify OLD Tag Definition DELETED (Cleanup logic passed)
        verify(compTagService).deleteAllByIdIn(argThat(set -> set.contains(oldTagId)), eq(userUUID));

        // 6. Verify Email Sent to Owner (updater is excluded, so only 1 email)
        verify(emailService, times(1)).sendCompetencyProposalUpdateEmail(eq("owner@tbm.com"), any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    @WithMockUser(authorities = {"CAN_MANAGE_COMPETENCY"})
    void updateCompetencyProposal_RemoveUsedTag_ShouldNotDeleteTagDefinition() throws Exception {
        // Setup: Request removes "SharedTag" (id=100)
        UUID staffId = UUID.randomUUID();
        Long proposalId = 1L;
        ProposalParticipantId participantId = new ProposalParticipantId(proposalId, userUUID);

        StaffDto staffDto = new StaffDto();
        staffDto.setId(userUUID);
        staffDto.setName("Test User");
        staffDto.setEmail("test@tbm.com");

        CompetencyProposalDto competencyProposalDto = new CompetencyProposalDto();
        competencyProposalDto.setId(participantId);
        competencyProposalDto.setName("Java Programming");
        competencyProposalDto.setDescription("Advanced Java skills");
        competencyProposalDto.setStaff(staffDto); // Collaborator info
        competencyProposalDto.setUpdatedBy(userUUID);

        EditCompetencyProposalRequestDto req = createEditRequest();
        req.setProposalParticipantId(new ProposalParticipantId(proposalId, staffId));
        req.setCompTagList(Collections.emptyList()); // Removing all tags

        // Setup Existing: Has "SharedTag" (id=100)
        CompetencyCompTagProposalDto oldTagPropDto = new CompetencyCompTagProposalDto();
        oldTagPropDto.setId(new CompetencyCompTagProposalId(proposalId, staffId, 100L));

        // Mock Basic Returns
        when(competencyProposalService.getById(any())).thenReturn(competencyProposalDto);
        when(competencyProposalService.updateCompetencyProposal(any(), any())).thenReturn(competencyProposalDto);
        when(competencyCompTagProposalService.getByProposalStaffId(any(), any())).thenReturn(List.of(oldTagPropDto));

        // Mock Recipients to avoid NPE
        ProposalParticipantDto updaterPart = new ProposalParticipantDto();
        updaterPart.setId(new ProposalParticipantId(proposalId, userUUID));
        updaterPart.setStaff(new StaffDto());
        when(proposalParticipantService.getAllByProposalId(any())).thenReturn(List.of(updaterPart));

        // --- CRITICAL MOCK: Tag IS used elsewhere ---
        CompetencyCompTagDto usageMock = new CompetencyCompTagDto();
        usageMock.setId(new CompetencyCompTagId(99L, 100L)); // Used in Comp 99

        when(competencyCompTagService.findAllByCompTagIdIn(argThat(s -> s.contains(100L))))
                .thenReturn(List.of(usageMock)); // Return list is NOT empty

        // Execution
        mockMvc.perform(put("/api/competency-definition-collaboration/edit-competency-proposal")
                        .principal(authentication)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk());

        // Verification
        // 1. Association IS removed from this proposal
        verify(competencyCompTagProposalService).deleteByProposalStaffIdAndCompTagIdIn(eq(proposalId), eq(staffId), anySet());

        // 2. BUT Global Tag Definition is NEVER deleted
        verify(compTagService, never()).deleteAllByIdIn(any(), any());
    }

    @Test
    @WithMockUser(authorities = {"CAN_MANAGE_COMPETENCY"})
    void rejectCompetencyProposal_NullId_ShouldThrowBadRequest() throws Exception {
        mockMvc.perform(put("/api/competency-definition-collaboration/reject-competency-proposal") // Verify your URL
                        .principal(authentication)
                        .contentType(MediaType.APPLICATION_JSON)) // Null body
                .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser(authorities = {"CAN_MANAGE_COMPETENCY"})
    void rejectCompetencyProposal_LastItem_ShouldRejectParentProposalAndRevokeAccess() throws Exception {
        // --- 1. Data Setup ---
        UUID targetStaffId = UUID.randomUUID();
        Long proposalId = 1L;
        Long roleId = 55L;
        ProposalParticipantId targetId = new ProposalParticipantId(proposalId, targetStaffId);

        // Current Admin User (Reviewer)
        ProposalParticipantDto adminParticipant = createParticipant(proposalId, userUUID, ProposalRole.REVIEWER, 99L);

        // Target User (Proposer)
        ProposalParticipantDto targetParticipant = createParticipant(proposalId, targetStaffId, ProposalRole.PROPOSER, roleId);

        // Competency Proposal (Only 1 exists = Last Man Standing)
        CompetencyProposalDto targetCompDto = new CompetencyProposalDto();
        targetCompDto.setId(targetId);
        targetCompDto.setName("To Reject");
        targetCompDto.setDescription("Desc");
        targetCompDto.setStaff(targetParticipant.getStaff());

        // Tags Setup (Tag 100 will be deleted)
        Long tagId = 100L;
        CompetencyCompTagProposalDto tagPropDto = new CompetencyCompTagProposalDto();
        tagPropDto.setId(new CompetencyCompTagProposalId(proposalId, targetStaffId, tagId));
        CompTagDto tagDto = new CompTagDto(); tagDto.setId(tagId); tagDto.setTag("DeleteMe");
        tagPropDto.setCompTag(tagDto);

        // Authority Mock for Access Revocation
        AuthorityDto authDto = new AuthorityDto(); authDto.setId(10L);

        // --- 2. Mocks ---
        // Return BOTH participants (Admin + Target)
        when(proposalParticipantService.getAllProposalParticipants())
                .thenReturn(List.of(adminParticipant, targetParticipant));

        // Return ONLY the target competency (List size 1)
        when(competencyProposalService.getAllByProposalId(proposalId))
                .thenReturn(List.of(targetCompDto));

        // Tag Logic: Return tag, and say it is unused elsewhere
        when(competencyCompTagProposalService.getByProposalStaffId(proposalId, targetStaffId))
                .thenReturn(List.of(tagPropDto));
        when(competencyCompTagService.findAllByCompTagIdIn(anySet())).thenReturn(Collections.emptyList());
        when(competencyCompTagProposalService.getByCompTagIdIn(anySet())).thenReturn(Collections.emptyList());

        // Access Control Mock
        when(authorityService.findByName(AuthorityName.CAN_PROPOSE_ROLE_COMPETENCIES)).thenReturn(authDto);

        // --- 3. Execution ---
        mockMvc.perform(put("/api/competency-definition-collaboration/reject-competency-proposal")
                        .principal(authentication)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(targetId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Success"));

        // --- 4. Verification ---
        // A. Verify Access Revoked (Because roleId 55 is NOT in any other proposal)
        verify(roleAuthorityService).deleteById(eq(new RoleAuthorityId(roleId, 10L)));

        // B. Verify Tag Deleted (Because it was unused)
        verify(compTagService).deleteAllByIdIn(argThat(set -> set.contains(tagId)), eq(userUUID));

        // C. Verify Parent Proposal Rejected (Because list became empty)
        verify(proposalService).updateProposalStatusById(eq(proposalId), eq(ProposalStatus.REJECTED), eq(userUUID));
        verify(proposalParticipantService).deleteAllByProposalId(proposalId);

        // D. Verify Email sent to target (Admin excluded)
        verify(emailService, times(1)).sendCompetencyProposalRejectedEmail(
                eq(targetParticipant.getStaff().getEmail()), any(), any(), any(), any(), any()
        );
    }

    @Test
    @WithMockUser(authorities = {"CAN_MANAGE_COMPETENCY"})
    void rejectCompetencyProposal_OthersRemainAndRoleBusy_ShouldOnlyDeleteTarget() throws Exception {
        // --- 1. Data Setup ---
        UUID targetStaffId = UUID.randomUUID();
        UUID otherStaffId = UUID.randomUUID();
        Long proposalId = 1L;
        Long sharedRoleId = 55L; // Role is shared/busy

        ProposalParticipantId targetId = new ProposalParticipantId(proposalId, targetStaffId);

        // Participants: Admin, Target, and a "Busy User" in a DIFFERENT proposal
        ProposalParticipantDto adminPart = createParticipant(proposalId, userUUID, ProposalRole.REVIEWER, 99L);
        ProposalParticipantDto targetPart = createParticipant(proposalId, targetStaffId, ProposalRole.PROPOSER, sharedRoleId);

        // THIS is the key: Another user, Different Proposal, Same Role ID -> Prevents Access Revocation
        ProposalParticipantDto busyOtherPart = createParticipant(2L, otherStaffId, ProposalRole.PROPOSER, sharedRoleId);

        // Competency Proposals: Target + One Other
        CompetencyProposalDto targetComp = new CompetencyProposalDto();
        targetComp.setId(targetId); targetComp.setStaff(targetPart.getStaff()); targetComp.setName("A"); targetComp.setDescription("A");

        CompetencyProposalDto otherComp = new CompetencyProposalDto();
        otherComp.setId(new ProposalParticipantId(proposalId, otherStaffId)); // Keeps list non-empty

        // Authority Mock
        AuthorityDto authDto = new AuthorityDto(); authDto.setId(10L);

        // --- 2. Mocks ---
        // Return ALL participants
        when(proposalParticipantService.getAllProposalParticipants())
                .thenReturn(List.of(adminPart, targetPart, busyOtherPart));

        // Return List of 2 Competencies (Target + Other)
        when(competencyProposalService.getAllByProposalId(proposalId))
                .thenReturn(List.of(targetComp, otherComp));

        // Access Control Helper Mock
        when(authorityService.findByName(AuthorityName.CAN_PROPOSE_ROLE_COMPETENCIES)).thenReturn(authDto);

        // Assume no tags for simplicity
        when(competencyCompTagProposalService.getByProposalStaffId(any(), any())).thenReturn(Collections.emptyList());

        // --- 3. Execution ---
        mockMvc.perform(put("/api/competency-definition-collaboration/reject-competency-proposal")
                        .principal(authentication)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(targetId)))
                .andExpect(status().isOk());

        // --- 4. Verification ---
        // A. Verify Access NOT Revoked (Because 'busyOtherPart' uses roleId 55)
        verify(roleAuthorityService, never()).deleteById(any());

        // B. Verify Parent Proposal NOT Rejected (Because 'otherComp' remained)
        verify(proposalService, never()).updateProposalStatusById(any(), any(), any());

        // C. Verify Specific Participant Deleted
        verify(proposalParticipantService).deleteById(eq(targetId));

        // D. Verify Specific Competency Deleted
        verify(competencyProposalService).delete(eq(targetId));
    }

    @Test
    @WithMockUser(authorities = {"CAN_MANAGE_COMPETENCY"})
    void rejectCompetencyProposal_TagUsedElsewhere_ShouldNotDeleteTagDefinition() throws Exception {
        UUID targetStaffId = UUID.randomUUID();
        Long proposalId = 1L;
        ProposalParticipantId targetId = new ProposalParticipantId(proposalId, targetStaffId);

        // Minimal Setup for Objects to prevent NPEs
        ProposalParticipantDto targetPart = createParticipant(proposalId, targetStaffId, ProposalRole.PROPOSER, 1L);
        ProposalParticipantDto adminPart = createParticipant(proposalId, userUUID, ProposalRole.REVIEWER, 99L);
        CompetencyProposalDto targetComp = new CompetencyProposalDto();
        targetComp.setId(targetId); targetComp.setStaff(targetPart.getStaff()); targetComp.setName("N"); targetComp.setDescription("D");

        // Tag Setup: Tag 100 is being removed from proposal...
        Long tagId = 100L;
        CompetencyCompTagProposalDto tagPropDto = new CompetencyCompTagProposalDto();
        tagPropDto.setId(new CompetencyCompTagProposalId(proposalId, targetStaffId, tagId));
        tagPropDto.setCompTag(new CompTagDto()); tagPropDto.getCompTag().setTag("SharedTag");

        // ...BUT Tag 100 is used in another Competency
        CompetencyCompTagDto usedCompTag = new CompetencyCompTagDto();
        usedCompTag.setId(new CompetencyCompTagId(500L, tagId));

        // Mocks
        when(proposalParticipantService.getAllProposalParticipants()).thenReturn(List.of(targetPart, adminPart));
        when(competencyProposalService.getAllByProposalId(proposalId)).thenReturn(List.of(targetComp));
        when(authorityService.findByName(any())).thenReturn(new AuthorityDto());

        // Tag Mocks
        when(competencyCompTagProposalService.getByProposalStaffId(any(), any())).thenReturn(List.of(tagPropDto));

        // ** CRITICAL: Return non-empty list for usage check **
        when(competencyCompTagService.findAllByCompTagIdIn(argThat(s -> s.contains(tagId))))
                .thenReturn(List.of(usedCompTag));

        // Execution
        mockMvc.perform(put("/api/competency-definition-collaboration/reject-competency-proposal")
                        .principal(authentication)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(targetId)))
                .andExpect(status().isOk());

        // Verification
        // 1. Association removed
        verify(competencyCompTagProposalService).deleteByProposalStaffIdAndCompTagIdIn(any(), any(), any());

        // 2. Definition NEVER deleted
        verify(compTagService, never()).deleteAllByIdIn(any(), any());
    }

    @Test
    @WithMockUser(authorities = {"CAN_MANAGE_COMPETENCY"})
    void bulkRejectCompetencyProposal_NullInput_ShouldThrowBadRequest() throws Exception {
        mockMvc.perform(put("/api/competency-definition-collaboration/bulk-reject-competency-proposal")
                        .principal(authentication)
                        .contentType(MediaType.APPLICATION_JSON)) // Empty body
                .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser(authorities = {"CAN_MANAGE_COMPETENCY"})
    void bulkReject_AllItemsInProposal_ShouldRejectParentAndRevokeAccess() throws Exception {
        // --- 1. Data Setup ---
        Long proposalId = 1L;
        UUID staffId = UUID.randomUUID();
        Long roleId = 100L;

        ProposalParticipantId targetId = new ProposalParticipantId(proposalId, staffId);
        Set<ProposalParticipantId> payload = Set.of(targetId);

        // Participants: 1 Admin (Reviewer), 1 Target (Proposer)
        ProposalParticipantDto adminPart = createParticipant(proposalId, userUUID, ProposalRole.REVIEWER, 99L);
        ProposalParticipantDto targetPart = createParticipant(proposalId, staffId, ProposalRole.PROPOSER, roleId);

        // Competency Proposals: Only the target exists
        CompetencyProposalDto targetComp = new CompetencyProposalDto();
        targetComp.setId(targetId);
        targetComp.setName("To Reject");
        targetComp.setDescription("Desc");
        // Link Proposal to DTO (Critical for 'abandonedProposalIds' logic)
        ProposalDto parentProposal = new ProposalDto(); parentProposal.setId(proposalId);
        targetComp.setProposal(parentProposal);

        // Authority Mock
        AuthorityDto authDto = new AuthorityDto(); authDto.setId(50L);

        // --- 2. Mocks ---
        // Return ALL participants
        when(proposalParticipantService.getAllProposalParticipants())
                .thenReturn(List.of(adminPart, targetPart));

        // Return ALL Competency Proposals involved
        when(competencyProposalService.getAllByProposalIdIn(Set.of(proposalId)))
                .thenReturn(List.of(targetComp));

        // Tag Mocks (Empty for simplicity)
        when(competencyCompTagProposalService.getByProposalStaffIdIn(any(), any())).thenReturn(Collections.emptyList());

        // Access Control Mock
        when(authorityService.findByName(AuthorityName.CAN_PROPOSE_ROLE_COMPETENCIES)).thenReturn(authDto);

        // --- 3. Execution ---
        mockMvc.perform(put("/api/competency-definition-collaboration/bulk-reject-competency-proposal")
                        .principal(authentication)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(payload)))
                .andExpect(status().isOk());

        // --- 4. Verification ---
        // A. Verify Parent Proposal Rejected (Because targetComp was the ONLY item)
        verify(proposalService).updateProposalStatusByIdIn(argThat(set -> set.contains(proposalId)), eq(ProposalStatus.REJECTED), eq(userUUID));

        // B. Verify Participation Deleted
        verify(proposalParticipantService).deleteAllByProposalIdIn(argThat(set -> set.contains(proposalId)));

        // C. Verify Access Revoked (Because roleId 100 is not used by adminPart)
        verify(roleAuthorityService).deleteAllByIdIn(argThat(set -> !set.isEmpty())); // Ensure something was deleted

        // D. Verify Email Sent
        verify(emailService, times(1)).sendCompetencyProposalRejectedEmail(
                eq("staff" + staffId + "@tbm.com"), any(), any(), any(), any(), any()
        );
    }

    @Test
    @WithMockUser(authorities = {"CAN_MANAGE_COMPETENCY"})
    void bulkReject_PartialItems_ShouldKeepParentOpen() throws Exception {
        // --- 1. Data Setup ---
        Long proposalId = 1L;
        UUID staffA = UUID.randomUUID(); // To be rejected
        UUID staffB = UUID.randomUUID(); // To remain
        Long sharedRoleId = 55L; // Both have same role

        ProposalParticipantId targetId = new ProposalParticipantId(proposalId, staffA);
        Set<ProposalParticipantId> payload = Set.of(targetId);

        // Participants: Admin, Target(A), Survivor(B)
        ProposalParticipantDto adminPart = createParticipant(proposalId, userUUID, ProposalRole.REVIEWER, 99L);
        ProposalParticipantDto partA = createParticipant(proposalId, staffA, ProposalRole.PROPOSER, sharedRoleId);
        ProposalParticipantDto partB = createParticipant(proposalId, staffB, ProposalRole.PROPOSER, sharedRoleId);

        // Competency Proposals: Both exist
        CompetencyProposalDto compA = new CompetencyProposalDto();
        compA.setId(targetId); compA.setName("A"); compA.setDescription("A");
        ProposalDto pDto = new ProposalDto(); pDto.setId(proposalId); compA.setProposal(pDto);

        CompetencyProposalDto compB = new CompetencyProposalDto();
        compB.setId(new ProposalParticipantId(proposalId, staffB)); // This ID is NOT in the payload
        compB.setProposal(pDto);

        // --- 2. Mocks ---
        when(proposalParticipantService.getAllProposalParticipants())
                .thenReturn(List.of(adminPart, partA, partB));

        // Return BOTH competencies
        when(competencyProposalService.getAllByProposalIdIn(Set.of(proposalId)))
                .thenReturn(List.of(compA, compB));

        when(competencyCompTagProposalService.getByProposalStaffIdIn(any(), any())).thenReturn(Collections.emptyList());
        when(authorityService.findByName(any())).thenReturn(new AuthorityDto());

        // --- 3. Execution ---
        mockMvc.perform(put("/api/competency-definition-collaboration/bulk-reject-competency-proposal")
                        .principal(authentication)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(payload)))
                .andExpect(status().isOk());

        // --- 4. Verification ---
        // A. Parent Proposal Status Update MUST NOT happen (Because compB remains)
        verify(proposalService, never()).updateProposalStatusByIdIn(any(), any(), any());

        // B. Proposal Participation Bulk Delete MUST NOT happen (Only specific delete)
        verify(proposalParticipantService, never()).deleteAllByProposalIdIn(any());

        // C. Specific ID Deletion MUST happen
        verify(proposalParticipantService).deleteAllByIdIn(argThat(set -> set.contains(targetId)));

        // D. Access MUST NOT be revoked (Because partB still holds sharedRoleId 55)
        verify(roleAuthorityService).deleteAllByIdIn(Collections.emptySet()); // Or verify never called with non-empty set
    }

    @Test
    @WithMockUser(authorities = {"CAN_MANAGE_COMPETENCY"})
    void bulkReject_MixedProposals_ShouldCloseEmptyAndKeepOthers() throws Exception {
        // Proposal 1: Only User A (Will be fully rejected)
        Long p1 = 1L; UUID u1 = UUID.randomUUID();
        ProposalParticipantId id1 = new ProposalParticipantId(p1, u1);

        // Proposal 2: User B (Reject) and User C (Keep)
        Long p2 = 2L; UUID u2 = UUID.randomUUID(); UUID u3 = UUID.randomUUID();
        ProposalParticipantId id2 = new ProposalParticipantId(p2, u2);

        Set<ProposalParticipantId> payload = Set.of(id1, id2);

        // Setup Target Participants (Proposers)
        ProposalParticipantDto part1 = createParticipant(p1, u1, ProposalRole.PROPOSER, 10L);
        ProposalParticipantDto part2 = createParticipant(p2, u2, ProposalRole.PROPOSER, 20L);
        ProposalParticipantDto part3 = createParticipant(p2, u3, ProposalRole.PROPOSER, 20L); // Keeps P2 alive

        // --- FIX START: Add the Current User (Admin) to BOTH proposals ---
        // The controller needs to find 'userUUID' in the participant list for P1 to send emails
        ProposalParticipantDto adminPartP1 = createParticipant(p1, userUUID, ProposalRole.REVIEWER, 99L);

        // The controller ALSO needs to find 'userUUID' in the participant list for P2
        ProposalParticipantDto adminPartP2 = createParticipant(p2, userUUID, ProposalRole.REVIEWER, 99L);
        // --- FIX END ---

        // Setup Competencies (What exists in DB)
        CompetencyProposalDto c1 = new CompetencyProposalDto(); c1.setId(id1);
        c1.setProposal(new ProposalDto()); c1.getProposal().setId(p1);
        c1.setName("C1"); c1.setDescription("D1");

        CompetencyProposalDto c2 = new CompetencyProposalDto(); c2.setId(id2);
        c2.setProposal(new ProposalDto()); c2.getProposal().setId(p2);
        c2.setName("C2"); c2.setDescription("D2");

        CompetencyProposalDto c3 = new CompetencyProposalDto(); c3.setId(new ProposalParticipantId(p2, u3));
        c3.setProposal(new ProposalDto()); c3.getProposal().setId(p2);

        // Mocks
        // Add the admin participants to the returned list
        when(proposalParticipantService.getAllProposalParticipants())
                .thenReturn(List.of(part1, part2, part3, adminPartP1, adminPartP2));

        // Return all involved comps (C1, C2, C3)
        when(competencyProposalService.getAllByProposalIdIn(argThat(s -> s.contains(p1) && s.contains(p2))))
                .thenReturn(List.of(c1, c2, c3));

        when(authorityService.findByName(any())).thenReturn(new AuthorityDto());
        when(competencyCompTagProposalService.getByProposalStaffIdIn(any(), any())).thenReturn(Collections.emptyList());

        // Execution
        mockMvc.perform(put("/api/competency-definition-collaboration/bulk-reject-competency-proposal")
                        .principal(authentication)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(payload)))
                .andExpect(status().isOk());

        // Verification
        // 1. Proposal 1 should be REJECTED (Fully empty)
        verify(proposalService).updateProposalStatusByIdIn(argThat(set -> set.contains(p1) && !set.contains(p2)), any(), any());

        // 2. Proposal 1 participants cleared
        verify(proposalParticipantService).deleteAllByProposalIdIn(argThat(set -> set.contains(p1)));

        // 3. Specific ID (id2) removed (From Proposal 2)
        verify(proposalParticipantService).deleteAllByIdIn(argThat(set -> set.contains(id2)));
    }

    @Test
    @WithMockUser(authorities = {"CAN_MANAGE_COMPETENCY"})
    void approveCompetencyProposal_NullInput_ShouldThrowBadRequest() throws Exception {
        mockMvc.perform(put("/api/competency-definition-collaboration/approve-competency-proposal") // Verify path matches controller
                        .principal(authentication)
                        .contentType(MediaType.APPLICATION_JSON)) // Null body
                .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser(authorities = {"CAN_MANAGE_COMPETENCY"})
    void approveCompetencyProposal_HappyPath_ShouldCreateCompetencyAndApprove() throws Exception {
        // --- 1. Data Setup ---
        Long proposalId = 1L;
        UUID staffId = UUID.randomUUID();
        ProposalParticipantId selectedId = new ProposalParticipantId(proposalId, staffId);

        setupApprovalMocks(proposalId, staffId, selectedId);

        // Tag Setup: 1 Tag exists
        Long tagId = 100L;
        CompetencyCompTagProposalDto tagPropDto = new CompetencyCompTagProposalDto();
        tagPropDto.setId(new CompetencyCompTagProposalId(proposalId, staffId, tagId));
        CompTagDto tagDto = new CompTagDto(); tagDto.setId(tagId); tagDto.setTag("Java");
        tagPropDto.setCompTag(tagDto);

        // --- 2. Mocks ---
        // Return tags for this proposal
        when(competencyCompTagProposalService.findAllByProposalId(proposalId))
                .thenReturn(List.of(tagPropDto));

        // Tag Cleanup Checks (Assume tag is used elsewhere, so NOT deleted)
        CompetencyCompTagDto usageMock = new CompetencyCompTagDto();
        usageMock.setId(new CompetencyCompTagId(999L, tagId));
        when(competencyCompTagService.findAllByCompTagIdIn(anySet())).thenReturn(List.of(usageMock));

        // --- 3. Execution ---
        mockMvc.perform(put("/api/competency-definition-collaboration/approve-competency-proposal")
                        .principal(authentication)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(selectedId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Success"));

        // --- 4. Verification ---
        // A. Verify New Competency Created
        verify(competencyService).create(argThat(dto -> dto.getName().equals("Valid Name")));

        // B. Verify Tags Assigned to New Competency
        verify(competencyCompTagService).createAll(argThat(list ->
                list.size() == 1 && list.get(0).getId().getCompetencyId().equals(555L)
        ));

        // C. Verify Parent Proposal Updated to APPROVED
        verify(proposalService).updateProposalStatusById(eq(proposalId), eq(ProposalStatus.APPROVED), eq(userUUID));

        // D. Verify Cleanup
        verify(competencyCompTagProposalService).deleteAllByProposalId(proposalId);
        verify(competencyProposalService).deleteAllByProposalId(proposalId);
        verify(proposalParticipantService).deleteAllByProposalId(proposalId);

        // E. Verify Tag Definition NOT deleted (because usageMock existed)
        verify(compTagService, never()).deleteAllByIdIn(any(), any());
    }

    @Test
    @WithMockUser(authorities = {"CAN_MANAGE_COMPETENCY"})
    void approveCompetencyProposal_UnusedTags_ShouldDeleteTagDefinition() throws Exception {
        Long proposalId = 1L;
        UUID staffId = UUID.randomUUID();
        ProposalParticipantId selectedId = new ProposalParticipantId(proposalId, staffId);

        setupApprovalMocks(proposalId, staffId, selectedId);

        // Tag Setup
        Long tagId = 100L;
        CompetencyCompTagProposalDto tagPropDto = new CompetencyCompTagProposalDto();
        tagPropDto.setId(new CompetencyCompTagProposalId(proposalId, staffId, tagId));
        tagPropDto.setCompTag(new CompTagDto()); tagPropDto.getCompTag().setId(tagId);

        // Mocks
        when(competencyCompTagProposalService.findAllByProposalId(proposalId)).thenReturn(List.of(tagPropDto));

        // ** CRITICAL: Tag is NOT used elsewhere **
        when(competencyCompTagService.findAllByCompTagIdIn(anySet())).thenReturn(Collections.emptyList());
        when(competencyCompTagProposalService.getByCompTagIdIn(anySet())).thenReturn(Collections.emptyList());

        // Execution
        mockMvc.perform(put("/api/competency-definition-collaboration/approve-competency-proposal")
                        .principal(authentication)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(selectedId)))
                .andExpect(status().isOk());

        // Verification
        // Verify Tag Definition DELETED
        verify(compTagService).deleteAllByIdIn(argThat(set -> set.contains(tagId)), eq(userUUID));
    }

    @Test
    @WithMockUser(authorities = {"CAN_MANAGE_COMPETENCY"})
    void approveCompetencyProposal_MultiProposer_ShouldSendCorrectEmails() throws Exception {
        // --- 1. Data Setup ---
        Long proposalId = 1L;
        UUID winnerId = UUID.randomUUID();
        UUID loserId = UUID.randomUUID();

        ProposalParticipantId selectedId = new ProposalParticipantId(proposalId, winnerId);

        // Participants
        ProposalParticipantDto winnerPart = new ProposalParticipantDto();
        winnerPart.setId(selectedId);
        winnerPart.setProposalRole(ProposalRole.PROPOSER);
        winnerPart.setStaff(new StaffDto()); winnerPart.getStaff().setEmail("winner@tbm.com");
        winnerPart.getStaff().setRole(new RoleDto());

        ProposalParticipantDto loserPart = new ProposalParticipantDto();
        loserPart.setId(new ProposalParticipantId(proposalId, loserId));
        loserPart.setProposalRole(ProposalRole.PROPOSER);
        loserPart.setStaff(new StaffDto()); loserPart.getStaff().setEmail("loser@tbm.com");

        ProposalParticipantDto adminPart = new ProposalParticipantDto();
        adminPart.setId(new ProposalParticipantId(proposalId, userUUID));
        adminPart.setProposalRole(ProposalRole.REVIEWER);
        adminPart.setStaff(new StaffDto()); adminPart.getStaff().setEmail("admin@tbm.com");

        // Competency Proposals (Both users submitted one)
        CompetencyProposalDto winnerComp = new CompetencyProposalDto();
        winnerComp.setId(selectedId);
        winnerComp.setName("Winner Doc");
        winnerComp.setDescription("Winner Desc");
        winnerComp.setStaff(winnerPart.getStaff());
        // Link to Proposal Parent (crucial for ID matching in controller logic)
        winnerComp.setProposal(new ProposalDto()); winnerComp.getProposal().setId(proposalId);

        CompetencyProposalDto loserComp = new CompetencyProposalDto();
        loserComp.setId(loserPart.getId());
        loserComp.setName("Loser Doc");
        loserComp.setDescription("Loser Desc");
        loserComp.setStaff(loserPart.getStaff());
        loserComp.setProposal(new ProposalDto()); loserComp.getProposal().setId(proposalId);


        // --- 2. Mocks ---
        when(proposalParticipantService.getAllProposalParticipants())
                .thenReturn(List.of(winnerPart, loserPart, adminPart));

        // Return BOTH proposals (Winner and Loser)
        when(competencyProposalService.getAllByProposalId(proposalId))
                .thenReturn(List.of(winnerComp, loserComp));

        when(competencyService.create(any())).thenReturn(new CompetencyDto());
        when(authorityService.findByName(any())).thenReturn(new AuthorityDto());
        when(competencyCompTagProposalService.findAllByProposalId(any())).thenReturn(Collections.emptyList());

        // --- 3. Execution ---
        mockMvc.perform(put("/api/competency-definition-collaboration/approve-competency-proposal")
                        .principal(authentication)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(selectedId)))
                .andExpect(status().isOk());

        // --- 4. Verification ---

        // A. Winner gets APPROVED email (Once, for their own proposal)
        verify(emailService).sendCompetencyProposalApprovedEmail(
                eq("winner@tbm.com"), any(), any(), any(), any(), any()
        );

        // B. Admin (Reviewer) gets APPROVED email
        // FIX: Expect 2 times (Once for WinnerComp loop, Once for LoserComp loop)
        verify(emailService, times(2)).sendCompetencyProposalApprovedEmail(
                eq("admin@tbm.com"), any(), any(), any(), any(), any()
        );

        // C. Loser gets REJECTED email
        // This happens when the loop iterates over 'winnerComp' and sees loser is not the owner
        verify(emailService).sendCompetencyProposalRejectedEmail(
                eq("loser@tbm.com"), any(), any(), any(), any(), any()
        );
    }

    @Test
    @WithMockUser(authorities = {"CAN_MANAGE_COMPETENCY"})
    void bulkApprove_NullInput_ShouldThrowBadRequest() throws Exception {
        mockMvc.perform(put("/api/competency-definition-collaboration/bulk-approve-competency-proposal") // Verify path
                        .principal(authentication)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser(authorities = {"CAN_MANAGE_COMPETENCY"})
    void bulkApprove_DuplicateProposals_ShouldThrowBadRequest() throws Exception {
        Long proposalId = 1L;
        UUID staffA = UUID.randomUUID();
        UUID staffB = UUID.randomUUID();

        // Selecting TWO versions of the SAME proposal
        ProposalParticipantId id1 = new ProposalParticipantId(proposalId, staffA);
        ProposalParticipantId id2 = new ProposalParticipantId(proposalId, staffB);

        Set<ProposalParticipantId> payload = Set.of(id1, id2);

        mockMvc.perform(put("/api/competency-definition-collaboration/bulk-approve-competency-proposal")
                        .principal(authentication)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(payload)))
                .andExpect(status().isBadRequest()); // Expect Conflict Error
    }

    @Test
    @WithMockUser(authorities = {"CAN_MANAGE_COMPETENCY"})
    void bulkApprove_MultipleValidProposals_ShouldCreateCompetenciesAndApprove() throws Exception {
        // --- 1. Data Setup ---
        Long p1 = 1L; UUID u1 = UUID.randomUUID();
        Long p2 = 2L; UUID u2 = UUID.randomUUID();

        ProposalParticipantId id1 = new ProposalParticipantId(p1, u1);
        ProposalParticipantId id2 = new ProposalParticipantId(p2, u2);
        Set<ProposalParticipantId> payload = Set.of(id1, id2);

        // Mock Proposal DTOs
        CompetencyProposalDto dto1 = new CompetencyProposalDto(); dto1.setId(id1); dto1.setName("Comp A");
        CompetencyProposalDto dto2 = new CompetencyProposalDto(); dto2.setId(id2); dto2.setName("Comp B");

        // Setup basic mocks
        setupBulkApprovalMocks(payload, List.of(dto1, dto2));

        // Mock Participants (Minimal)
        ProposalParticipantDto part1 = new ProposalParticipantDto(); part1.setId(id1); part1.setStaff(new StaffDto());
        ProposalParticipantDto part2 = new ProposalParticipantDto(); part2.setId(id2); part2.setStaff(new StaffDto());
        when(proposalParticipantService.getAllProposalParticipants()).thenReturn(List.of(part1, part2));

        // --- 2. Execution ---
        mockMvc.perform(put("/api/competency-definition-collaboration/bulk-approve-competency-proposal")
                        .principal(authentication)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(payload)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Success"));

        // --- 3. Verification ---
        // A. Verify 2 Competencies Created
        verify(competencyService).createAll(argThat(list -> list.size() == 2));

        // B. Verify Parent Proposals Approved
        verify(proposalService).updateProposalStatusByIdIn(
                argThat(set -> set.contains(p1) && set.contains(p2)),
                eq(ProposalStatus.APPROVED),
                eq(userUUID)
        );

        // C. Verify Cleanup
        verify(competencyProposalService).deleteAllByProposalIdIn(anySet());
        verify(proposalParticipantService).deleteAllByProposalIdIn(anySet());
    }

    @Test
    @WithMockUser(authorities = {"CAN_MANAGE_COMPETENCY"})
    void bulkApprove_UnusedTags_ShouldDeleteTagDefinition() throws Exception {
        Long p1 = 1L; UUID u1 = UUID.randomUUID();
        ProposalParticipantId id1 = new ProposalParticipantId(p1, u1);
        Set<ProposalParticipantId> payload = Set.of(id1);

        CompetencyProposalDto dto1 = new CompetencyProposalDto(); dto1.setId(id1); dto1.setName("Comp Tagged");
        setupBulkApprovalMocks(payload, List.of(dto1));

        // --- Tag Setup ---
        Long tagId = 100L;
        CompetencyCompTagProposalDto tagProp = new CompetencyCompTagProposalDto();
        tagProp.setId(new CompetencyCompTagProposalId(p1, u1, tagId));
        tagProp.setCompTag(new CompTagDto()); tagProp.getCompTag().setId(tagId);

        // Mock: Tag exists in proposal
        when(competencyCompTagProposalService.findAllByProposalIdIn(anySet())).thenReturn(List.of(tagProp));

        // Mock: Tag NOT used elsewhere
        when(competencyCompTagService.findAllByCompTagIdIn(anySet())).thenReturn(Collections.emptyList());
        when(competencyCompTagProposalService.getByCompTagIdIn(anySet())).thenReturn(Collections.emptyList());

        // --- FIX START: Create a valid participant to avoid NPE in access control logic ---
        ProposalParticipantDto dummyPart = new ProposalParticipantDto();
        dummyPart.setId(id1); // 1. Set ID so getId().getProposalId() works

        StaffDto staff = new StaffDto();
        staff.setId(u1);
        // 2. Set Role so getStaff().getRole().getId() works (used in bulkRemoveAccessGranted)
        staff.setRole(new RoleDto());
        dummyPart.setStaff(staff);

        when(proposalParticipantService.getAllProposalParticipants()).thenReturn(List.of(dummyPart));
        // --- FIX END ---

        // Execution
        mockMvc.perform(put("/api/competency-definition-collaboration/bulk-approve-competency-proposal")
                        .principal(authentication)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(payload)))
                .andExpect(status().isOk());

        // Verification
        verify(compTagService).deleteAllByIdIn(argThat(set -> set.contains(tagId)), eq(userUUID));
    }

    @Test
    @WithMockUser(authorities = {"CAN_MANAGE_COMPETENCY"})
    void bulkApprove_WinLoss_ShouldSendCorrectEmails() throws Exception {
        // --- 1. Data Setup ---
        Long p1 = 1L;
        UUID winnerId = UUID.randomUUID();
        UUID loserId = UUID.randomUUID();

        ProposalParticipantId winnerIdStruct = new ProposalParticipantId(p1, winnerId);
        Set<ProposalParticipantId> payload = Set.of(winnerIdStruct);

        // Winner's Proposal Content
        CompetencyProposalDto winnerDto = new CompetencyProposalDto();
        winnerDto.setId(winnerIdStruct);
        winnerDto.setName("Winner Doc");
        setupBulkApprovalMocks(payload, List.of(winnerDto));

        // Create a dummy role for access control logic
        RoleDto dummyRole = new RoleDto();
        dummyRole.setId(10L);

        // Participants: Winner, Loser, Admin(Reviewer)
        ProposalParticipantDto winnerPart = new ProposalParticipantDto();
        winnerPart.setId(winnerIdStruct);
        winnerPart.setProposalRole(ProposalRole.PROPOSER);
        winnerPart.setStaff(new StaffDto());
        winnerPart.getStaff().setEmail("winner@tbm.com");
        // FIX: Set Role to prevent NPE
        winnerPart.getStaff().setRole(dummyRole);

        ProposalParticipantDto loserPart = new ProposalParticipantDto();
        loserPart.setId(new ProposalParticipantId(p1, loserId));
        loserPart.setProposalRole(ProposalRole.PROPOSER);
        loserPart.setStaff(new StaffDto());
        loserPart.getStaff().setEmail("loser@tbm.com");
        // FIX: Set Role to prevent NPE
        loserPart.getStaff().setRole(dummyRole);

        // CRITICAL: Admin must be Reviewer on THIS proposal to get email
        ProposalParticipantDto adminPart = new ProposalParticipantDto();
        adminPart.setId(new ProposalParticipantId(p1, userUUID));
        adminPart.setProposalRole(ProposalRole.REVIEWER);
        adminPart.setStaff(new StaffDto());
        adminPart.getStaff().setEmail("admin@tbm.com");
        // Note: Admin usually doesn't need a role for the bulkRemoveAccessGranted filter
        // because that filter checks for ProposalRole.PROPOSER, but it's safe to add if needed.

        // Return all 3
        when(proposalParticipantService.getAllProposalParticipants())
                .thenReturn(List.of(winnerPart, loserPart, adminPart));

        // --- 2. Execution ---
        mockMvc.perform(put("/api/competency-definition-collaboration/bulk-approve-competency-proposal")
                        .principal(authentication)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(payload)))
                .andExpect(status().isOk());

        // --- 3. Verification ---

        // A. Winner (Approved Email)
        verify(emailService).sendCompetencyProposalApprovedEmail(
                eq("winner@tbm.com"), any(), any(), any(), any(), any()
        );

        // B. Admin (Approved Email)
        verify(emailService).sendCompetencyProposalApprovedEmail(
                eq("admin@tbm.com"), any(), any(), any(), any(), any()
        );

        // C. Loser (Rejected Email)
        verify(emailService).sendCompetencyProposalRejectedEmail(
                eq("loser@tbm.com"), any(), any(), any(), any(), any()
        );
    }

    // Helper method to create a valid request object
    private ProposeCompetencyRequest createValidRequest() {
        ProposeCompetencyRequest req = new ProposeCompetencyRequest();
        req.setCompetencyName("Cloud Computing");
        req.setCompetencyDescription("AWS and Azure skills");
        req.setReviewerList(new ArrayList<>());
        req.setProposerList(new ArrayList<>());
        req.setCompetencyTagList(List.of("Cloud", "Infrastructure"));
        return req;
    }

    private EditCompetencyProposalRequestDto createEditRequest() {
        EditCompetencyProposalRequestDto req = new EditCompetencyProposalRequestDto();
        req.setProposalParticipantId(new ProposalParticipantId(1L, UUID.randomUUID()));
        req.setCompetencyName("New Name");
        req.setCompetencyDescription("New Desc");
        req.setCompTagList(List.of("Java", "Spring"));
        return req;
    }

    private ProposalParticipantDto createParticipant(Long proposalId, UUID staffId, ProposalRole role, Long roleId) {
        ProposalParticipantDto dto = new ProposalParticipantDto();
        dto.setId(new ProposalParticipantId(proposalId, staffId));
        dto.setProposalRole(role);
        dto.setInitiator(role == ProposalRole.REVIEWER); // Simplified logic for test

        StaffDto staff = new StaffDto();
        staff.setId(staffId);
        staff.setEmail("staff" + staffId + "@tbm.com");

        RoleDto roleDto = new RoleDto();
        roleDto.setId(roleId);
        staff.setRole(roleDto);

        dto.setStaff(staff);
        return dto;
    }

    private void setupApprovalMocks(Long proposalId, UUID staffId, ProposalParticipantId selectedId) {
        // Basic Staff & Role Setup
        StaffDto staff = new StaffDto();
        staff.setId(staffId);
        staff.setEmail("winner@tbm.com");
        RoleDto role = new RoleDto(); role.setId(10L);
        staff.setRole(role);

        // Selected Competency Proposal
        CompetencyProposalDto selectedCompProp = new CompetencyProposalDto();
        selectedCompProp.setId(selectedId);
        selectedCompProp.setName("Valid Name");
        selectedCompProp.setDescription("Valid Desc");
        selectedCompProp.setStaff(staff);

        // Mock: Get All Participants (Minimal Return)
        ProposalParticipantDto partDto = new ProposalParticipantDto();
        partDto.setId(selectedId);
        partDto.setStaff(staff);
        partDto.setProposalRole(ProposalRole.PROPOSER);
        when(proposalParticipantService.getAllProposalParticipants()).thenReturn(List.of(partDto));

        // Mock: Get Competency Proposals
        when(competencyProposalService.getAllByProposalId(proposalId))
                .thenReturn(List.of(selectedCompProp));

        // Mock: Competency Creation
        CompetencyDto createdComp = new CompetencyDto(); createdComp.setId(555L);
        when(competencyService.create(any())).thenReturn(createdComp);

        // Mock: Access Control
        when(authorityService.findByName(any())).thenReturn(new AuthorityDto());
    }

    private void setupBulkApprovalMocks(Set<ProposalParticipantId> inputIds, List<CompetencyProposalDto> mockProposalDtos) {
        // 1. Mock finding the specific proposals selected
        when(competencyProposalService.getAllByIdIn(inputIds)).thenReturn(mockProposalDtos);

        // 2. Mock converting them to Competencies (Return a valid Competency with same Name)
        List<CompetencyDto> createdCompetencies = mockProposalDtos.stream().map(p -> {
            CompetencyDto c = new CompetencyDto();
            c.setId(new Random().nextLong());
            c.setName(p.getName()); // CRITICAL: Controller maps by Name
            c.setCreatedBy(p.getId().getStaffId());
            return c;
        }).toList();
        when(competencyService.createAll(anyList())).thenReturn(createdCompetencies);

        // 3. Mock Basic Services to prevent NPEs
        when(authorityService.findByName(any())).thenReturn(new AuthorityDto());
        when(competencyCompTagProposalService.findAllByProposalIdIn(any())).thenReturn(Collections.emptyList());
    }
}