package com.tbm.careerpathlearning.controller;

import com.tbm.careerpathlearning.dto.*;
import com.tbm.careerpathlearning.enums.AuthorityName;
import com.tbm.careerpathlearning.enums.ProposalRole;
import com.tbm.careerpathlearning.enums.ProposalStatus;
import com.tbm.careerpathlearning.enums.ProposalType;
import com.tbm.careerpathlearning.exception.BadRequestException;
import com.tbm.careerpathlearning.model.*;
import com.tbm.careerpathlearning.service.*;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.MessageSource;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
@RestController
@RequestMapping("/api/competency-assignment-collaboration")
@CrossOrigin
public class CompetencyAssignmentCollaborationController {

    private Logger logger = LoggerFactory.getLogger(CompetencyAssignmentCollaborationController.class);

    @Autowired
    private StaffService staffService;

    @Autowired
    private RoleService roleService;

    @Autowired
    private MessageSource messageSource;

    @Autowired
    private EmailService emailService;

    @Autowired
    private ProposalService proposalService;

    @Autowired
    private CompetencyService competencyService;

    @Autowired
    private CompetencyProposalService competencyProposalService;

    @Autowired
    private CompTagService compTagService;

    @Autowired
    private CompetencyCompTagProposalService competencyCompTagProposalService;

    @Autowired
    private ProposalParticipantService proposalParticipantService;

    @Autowired
    private RoleAuthorityService roleAuthorityService;

    @Autowired
    private AuthorityService authorityService;

    @Autowired
    private CompetencyCompTagService competencyCompTagService;

    @Autowired
    private JobScopeService jobScopeService;

    @Autowired
    private RoleCompetencyProposalService roleCompetencyProposalService;

    @Autowired
    private RoleJobScopeProposalService roleJobScopeProposalService;

    @Autowired
    private RoleCompetencyItemService roleCompetencyItemService;

    @Autowired
    private RoleCompetencyProposalItemService roleCompetencyProposalItemService;

    @Autowired
    private RoleJobScopeService roleJobScopeService;

    @Autowired
    private RoleCompetencyService roleCompetencyService;

    private static final Integer WEIGHTAGE_MAX = 100;

    private static final Integer WEIGHTAGE_MIN = 1;

    private static final String INVALID_REQUEST_BODY_ERR_MSG_CODE = "invalid.request.body.err.msg";

    private static final String PROPOSAL_PARTICIPANT_CONFLICT_ERR_TITLE_CODE = "proposal.participant.conflict.err.title";

    private static final String PROPOSAL_PARTICIPANT_CONFLICT_ERR_MSG_CODE = "proposal.participant.conflict.err.msg";

    private static final String PROPOSAL_APPROVAL_CONFLICT_ERR_TITLE_CODE = "proposal.approval.conflict.err.title";

    private static final String PROPOSAL_APPROVAL_CONFLICT_ERR_MSG_CODE = "proposal.approval.conflict.err.msg";

    private static final String PROPOSAL_APPROVE_CONFLICT_ROLE_ERR_TITLE_CODE = "proposal.approval.conflict.role.err.title";

    private static final String PROPOSAL_APPROVE_CONFLICT_ROLE_ERR_MSG_CODE = "proposal.approval.conflict.role.err.msg";

    private static final String ATTRIBUTE_UNIQUE_ERR_TITLE_CODE = "attribute.unique.err.title";

    private static final String ATTRIBUTE_UNIQUE_ERR_MSG_CODE = "attribute.unique.err.msg";

    private static final String COMPETENCY_PROPOSAL_ASSIGNMENT_CREATION_OK = "proposal.competency.assignment.creation.ok.msg";

    private static final String COMPETENCY_PROPOSAL_ASSIGNMENT_EDIT_OK = "proposal.competency.assignment.edit.ok.msg";

    private static final String COMPETENCY_PROPOSAL_ASSIGNMENT_REJECT_OK = "proposal.competency.assignment.reject.ok.msg";

    private static final String COMPETENCY_PROPOSAL_ASSIGNMENT_APPROVE_OK = "proposal.competency.assignment.approve.ok.msg";

    private static final String COMPETENCY_NAME = "Competency Name";

    private static final String COMPETENCY_ASSIGNMENT_OPERATION = "Competency Assignment";

    private static final String PROPOSE_COMPETENCY_OPERATION = "Propose Competency Assignment";

    private static final String COMPETENCY_PROPOSAL_EDIT_OPERATION = "Update Competency Assignment Collaboration";

    private static final String COMPETENCY_PROPOSAL_REJECT_OPERATION = "Reject Competency Assignment Collaboration";

    private static final String COMPETENCY_PROPOSAL_APPROVE_OPERATION = "Approve Competency Assignment Collaboration";


    @PreAuthorize("""
            hasAnyAuthority(
                T(com.tbm.careerpathlearning.enums.AuthorityName).CAN_MANAGE_ROLE.getAuthorityName(),
                T(com.tbm.careerpathlearning.enums.AuthorityName).CAN_PROPOSE_ROLE_COMPETENCIES.getAuthorityName()
            )
            """)
    @GetMapping("/creation-required-competency")
    @Transactional
    public ResponseEntity<?> getCompetencyForProposingCompetencyAssignment(
            @RequestParam(required = false) Long proposalId,
            @RequestParam(required = false) Long roleId,
            @RequestParam(required = false) UUID staffId,
            Authentication authentication) throws Exception {

        String userId = authentication.getName();
        UUID userUUID = UUID.fromString(userId);

        List<CompetencyDto> existingCompetencyDtos = competencyService.findAllByIsDeletedIsFalse();

        Set<CompetencyProposalDto> participatedCompetencyProposalDtos = new HashSet<>(competencyProposalService.getAllByStaffId(userUUID));
        Set<CompetencyProposalDto> availableCompetencyProposalDtos = new HashSet<>(participatedCompetencyProposalDtos);

        if (proposalId != null && roleId != null && staffId != null) {
            Set<CompetencyProposalDto> consumedCompetencyProposalDtos = roleCompetencyProposalItemService
                    .findAllByRoleCompetencyProposalId(proposalId, roleId, staffId)
                    .stream().map(RoleCompetencyProposalItemDto::getCompetencyProposal).collect(Collectors.toSet());
            availableCompetencyProposalDtos.addAll(consumedCompetencyProposalDtos);
        }

        List<CompetencyAssignmentCreationRequiredDataDto> permanentCompetency = existingCompetencyDtos.stream()
                .map(dto -> new CompetencyAssignmentCreationRequiredDataDto(
                        new CompetencyAssignmentCreationRequiredDataId(dto.getId(), null, false),
                        dto.getName(),
                        null
                )).toList();

        List<CompetencyAssignmentCreationRequiredDataDto> proposedCompetency = availableCompetencyProposalDtos.stream()
                .map(dto -> new CompetencyAssignmentCreationRequiredDataDto(
                        new CompetencyAssignmentCreationRequiredDataId(null, dto.getId(), true),
                        dto.getName(),
                        dto.getStaff().getEmail()
                )).toList();

        List<CompetencyAssignmentCreationRequiredDataDto> result = new ArrayList<>(permanentCompetency);
        result.addAll(proposedCompetency);

        return ResponseEntity.ok(result);
    }

    @PreAuthorize("""
            hasAnyAuthority(
                T(com.tbm.careerpathlearning.enums.AuthorityName).CAN_MANAGE_ROLE.getAuthorityName()
            )
            """)
    @PostMapping()
    @Transactional
    public ResponseEntity<?> proposeCompetencyAssignment(@RequestBody ProposeCompetencyAssignmentRequest req,
                                                         Authentication authentication, Locale locale) throws Exception {
        // -- error handling --
        if (req.getOrgChartId() == null || req.getRoleId() == null) {
            String errorMessage = messageSource.getMessage(INVALID_REQUEST_BODY_ERR_MSG_CODE, new String[]{PROPOSE_COMPETENCY_OPERATION}, Locale.getDefault());
            throw new BadRequestException(errorMessage);
        }

        String userId = authentication.getName();
        UUID userUUID = UUID.fromString(userId);

        Set<UUID> reviewerIds = new HashSet<>(req.getReviewerList());
        Set<UUID> proposerIds = new HashSet<>(req.getProposerList());
        Set<UUID> intersection = new HashSet<>(reviewerIds);
        intersection.retainAll(proposerIds);
        Set<UUID> allParticipantIds = new HashSet<>(proposerIds);
        allParticipantIds.addAll(reviewerIds);
        allParticipantIds.add(userUUID);

        // check reviewer and proposer redundancy
        if (!intersection.isEmpty() || reviewerIds.contains(userUUID) || proposerIds.contains(userUUID)) {
            String errorTitle = messageSource.getMessage(PROPOSAL_PARTICIPANT_CONFLICT_ERR_TITLE_CODE, null, Locale.getDefault());
            String errorMessage = messageSource.getMessage(PROPOSAL_PARTICIPANT_CONFLICT_ERR_MSG_CODE, null, Locale.getDefault());

            throw new BadRequestException(errorTitle, errorMessage);
        }

        // -- basic info --
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC).withOffsetSameInstant(ZoneOffset.ofHours(8));

        RoleDto selectedRoleDto = roleService.getAllById(req.getRoleId());

        List<StaffDto> allStaffDtos = staffService.findAllByIdIn(allParticipantIds);
        StaffDto userDto = allStaffDtos.stream().filter(staff -> staff.getId().equals(userUUID)).findAny().orElse(null);
        Map<UUID, StaffDto> reviewerMap = allStaffDtos.stream()
                .filter(dto -> req.getReviewerList().contains(dto.getId()))
                .collect(Collectors.toMap(
                        StaffDto::getId,
                        Function.identity()
                ));
        Map<UUID, StaffDto> proposerMap = allStaffDtos.stream()
                .filter(dto -> req.getProposerList().contains(dto.getId()))
                .collect(Collectors.toMap(
                        StaffDto::getId,
                        Function.identity()
                ));

        for (ProposeCompetencyAssignmentMap comp : req.getCompetencyList()) {
            if (comp.getWeightage() < WEIGHTAGE_MIN || comp.getWeightage() > WEIGHTAGE_MAX) {
                String errorMessage = messageSource.getMessage(INVALID_REQUEST_BODY_ERR_MSG_CODE, new String[]{PROPOSE_COMPETENCY_OPERATION}, Locale.getDefault());
                throw new BadRequestException(errorMessage);
            }
        }

        Map<Long, Integer> selectedCompetencyMap = req.getCompetencyList().stream()
                .filter(assignedCompetency -> !assignedCompetency.getId().isProposal())
                .collect(Collectors.toMap(
                        assignedCompetency -> assignedCompetency.getId().getCompetencyId(),
                        ProposeCompetencyAssignmentMap::getWeightage
                ));
        Set<Long> selectedCompetencyIds = selectedCompetencyMap.keySet();
        List<CompetencyDto> selectedCompetencyDtos = competencyService.findAllByIsDeletedIsFalseAndIdIn(selectedCompetencyIds);
        List<String> selectedCompetencyName = selectedCompetencyDtos.stream().map(CompetencyDto::getName).toList();

        Map<ProposalParticipantId, Integer> selectedCompetencyProposalMap = req.getCompetencyList().stream()
                .filter(assignedCompetency -> assignedCompetency.getId().isProposal())
                .collect(Collectors.toMap(
                        assignedCompetency -> assignedCompetency.getId().getCompetencyProposalId(),
                        ProposeCompetencyAssignmentMap::getWeightage
                ));
        Set<ProposalParticipantId> selectedCompetencyProposalIds = selectedCompetencyProposalMap.keySet();
        Set<Long> selectedCompetencyProposalProposalIds = selectedCompetencyProposalMap.keySet().stream()
                .map(ProposalParticipantId::getProposalId)
                .collect(Collectors.toSet());
        List<CompetencyProposalDto> selectedCompetencyProposalDtos = competencyProposalService.getAllByIdIn(selectedCompetencyProposalIds);
        List<String> selectedCompetencyProposalName = selectedCompetencyProposalDtos.stream().map(CompetencyProposalDto::getName).toList();

        List<String> allCompetencyName = new ArrayList<>(selectedCompetencyName);
        allCompetencyName.addAll(selectedCompetencyProposalName);

        Set<String> seen = new HashSet<>();
        Set<String> duplicates = allCompetencyName.stream()
                .filter(name -> !seen.add(name))
                .collect(Collectors.toSet());

        if (!duplicates.isEmpty()) { // same proposals are approved within the same collaboration
            String errorTitle = messageSource.getMessage(ATTRIBUTE_UNIQUE_ERR_TITLE_CODE, new String[]{COMPETENCY_NAME}, Locale.getDefault());
            String errorMessage = messageSource.getMessage(ATTRIBUTE_UNIQUE_ERR_MSG_CODE, new String[]{COMPETENCY_NAME, COMPETENCY_ASSIGNMENT_OPERATION}, Locale.getDefault());
            throw new BadRequestException(errorTitle, errorMessage);
        }

        // -- proposal --
        ProposalDto proposalToBeCreated = new ProposalDto(
                ProposalType.ROLE_COMPETENCY,
                ProposalStatus.ONGOING,
                userUUID,
                now,
                userUUID,
                now
        );

        ProposalDto proposalCreated = proposalService.createProposal(proposalToBeCreated);
        // -- proposal participant --
        ProposalParticipantDto proposalInitiator = new ProposalParticipantDto(
                new ProposalParticipantId(proposalCreated.getId(), userDto.getId()),
                proposalCreated,
                userDto,
                ProposalRole.REVIEWER,
                true,
                userUUID,
                now,
                userUUID,
                now
        );

        List<ProposalParticipantDto> proposalReviewer = reviewerMap.entrySet().stream()
                .map(set -> new ProposalParticipantDto(
                        new ProposalParticipantId(proposalCreated.getId(), set.getKey()),
                        proposalCreated,
                        set.getValue(),
                        ProposalRole.REVIEWER,
                        false,
                        userUUID,
                        now,
                        userUUID,
                        now
                ))
                .toList();

        List<ProposalParticipantDto> proposalProposer = proposerMap.entrySet().stream()
                .map(set -> new ProposalParticipantDto(
                        new ProposalParticipantId(proposalCreated.getId(), set.getKey()),
                        proposalCreated,
                        set.getValue(),
                        ProposalRole.PROPOSER,
                        false,
                        userUUID,
                        now,
                        userUUID,
                        now
                ))
                .toList();

        List<ProposalParticipantDto> proposalParticipantToBeAdded = new ArrayList<>();
        proposalParticipantToBeAdded.add(proposalInitiator);
        proposalParticipantToBeAdded.addAll(proposalProposer);
        proposalParticipantToBeAdded.addAll(proposalReviewer);

        List<ProposalParticipantDto> proposalParticipantCreated
                = proposalParticipantService.createProposalParticipants(proposalParticipantToBeAdded);


        // Grant access
        Set<RoleDto> participantRole = proposalParticipantCreated.stream()
                .map(dto -> dto.getStaff().getRole())
                .collect(Collectors.toSet());
        this.updateGrantedAccess(participantRole, userUUID, now);

        // -- role competency proposal --

        RoleCompetencyProposalDto originalCopy = new RoleCompetencyProposalDto(
                new RoleCompetencyProposalId(proposalCreated.getId(), selectedRoleDto.getId(), userDto.getId()),
                proposalCreated,
                selectedRoleDto,
                userDto,
                req.getDescription().trim(),
                userDto.getId(),
                now,
                userDto.getId(),
                now
        );

        List<RoleCompetencyProposalDto> proposerCopies = proposerMap.entrySet().stream().map(set ->
                        new RoleCompetencyProposalDto(
                                new RoleCompetencyProposalId(proposalCreated.getId(), selectedRoleDto.getId(), set.getKey()),
                                proposalCreated,
                                selectedRoleDto,
                                set.getValue(),
                                req.getDescription().trim(),
                                set.getKey(),
                                now,
                                set.getKey(),
                                now
                        ))
                .toList();

        List<RoleCompetencyProposalDto> roleCompetencyProposalDtoToBeAdded = new ArrayList<>();
        roleCompetencyProposalDtoToBeAdded.add(originalCopy);
        roleCompetencyProposalDtoToBeAdded.addAll(proposerCopies);

        List<RoleCompetencyProposalDto> roleCompetencyProposalCreated =
                roleCompetencyProposalService.createAll(roleCompetencyProposalDtoToBeAdded);


        // -- job scopes --
        List<JobScopeDto> selectedJobScopeDtos = req.getJobScopeList().stream().map(jobScope ->
                new JobScopeDto(
                        jobScope.trim(),
                        false,
                        userUUID,
                        now,
                        userUUID,
                        now
                )).toList();

        // get all related job scopes
        List<JobScopeDto> createdJobScopeDtos;
        if (!selectedJobScopeDtos.isEmpty()) {
            createdJobScopeDtos = jobScopeService.createAll(selectedJobScopeDtos);
        } else {
            createdJobScopeDtos = Collections.emptyList();
        }


        // assign job scopes
        List<RoleJobScopeProposalDto> roleJobScopeProposalDtoToBeAdded = roleCompetencyProposalCreated.stream()
                .flatMap(proposal -> createdJobScopeDtos.stream().map(jobScope -> new RoleJobScopeProposalDto(
                        new RoleJobScopeProposalId(proposal.getId().getProposalId(), proposal.getId().getRoleId(), proposal.getId().getStaffId(), jobScope.getId()),
                        proposal.getProposal(),
                        proposal.getRole(),
                        proposal.getStaff(),
                        jobScope,
                        userUUID,
                        now,
                        userUUID,
                        now
                ))).toList();

        if (!roleJobScopeProposalDtoToBeAdded.isEmpty()) {
            roleJobScopeProposalService.createAll(roleJobScopeProposalDtoToBeAdded);
        }

        // -- existing competency assignment --
        List<RoleCompetencyItemDto> roleCompetencyItemToBeAdded = roleCompetencyProposalCreated.stream()
                .flatMap(proposal -> selectedCompetencyDtos.stream().map(competency ->
                        new RoleCompetencyItemDto(
                                new RoleCompetencyItemId(proposalCreated.getId(), selectedRoleDto.getId(), proposal.getId().getStaffId(), competency.getId()),
                                proposalCreated,
                                selectedRoleDto,
                                proposal.getStaff(),
                                competency,
                                selectedCompetencyMap.get(competency.getId()),
                                userUUID,
                                now,
                                userUUID,
                                now
                        )
                )).toList();

        List<RoleCompetencyItemDto> roleCompetencyItemCreated;
        if (!roleCompetencyItemToBeAdded.isEmpty()) {
            roleCompetencyItemCreated = roleCompetencyItemService.createAll(roleCompetencyItemToBeAdded);
        } else {
            roleCompetencyItemCreated = Collections.emptyList();
        }

        Map<String, Integer> roleCompetencyItemCreatedMap = roleCompetencyItemCreated.stream()
                .collect(Collectors.toMap(
                        dto -> dto.getCompetency().getName(),
                        RoleCompetencyItemDto::getWeightage,
                        (first, second) -> first
                ));

        // -- competency proposal assignment --

        // add the participants into competency proposal if not existed
        List<ProposalParticipantDto> existingCompetencyProposalParticipant = proposalParticipantService.getAllByProposalIdIn(selectedCompetencyProposalProposalIds);
        List<ProposalParticipantDto> competencyProposalParticipantToBeAdded =
                selectedCompetencyProposalDtos.stream()
                        .flatMap(competencyProposalDto -> {
                            Set<ProposalParticipantId> existingParticipantIds = existingCompetencyProposalParticipant.stream()
                                    .map(ProposalParticipantDto::getId)
                                    .filter(id -> Objects.equals(id.getProposalId(), competencyProposalDto.getId().getProposalId()))
                                    .collect(Collectors.toSet());

                            // Reviewers
                            Set<ProposalParticipantId> reviewerToAdd = reviewerMap.keySet().stream()
                                    .map(reviewer -> new ProposalParticipantId(competencyProposalDto.getId().getProposalId(), reviewer))
                                    .collect(Collectors.toSet());
                            reviewerToAdd.removeAll(existingParticipantIds);

                            List<ProposalParticipantDto> reviewerParticipantDto = reviewerToAdd.stream()
                                    .map(reviewerId -> new ProposalParticipantDto(
                                            reviewerId,
                                            competencyProposalDto.getProposal(),
                                            reviewerMap.get(reviewerId.getStaffId()),
                                            ProposalRole.CONSUMER,
                                            false,
                                            userUUID,
                                            now,
                                            userUUID,
                                            now
                                    ))
                                    .toList();

                            // Proposers
                            Set<ProposalParticipantId> proposerToAdd = proposerMap.keySet().stream()
                                    .map(proposer -> new ProposalParticipantId(competencyProposalDto.getId().getProposalId(), proposer))
                                    .collect(Collectors.toSet());
                            proposerToAdd.removeAll(existingParticipantIds);

                            List<ProposalParticipantDto> proposerParticipantDto = proposerToAdd.stream()
                                    .map(proposerId -> new ProposalParticipantDto(
                                            proposerId,
                                            competencyProposalDto.getProposal(),
                                            proposerMap.get(proposerId.getStaffId()),
                                            ProposalRole.CONSUMER,
                                            false,
                                            userUUID,
                                            now,
                                            userUUID,
                                            now
                                    ))
                                    .toList();

                            // Combine both
                            List<ProposalParticipantDto> toAdd = new ArrayList<>(reviewerParticipantDto);
                            toAdd.addAll(proposerParticipantDto);

                            return toAdd.stream();
                        })
                        .toList();

        if (!competencyProposalParticipantToBeAdded.isEmpty()) {
            proposalParticipantService.createProposalParticipants(competencyProposalParticipantToBeAdded);
        }

        // add role competency proposal item
        List<RoleCompetencyProposalItemDto> roleCompetencyProposalItemToBeAdded = roleCompetencyProposalCreated.stream()
                .flatMap(proposal -> selectedCompetencyProposalDtos.stream().map(competencyProposal -> {
                            return new RoleCompetencyProposalItemDto(
                                    new RoleCompetencyProposalItemId(proposalCreated.getId(), selectedRoleDto.getId(), proposal.getId().getStaffId(), competencyProposal.getId()),
                                    proposalCreated,
                                    selectedRoleDto,
                                    proposal.getStaff(),
                                    competencyProposal,
                                    selectedCompetencyProposalMap.get(competencyProposal.getId()),
                                    userUUID,
                                    now,
                                    userUUID,
                                    now
                            );
                        }
                )).toList();

        List<RoleCompetencyProposalItemDto> roleCompetencyProposalItemCreated;
        if (!roleCompetencyProposalItemToBeAdded.isEmpty()) {
            roleCompetencyProposalItemCreated = roleCompetencyProposalItemService.createAll(roleCompetencyProposalItemToBeAdded);
        } else {
            roleCompetencyProposalItemCreated = Collections.emptyList();
        }

        Map<String, Integer> roleCompetencyProposalItemCreatedMap = roleCompetencyProposalItemCreated.stream()
                .collect(Collectors.toMap(
                        dto -> dto.getCompetencyProposal().getName(),
                        RoleCompetencyProposalItemDto::getWeightage,
                        (first, second) -> first
                ));

        // -- send email --

        // reviewer
        Map<String, Integer> competencyMap = new HashMap<>();
        competencyMap.putAll(roleCompetencyItemCreatedMap);
        competencyMap.putAll(roleCompetencyProposalItemCreatedMap);

        for (ProposalParticipantDto reviewer : proposalReviewer) {
            emailService.sendCompetencyAssignmentProposalReviewInvitationEmail(
                    reviewer.getStaff().getEmail(),
                    proposalCreated,
                    selectedRoleDto.getOrgChart().getName(),
                    selectedRoleDto.getName(),
                    req.getDescription().trim(),
                    req.getJobScopeList(),
                    competencyMap,
                    userUUID,
                    Locale.getDefault()
            );
        }

        for (ProposalParticipantDto proposer : proposalProposer) {
            emailService.sendCompetencyAssignmentProposalProposeInvitationEmail(
                    proposer.getStaff().getEmail(),
                    proposalCreated,
                    selectedRoleDto.getOrgChart().getName(),
                    selectedRoleDto.getName(),
                    req.getDescription().trim(),
                    req.getJobScopeList(),
                    competencyMap,
                    proposer.getId().getStaffId(),
                    Locale.getDefault()
            );
        }

        return ResponseEntity.ok(Map.of(
                "message", messageSource.getMessage(COMPETENCY_PROPOSAL_ASSIGNMENT_CREATION_OK, null, Locale.getDefault()
                )));
    }

    @PreAuthorize("""
            hasAnyAuthority(
                T(com.tbm.careerpathlearning.enums.AuthorityName).CAN_MANAGE_ROLE.getAuthorityName(),
                T(com.tbm.careerpathlearning.enums.AuthorityName).CAN_PROPOSE_ROLE_COMPETENCIES.getAuthorityName()
            )
            """)
    @GetMapping("/overview")
    @Transactional
    public ResponseEntity<?> competencyAssignmentProposalOverview(Authentication authentication) throws Exception {

        // -- basic info --
        String userId = authentication.getName();
        UUID userUUID = UUID.fromString(userId);

        Set<Long> ongoingProposalParticipated = proposalService.getAllByType(ProposalType.ROLE_COMPETENCY).stream()
                .filter(proposalDto -> Objects.equals(proposalDto.getStatus(), ProposalStatus.ONGOING))
                .map(ProposalDto::getId)
                .collect(Collectors.toSet());
        List<ProposalParticipantDto> allParticipants = proposalParticipantService.getAllByProposalIdIn(ongoingProposalParticipated);
        Map<UUID, StaffDto> participantMap = allParticipants.stream()
                .collect(Collectors.toMap(
                        dto -> dto.getStaff().getId(),
                        ProposalParticipantDto::getStaff,
                        (first, second) -> first
                ));
        Set<Long> proposalIdAsReviewer = allParticipants.stream()
                .filter(dto -> Objects.equals(dto.getId().getStaffId(), userUUID)
                        && Objects.equals(dto.getProposalRole(), ProposalRole.REVIEWER))
                .map(dto -> dto.getId().getProposalId())
                .collect(Collectors.toSet());
        Set<Long> proposalIdAsProposer = allParticipants.stream()
                .filter(dto -> Objects.equals(dto.getId().getStaffId(), userUUID)
                        && Objects.equals(dto.getProposalRole(), ProposalRole.PROPOSER))
                .map(dto -> dto.getId().getProposalId())
                .collect(Collectors.toSet());

        List<RoleCompetencyProposalDto> roleCompetencyProposalParticipated = roleCompetencyProposalService.getAllByProposalIdIn(ongoingProposalParticipated);

        List<RoleJobScopeProposalDto> roleJobScopeProposalParticipated = roleJobScopeProposalService.findAllByProposalIdIn(ongoingProposalParticipated);

        List<RoleCompetencyItemDto> roleCompetencyAssignmentParticipated = roleCompetencyItemService.findAllByProposalIdIn(ongoingProposalParticipated);

        List<RoleCompetencyProposalItemDto> roleCompetencyAssignmentProposalParticipated = roleCompetencyProposalItemService.findAllByProposalIdIn(ongoingProposalParticipated);

        // -- All Reviewing Role Competency Dto --

        // reviewing record
        List<RoleCompetencyProposalDto> reviewingRoleCompetencyProposal = roleCompetencyProposalParticipated.stream()
                .filter(dto -> proposalIdAsReviewer.contains(dto.getId().getProposalId()))
                .toList();

        List<RoleCompetencyProposalOverviewDto> reviewingRecord = reviewingRoleCompetencyProposal.stream()
                .map(dto -> {
                    List<JobScopeDto> assignedJobScopes = roleJobScopeProposalParticipated.stream()
                            .filter(jobScope -> Objects.equals(jobScope.getId().getProposalId(), dto.getId().getProposalId())
                                    && Objects.equals(jobScope.getId().getRoleId(), dto.getId().getRoleId())
                                    && Objects.equals(jobScope.getId().getStaffId(), dto.getId().getStaffId()))
                            .map(RoleJobScopeProposalDto::getJobScope)
                            .toList();

                    List<ProposeCompetencyAssignmentMap> assignedPermanentCompetencies = roleCompetencyAssignmentParticipated
                            .stream().filter(competency -> Objects.equals(competency.getId().getProposalId(), dto.getId().getProposalId())
                                    && Objects.equals(competency.getId().getRoleId(), dto.getId().getRoleId())
                                    && Objects.equals(competency.getId().getStaffId(), dto.getId().getStaffId()))
                            .map(competency -> new ProposeCompetencyAssignmentMap(
                                    new CompetencyAssignmentCreationRequiredDataId(competency.getId().getCompetencyId(), null, false),
                                    competency.getCompetency(),
                                    null,
                                    competency.getWeightage()
                            )).toList();

                    List<ProposeCompetencyAssignmentMap> assignedProposedCompetencies = roleCompetencyAssignmentProposalParticipated
                            .stream().filter(competency -> Objects.equals(competency.getId().getProposalId(), dto.getId().getProposalId())
                                    && Objects.equals(competency.getId().getRoleId(), dto.getId().getRoleId())
                                    && Objects.equals(competency.getId().getStaffId(), dto.getId().getStaffId()))
                            .map(competency -> new ProposeCompetencyAssignmentMap(
                                    new CompetencyAssignmentCreationRequiredDataId(null, competency.getId().getCompetencyProposalId(), true),
                                    null,
                                    competency.getCompetencyProposal(),
                                    competency.getWeightage()
                            )).toList();

                    List<ProposeCompetencyAssignmentMap> assignmentCompetencyMap = new ArrayList<>(assignedPermanentCompetencies);
                    assignmentCompetencyMap.addAll(assignedProposedCompetencies);

                    int totalWeightage = assignmentCompetencyMap.stream().mapToInt(competency -> competency.getWeightage()).sum();

                    return new RoleCompetencyProposalOverviewDto(
                            dto.getId(),
                            dto.getRole().getOrgChart().getId(),
                            dto.getRole().getOrgChart().getName(),
                            dto.getRole().getId(),
                            dto.getRole().getName(),
                            dto.getDescription(),
                            assignedJobScopes,
                            assignmentCompetencyMap,
                            dto.getStaff().getName(),
                            dto.getStaff().getEmail(),
                            true,
                            participantMap.get(dto.getUpdatedBy()),
                            totalWeightage
                    );
                }).toList();

        // proposing record
        List<RoleCompetencyProposalDto> proposingRoleCompetencyProposal = roleCompetencyProposalParticipated.stream()
                .filter(dto -> proposalIdAsProposer.contains(dto.getId().getProposalId())
                        && Objects.equals(dto.getId().getStaffId(), userUUID))
                .toList();

        List<RoleCompetencyProposalOverviewDto> proposingRecord = proposingRoleCompetencyProposal.stream()
                .map(dto -> {
                    List<JobScopeDto> assignedJobScopes = roleJobScopeProposalParticipated.stream()
                            .filter(jobScope -> Objects.equals(jobScope.getId().getProposalId(), dto.getId().getProposalId())
                                    && Objects.equals(jobScope.getId().getRoleId(), dto.getId().getRoleId())
                                    && Objects.equals(jobScope.getId().getStaffId(), dto.getId().getStaffId()))
                            .map(RoleJobScopeProposalDto::getJobScope)
                            .toList();

                    List<ProposeCompetencyAssignmentMap> assignedPermanentCompetencies = roleCompetencyAssignmentParticipated
                            .stream().filter(competency -> Objects.equals(competency.getId().getProposalId(), dto.getId().getProposalId())
                                    && Objects.equals(competency.getId().getRoleId(), dto.getId().getRoleId())
                                    && Objects.equals(competency.getId().getStaffId(), dto.getId().getStaffId()))
                            .map(competency -> new ProposeCompetencyAssignmentMap(
                                    new CompetencyAssignmentCreationRequiredDataId(competency.getId().getCompetencyId(), null, false),
                                    competency.getCompetency(),
                                    null,
                                    competency.getWeightage()
                            )).toList();

                    List<ProposeCompetencyAssignmentMap> assignedProposedCompetencies = roleCompetencyAssignmentProposalParticipated
                            .stream().filter(competency -> Objects.equals(competency.getId().getProposalId(), dto.getId().getProposalId())
                                    && Objects.equals(competency.getId().getRoleId(), dto.getId().getRoleId())
                                    && Objects.equals(competency.getId().getStaffId(), dto.getId().getStaffId()))
                            .map(competency -> new ProposeCompetencyAssignmentMap(
                                    new CompetencyAssignmentCreationRequiredDataId(null, competency.getId().getCompetencyProposalId(), true),
                                    null,
                                    competency.getCompetencyProposal(),
                                    competency.getWeightage()
                            )).toList();

                    List<ProposeCompetencyAssignmentMap> assignmentCompetencyMap = new ArrayList<>(assignedPermanentCompetencies);
                    assignmentCompetencyMap.addAll(assignedProposedCompetencies);

                    int totalWeightage = assignmentCompetencyMap.stream().mapToInt(competency -> competency.getWeightage()).sum();

                    return new RoleCompetencyProposalOverviewDto(
                            dto.getId(),
                            dto.getRole().getOrgChart().getId(),
                            dto.getRole().getOrgChart().getName(),
                            dto.getRole().getId(),
                            dto.getRole().getName(),
                            dto.getDescription(),
                            assignedJobScopes,
                            assignmentCompetencyMap,
                            dto.getStaff().getName(),
                            dto.getStaff().getEmail(),
                            false,
                            participantMap.get(dto.getUpdatedBy()),
                            totalWeightage
                    );
                }).toList();

        List<RoleCompetencyProposalOverviewDto> result = new ArrayList<>(reviewingRecord);
        result.addAll(proposingRecord);

        return ResponseEntity.ok(result);
    }

    @PreAuthorize("""
            hasAnyAuthority(
                T(com.tbm.careerpathlearning.enums.AuthorityName).CAN_MANAGE_ROLE.getAuthorityName(),
                T(com.tbm.careerpathlearning.enums.AuthorityName).CAN_PROPOSE_ROLE_COMPETENCIES.getAuthorityName()
            )
            """)
    @PutMapping("/edit-competency-assignment-proposal")
    @Transactional
    public ResponseEntity<?> updateCompetencyProposal(@RequestBody EditCompetencyAssignmentProposalRequestDto req, Authentication authentication) throws Exception {
        if (req == null || req.getId() == null || req.getCompetencyList().isEmpty()) {
            String errorMessage = messageSource.getMessage(INVALID_REQUEST_BODY_ERR_MSG_CODE, new String[]{COMPETENCY_PROPOSAL_EDIT_OPERATION}, Locale.getDefault());
            throw new BadRequestException(errorMessage);
        }

        // --  basic info --
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC).withOffsetSameInstant(ZoneOffset.ofHours(8));
        String userId = authentication.getName();
        UUID userUUID = UUID.fromString(userId);

        RoleCompetencyProposalDto selectedRecord = roleCompetencyProposalService.getById(req.getId());
        List<JobScopeDto> selectedJobScopes = req.getJobScopeList().stream().map(jobScope -> new JobScopeDto(
                jobScope,
                false,
                userUUID,
                now,
                userUUID,
                now
        )).toList();

        for (ProposeCompetencyAssignmentMap comp : req.getCompetencyList()) {
            if (comp.getWeightage() < WEIGHTAGE_MIN || comp.getWeightage() > WEIGHTAGE_MAX) {
                String errorMessage = messageSource.getMessage(INVALID_REQUEST_BODY_ERR_MSG_CODE, new String[]{PROPOSE_COMPETENCY_OPERATION}, Locale.getDefault());
                throw new BadRequestException(errorMessage);
            }
        }

        Map<Long, Integer> selectedPermanentCompetencyMap = req.getCompetencyList().stream()
                .filter(dto -> !dto.getId().isProposal())
                .collect(Collectors.toMap(
                        dto -> dto.getId().getCompetencyId(),
                        ProposeCompetencyAssignmentMap::getWeightage
                ));

        Map<ProposalParticipantId, Integer> selectedProposedCompetencyMap = req.getCompetencyList().stream()
                .filter(dto -> dto.getId().isProposal())
                .collect(Collectors.toMap(
                        dto -> dto.getId().getCompetencyProposalId(),
                        ProposeCompetencyAssignmentMap::getWeightage
                ));

        Map<UUID, ProposalParticipantDto> allParticipant = proposalParticipantService.getAllByProposalId(req.getId().getProposalId())
                .stream().collect(Collectors.toMap(
                        dto -> dto.getId().getStaffId(),
                        Function.identity()
                ));
        ProposalParticipantDto updatedBy = allParticipant.get(userUUID);

        // -- role details update--
        RoleCompetencyProposalDto updatedRecord;

        if (!selectedRecord.getDescription().trim().equalsIgnoreCase(req.getDescription().trim())) {
            selectedRecord.setDescription(req.getDescription().trim());
            selectedRecord.setUpdatedAt(now);
            selectedRecord.setUpdatedBy(userUUID);

            updatedRecord = roleCompetencyProposalService.update(selectedRecord.getId(), selectedRecord);
        } else {
            updatedRecord = selectedRecord;
        }

        // -- role job scope update --

        Map<Long, JobScopeDto> requiredJobScopeMap;
        if (!selectedJobScopes.isEmpty()) {
            requiredJobScopeMap = jobScopeService.createAll(selectedJobScopes).stream()
                    .collect(Collectors.toMap(
                            JobScopeDto::getId,
                            Function.identity()
                    ));
        } else {
            requiredJobScopeMap = Collections.emptyMap();
        }


        Set<Long> assignedJobScopeIDs = roleJobScopeProposalService.getByRoleCompetencyProposalId(
                req.getId().getProposalId(),
                req.getId().getRoleId(),
                req.getId().getStaffId()
        ).stream().map(dto -> dto.getId().getJobScopeId()).collect(Collectors.toSet());

        Set<Long> jobScopeToBeAdded = new HashSet<>(requiredJobScopeMap.keySet());
        jobScopeToBeAdded.removeAll(assignedJobScopeIDs);

        Set<Long> jobScopeToBeRemoved = new HashSet<>(assignedJobScopeIDs);
        jobScopeToBeRemoved.removeAll(requiredJobScopeMap.keySet());

        if (!jobScopeToBeAdded.isEmpty()) { // assign new job scope
            List<RoleJobScopeProposalDto> roleJobScopeToBeAdded = jobScopeToBeAdded.stream().map(id ->
                    new RoleJobScopeProposalDto(
                            new RoleJobScopeProposalId(updatedRecord.getId().getProposalId(),
                                    updatedRecord.getId().getRoleId(),
                                    updatedRecord.getId().getStaffId(),
                                    id),
                            updatedRecord.getProposal(),
                            updatedRecord.getRole(),
                            updatedRecord.getStaff(),
                            requiredJobScopeMap.get(id),
                            userUUID,
                            now,
                            userUUID,
                            now
                    )
            ).toList();

            if (!roleJobScopeToBeAdded.isEmpty()) {
                roleJobScopeProposalService.createAll(roleJobScopeToBeAdded);
            }
        }

        if (!jobScopeToBeRemoved.isEmpty()) { // unassign job Scope
            Set<RoleJobScopeProposalId> roleJobScopeProposalToRemove = jobScopeToBeRemoved.stream().map(id ->
                            new RoleJobScopeProposalId(updatedRecord.getId().getProposalId(),
                                    updatedRecord.getId().getRoleId(),
                                    updatedRecord.getId().getStaffId(),
                                    id))
                    .collect(Collectors.toSet());
            roleJobScopeProposalService.deleteAllByIdIn(roleJobScopeProposalToRemove);

            this.removeJobScopeIfNoLongerUsed(jobScopeToBeRemoved, userUUID);
        }

        // -- permanent competency assignment update --
        Map<Long, CompetencyDto> existingPermanentCompetencyMap = competencyService.findAllByIsDeletedIsFalse().stream()
                .collect(Collectors.toMap(
                        CompetencyDto::getId,
                        Function.identity()
                ));

        Map<RoleCompetencyItemId, RoleCompetencyItemDto> assignedRoleCompetencyMap = roleCompetencyItemService
                .findAllByProposalStaffId(req.getId().getProposalId(), req.getId().getStaffId()).stream()
                .collect(Collectors.toMap(
                        RoleCompetencyItemDto::getId,
                        Function.identity()
                ));

        Map<Long, CompetencyDto> assignedPermanentCompetencyMap = assignedRoleCompetencyMap.entrySet().stream()
                .collect(Collectors.toMap(
                        map -> map.getKey().getCompetencyId(),
                        map -> map.getValue().getCompetency()
                ));

        Set<Long> permanentCompetencyIdToAdd = new HashSet<>(selectedPermanentCompetencyMap.keySet());
        permanentCompetencyIdToAdd.removeAll(assignedPermanentCompetencyMap.keySet());

        Set<Long> permanentCompetencyIdToRemove = new HashSet<>(assignedPermanentCompetencyMap.keySet());
        permanentCompetencyIdToRemove.removeAll(selectedPermanentCompetencyMap.keySet());

        Set<Long> permanentCompetencyIdToCheck = new HashSet<>(assignedPermanentCompetencyMap.keySet());
        permanentCompetencyIdToCheck.retainAll(selectedPermanentCompetencyMap.keySet());

        if (!permanentCompetencyIdToAdd.isEmpty()) { // assign new competency
            List<RoleCompetencyItemDto> roleCompetencyItemDtoToAdd = permanentCompetencyIdToAdd.stream().map(id ->
                    new RoleCompetencyItemDto(
                            new RoleCompetencyItemId(updatedRecord.getId().getProposalId(),
                                    updatedRecord.getId().getRoleId(),
                                    updatedRecord.getId().getStaffId(),
                                    id),
                            updatedRecord.getProposal(),
                            updatedRecord.getRole(),
                            updatedRecord.getStaff(),
                            existingPermanentCompetencyMap.get(id),
                            selectedPermanentCompetencyMap.get(id),
                            userUUID,
                            now,
                            userUUID,
                            now
                    )
            ).toList();

            if (!roleCompetencyItemDtoToAdd.isEmpty()) {
                roleCompetencyItemService.createAll(roleCompetencyItemDtoToAdd);
            }
        }

        if (!permanentCompetencyIdToRemove.isEmpty()) { // remove competency assignment
            Set<RoleCompetencyItemId> roleCompetencyItemToRemove = permanentCompetencyIdToRemove.stream().map(id ->
                    new RoleCompetencyItemId(updatedRecord.getId().getProposalId(),
                            updatedRecord.getId().getRoleId(),
                            updatedRecord.getId().getStaffId(),
                            id)
            ).collect(Collectors.toSet());

            roleCompetencyItemService.deleteAllByIdIn(roleCompetencyItemToRemove);
        }

        if (!permanentCompetencyIdToCheck.isEmpty()) { // update the weightage
            Set<RoleCompetencyItemId> roleCompetencyToCheck =
                    permanentCompetencyIdToCheck.stream().map(id -> new RoleCompetencyItemId(updatedRecord.getId().getProposalId(),
                            updatedRecord.getId().getRoleId(),
                            updatedRecord.getId().getStaffId(),
                            id)
                    ).collect(Collectors.toSet());
            Map<RoleCompetencyItemId, RoleCompetencyItemDto> roleCompetencyItemToUpdate = roleCompetencyToCheck.stream().filter(
                    id -> !Objects.equals(
                            assignedRoleCompetencyMap.get(id).getWeightage(),
                            selectedPermanentCompetencyMap.get(id.getCompetencyId())) // weightage changed
            ).collect(Collectors.toMap(
                    Function.identity(),
                    id -> {
                        RoleCompetencyItemDto toUpdate = assignedRoleCompetencyMap.get(id);
                        toUpdate.setWeightage(selectedPermanentCompetencyMap.get(id.getCompetencyId()));
                        toUpdate.setUpdatedBy(userUUID);
                        toUpdate.setUpdatedAt(now);

                        return toUpdate;
                    }
            ));
            if (!roleCompetencyItemToUpdate.isEmpty()) {
                roleCompetencyItemService.updateAll(roleCompetencyItemToUpdate.keySet(), roleCompetencyItemToUpdate.values().stream().toList());
            }
        }

        // -- competency proposal assignment update --
        Map<ProposalParticipantId, CompetencyProposalDto> existingProposedCompetencyMap = competencyProposalService.getAll().stream()
                .collect(Collectors.toMap(
                        CompetencyProposalDto::getId,
                        Function.identity()
                ));
        Map<RoleCompetencyProposalItemId, RoleCompetencyProposalItemDto> assignedRoleCompetencyProposalMap =
                roleCompetencyProposalItemService.findAllByProposalStaffId(req.getId().getProposalId(), req.getId().getStaffId()
                        ).stream()
                        .collect(Collectors.toMap(
                                RoleCompetencyProposalItemDto::getId,
                                Function.identity()
                        ));
        Map<ProposalParticipantId, CompetencyProposalDto> assignedProposedCompetencyMap = assignedRoleCompetencyProposalMap.entrySet().stream()
                .collect(Collectors.toMap(
                        map -> map.getKey().getCompetencyProposalId(),
                        map -> map.getValue().getCompetencyProposal()
                ));


        Set<ProposalParticipantId> proposedCompetencyIdToAdd = new HashSet<>(selectedProposedCompetencyMap.keySet());
        proposedCompetencyIdToAdd.removeAll(assignedProposedCompetencyMap.keySet());

        Set<ProposalParticipantId> proposedCompetencyIdToRemove = new HashSet<>(assignedProposedCompetencyMap.keySet());
        proposedCompetencyIdToRemove.removeAll(selectedProposedCompetencyMap.keySet());

        Set<ProposalParticipantId> proposedCompetencyIdToCheck = new HashSet<>(assignedProposedCompetencyMap.keySet());
        proposedCompetencyIdToCheck.retainAll(selectedProposedCompetencyMap.keySet());

        if (!proposedCompetencyIdToAdd.isEmpty()) { // assign new competency proposal
            List<RoleCompetencyProposalItemDto> roleCompetencyProposalItemDtoToAdd = proposedCompetencyIdToAdd.stream().map(id ->
                    new RoleCompetencyProposalItemDto(
                            new RoleCompetencyProposalItemId(updatedRecord.getId().getProposalId(),
                                    updatedRecord.getId().getRoleId(),
                                    updatedRecord.getId().getStaffId(),
                                    id),
                            updatedRecord.getProposal(),
                            updatedRecord.getRole(),
                            updatedRecord.getStaff(),
                            existingProposedCompetencyMap.get(id),
                            selectedProposedCompetencyMap.get(id),
                            userUUID,
                            now,
                            userUUID,
                            now
                    )
            ).toList();

            if (!roleCompetencyProposalItemDtoToAdd.isEmpty()) {
                roleCompetencyProposalItemService.createAll(roleCompetencyProposalItemDtoToAdd);
            }
        }

        if (!proposedCompetencyIdToRemove.isEmpty()) { // remove competency proposal assignment
            Set<RoleCompetencyProposalItemId> roleCompetencyProposalItemToRemove = proposedCompetencyIdToRemove.stream().map(id ->
                    new RoleCompetencyProposalItemId(updatedRecord.getId().getProposalId(),
                            updatedRecord.getId().getRoleId(),
                            updatedRecord.getId().getStaffId(),
                            id)
            ).collect(Collectors.toSet());

            roleCompetencyProposalItemService.deleteAllByIdIn(roleCompetencyProposalItemToRemove);
        }

        if (!proposedCompetencyIdToCheck.isEmpty()) { // update the weightage
            Set<RoleCompetencyProposalItemId> roleCompetencyProposalToCheck =
                    proposedCompetencyIdToCheck.stream().map(id -> new RoleCompetencyProposalItemId(updatedRecord.getId().getProposalId(),
                            updatedRecord.getId().getRoleId(),
                            updatedRecord.getId().getStaffId(),
                            id)
                    ).collect(Collectors.toSet());
            Map<RoleCompetencyProposalItemId, RoleCompetencyProposalItemDto> roleCompetencyProposalItemToUpdate = roleCompetencyProposalToCheck.stream().filter(
                    id -> !Objects.equals(
                            assignedRoleCompetencyProposalMap.get(id).getWeightage(),
                            selectedProposedCompetencyMap.get(id.getCompetencyProposalId())) // weightage changed
            ).collect(Collectors.toMap(
                    Function.identity(),
                    id -> {
                        RoleCompetencyProposalItemDto toUpdate = assignedRoleCompetencyProposalMap.get(id);
                        toUpdate.setWeightage(selectedProposedCompetencyMap.get(id.getCompetencyProposalId()));
                        toUpdate.setUpdatedBy(userUUID);
                        toUpdate.setUpdatedAt(now);

                        return toUpdate;
                    }
            ));
            if (!roleCompetencyProposalItemToUpdate.isEmpty()) {
                roleCompetencyProposalItemService.updateAll(roleCompetencyProposalItemToUpdate.keySet(), roleCompetencyProposalItemToUpdate.values().stream().toList());
            }
        }

        // -- send email --
        Map<UUID, ProposalParticipantDto> recipientMap = new HashMap<>(allParticipant);
        recipientMap.remove(userUUID);

        List<RoleCompetencyItemDto> roleCompetencyItemAfter = roleCompetencyItemService.findAllByProposalStaffId(
                req.getId().getProposalId(),
                req.getId().getStaffId());
        List<String> permanentCompetencyNameAfter = roleCompetencyItemAfter.stream()
                .map(dto -> dto.getCompetency().getName()).toList();
        List<RoleCompetencyProposalItemDto> roleCompetencyProposalItemAfter = roleCompetencyProposalItemService.findAllByProposalStaffId(
                req.getId().getProposalId(),
                req.getId().getStaffId());
        List<String> permanentCompetencyProposalNameAfter = roleCompetencyProposalItemAfter.stream()
                .map(dto -> dto.getCompetencyProposal().getName()).toList();
        List<String> allCompetencyNameAfter = new ArrayList<>(permanentCompetencyNameAfter);
        allCompetencyNameAfter.addAll(permanentCompetencyProposalNameAfter);

        Set<String> seen = new HashSet<>();
        Set<String> duplicates = allCompetencyNameAfter.stream()
                .filter(name -> !seen.add(name))
                .collect(Collectors.toSet());

        if (!duplicates.isEmpty()) { // same proposals are approved within the same collaboration
            String errorTitle = messageSource.getMessage(ATTRIBUTE_UNIQUE_ERR_TITLE_CODE, new String[]{COMPETENCY_NAME}, Locale.getDefault());
            String errorMessage = messageSource.getMessage(ATTRIBUTE_UNIQUE_ERR_MSG_CODE, new String[]{COMPETENCY_NAME, COMPETENCY_ASSIGNMENT_OPERATION}, Locale.getDefault());
            throw new BadRequestException(errorTitle, errorMessage);
        }

        Map<String, Integer> roleCompetencyItemAfterMap = roleCompetencyItemAfter.stream().collect(Collectors.toMap(
                dto -> dto.getCompetency().getName(),
                RoleCompetencyItemDto::getWeightage
        ));
        Map<String, Integer> roleCompetencyProposalItemAfterMap = roleCompetencyProposalItemAfter.stream().collect(Collectors.toMap(
                dto -> dto.getCompetencyProposal().getName(),
                RoleCompetencyProposalItemDto::getWeightage
        ));
        Map<String, Integer> roleCompetencyAfterMap = new HashMap<>(roleCompetencyItemAfterMap);
        roleCompetencyAfterMap.putAll(roleCompetencyProposalItemAfterMap);

        recipientMap.forEach((staffId, dto) -> {
            emailService.sendCompetencyAssignmentProposalUpdateEmail(
                    dto.getStaff().getEmail(),
                    dto.getProposal(),
                    updatedRecord.getRole().getOrgChart().getName(),
                    updatedRecord.getRole().getName(),
                    updatedRecord.getDescription(),
                    req.getJobScopeList(),
                    roleCompetencyAfterMap,
                    updatedBy.getStaff().getEmail(),
                    selectedRecord.getId().getStaffId(),
                    Locale.getDefault()
            );
        });

        return ResponseEntity.ok(Map.of(
                "message", messageSource.getMessage(COMPETENCY_PROPOSAL_ASSIGNMENT_EDIT_OK, null, Locale.getDefault())
        ));
    }

    private void removeJobScopeIfNoLongerUsed(Set<Long> jobScopeIds, UUID userUUID) {
        Set<Long> jobScopeUsedByPermanentRole = roleJobScopeService.findAllByJobScopeIdIn(jobScopeIds)
                .stream().map(dto -> dto.getId().getJobScopeId()).collect(Collectors.toSet());

        // Ensure the dto had been removed first
        Set<Long> jobScopeUsedByProposedRole = roleJobScopeProposalService.findAllByJobScopeIdIn(jobScopeIds)
                .stream().map(dto -> dto.getId().getJobScopeId()).collect(Collectors.toSet());

        Set<Long> toRemove = new HashSet<>(jobScopeIds);
        toRemove.removeAll(jobScopeUsedByPermanentRole);
        toRemove.removeAll(jobScopeUsedByProposedRole);

        jobScopeService.deleteAllByIdIn(toRemove, userUUID);
    }

    private void removeCompTagIfNoLongerUsed(Set<Long> compTagIds, UUID userUUID) {
        Set<Long> compTagUsedByPermanentCompetency = competencyCompTagService.findAllByCompTagIdIn(compTagIds)
                .stream().map(dto -> dto.getId().getCompTagId()).collect(Collectors.toSet());

        // Ensure the dto had been removed first
        Set<Long> compTagUsedByProposedCompetency = competencyCompTagProposalService.getByCompTagIdIn(compTagIds)
                .stream().map(dto -> dto.getId().getCompTagId()).collect(Collectors.toSet());

        Set<Long> toRemove = new HashSet<>(compTagIds);
        toRemove.removeAll(compTagUsedByPermanentCompetency);
        toRemove.removeAll(compTagUsedByProposedCompetency);

        compTagService.deleteAllByIdIn(toRemove, userUUID);
    }

    @PreAuthorize("""
            hasAnyAuthority(
                T(com.tbm.careerpathlearning.enums.AuthorityName).CAN_MANAGE_ROLE.getAuthorityName()
            )
            """)
    @PutMapping("/reject-competency-assignment-proposal")
    @Transactional
    public ResponseEntity<?> rejectCompetencyAssignmentProposal(@RequestBody RoleCompetencyProposalId selectId, Authentication authentication) throws Exception {
        if (selectId == null) {
            String errorMessage = messageSource.getMessage(INVALID_REQUEST_BODY_ERR_MSG_CODE, new String[]{COMPETENCY_PROPOSAL_REJECT_OPERATION}, Locale.getDefault());
            throw new BadRequestException(errorMessage);
        }

        // -- basic info --
        String userId = authentication.getName();
        UUID userUUID = UUID.fromString(userId);

        List<ProposalParticipantDto> allParticipantBefore = proposalParticipantService
                .getAllByProposalId(selectId.getProposalId());
        ProposalParticipantDto updatedBy = allParticipantBefore.stream().filter(
                        dto -> Objects.equals(dto.getId().getStaffId(), userUUID))
                .findAny()
                .orElse(null);

        // -- remove permanent competency assignment --
        Map<String, Integer> roleCompetencyItemDeletedMap = roleCompetencyItemService
                .findAndDeleteAllByProposalStaffId(selectId.getProposalId(), selectId.getStaffId())
                .stream().collect(Collectors.toMap(
                        dto -> dto.getCompetency().getName(),
                        RoleCompetencyItemDto::getWeightage
                ));

        // -- remove proposed competency assignment --
        Map<String, Integer> roleCompetencyProposalItemDeletedMap = roleCompetencyProposalItemService
                .findAndDeleteAllByProposalStaffId(selectId.getProposalId(), selectId.getStaffId())
                .stream().collect(Collectors.toMap(
                        dto -> dto.getCompetencyProposal().getName(),
                        RoleCompetencyProposalItemDto::getWeightage
                ));

        // -- remove job scope assignment --
        List<JobScopeDto> deletedJobScopes = roleJobScopeProposalService
                .findAndDeleteAllByProposalStaffId(selectId.getProposalId(), selectId.getStaffId())
                .stream().map(RoleJobScopeProposalDto::getJobScope)
                .toList();
        Set<Long> deletedJobScopeIds = deletedJobScopes.stream().map(JobScopeDto::getId).collect(Collectors.toSet());

        this.removeJobScopeIfNoLongerUsed(deletedJobScopeIds, userUUID);

        // -- remove role competency assignment record --
        RoleCompetencyProposalDto deletedSelectedRecord = roleCompetencyProposalService.findAndDeleteById(selectId);

        // notify owner
        Map<String, Integer> deletedCompetencyMap = new HashMap<>(roleCompetencyItemDeletedMap);
        deletedCompetencyMap.putAll(roleCompetencyProposalItemDeletedMap);

        emailService.sendCompetencyAssignmentProposalRejectedEmail(
                deletedSelectedRecord.getStaff().getEmail(),
                deletedSelectedRecord.getRole().getOrgChart().getName(),
                deletedSelectedRecord.getRole().getName(),
                deletedSelectedRecord.getDescription(),
                deletedJobScopes.stream().map(JobScopeDto::getJobScope).toList(),
                deletedCompetencyMap,
                updatedBy == null ? "Email not found" : updatedBy.getStaff().getEmail(),
                Locale.getDefault()
        );

        // notify other reviewers
        Set<ProposalParticipantDto> reviewers = allParticipantBefore.stream().filter(
                        dto -> Objects.equals(dto.getProposalRole(), ProposalRole.REVIEWER))
                .collect(Collectors.toSet());
        if (updatedBy != null) {
            reviewers.remove(updatedBy);
        }

        for (ProposalParticipantDto reviewer : reviewers) {
            emailService.sendCompetencyAssignmentProposalRejectedEmail(
                    reviewer.getStaff().getEmail(),
                    deletedSelectedRecord.getRole().getOrgChart().getName(),
                    deletedSelectedRecord.getRole().getName(),
                    deletedSelectedRecord.getDescription(),
                    deletedJobScopes.stream().map(JobScopeDto::getJobScope).toList(),
                    deletedCompetencyMap,
                    updatedBy == null ? "Email not found" : updatedBy.getStaff().getEmail(),
                    Locale.getDefault()
            );
        }

        // -- remove proposal participant record --
        ProposalParticipantDto deletedParticipant = proposalParticipantService
                .findAndDeleteById(new ProposalParticipantId(selectId.getProposalId(), selectId.getStaffId()));

        // -- update proposal parent record (optional) --
        List<RoleCompetencyProposalDto> existingRoleCompetencyProposal = roleCompetencyProposalService
                .getAllByProposalId(selectId.getProposalId());

        Set<Long> deletedRoleIds = new HashSet<>();
        if (existingRoleCompetencyProposal.isEmpty()) { // no more proposal, reject directly (no more owner to notify)
            Set<Long> deletedParticipantRoleIds =
                    proposalParticipantService.findAndDeleteByProposalId(selectId.getProposalId())
                            .stream().map(dto -> dto.getStaff().getRole().getId())
                            .collect(Collectors.toSet());
            deletedRoleIds.addAll(deletedParticipantRoleIds);

            proposalService.updateProposalStatusById(selectId.getProposalId(), ProposalStatus.REJECTED, userUUID);
        }

        // -- update granted access --
        deletedRoleIds.add(deletedParticipant.getStaff().getRole().getId());
        this.removeGrantedAccessIfNotLongerUsed(deletedRoleIds);

        return ResponseEntity.ok(Map.of(
                "message", messageSource.getMessage(COMPETENCY_PROPOSAL_ASSIGNMENT_REJECT_OK, null, Locale.getDefault())
        ));
    }

    private void removeGrantedAccessIfNotLongerUsed(Set<Long> roleId) {
        AuthorityDto authorityDto = authorityService.findByName(AuthorityName.CAN_PROPOSE_ROLE_COMPETENCIES);

        Set<Long> existedParticipation = proposalParticipantService.getByStaffRoleIdIn(roleId)
                .stream().map(dto -> dto.getStaff().getRole().getId()).collect(Collectors.toSet());

        Set<Long> toRemove = new HashSet<>(roleId);
        toRemove.removeAll(existedParticipation);

        if (!toRemove.isEmpty()) {
            Set<RoleAuthorityId> roleAuthorityIdToRemove = toRemove.stream().map(id ->
                    new RoleAuthorityId(id, authorityDto.getId())).collect(Collectors.toSet());
            roleAuthorityService.deleteAllByIdIn(roleAuthorityIdToRemove);
        }
    }

    @PreAuthorize("""
            hasAnyAuthority(
                T(com.tbm.careerpathlearning.enums.AuthorityName).CAN_MANAGE_ROLE.getAuthorityName()
            )
            """)
    @PutMapping("/bulk-reject-competency-assignment-proposal")
    @Transactional
    public ResponseEntity<?> bulkRejectCompetencyProposal(@RequestBody Set<RoleCompetencyProposalId> selectedIds, Authentication authentication) throws Exception {
        if (selectedIds == null) {
            String errorMessage = messageSource.getMessage(INVALID_REQUEST_BODY_ERR_MSG_CODE, new String[]{COMPETENCY_PROPOSAL_REJECT_OPERATION}, Locale.getDefault());
            throw new BadRequestException(errorMessage);
        }

        // -- basic info --
        String userId = authentication.getName();
        UUID userUUID = UUID.fromString(userId);

        Set<Long> selectedProposalIds = selectedIds.stream().map(RoleCompetencyProposalId::getProposalId).collect(Collectors.toSet());
        Set<UUID> selectedStaffIds = selectedIds.stream().map(RoleCompetencyProposalId::getStaffId).collect(Collectors.toSet());

        List<ProposalParticipantDto> allParticipants = proposalParticipantService.getAllByProposalIdIn(selectedProposalIds);
        ProposalParticipantDto updatedBy = allParticipants.stream()
                .filter(dto -> Objects.equals(dto.getId().getStaffId(), userUUID))
                .findAny().orElse(null);

        // -- remove permanent competency assignment --
        List<RoleCompetencyItemDto> deletedRoleCompetency = roleCompetencyItemService
                .findAndDeleteAllByProposalStaffIdIn(selectedProposalIds, selectedStaffIds);

        // -- remove competency proposal assignment --
        List<RoleCompetencyProposalItemDto> deletedRoleCompetencyProposal = roleCompetencyProposalItemService
                .findAndDeleteAllByProposalStaffIdIn(selectedProposalIds, selectedStaffIds);

        // -- remove job scope proposal assignment --
        List<RoleJobScopeProposalDto> deletedJobScopeProposal = roleJobScopeProposalService
                .findAndDeleteAllByProposalStaffIdIn(selectedProposalIds, selectedStaffIds);
        Set<Long> deletedJobScopeIds = deletedJobScopeProposal.stream()
                .map(dto -> dto.getId().getJobScopeId()).collect(Collectors.toSet());

        this.removeJobScopeIfNoLongerUsed(deletedJobScopeIds, userUUID);

        // -- remove role competency proposal record --
        List<RoleCompetencyProposalDto> deletedRecords = roleCompetencyProposalService.findAndDeleteByIdIn(selectedIds);

        // notify owner & reviewers
        for (RoleCompetencyProposalDto deletedRecord : deletedRecords) {
            List<ProposalParticipantDto> recipients = allParticipants.stream().filter(dto ->
                    (Objects.equals(dto.getId().getProposalId(), deletedRecord.getId().getProposalId()) &&
                            Objects.equals(dto.getId().getStaffId(), deletedRecord.getId().getStaffId())) // owner
                            || (Objects.equals(dto.getId().getProposalId(), deletedRecord.getId().getProposalId()) &&
                            Objects.equals(dto.getProposalRole(), ProposalRole.REVIEWER)) && // reviewer
                            (updatedBy == null || !Objects.equals(dto.getId().getStaffId(), updatedBy.getId().getStaffId())) // not the one who reject
            ).toList();

            List<String> assignedJobScopesBefore = deletedJobScopeProposal.stream().filter(jobScope ->
                            Objects.equals(jobScope.getId().getProposalId(), deletedRecord.getId().getProposalId())
                                    && Objects.equals(jobScope.getId().getStaffId(), deletedRecord.getId().getStaffId()))
                    .map(jobScope -> jobScope.getJobScope().getJobScope())
                    .collect(Collectors.toList());

            Map<String, Integer> deletedRoleCompetencyMap = deletedRoleCompetency.stream().filter(comp ->
                            Objects.equals(comp.getId().getProposalId(), deletedRecord.getId().getProposalId())
                                    && Objects.equals(comp.getId().getStaffId(), deletedRecord.getId().getStaffId()))
                    .collect(Collectors.toMap(
                            comp -> comp.getCompetency().getName(),
                            RoleCompetencyItemDto::getWeightage
                    ));

            Map<String, Integer> deletedRoleCompetencyProposalMap = deletedRoleCompetencyProposal.stream().filter(comp ->
                            Objects.equals(comp.getId().getProposalId(), deletedRecord.getId().getProposalId())
                                    && Objects.equals(comp.getId().getStaffId(), deletedRecord.getId().getStaffId()))
                    .collect(Collectors.toMap(
                            comp -> comp.getCompetencyProposal().getName(),
                            RoleCompetencyProposalItemDto::getWeightage
                    ));

            Map<String, Integer> deletedCompetencyAssignmentMap = new HashMap<>(deletedRoleCompetencyMap);
            deletedCompetencyAssignmentMap.putAll(deletedRoleCompetencyProposalMap);

            for (ProposalParticipantDto recipient : recipients) {
                emailService.sendCompetencyAssignmentProposalRejectedEmail(
                        recipient.getStaff().getEmail(),
                        deletedRecord.getRole().getOrgChart().getName(),
                        deletedRecord.getRole().getName(),
                        deletedRecord.getDescription(),
                        assignedJobScopesBefore,
                        deletedCompetencyAssignmentMap,
                        updatedBy == null ? "Email not found" : updatedBy.getStaff().getEmail(),
                        Locale.getDefault()
                );
            }

        }

        // -- remove proposal participant record --
        Set<ProposalParticipantId> participantsToBeDeleted = selectedIds.stream()
                .map(id -> new ProposalParticipantId(
                        id.getProposalId(),
                        id.getStaffId()
                )).collect(Collectors.toSet());
        Set<Long> deletedSelectedRoleIds = proposalParticipantService
                .findAndDeleteByIdIn(participantsToBeDeleted)
                .stream().map(dto -> dto.getStaff().getRole().getId())
                .collect(Collectors.toSet());

        // -- update proposal parent record (optional) --
        Set<Long> deletedRoleIds = new HashSet<>();

        for (RoleCompetencyProposalDto record : deletedRecords) {
            List<RoleCompetencyProposalDto> existingRoleCompetencyProposal = roleCompetencyProposalService
                    .getAllByProposalId(record.getId().getProposalId());

            if (existingRoleCompetencyProposal.isEmpty()) { // no more proposal, reject directly (no more owner to notify)
                Set<Long> deletedParticipantRoleIds =
                        proposalParticipantService.findAndDeleteByProposalId(record.getId().getProposalId())
                                .stream().map(dto -> dto.getStaff().getRole().getId())
                                .collect(Collectors.toSet());
                deletedRoleIds.addAll(deletedParticipantRoleIds);

                proposalService.updateProposalStatusById(record.getId().getProposalId(), ProposalStatus.REJECTED, userUUID);
            }
        }

        deletedRoleIds.addAll(deletedSelectedRoleIds);

        // -- update granted access --
        this.removeGrantedAccessIfNotLongerUsed(deletedRoleIds);

        return ResponseEntity.ok(Map.of(
                "message", messageSource.getMessage(COMPETENCY_PROPOSAL_ASSIGNMENT_REJECT_OK, null, Locale.getDefault())
        ));
    }

    @PreAuthorize("""
            hasAnyAuthority(
                T(com.tbm.careerpathlearning.enums.AuthorityName).CAN_MANAGE_ROLE.getAuthorityName()
            )
            """)
    @PutMapping("/approve-competency-assignment-proposal")
    @Transactional
    public ResponseEntity<?> approveCompetencyAssignmentProposal(@RequestBody RoleCompetencyProposalId selectedId, Authentication authentication) throws Exception {
        if (selectedId == null) {
            String errorMessage = messageSource.getMessage(INVALID_REQUEST_BODY_ERR_MSG_CODE, new String[]{COMPETENCY_PROPOSAL_APPROVE_OPERATION}, Locale.getDefault());
            throw new BadRequestException(errorMessage);
        }

        // -- basic info --
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC).withOffsetSameInstant(ZoneOffset.ofHours(8));
        String userId = authentication.getName();
        UUID userUUID = UUID.fromString(userId);

        RoleCompetencyProposalDto selectedRecord = roleCompetencyProposalService.getById(selectedId);
        RoleDto selectedRole = selectedRecord.getRole();

        List<ProposalParticipantDto> allParticipants = proposalParticipantService.getAllByProposalId(selectedId.getProposalId());
        ProposalParticipantDto updatedBy = allParticipants.stream().filter(dto ->
                Objects.equals(dto.getId().getStaffId(), userUUID)
        ).findAny().orElse(null);

        // -- remove competency assignment for proposal --
        List<RoleCompetencyItemDto> deletedRoleCompetency = roleCompetencyItemService
                .findAndDeleteAllByProposalId(selectedId.getProposalId());
        List<RoleCompetencyItemDto> approvedRoleCompetency = deletedRoleCompetency.stream().filter(dto ->
                Objects.equals(dto.getId().getProposalId(), selectedId.getProposalId())
                        && Objects.equals(dto.getId().getStaffId(), selectedId.getStaffId())
        ).toList();

        // -- remove proposed competency assignment for proposal --
        List<RoleCompetencyProposalItemDto> deletedRoleCompetencyProposal = roleCompetencyProposalItemService
                .findAndDeleteAllByProposalId(selectedId.getProposalId());
        List<RoleCompetencyProposalItemDto> approvedRoleCompetencyProposal = deletedRoleCompetencyProposal.stream().filter(dto ->
                Objects.equals(dto.getId().getProposalId(), selectedId.getProposalId())
                        && Objects.equals(dto.getId().getStaffId(), selectedId.getStaffId())
        ).toList();

        // -- Update Role Dto --
        RoleDto updatedRoleDto;
        if ((selectedRole.getDescription() != null && selectedRecord.getDescription() != null && !selectedRole.getDescription().equalsIgnoreCase(selectedRecord.getDescription()))
                || (selectedRole.getDescription() == null && selectedRecord.getDescription() != null)
                || (selectedRecord.getDescription() == null && selectedRole.getDescription() != null)) {
            selectedRole.setDescription(selectedRecord.getDescription());
            selectedRole.setUpdatedBy(userUUID);
            selectedRole.setUpdatedAt(now);

            updatedRoleDto = roleService.update(selectedRole.getId(), selectedRole);
        } else {
            updatedRoleDto = selectedRole;
        }

        // -- assign competency to role --
        // remove existing role competency assignment
        roleCompetencyService.deleteAllByRoleId(selectedId.getRoleId());

        // get and approve competency proposal
        Map<String, CompetencyDto> approvedCompetencyDtoMap = this.approveCompetencyFromProposal(approvedRoleCompetencyProposal,
                        now,
                        updatedBy,
                        userUUID)
                .stream().collect(Collectors.toMap(CompetencyDto::getName, Function.identity()));

        List<RoleCompetencyDto> proposedRoleCompetencyDto = approvedRoleCompetencyProposal.stream().map(dto ->
                        new RoleCompetencyDto(
                                new RoleCompetencyId(dto.getId().getRoleId(),
                                        approvedCompetencyDtoMap.get(dto.getCompetencyProposal().getName()).getId()),
                                updatedRoleDto,
                                approvedCompetencyDtoMap.get(dto.getCompetencyProposal().getName()),
                                dto.getWeightage(),
                                dto.getId().getStaffId(),
                                now,
                                userUUID,
                                now))
                .toList();

        // get permanent competency assign --

        List<RoleCompetencyDto> permanentRoleCompetencyDto = approvedRoleCompetency.stream().map(dto ->
                        new RoleCompetencyDto(
                                new RoleCompetencyId(dto.getId().getRoleId(), dto.getId().getCompetencyId()),
                                updatedRoleDto,
                                dto.getCompetency(),
                                dto.getWeightage(),
                                dto.getId().getStaffId(),
                                now,
                                userUUID,
                                now))
                .toList();

        List<RoleCompetencyDto> roleCompetencyToAdd = new ArrayList<>(permanentRoleCompetencyDto);
        roleCompetencyToAdd.addAll(proposedRoleCompetencyDto);

        List<RoleCompetencyDto> roleCompetencyCreated;
        if (!roleCompetencyToAdd.isEmpty()) {
            roleCompetencyCreated = roleCompetencyService.createAll(roleCompetencyToAdd);
        } else {
            roleCompetencyCreated = Collections.emptyList();
        }

        // -- assign job Scope to role --
        List<RoleJobScopeProposalDto> deletedRoleJobScope = roleJobScopeProposalService.findAndDeleteAllByProposalId(selectedId.getProposalId());
        List<RoleJobScopeProposalDto> approvedRoleJobScope = deletedRoleJobScope.stream().filter(dto ->
                        Objects.equals(dto.getId().getProposalId(), selectedId.getProposalId())
                                && Objects.equals(dto.getId().getStaffId(), selectedId.getStaffId()))
                .toList();
        Set<Long> deletedProposedJobScopeIds = deletedRoleJobScope.stream().map(dto -> dto.getId().getJobScopeId())
                .collect(Collectors.toSet());

        List<RoleJobScopeDto> deletedExistingJobScope = roleJobScopeService.findAndDeleteAllByRoleId(selectedRole.getId());
        Set<Long> deletedPermanentJobScope = deletedExistingJobScope.stream().map(dto -> dto.getId().getJobScopeId())
                .collect(Collectors.toSet());

        Set<Long> deletedJobScopeIds = new HashSet<>(deletedProposedJobScopeIds);
        deletedJobScopeIds.addAll(deletedPermanentJobScope);

        List<RoleJobScopeDto> roleJobScopeToAdded = approvedRoleJobScope.stream().map(dto ->
                new RoleJobScopeDto(
                        new RoleJobScopeId(updatedRoleDto.getId(), dto.getId().getJobScopeId()),
                        updatedRoleDto,
                        dto.getJobScope(),
                        dto.getId().getStaffId(),
                        now,
                        userUUID,
                        now
                )
        ).toList();

        if (!roleJobScopeToAdded.isEmpty()) {
            roleJobScopeService.createAll(roleJobScopeToAdded);
        }

        // -- removing existing competency proposal
        List<RoleCompetencyProposalDto> deletedRecords = roleCompetencyProposalService
                .findAndDeleteByProposalId(selectedId.getProposalId());
        RoleCompetencyProposalDto approvedRecord = Objects.requireNonNull(deletedRecords.stream().filter(dto ->
                Objects.equals(dto.getId().getProposalId(), selectedRecord.getId().getProposalId())
                        && Objects.equals(dto.getId().getStaffId(), selectedRecord.getId().getStaffId())
        ).findAny().orElse(null));
        List<RoleCompetencyProposalDto> rejectedRecords = new ArrayList<>(deletedRecords);
        rejectedRecords.remove(approvedRecord);

        // notify owner and reviewers

        // notify owner and reviewer of approved proposal
        List<ProposalParticipantDto> recipients = allParticipants.stream().filter(dto ->
                        (Objects.equals(dto.getId().getStaffId(), selectedId.getStaffId())
                                && Objects.equals(dto.getId().getProposalId(), selectedId.getProposalId())) // Owner
                                || (Objects.equals(dto.getProposalRole(), ProposalRole.REVIEWER)) // reviewer
                                && (updatedBy == null || !Objects.equals(dto.getId().getStaffId(), updatedBy.getId().getStaffId()))) // not the one who approved
                .toList();

        for (ProposalParticipantDto recipient : recipients) {
            Map<String, Integer> approvedPermanentCompetencyMap = roleCompetencyCreated.stream()
                    .collect(Collectors.toMap(
                            dto -> dto.getCompetency().getName(),
                            RoleCompetencyDto::getWeightage
                    ));

            emailService.sendCompetencyAssignmentProposalApprovedEmail(
                    recipient.getStaff().getEmail(),
                    approvedRecord.getRole().getOrgChart().getName(),
                    approvedRecord.getRole().getName(),
                    approvedRecord.getDescription(),
                    approvedRoleJobScope.stream().map(jobScope -> jobScope.getJobScope().getJobScope()).toList(),
                    approvedPermanentCompetencyMap,
                    updatedBy == null ? "Email not found" : updatedBy.getStaff().getEmail(),
                    Locale.getDefault()
            );
        }

        // notify owner of rejected proposal
        for (RoleCompetencyProposalDto rejected : rejectedRecords) {
            List<String> rejectedJobScopes = deletedRoleJobScope.stream().filter(dto ->
                            Objects.equals(dto.getId().getProposalId(), rejected.getId().getProposalId())
                                    && Objects.equals(dto.getId().getStaffId(), rejected.getId().getStaffId())
                    ).map(dto -> dto.getJobScope().getJobScope())
                    .toList();

            Map<String, Integer> deletedPermanentCompetencyMap = deletedRoleCompetency.stream().filter(dto ->
                            Objects.equals(dto.getId().getProposalId(), rejected.getId().getProposalId())
                                    && Objects.equals(dto.getId().getStaffId(), rejected.getId().getStaffId()))
                    .collect(Collectors.toMap(
                            dto -> dto.getCompetency().getName(),
                            RoleCompetencyItemDto::getWeightage));

            Map<String, Integer> deletedProposedCompetencyMap = deletedRoleCompetencyProposal.stream().filter(dto ->
                            Objects.equals(dto.getId().getProposalId(), rejected.getId().getProposalId())
                                    && Objects.equals(dto.getId().getStaffId(), rejected.getId().getStaffId()))
                    .collect(Collectors.toMap(
                            dto -> dto.getCompetencyProposal().getName(),
                            RoleCompetencyProposalItemDto::getWeightage
                    ));

            Map<String, Integer> deletedCompetency = new HashMap<>(deletedPermanentCompetencyMap);
            deletedCompetency.putAll(deletedProposedCompetencyMap);

            emailService.sendCompetencyAssignmentProposalRejectedEmail(
                    rejected.getStaff().getEmail(),
                    rejected.getRole().getOrgChart().getName(),
                    rejected.getRole().getName(),
                    rejected.getDescription(),
                    rejectedJobScopes,
                    deletedCompetency,
                    updatedBy == null ? "Email not found" : updatedBy.getStaff().getEmail(),
                    Locale.getDefault()
            );
        }

        List<ProposalParticipantDto> deletedParticipants = proposalParticipantService.findAndDeleteByProposalId(selectedId.getProposalId());
        Set<Long> removedStaffRoleIds = deletedParticipants.stream()
                .map(dto -> dto.getStaff().getRole().getId())
                .collect(Collectors.toSet());

        this.removeGrantedAccessIfNotLongerUsed(removedStaffRoleIds);
        this.removeJobScopeIfNoLongerUsed(deletedJobScopeIds, userUUID);

        proposalService.updateProposalStatusById(selectedId.getProposalId(), ProposalStatus.APPROVED, userUUID);

        return ResponseEntity.ok(Map.of(
                "message", messageSource.getMessage(COMPETENCY_PROPOSAL_ASSIGNMENT_APPROVE_OK, null, Locale.getDefault())
        ));
    }

    private List<CompetencyDto> approveCompetencyFromProposal(List<RoleCompetencyProposalItemDto> approvedRoleCompetencyProposal,
                                                              OffsetDateTime now, ProposalParticipantDto updatedBy,
                                                              UUID userUUID) {
        // -- basic info --
        Set<ProposalParticipantId> approvedCompetencyProposalIds = approvedRoleCompetencyProposal.stream()
                .map(dto -> dto.getCompetencyProposal().getId()).collect(Collectors.toSet());
        Set<Long> approvedProposalIds = approvedCompetencyProposalIds.stream()
                .map(ProposalParticipantId::getProposalId).collect(Collectors.toSet());
        List<ProposalParticipantDto> allParticipants = proposalParticipantService.getAllByProposalIdIn(approvedProposalIds);

        // -- remove competency comp tag proposal --
        List<CompetencyCompTagProposalDto> deletedCompetencyCompTagProposal =
                competencyCompTagProposalService.findAndDeleteAllByProposalIdIn(approvedProposalIds);
        List<CompetencyCompTagProposalDto> approvedCompetencyCompTagProposal =
                deletedCompetencyCompTagProposal.stream().filter(
                        dto -> approvedCompetencyProposalIds.contains(
                                new ProposalParticipantId(dto.getId().getProposalId(), dto.getId().getStaffId())
                        )
                ).toList();
        Set<Long> deletedCompTagIds = deletedCompetencyCompTagProposal.stream()
                .map(dto -> dto.getCompTag().getId()).collect(Collectors.toSet());

        // -- remove competency proposal --
        List<CompetencyProposalDto> deletedCompetencyProposal =
                competencyProposalService.findAndDeleteAllByProposalIdIn(approvedProposalIds);
        List<CompetencyProposalDto> approvedCompetencyProposal = deletedCompetencyProposal.stream()
                .filter(dto -> approvedCompetencyProposalIds.contains(dto.getId()))
                .toList();
        List<CompetencyProposalDto> rejectedCompetencyProposal = new ArrayList<>(deletedCompetencyProposal);
        rejectedCompetencyProposal.removeAll(approvedCompetencyProposal);

        // -- notify owner and reviewer --
        // notify owner and reviewer of approved proposal
        for (CompetencyProposalDto approved : approvedCompetencyProposal) {
            List<String> approvedCompTag = deletedCompetencyCompTagProposal.stream()
                    .filter(compTag ->
                            Objects.equals(compTag.getId().getProposalId(), approved.getId().getProposalId())
                                    && Objects.equals(compTag.getId().getStaffId(), approved.getId().getStaffId()))
                    .map(compTag -> compTag.getCompTag().getTag())
                    .toList();

            List<String> recipientEmail = allParticipants.stream().filter(participant ->
                            (Objects.equals(participant.getId().getProposalId(), approved.getId().getProposalId())
                                    && Objects.equals(participant.getId().getStaffId(), approved.getId().getStaffId()) // owner
                                    || (Objects.equals(participant.getProposalRole(), ProposalRole.REVIEWER)) // reviewer
                                    && (updatedBy == null || !Objects.equals(updatedBy.getId(), participant.getId()))) // not the one who approved
                    ).map(participant -> participant.getStaff().getEmail())
                    .toList();

            for (String recipient : recipientEmail) {
                emailService.sendCompetencyProposalApprovedEmail(
                        recipient,
                        approved.getName(),
                        approved.getDescription(),
                        approvedCompTag,
                        updatedBy == null ? "Email not Found" : updatedBy.getStaff().getEmail(),
                        Locale.getDefault()
                );
            }

        }

        // notify owner of rejected proposal
        for (CompetencyProposalDto rejected : rejectedCompetencyProposal) {
            List<String> rejectedCompTag = deletedCompetencyCompTagProposal.stream()
                    .filter(compTag ->
                            Objects.equals(compTag.getId().getProposalId(), rejected.getId().getProposalId())
                                    && Objects.equals(compTag.getId().getStaffId(), rejected.getId().getStaffId()))
                    .map(compTag -> compTag.getCompTag().getTag())
                    .toList();

            emailService.sendCompetencyProposalRejectedEmail(
                    rejected.getStaff().getEmail(),
                    rejected.getName(),
                    rejected.getDescription(),
                    rejectedCompTag,
                    updatedBy == null ? "Email not Found" : updatedBy.getStaff().getEmail(),
                    Locale.getDefault()
            );
        }

        // -- remove proposal participant --
        proposalParticipantService.findAndDeleteByProposalIdIn(approvedProposalIds);

        // -- update parent proposal record --
        proposalService.updateProposalStatusByIdIn(approvedProposalIds, ProposalStatus.APPROVED, userUUID);

        // -- convert into permanent records --
        List<CompetencyDto> competencyToBeAdded = approvedCompetencyProposal.stream()
                .map(dto -> new CompetencyDto(
                        dto.getName(),
                        dto.getDescription(),
                        false,
                        dto.getId().getStaffId(),
                        now,
                        userUUID,
                        now
                )).collect(Collectors.toList());

        List<CompetencyDto> competencyDtoCreated;
        if (!competencyToBeAdded.isEmpty()) {
            competencyDtoCreated = competencyService.createAll(competencyToBeAdded);
        } else {
            competencyDtoCreated = Collections.emptyList();
        }

        Map<ProposalParticipantId, CompetencyDto> competencyMap = competencyDtoCreated.stream().collect(Collectors.toMap(
                dto -> Objects.requireNonNull(approvedRoleCompetencyProposal.stream().filter(competency ->
                                Objects.equals(dto.getName(), competency.getCompetencyProposal().getName()))
                        .findAny().orElse(null)).getCompetencyProposal().getId(),
                Function.identity()
        ));

        // -- Assign comp tag --
        List<CompetencyCompTagDto> competencyCompTagToBeAdded = competencyDtoCreated.stream().flatMap(dto -> {
                    ProposalParticipantId competencyProposalIdBefore = competencyMap.entrySet().stream().filter(
                            map -> map.getValue().getName().equalsIgnoreCase(dto.getName())
                    ).findAny().map(Map.Entry::getKey).orElse(null);

                    List<CompTagDto> assignedCompTag;
                    List<CompetencyCompTagDto> toAdd;
                    if (competencyProposalIdBefore != null) {
                        assignedCompTag = approvedCompetencyCompTagProposal.stream().filter(
                                        compTag -> Objects.equals(compTag.getId().getProposalId(), competencyProposalIdBefore.getProposalId())
                                                && Objects.equals(compTag.getId().getStaffId(), competencyProposalIdBefore.getStaffId())
                                ).map(CompetencyCompTagProposalDto::getCompTag)
                                .toList();

                        toAdd = assignedCompTag.stream().map(compTag ->
                                new CompetencyCompTagDto(
                                        new CompetencyCompTagId(dto.getId(), compTag.getId()),
                                        dto,
                                        compTag,
                                        dto.getCreatedBy(),
                                        now,
                                        userUUID,
                                        now
                                )
                        ).toList();
                        return toAdd.stream();
                    } else {
                        return null;
                    }
                }
        ).toList();

        if (!competencyCompTagToBeAdded.isEmpty()) {
            competencyCompTagService.createAll(competencyCompTagToBeAdded);
        }

        this.removeCompTagIfNoLongerUsed(deletedCompTagIds, userUUID);

        return competencyDtoCreated;
    }

    @PreAuthorize("""
            hasAnyAuthority(
                T(com.tbm.careerpathlearning.enums.AuthorityName).CAN_MANAGE_ROLE.getAuthorityName()
            )
            """)
    @PutMapping("/bulk-approve-competency-assignment-proposal")
    @Transactional
    public ResponseEntity<?> bulkApproveCompetencyProposal(@RequestBody Set<RoleCompetencyProposalId> selectedIds, Authentication authentication) throws Exception {
        if (selectedIds == null) {
            String errorMessage = messageSource.getMessage(INVALID_REQUEST_BODY_ERR_MSG_CODE, new String[]{COMPETENCY_PROPOSAL_APPROVE_OPERATION}, Locale.getDefault());
            throw new BadRequestException(errorMessage);
        }

        // -- Basic info --
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC).withOffsetSameInstant(ZoneOffset.ofHours(8));
        String userId = authentication.getName();
        UUID userUUID = UUID.fromString(userId);

        Set<Long> selectedProposalIds = selectedIds.stream().map(RoleCompetencyProposalId::getProposalId).collect(Collectors.toSet());
        Set<Long> selectedRoleIds = selectedIds.stream().map(RoleCompetencyProposalId::getRoleId).collect(Collectors.toSet());

        List<Long> selectedProposalIdList = selectedIds.stream().map(RoleCompetencyProposalId::getProposalId).toList();
        Set<Long> proposalIdSeen = new HashSet<>();
        Set<Long> proposalIdsDuplicates = selectedProposalIdList.stream()
                .filter(id -> !proposalIdSeen.add(id))
                .collect(Collectors.toSet());
        if (!proposalIdsDuplicates.isEmpty()) { // multiple proposals are approved within the same collaboration
            String errorTitle = messageSource.getMessage(PROPOSAL_APPROVAL_CONFLICT_ERR_TITLE_CODE, null, Locale.getDefault());
            String errorMessage = messageSource.getMessage(PROPOSAL_APPROVAL_CONFLICT_ERR_MSG_CODE, new String[]{proposalIdsDuplicates.toString()}, Locale.getDefault());
            throw new BadRequestException(errorTitle, errorMessage);
        }

        List<Long> selectedRoleIdList = selectedIds.stream().map(RoleCompetencyProposalId::getRoleId).toList();
        Set<Long> roleIdSeen = new HashSet<>();
        Set<Long> roleIdDuplicates = selectedRoleIdList.stream()
                .filter(id -> !roleIdSeen.add(id))
                .collect(Collectors.toSet());

        if (!roleIdDuplicates.isEmpty()) { // same role are approved within the same collaboration
            String errorTitle = messageSource.getMessage(PROPOSAL_APPROVE_CONFLICT_ROLE_ERR_TITLE_CODE, null, Locale.getDefault());
            String errorMessage = messageSource.getMessage(PROPOSAL_APPROVE_CONFLICT_ROLE_ERR_MSG_CODE, null, Locale.getDefault());
            throw new BadRequestException(errorTitle, errorMessage);
        }

        List<ProposalParticipantDto> allParticipants = proposalParticipantService.getAllByProposalIdIn(selectedProposalIds);
        ProposalParticipantDto updatedBy = Objects.requireNonNull(
                allParticipants.stream().filter(dto -> Objects.equals(dto.getId().getStaffId(), userUUID))
                        .findFirst().orElse(null)
        );

        // -- remove permanent role competency assignment --
        List<RoleCompetencyItemDto> deletedPermanentCompetency = roleCompetencyItemService.findAllByProposalIdIn(selectedProposalIds);
        List<RoleCompetencyItemDto> approvedPermanentCompetency = deletedPermanentCompetency.stream().filter(dto ->
                        selectedIds.contains(new RoleCompetencyProposalId(
                                dto.getId().getProposalId(),
                                dto.getId().getRoleId(),
                                dto.getId().getStaffId()
                        )))
                .toList();
        List<RoleCompetencyItemDto> rejectedPermanentCompetency = new ArrayList<>(deletedPermanentCompetency);
        rejectedPermanentCompetency.removeAll(approvedPermanentCompetency);

        // -- remove proposed role competency assignment --
        List<RoleCompetencyProposalItemDto> deletedProposedCompetency = roleCompetencyProposalItemService.findAllByProposalIdIn(selectedProposalIds);
        List<RoleCompetencyProposalItemDto> approvedProposedCompetency = deletedProposedCompetency.stream().filter(dto ->
                        selectedIds.contains(new RoleCompetencyProposalId(
                                dto.getId().getProposalId(),
                                dto.getId().getRoleId(),
                                dto.getId().getStaffId()
                        )))
                .toList();
        List<RoleCompetencyProposalItemDto> rejectedProposedCompetency = new ArrayList<>(deletedProposedCompetency);
        rejectedProposedCompetency.removeAll(approvedProposedCompetency);

        // -- remove role job scope proposal--
        List<RoleJobScopeProposalDto> deletedRoleJobScopeProposal = roleJobScopeProposalService.findAllByProposalIdIn(selectedProposalIds);
        List<RoleJobScopeProposalDto> approvedRoleJobScopeProposal = deletedRoleJobScopeProposal.stream().filter(dto ->
                        selectedIds.contains(new RoleCompetencyProposalId(
                                dto.getId().getProposalId(),
                                dto.getId().getRoleId(),
                                dto.getId().getStaffId()
                        )))
                .toList();
        List<RoleJobScopeProposalDto> rejectedRoleJobScopeProposal = new ArrayList<>(deletedRoleJobScopeProposal);
        rejectedRoleJobScopeProposal.removeAll(approvedRoleJobScopeProposal);
        Set<Long> rejectedJobScopeIds = rejectedRoleJobScopeProposal.stream()
                .map(dto -> dto.getJobScope().getId()).collect(Collectors.toSet());

        // -- update role proposal --
        List<RoleDto> selectedRoleDto = roleService.getAllByIsDeletedIsFalseAndIdIn(selectedRoleIds);
        List<RoleCompetencyProposalDto> selectedRecords = roleCompetencyProposalService.getAllByProposalIdIn(selectedProposalIds);
        List<RoleCompetencyProposalDto> approvedRecords = selectedRecords.stream().filter(dto ->
                        selectedIds.contains(dto.getId()))
                .toList();
        List<RoleCompetencyProposalDto> rejectedRecords = new ArrayList<>(selectedRecords);
        rejectedRecords.removeAll(approvedRecords);
        Map<Long, RoleCompetencyProposalDto> selectedRecordsMapByRoleId = approvedRecords.stream().collect(Collectors.toMap(
                dto -> dto.getId().getRoleId(),
                Function.identity()
        ));

        List<RoleDto> roleDtosToBeUpdated = selectedRoleDto.stream()
                .peek(dto -> dto.setDescription(selectedRecordsMapByRoleId.get(dto.getId()).getDescription().trim()))
                .toList();

        List<RoleDto> updatedRoleDtos = roleService.updateAll(selectedRoleIds, roleDtosToBeUpdated);
        Map<Long, RoleDto> updatedRoleMap = updatedRoleDtos.stream()
                .collect(Collectors.toMap(RoleDto::getId, Function.identity()));

        // -- update role job Scope --
        // clear existing job scope assignment
        List<RoleJobScopeDto> deletedRoleJobScope = roleJobScopeService.findAndDeleteAllByRoleIdIn(selectedRoleIds);
        Set<Long> deletedRoleJobScopeIds = deletedRoleJobScope.stream()
                .map(dto -> dto.getJobScope().getId()).collect(Collectors.toSet());

        // assign approve job scope to update role
        List<RoleJobScopeDto> roleJobScopeToBeCreated = approvedRoleJobScopeProposal.stream().map(dto ->
                        new RoleJobScopeDto(
                                new RoleJobScopeId(dto.getId().getRoleId(), dto.getId().getJobScopeId()),
                                updatedRoleMap.get(dto.getId().getRoleId()),
                                dto.getJobScope(),
                                dto.getId().getStaffId(),
                                now,
                                userUUID,
                                now
                        ))
                .toList();

        List<RoleJobScopeDto> roleJobScopeCreated;
        if (!roleJobScopeToBeCreated.isEmpty()) {
            roleJobScopeCreated = roleJobScopeService.createAll(roleJobScopeToBeCreated);
        } else {
            roleJobScopeCreated = Collections.emptyList();
        }

        Set<Long> deletedJobScopes = new HashSet<>(deletedRoleJobScopeIds);
        deletedJobScopes.addAll(rejectedJobScopeIds);
        this.removeJobScopeIfNoLongerUsed(deletedJobScopes, userUUID);

        // -- update role competency assignment --
        // remove existing competency assignment
        roleCompetencyService.deleteAllByRoleIdIn(selectedRoleIds);

        // assign from permanent competency
        List<RoleCompetencyDto> permanentRoleCompetencyToBeAdded = approvedPermanentCompetency.stream().map(dto ->
                        new RoleCompetencyDto(
                                new RoleCompetencyId(dto.getId().getRoleId(), dto.getId().getCompetencyId()),
                                updatedRoleMap.get(dto.getId().getRoleId()),
                                dto.getCompetency(),
                                dto.getWeightage(),
                                dto.getId().getStaffId(),
                                now,
                                userUUID,
                                now))
                .toList();

        // assign from proposed competency
        Map<String, CompetencyDto> proposedCompetencyMap = this.approveCompetencyFromProposal(
                        approvedProposedCompetency, now, updatedBy, userUUID)
                .stream().collect(Collectors.toMap(
                        CompetencyDto::getName,
                        Function.identity()));

        List<RoleCompetencyDto> proposedRoleCompetencyToBeAdded = approvedProposedCompetency.stream().map(dto ->
                        new RoleCompetencyDto(
                                new RoleCompetencyId(dto.getId().getRoleId(),
                                        proposedCompetencyMap.get(dto.getCompetencyProposal().getName()).getId()),
                                updatedRoleMap.get(dto.getId().getRoleId()),
                                proposedCompetencyMap.get(dto.getCompetencyProposal().getName()),
                                dto.getWeightage(),
                                dto.getId().getStaffId(),
                                now,
                                userUUID,
                                now))
                .toList();

        List<RoleCompetencyDto> roleCompetencyDtoToBeAdded = new ArrayList<>(permanentRoleCompetencyToBeAdded);
        roleCompetencyDtoToBeAdded.addAll(proposedRoleCompetencyToBeAdded);

        List<RoleCompetencyDto> roleCompetencyDtoAdded;
        if (!roleCompetencyDtoToBeAdded.isEmpty()) {
            roleCompetencyDtoAdded = roleCompetencyService.createAll(roleCompetencyDtoToBeAdded);
        } else {
            roleCompetencyDtoAdded = Collections.emptyList();
        }

        // -- delete role competency records --
        roleCompetencyProposalService.findAndDeleteByProposalIdIn(selectedProposalIds);

        for (RoleCompetencyProposalDto approved : approvedRecords) { // notify owner and reviewers
            List<String> recipientEmails = allParticipants.stream().filter(dto ->
                            (Objects.equals(dto.getId().getProposalId(), approved.getId().getProposalId())) // same proposal
                                    && (
                                    (
                                            Objects.equals(dto.getId().getStaffId(), approved.getId().getStaffId())) // owner
                                            || (Objects.equals(dto.getProposalRole(), ProposalRole.REVIEWER) // reviewer
                                            && (!Objects.equals(dto.getId().getStaffId(), updatedBy.getId().getStaffId())) // not the one who approved
                                    )
                            ))
                    .map(dto -> dto.getStaff().getEmail())
                    .toList();

            List<String> approvedJobScopes = approvedRoleJobScopeProposal.stream().filter(dto ->
                            Objects.equals(dto.getId().getProposalId(), approved.getId().getProposalId())
                                    && Objects.equals(dto.getId().getStaffId(), approved.getId().getStaffId()))
                    .map(dto -> dto.getJobScope().getJobScope())
                    .toList();

            Map<String, Integer> approvedPermanentCompetencyMap = approvedPermanentCompetency.stream().filter(dto ->
                            Objects.equals(dto.getId().getProposalId(), approved.getId().getProposalId())
                                    && Objects.equals(dto.getId().getStaffId(), approved.getId().getStaffId()))
                    .collect(Collectors.toMap(
                            dto -> dto.getCompetency().getName(),
                            RoleCompetencyItemDto::getWeightage
                    ));

            Map<String, Integer> approvedProposedCompetencyMap = approvedProposedCompetency.stream().filter(dto ->
                            Objects.equals(dto.getId().getProposalId(), approved.getId().getProposalId())
                                    && Objects.equals(dto.getId().getStaffId(), approved.getId().getStaffId()))
                    .collect(Collectors.toMap(
                            dto -> dto.getCompetencyProposal().getName(),
                            RoleCompetencyProposalItemDto::getWeightage
                    ));

            Map<String, Integer> approvedCompetencyMap = new HashMap<>(approvedPermanentCompetencyMap);
            approvedCompetencyMap.putAll(approvedProposedCompetencyMap);

            for (String recipientEmail : recipientEmails) {
                emailService.sendCompetencyAssignmentProposalApprovedEmail(
                        recipientEmail,
                        updatedRoleMap.get(approved.getRole().getId()).getOrgChart().getName(),
                        updatedRoleMap.get(approved.getRole().getId()).getName(),
                        updatedRoleMap.get(approved.getRole().getId()).getDescription(),
                        approvedJobScopes,
                        approvedCompetencyMap,
                        updatedBy.getStaff().getEmail(),
                        Locale.getDefault()
                );
            }
        }

        for (RoleCompetencyProposalDto rejected : rejectedRecords) { // notify owner
            List<String> recipientEmails = allParticipants.stream().filter(dto ->
                            Objects.equals(dto.getId().getProposalId(), rejected.getId().getProposalId())
                                    && Objects.equals(dto.getId().getStaffId(), rejected.getId().getStaffId())) // owner
                    .map(dto -> dto.getStaff().getEmail())
                    .toList();

            List<String> rejectedJobScopes = rejectedRoleJobScopeProposal.stream().filter(dto ->
                            Objects.equals(dto.getId().getProposalId(), rejected.getId().getProposalId())
                                    && Objects.equals(dto.getId().getStaffId(), rejected.getId().getStaffId()))
                    .map(dto -> dto.getJobScope().getJobScope())
                    .toList();

            Map<String, Integer> rejectedPermanentCompetencyMap = rejectedPermanentCompetency.stream().filter(dto ->
                            Objects.equals(dto.getId().getProposalId(), rejected.getId().getProposalId())
                                    && Objects.equals(dto.getId().getStaffId(), rejected.getId().getStaffId()))
                    .collect(Collectors.toMap(
                            dto -> dto.getCompetency().getName(),
                            RoleCompetencyItemDto::getWeightage
                    ));

            Map<String, Integer> rejectedProposedCompetencyMap = rejectedProposedCompetency.stream().filter(dto ->
                            Objects.equals(dto.getId().getProposalId(), rejected.getId().getProposalId())
                                    && Objects.equals(dto.getId().getStaffId(), rejected.getId().getStaffId()))
                    .collect(Collectors.toMap(
                            dto -> dto.getCompetencyProposal().getName(),
                            RoleCompetencyProposalItemDto::getWeightage
                    ));

            Map<String, Integer> rejectedCompetencyMap = new HashMap<>(rejectedPermanentCompetencyMap);
            rejectedCompetencyMap.putAll(rejectedProposedCompetencyMap);

            for (String recipientEmail : recipientEmails) {
                emailService.sendCompetencyAssignmentProposalApprovedEmail(
                        recipientEmail,
                        updatedRoleMap.get(rejected.getRole().getId()).getOrgChart().getName(),
                        updatedRoleMap.get(rejected.getRole().getId()).getName(),
                        updatedRoleMap.get(rejected.getRole().getId()).getDescription(),
                        rejectedJobScopes,
                        rejectedCompetencyMap,
                        updatedBy.getStaff().getEmail(),
                        Locale.getDefault()
                );
            }
        }

        // -- delete proposal participants --
        Set<Long> deletedParticipantRoleIds = proposalParticipantService.findAndDeleteByProposalIdIn(selectedProposalIds)
                .stream().map(dto -> dto.getStaff().getRole().getId())
                .collect(Collectors.toSet());

        this.removeGrantedAccessIfNotLongerUsed(deletedParticipantRoleIds);

        // -- update parent Records --
        proposalService.updateProposalStatusByIdIn(selectedProposalIds, ProposalStatus.APPROVED, userUUID);

        return ResponseEntity.ok(Map.of(
                "message", messageSource.getMessage(COMPETENCY_PROPOSAL_ASSIGNMENT_APPROVE_OK, null, Locale.getDefault())
        ));
    }

    private void updateGrantedAccess(Set<RoleDto> roleDtos, UUID userUUID, OffsetDateTime now) {
        AuthorityDto authorityDto = authorityService.findByName(AuthorityName.CAN_PROPOSE_ROLE_COMPETENCIES);

        List<RoleAuthorityDto> roleAuthorityDtos = roleDtos.stream().map(dto -> {
            RoleAuthorityDto roleAuthorityDto = new RoleAuthorityDto();
            roleAuthorityDto.setCreatedBy(userUUID);
            roleAuthorityDto.setCreatedAt(now);
            roleAuthorityDto.setUpdatedBy(userUUID);
            roleAuthorityDto.setUpdatedAt(now);
            roleAuthorityDto.setId(new RoleAuthorityId(dto.getId(), authorityDto.getId()));
            roleAuthorityDto.setAuthority(authorityDto);
            roleAuthorityDto.setRole(dto);

            return roleAuthorityDto;
        }).collect(Collectors.toList());

        if (!roleAuthorityDtos.isEmpty()) {
            roleAuthorityService.createAll(roleAuthorityDtos); // assign access
        }
    }

}