package com.tbm.careerpathlearning.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tbm.careerpathlearning.dto.CompetencyAssignmentCreationRequiredDataDto;
import com.tbm.careerpathlearning.dto.CompetencyAssignmentCreationRequiredDataId;
import com.tbm.careerpathlearning.dto.ProposeCompetencyAssignmentMap;
import com.tbm.careerpathlearning.dto.ProposeCompetencyAssignmentRequest;
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
import org.springframework.test.web.servlet.MvcResult;
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
public class CompetencyAssignmentCollaborationIntegrationTest {

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
    private CompetencyRepository competencyRepository;

    @Autowired
    private ProposalRepository proposalRepository;

    @Autowired
    private RoleCompetencyProposalRepository roleCompetencyProposalRepository;

    @Autowired
    private RoleCompetencyItemRepository roleCompetencyItemRepository;

    @Autowired
    private RoleCompetencyRepository roleCompetencyRepository;

    @Autowired
    private AuthorityRepository authorityRepository;

    @Autowired
    private ProposalParticipantRepository proposalParticipantRepository;

    @MockitoBean
    private EmailService emailService;

    private static final String MANAGER_UUID = "aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa";
    private static final String STAFF_UUID = "bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb";

    private Staff manager;
    private Staff staff;
    private Role targetRole;
    private Competency javaComp;

    @BeforeEach
    void setUp() {
        // Cleanup
        roleCompetencyRepository.deleteAll();
        roleCompetencyItemRepository.deleteAll();
        roleCompetencyProposalRepository.deleteAll();
        proposalRepository.deleteAll();
        staffRepository.deleteAll();
        roleRepository.deleteAll();
        competencyRepository.deleteAll();
        orgChartRepository.deleteAll();
        authorityRepository.deleteAll();

        // Setup Auth
        createAuthority(AuthorityName.CAN_MANAGE_ROLE);
        createAuthority(AuthorityName.CAN_PROPOSE_ROLE_COMPETENCIES);

        // Setup Org & Role
        OrgChart dept = createOrgChart();
        targetRole = createRole("Senior Dev", dept);

        // Setup Users
        manager = createStaff(UUID.fromString(MANAGER_UUID), "Manager", "mgr@tbm.com", targetRole);
        staff = createStaff(UUID.fromString(STAFF_UUID), "Staff", "staff@tbm.com", targetRole);

        // Setup Competency
        javaComp = createCompetency("Java");

        // Mock Email
        doNothing().when(emailService).sendCompetencyAssignmentProposalReviewInvitationEmail(any(), any(), any(), any(), any(), any(), any(), any(), any());
    }

    // --- Scenario 1: Get Available Competencies ---
    @Test
    void getCompetencyForProposing_ShouldReturnList() throws Exception {
        mockMvc.perform(get("/api/competency-assignment-collaboration/creation-required-competency")
                        .with(user(MANAGER_UUID).authorities(() -> "CAN_MANAGE_ROLE")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name").value("Java"));
    }

    // --- Scenario 2: Initiate Proposal (Assign Competency) ---
    @Test
    void proposeCompetencyAssignment_ShouldCreateDraft() throws Exception {
        ProposeCompetencyAssignmentRequest req = new ProposeCompetencyAssignmentRequest();
        req.setOrgChartId(targetRole.getOrgChart().getId());
        req.setRoleId(targetRole.getId());
        req.setDescription("Assigning Java skills");

        // Exclude Initiator from lists to avoid conflict error
        req.setReviewerList(Collections.emptyList());
        req.setProposerList(List.of(staff.getId()));
        req.setJobScopeList(List.of("Coding"));

        // Assign Java (ID=1, Weight=80)
        ProposeCompetencyAssignmentMap compMap = new ProposeCompetencyAssignmentMap();
        compMap.setId(new CompetencyAssignmentCreationRequiredDataId(javaComp.getId(), null, false));
        compMap.setWeightage(80);
        req.setCompetencyList(List.of(compMap));

        mockMvc.perform(post("/api/competency-assignment-collaboration")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req))
                        .with(user(MANAGER_UUID).authorities(() -> "CAN_MANAGE_ROLE")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").exists());

        // Verify Proposal Created
        List<Proposal> proposals = proposalRepository.findAll();
        assertThat(proposals).hasSize(1);

        // Verify Draft Item Created
        List<RoleCompetencyItem> items = roleCompetencyItemRepository.findAll();
        assertThat(items).isNotEmpty();
        assertThat(items.get(0).getCompetency().getName()).isEqualTo("Java");
        assertThat(items.get(0).getWeightage()).isEqualTo(80);
    }

    // --- Scenario 3: Approve Proposal (Finalize Assignment) ---
    @Test
    void approveCompetencyAssignmentProposal_ShouldCreateMasterRecord() throws Exception {
        // 1. Setup existing proposal flow manually or via helper
        // Ideally, call the service methods directly to setup state, but here we simulate db state
        Proposal p = createProposal();
        RoleCompetencyProposal rcp = createRoleCompetencyProposal(p, manager, targetRole);

        // Create draft item linking Java to this proposal
        createRoleCompetencyItem(p, targetRole, manager, javaComp, 90);

        // 2. Approve Request
        RoleCompetencyProposalId targetId = rcp.getId();

        mockMvc.perform(put("/api/competency-assignment-collaboration/approve-competency-assignment-proposal")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(targetId))
                        .with(user(MANAGER_UUID).authorities(() -> "CAN_MANAGE_ROLE")))
                .andExpect(status().isOk());

        // 3. Verify Master Record
        List<RoleCompetency> masterRecords = roleCompetencyRepository.findAll();
        assertThat(masterRecords).hasSize(1);
        RoleCompetency rc = masterRecords.get(0);
        assertThat(rc.getCompetency().getName()).isEqualTo("Java");
        assertThat(rc.getWeightage()).isEqualTo(90); // Weight preserved from draft

        // Verify Draft Removed
        assertThat(roleCompetencyProposalRepository.findAll()).isEmpty();
    }

    // --- Scenario 4: Reject Proposal ---
    @Test
    void rejectCompetencyAssignmentProposal_ShouldDeleteDraft() throws Exception {
        // 1. Setup
        Proposal p = createProposal();
        RoleCompetencyProposal rcp = createRoleCompetencyProposal(p, manager, targetRole);

        createParticipant(p, manager, ProposalRole.REVIEWER);

        RoleCompetencyProposalId targetId = rcp.getId();

        // 2. Reject
        mockMvc.perform(put("/api/competency-assignment-collaboration/reject-competency-assignment-proposal")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(targetId))
                        .with(user(MANAGER_UUID).authorities(() -> "CAN_MANAGE_ROLE")))
                .andExpect(status().isOk());

        // 3. Verify
        assertThat(roleCompetencyProposalRepository.findAll()).isEmpty();
        Proposal updatedP = proposalRepository.findById(p.getId()).orElseThrow();
        assertThat(updatedP.getStatus()).isEqualTo(ProposalStatus.REJECTED);
    }

    // Helpers
    private Authority createAuthority(AuthorityName name) {
        Authority a = new Authority();
        a.setName(name);
        a.setLabelKey("l");
        a.setDescriptionKey("d");
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

    private Competency createCompetency(String name) {
        Competency c = new Competency();
        c.setName(name);
        c.setDeleted(false);
        c.setCreatedAt(OffsetDateTime.now());
        c.setUpdatedAt(OffsetDateTime.now());
        return competencyRepository.save(c);
    }

    private Proposal createProposal() {
        Proposal p = new Proposal();
        p.setType(ProposalType.ROLE_COMPETENCY);
        p.setStatus(ProposalStatus.ONGOING);
        p.setCreatedBy(UUID.fromString(MANAGER_UUID));
        p.setCreatedAt(OffsetDateTime.now());
        p.setUpdatedBy(UUID.fromString(MANAGER_UUID));
        p.setUpdatedAt(OffsetDateTime.now());
        return proposalRepository.save(p);
    }

    private RoleCompetencyProposal createRoleCompetencyProposal(Proposal p, Staff s, Role r) {
        RoleCompetencyProposal rcp = new RoleCompetencyProposal();
        rcp.setId(new RoleCompetencyProposalId(p.getId(), r.getId(), s.getId()));
        rcp.setProposal(p);
        rcp.setRole(r);
        rcp.setStaff(s);
        rcp.setDescription("Draft Proposal");
        rcp.setCreatedAt(OffsetDateTime.now());
        rcp.setUpdatedAt(OffsetDateTime.now());
        rcp.setCreatedBy(s.getId());
        return roleCompetencyProposalRepository.save(rcp);
    }

    private void createRoleCompetencyItem(Proposal p, Role r, Staff s, Competency c, int weight) {
        RoleCompetencyItem item = new RoleCompetencyItem();
        item.setId(new RoleCompetencyItemId(p.getId(), r.getId(), s.getId(), c.getId()));
        item.setProposal(p);
        item.setRole(r);
        item.setStaff(s);
        item.setCompetency(c);
        item.setWeightage(weight);
        item.setCreatedAt(OffsetDateTime.now());
        item.setUpdatedAt(OffsetDateTime.now());
        roleCompetencyItemRepository.save(item);
    }

    private void createParticipant(Proposal p, Staff s, ProposalRole role) {
        ProposalParticipant pp = new ProposalParticipant();
        pp.setId(new ProposalParticipantId(p.getId(), s.getId()));
        pp.setProposal(p);
        pp.setStaff(s);
        pp.setProposalRole(role);
        pp.setInitiator(true); // Manager is usually initiator
        pp.setCreatedAt(OffsetDateTime.now());
        pp.setUpdatedAt(OffsetDateTime.now());
        pp.setCreatedBy(UUID.fromString(MANAGER_UUID));
        proposalParticipantRepository.save(pp);
    }
}