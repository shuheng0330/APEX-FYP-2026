package com.tbm.careerpathlearning.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tbm.careerpathlearning.dto.EditCompetencyProposalRequestDto;
import com.tbm.careerpathlearning.dto.ProposeCompetencyRequest;
import com.tbm.careerpathlearning.enums.*;
import com.tbm.careerpathlearning.model.*;
import com.tbm.careerpathlearning.repository.*;
import com.tbm.careerpathlearning.service.EmailService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
public class CompetencyDefinitionCollaborationIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private StaffRepository staffRepository;

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private OrgChartRepository orgChartRepository;

    @Autowired
    private ProposalRepository proposalRepository;

    @Autowired
    private ProposalParticipantRepository proposalParticipantRepository;

    @Autowired
    private CompetencyProposalRepository competencyProposalRepository;

    @Autowired
    private CompetencyRepository competencyRepository;

    @Autowired
    private AuthorityRepository authorityRepository;

    @Autowired
    private CompTagRepository compTagRepository;

    @MockitoBean
    private EmailService emailService;

    // UUIDs for Mock Users
    private static final String MANAGER_UUID = "aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa";
    private static final String STAFF_UUID = "bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb";

    private Staff manager;
    private Staff staff;

    @BeforeEach
    void setUp() {
        // 1. Cleanup
        competencyProposalRepository.deleteAll();
        proposalParticipantRepository.deleteAll();
        proposalRepository.deleteAll();
        staffRepository.deleteAll();
        roleRepository.deleteAll();
        orgChartRepository.deleteAll();
        competencyRepository.deleteAll();
        compTagRepository.deleteAll();
        authorityRepository.deleteAll();

        // 2. Setup Authorities
        createAuthority(AuthorityName.CAN_MANAGE_COMPETENCY);
        createAuthority(AuthorityName.CAN_PROPOSE_ROLE_COMPETENCIES);

        // 3. Setup Org & Roles
        OrgChart dept = createOrgChart();
        Role managerRole = createRole("Manager", dept);
        Role staffRole = createRole("Staff", dept);

        // 4. Setup Users
        manager = createStaff(UUID.fromString(MANAGER_UUID), "Manager User", "manager@tbm.com", managerRole);
        staff = createStaff(UUID.fromString(STAFF_UUID), "Staff User", "staff@tbm.com", staffRole);

        // 5. Mock Email Service to avoid errors
        doNothing().when(emailService).sendCompetencyProposalReviewInvitationEmail(any(), any(), any(), any(), any(), any(), any());
        doNothing().when(emailService).sendCompetencyProposalProposeInvitationEmail(any(), any(), any(), any(), any(), any(), any());
    }

    // --- Scenario 1: Initiate Proposal (Manager initiates) ---
    @Test
    void proposeCompetency_ShouldCreateProposalAndParticipants() throws Exception {
        ProposeCompetencyRequest req = new ProposeCompetencyRequest();
        req.setCompetencyName("Cloud Computing");
        req.setCompetencyDescription("AWS and Azure skills");
        req.setReviewerList(Collections.emptyList());
        req.setProposerList(List.of(staff.getId()));
        req.setCompetencyTagList(List.of("Tech", "Cloud"));

        mockMvc.perform(post("/api/competency-definition-collaboration")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req))
                        .with(user(MANAGER_UUID).authorities(() -> "CAN_MANAGE_COMPETENCY")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").exists());

        // Verify DB
        List<Proposal> proposals = proposalRepository.findAll();
        assertThat(proposals).hasSize(1);
        Long proposalId = proposals.get(0).getId();

        // Verify Participants (Manager = Reviewer/Initiator, Staff = Proposer)
        List<ProposalParticipant> participants = proposalParticipantRepository.findAll();
        assertThat(participants).hasSize(2); // 1 Reviewer + 1 Proposer (Manager is initiator, but added as reviewer in logic)

        // Verify Competency Proposal Draft created for Proposer
        List<CompetencyProposal> drafts = competencyProposalRepository.findAll();
        assertThat(drafts).isNotEmpty();
        assertThat(drafts.get(0).getName()).isEqualTo("Cloud Computing");
    }

    // --- Scenario 2: Edit Proposal (Staff updates draft) ---
    @Test
    void updateCompetencyProposal_ShouldUpdateDraftAndTags() throws Exception {
        // 1. Setup existing proposal
        Proposal proposal = createProposal();
        createParticipant(proposal, manager, ProposalRole.REVIEWER);
        createParticipant(proposal, staff, ProposalRole.PROPOSER);
        createCompetencyProposal(proposal, staff, "Old Name", "Old Desc");

        // 2. Edit Request
        EditCompetencyProposalRequestDto req = new EditCompetencyProposalRequestDto();
        req.setProposalParticipantId(new ProposalParticipantId(proposal.getId(), staff.getId()));
        req.setCompetencyName("Cloud Computing Updated");
        req.setCompetencyDescription("Updated Description");
        req.setCompTagList(List.of("New Tag"));

        mockMvc.perform(put("/api/competency-definition-collaboration/edit-competency-proposal")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req))
                        .with(user(STAFF_UUID).authorities(() -> "CAN_PROPOSE_ROLE_COMPETENCIES")))
                .andExpect(status().isOk());

        // 3. Verify DB Update
        CompetencyProposal updated = competencyProposalRepository.findById(new ProposalParticipantId(proposal.getId(), staff.getId())).orElseThrow();
        assertThat(updated.getName()).isEqualTo("Cloud Computing Updated");

        // Verify Tags created
        assertThat(compTagRepository.findByTag("New Tag")).isPresent();
    }

    // --- Scenario 3: Approve Proposal (Manager Approves) ---
    @Test
    void approveCompetencyProposal_ShouldCreateMasterCompetency() throws Exception {
        // 1. Setup existing proposal
        Proposal proposal = createProposal();
        createParticipant(proposal, manager, ProposalRole.REVIEWER);
        createParticipant(proposal, staff, ProposalRole.PROPOSER);
        // Staff submitted a draft
        CompetencyProposal draft = createCompetencyProposal(proposal, staff, "Final Competency", "Final Desc");

        // 2. Approve Request
        ProposalParticipantId targetId = new ProposalParticipantId(proposal.getId(), staff.getId());

        mockMvc.perform(put("/api/competency-definition-collaboration/approve-competency-proposal")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(targetId))
                        .with(user(MANAGER_UUID).authorities(() -> "CAN_MANAGE_COMPETENCY")))
                .andExpect(status().isOk());

        // 3. Verify Master Data Creation
        // The Draft should be GONE (or status changed, controller deletes drafts upon approval)
        assertThat(competencyProposalRepository.findById(targetId)).isEmpty();

        // The Real Competency should EXIST
        List<Competency> masterRecords = competencyRepository.findAll();
        assertThat(masterRecords).hasSize(1);
        assertThat(masterRecords.get(0).getName()).isEqualTo("Final Competency");

        // Verify Parent Proposal Status
        Proposal p = proposalRepository.findById(proposal.getId()).orElseThrow();
        assertThat(p.getStatus()).isEqualTo(ProposalStatus.APPROVED);
    }

    // --- Scenario 4: Reject Proposal ---
    @Test
    void rejectCompetencyProposal_ShouldDeleteDraft() throws Exception {
        // 1. Setup
        Proposal proposal = createProposal();
        createParticipant(proposal, manager, ProposalRole.REVIEWER);
        createParticipant(proposal, staff, ProposalRole.PROPOSER);
        createCompetencyProposal(proposal, staff, "Bad Draft", "Desc");

        ProposalParticipantId targetId = new ProposalParticipantId(proposal.getId(), staff.getId());

        // 2. Reject
        mockMvc.perform(put("/api/competency-definition-collaboration/reject-competency-proposal")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(targetId))
                        .with(user(MANAGER_UUID).authorities(() -> "CAN_MANAGE_COMPETENCY")))
                .andExpect(status().isOk());

        // 3. Verify
        assertThat(competencyProposalRepository.findById(targetId)).isEmpty();

        // Since it was the only draft, parent proposal should be rejected/closed
        Proposal p = proposalRepository.findById(proposal.getId()).orElseThrow();
        assertThat(p.getStatus()).isEqualTo(ProposalStatus.REJECTED);
    }

    // --- Scenario 5: Get Overview ---
    @Test
    void overview_ShouldReturnMyProposals() throws Exception {
        // Setup: Staff has an ongoing proposal
        Proposal proposal = createProposal();
        createParticipant(proposal, staff, ProposalRole.PROPOSER);
        createCompetencyProposal(proposal, staff, "My Draft", "Desc");

        mockMvc.perform(get("/api/competency-definition-collaboration/overview")
                        .with(user(STAFF_UUID).authorities(() -> "CAN_PROPOSE_ROLE_COMPETENCIES")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].competencyName").value("My Draft"));
    }

    // Helpers
    private Authority createAuthority(AuthorityName name) {
        Authority a = new Authority();
        a.setName(name);
        a.setLabelKey("label");
        a.setDescriptionKey("desc");
        return authorityRepository.save(a);
    }

    private OrgChart createOrgChart() {
        OrgChart oc = new OrgChart();
        oc.setName("Dept");
        oc.setType(OrgChartType.D);
        oc.setDeleted(false);
        oc.setCreatedAt(OffsetDateTime.now());
        oc.setUpdatedAt(OffsetDateTime.now());
        return orgChartRepository.save(oc);
    }

    private Role createRole(String name, OrgChart oc) {
        Role r = new Role();
        r.setName(name);
        r.setOrgChart(oc);
        r.setDeleted(false);
        r.setCreatedAt(OffsetDateTime.now());
        r.setUpdatedAt(OffsetDateTime.now());
        return roleRepository.save(r);
    }

    private Staff createStaff(UUID id, String name, String email, Role role) {
        Staff s = new Staff();
        s.setId(id);
        s.setName(name);
        s.setEmail(email);
        s.setRole(role);
        s.setAccountStatus(StaffAccountStatus.ACTIVE);
        s.setDeleted(false);
        s.setCreatedAt(OffsetDateTime.now());
        s.setUpdatedAt(OffsetDateTime.now());
        return staffRepository.save(s);
    }

    private Proposal createProposal() {
        Proposal p = new Proposal();
        p.setType(ProposalType.COMPETENCY);
        p.setStatus(ProposalStatus.ONGOING);
        p.setCreatedBy(UUID.fromString(MANAGER_UUID));
        p.setCreatedAt(OffsetDateTime.now());
        p.setUpdatedBy(UUID.fromString(MANAGER_UUID));
        p.setUpdatedAt(OffsetDateTime.now());
        return proposalRepository.save(p);
    }

    private void createParticipant(Proposal p, Staff s, ProposalRole role) {
        ProposalParticipant pp = new ProposalParticipant();
        pp.setId(new ProposalParticipantId(p.getId(), s.getId()));
        pp.setProposal(p);
        pp.setStaff(s);
        pp.setProposalRole(role);
        pp.setInitiator(role == ProposalRole.REVIEWER);
        pp.setCreatedAt(OffsetDateTime.now());
        pp.setUpdatedAt(OffsetDateTime.now());
        pp.setCreatedBy(UUID.fromString(MANAGER_UUID));
        proposalParticipantRepository.save(pp);
    }

    private CompetencyProposal createCompetencyProposal(Proposal p, Staff s, String name, String desc) {
        CompetencyProposal cp = new CompetencyProposal();
        cp.setId(new ProposalParticipantId(p.getId(), s.getId()));
        cp.setProposal(p);
        cp.setStaff(s);
        cp.setName(name);
        cp.setDescription(desc);
        cp.setCreatedAt(OffsetDateTime.now());
        cp.setUpdatedAt(OffsetDateTime.now());
        cp.setCreatedBy(UUID.fromString(MANAGER_UUID));
        return competencyProposalRepository.save(cp);
    }
}