package com.tbm.careerpathlearning.controller;

import com.tbm.careerpathlearning.dto.*;
import com.tbm.careerpathlearning.enums.*;
import com.tbm.careerpathlearning.exception.BadRequestException;
import com.tbm.careerpathlearning.exception.ForbiddenRequestException;
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
import org.springframework.security.core.GrantedAuthority;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
@RestController
@RequestMapping("/api/competency-definition-collaboration")
@CrossOrigin
public class CompetencyDefinitionCollaborationController {

    private Logger logger = LoggerFactory.getLogger(CompetencyDefinitionCollaborationController.class);

    @Autowired
    private StaffService staffService;

    @Autowired
    private MessageSource messageSource;

    @Autowired
    private EmailService emailService;

    @Autowired
    private ValidationService validationService;

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

    private static final String INVALID_REQUEST_BODY_ERR_MSG_CODE = "invalid.request.body.err.msg";

    private static final String PROPOSAL_PARTICIPANT_CONFLICT_ERR_TITLE_CODE = "proposal.participant.conflict.err.title";

    private static final String PROPOSAL_PARTICIPANT_CONFLICT_ERR_MSG_CODE = "proposal.participant.conflict.err.msg";

    private static final String PROPOSAL_REQUIRED_REVIEWER_ERR_TITLE_CODE = "proposal.required.reviewer.err.title";

    private static final String PROPOSAL_REQUIRED_REVIEWER_ERR_MSG_CODE = "proposal.required.reviewer.err.msg";

    private static final String FORBIDDEN_REQUEST_ERR_MSG_CODE = "forbidden.request.err.msg";

    private static final String PROPOSAL_APPROVAL_CONFLICT_ERR_TITLE_CODE = "proposal.approval.conflict.err.title";

    private static final String PROPOSAL_APPROVAL_CONFLICT_ERR_MSG_CODE = "proposal.approval.conflict.err.msg";

    private static final String PROPOSE_COMPETENCY_OPERATION = "Propose Competency";

    private static final String COMPETENCY_PROPOSAL_EDIT_OPERATION = "Update Competency Definition Collaboration";

    private static final String COMPETENCY_PROPOSAL_REJECT_OPERATION = "Reject Competency Definition Collaboration";

    private static final String COMPETENCY_PROPOSAL_APPROVE_OPERATION = "Approve Competency Definition Collaboration";

    private static final String COMPETENCY_PROPOSAL_CREATION_OK = "proposal.competency.creation.ok.msg";

    private static final String COMPETENCY_PROPOSAL_EDIT_OK = "proposal.competency.edit.ok.msg";

    private static final String COMPETENCY_PROPOSAL_REJECT_OK = "proposal.competency.reject.ok.msg";

    private static final String COMPETENCY_PROPOSAL_APPROVE_OK = "proposal.competency.approve.ok.msg";

    @PreAuthorize("""
            hasAnyAuthority(
                T(com.tbm.careerpathlearning.enums.AuthorityName).CAN_MANAGE_COMPETENCY.getAuthorityName(),
                T(com.tbm.careerpathlearning.enums.AuthorityName).CAN_PROPOSE_ROLE_COMPETENCIES.getAuthorityName()
            )
            """)
    @GetMapping("/overview")
    @Transactional
    public ResponseEntity<?> competencyProposalOverview(Authentication authentication) throws Exception {
        String userId = authentication.getName();
        UUID userUUID = UUID.fromString(userId);

        // Get All Proposal Records under User's UUID
        List<ProposalParticipantDto> existingProposalParticipantDtos = proposalParticipantService.getAllProposalParticipants();
        Set<Long> existingProposalIds = existingProposalParticipantDtos.stream()
                .filter(dto -> Objects.equals(dto.getId().getStaffId(), userUUID))
                .map(dto -> dto.getId().getProposalId())
                .collect(Collectors.toCollection(LinkedHashSet::new));

        // Filter the proposal by types and status
        List<ProposalDto> existingProposalDtos = proposalService.getAllProposalsByIdIn(existingProposalIds);
        List<ProposalDto> existingOngoingProposalDtos = existingProposalDtos.stream()
                .filter(dto -> dto.getStatus() == ProposalStatus.ONGOING && dto.getType() == ProposalType.COMPETENCY)
                .toList();
        Set<Long> ongoingProposalIds = existingOngoingProposalDtos.stream()
                .map(ProposalDto::getId)
                .collect(Collectors.toCollection(LinkedHashSet::new));

        // Map if the user has access to review
        List<ProposalParticipantDto> ongoingProposalParticipantDto = existingProposalParticipantDtos.stream()
                .filter(dto -> ongoingProposalIds.contains(dto.getId().getProposalId())).toList();
        Map<ProposalParticipantId, Boolean> ongoingProposalParticipantReviewerMap = ongoingProposalParticipantDto.stream()
                .collect(Collectors.toMap(
                        ProposalParticipantDto::getId,
                        dto -> Objects.equals(dto.getProposalRole(), ProposalRole.REVIEWER)
                ));
        Map<UUID, StaffDto> ongoingParticipantMap = ongoingProposalParticipantDto.stream()
                .collect(Collectors.toMap(
                        dto -> dto.getId().getStaffId(),
                        ProposalParticipantDto::getStaff,
                        (first, second) -> first
                ));

        // Get Competency Proposal Records
        List<CompetencyProposalDto> ongoingCompetencyProposalDtos = competencyProposalService.getAllByProposalIdIn(ongoingProposalIds);

        // Get Comp Tag Dto
        List<CompetencyCompTagProposalDto> ongoingCompetencyCompTagDtos =
                competencyCompTagProposalService.findAllByProposalIdIn(ongoingProposalIds);

        Set<Long> existingCompTagIds = ongoingCompetencyCompTagDtos.stream()
                .map(dto -> dto.getCompTag().getId())
                .collect(Collectors.toSet());

        List<CompTagDto> existingCompTagDtos = compTagService.findAllByIsDeletedIsFalseAndIdIn(existingCompTagIds);
        Map<Long, CompTagDto> existingCompTagDtoMap = existingCompTagDtos.stream()
                .collect(Collectors.toMap(
                        CompTagDto::getId,
                        Function.identity()
                ));

        List<CompetencyProposalOverviewDto> overviewDtos = ongoingProposalIds.stream().flatMap(id -> {
            boolean isReviewer = ongoingProposalParticipantReviewerMap.get(new ProposalParticipantId(id, userUUID));

            if (isReviewer) {
                return ongoingCompetencyProposalDtos.stream()
                        .filter(dto -> Objects.equals(dto.getId().getProposalId(), id))
                        .map(dto -> {
                            CompetencyProposalOverviewDto overviewDto = new CompetencyProposalOverviewDto();

                            overviewDto.setProposalParticipantId(dto.getId());
                            overviewDto.setCompetencyName(dto.getName());
                            overviewDto.setCompetencyDescription(dto.getDescription() == null ? null : dto.getDescription().trim());

                            List<CompetencyCompTagProposalDto> assignedCompetencyCompTagDto = ongoingCompetencyCompTagDtos.stream()
                                    .filter(cct -> Objects.equals(cct.getId().getProposalId(), dto.getId().getProposalId())
                                            && Objects.equals(cct.getId().getStaffId(), dto.getId().getStaffId()))
                                    .toList();
                            Set<Long> assignedCompTagIds = assignedCompetencyCompTagDto.stream()
                                    .map(cct -> cct.getId().getCompTagId())
                                    .collect(Collectors.toSet());
                            List<CompTagDto> assignedCompTagDtos = assignedCompTagIds.stream().map(existingCompTagDtoMap::get).toList();
                            overviewDto.setAssignedCompTags(assignedCompTagDtos);

                            overviewDto.setCollaboratorName(dto.getStaff().getName());
                            overviewDto.setCollaboratorEmail(dto.getStaff().getEmail());
                            overviewDto.setReviewer(true);
                            overviewDto.setLastUpdatedBy(ongoingParticipantMap.get(dto.getUpdatedBy()));

                            return overviewDto;
                        });
            }
            return ongoingCompetencyProposalDtos.stream()
                    .filter(dto ->
                            Objects.equals(dto.getId().getProposalId(), id)
                                    && Objects.equals(dto.getId().getStaffId(), userUUID))
                    .map(dto -> {
                        CompetencyProposalOverviewDto overviewDto = new CompetencyProposalOverviewDto();

                        overviewDto.setProposalParticipantId(dto.getId());
                        overviewDto.setCompetencyName(dto.getName());
                        overviewDto.setCompetencyDescription(dto.getDescription() == null ? null : dto.getDescription().trim());

                        List<CompetencyCompTagProposalDto> assignedCompetencyCompTagDto = ongoingCompetencyCompTagDtos.stream()
                                .filter(cct -> Objects.equals(cct.getId().getProposalId(), dto.getId().getProposalId())
                                        && Objects.equals(cct.getId().getStaffId(), dto.getId().getStaffId()))
                                .toList();
                        Set<Long> assignedCompTagIds = assignedCompetencyCompTagDto.stream()
                                .map(cct -> cct.getId().getCompTagId())
                                .collect(Collectors.toSet());
                        List<CompTagDto> assignedCompTagDtos = assignedCompTagIds.stream().map(existingCompTagDtoMap::get).toList();
                        overviewDto.setAssignedCompTags(assignedCompTagDtos);


                        overviewDto.setCollaboratorName(dto.getStaff().getName());
                        overviewDto.setCollaboratorEmail(dto.getStaff().getEmail());
                        overviewDto.setReviewer(false);
                        overviewDto.setLastUpdatedBy(ongoingParticipantMap.get(dto.getUpdatedBy()));

                        return overviewDto;
                    });
        }).toList();

        return ResponseEntity.ok(overviewDtos);
    }

    @PreAuthorize("""
            hasAnyAuthority(
                T(com.tbm.careerpathlearning.enums.AuthorityName).CAN_MANAGE_COMPETENCY.getAuthorityName(),
                T(com.tbm.careerpathlearning.enums.AuthorityName).CAN_PROPOSE_ROLE_COMPETENCIES.getAuthorityName()
            )
            """)
    @PostMapping()
    @Transactional
    public ResponseEntity<?> proposeCompetency(@RequestBody ProposeCompetencyRequest req,
                                               Authentication authentication) throws Exception {
        if (validationService.isNullOrBlank(req.getCompetencyName())) {
            String errorMessage = messageSource.getMessage(INVALID_REQUEST_BODY_ERR_MSG_CODE, new String[]{PROPOSE_COMPETENCY_OPERATION}, Locale.getDefault());
            throw new BadRequestException(errorMessage);
        }

        List<String> roles = authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .toList();

        if (!roles.contains(AuthorityName.CAN_MANAGE_COMPETENCY.getAuthorityName()) && req.getReviewerList().isEmpty()) {
            String errorTitle = messageSource.getMessage(PROPOSAL_REQUIRED_REVIEWER_ERR_TITLE_CODE, null, Locale.getDefault());
            String errorMessage = messageSource.getMessage(PROPOSAL_REQUIRED_REVIEWER_ERR_MSG_CODE, null, Locale.getDefault());

            throw new BadRequestException(errorTitle, errorMessage);
        }

        if (!roles.contains(AuthorityName.CAN_MANAGE_COMPETENCY.getAuthorityName()) && !req.getProposerList().isEmpty()) {
            String errorMessage = messageSource.getMessage(FORBIDDEN_REQUEST_ERR_MSG_CODE, null, Locale.getDefault());

            throw new ForbiddenRequestException(errorMessage);
        }

        String competencyName = req.getCompetencyName().trim();
        competencyService.checkRedundancyByName(null, competencyName); // check redundancy before proceed

        String competencyDescription = req.getCompetencyDescription().trim();

        Set<UUID> reviewerIds = new HashSet<>(req.getReviewerList());
        List<StaffDto> reviewerList = staffService.findAllByIdIn(reviewerIds);

        Set<UUID> proposerIds = new HashSet<>(req.getProposerList());
        List<StaffDto> proposerList = staffService.findAllByIdIn(proposerIds);

        String userId = authentication.getName();
        UUID userUUID = UUID.fromString(userId);

        Set<UUID> intersection = new HashSet<>(reviewerIds);
        intersection.retainAll(proposerIds);

        if (!intersection.isEmpty() || reviewerIds.contains(userUUID) || proposerIds.contains(userUUID)) { // check reviewer and proposer redundancy
            String errorTitle = messageSource.getMessage(PROPOSAL_PARTICIPANT_CONFLICT_ERR_TITLE_CODE, null, Locale.getDefault());
            String errorMessage = messageSource.getMessage(PROPOSAL_PARTICIPANT_CONFLICT_ERR_MSG_CODE, null, Locale.getDefault());

            throw new BadRequestException(errorTitle, errorMessage);
        }

        StaffDto userDto = staffService.findById(userUUID);

        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC).withOffsetSameInstant(ZoneOffset.ofHours(8));

        ProposalDto proposalDto = new ProposalDto();
        proposalDto.setType(ProposalType.COMPETENCY);
        proposalDto.setStatus(ProposalStatus.ONGOING);
        proposalDto.setCreatedBy(userUUID);
        proposalDto.setCreatedAt(now);
        proposalDto.setUpdatedBy(userUUID);
        proposalDto.setUpdatedAt(now);

        ProposalDto proposalDtoAdded = proposalService.createProposal(proposalDto); // add proposal

        List<ProposalParticipantDto> proposalParticipantDtosToBeAdded = new ArrayList<>();
        List<CompetencyProposalDto> competencyProposalDtosToBeAdded = new ArrayList<>();

        ProposalParticipantDto proposalParticipantDto = new ProposalParticipantDto();
        proposalParticipantDto.setProposal(proposalDtoAdded);
        proposalParticipantDto.setStaff(userDto);
        proposalParticipantDto.setId(new ProposalParticipantId(proposalDtoAdded.getId(), userDto.getId()));
        if (roles.contains(AuthorityName.CAN_MANAGE_COMPETENCY.getAuthorityName())) {
            proposalParticipantDto.setProposalRole(ProposalRole.REVIEWER);
        } else {
            proposalParticipantDto.setProposalRole(ProposalRole.PROPOSER);
        }
        proposalParticipantDto.setInitiator(true);
        proposalParticipantDto.setCreatedBy(userUUID);
        proposalParticipantDto.setCreatedAt(now);
        proposalParticipantDto.setUpdatedBy(userUUID);
        proposalParticipantDto.setUpdatedAt(now);

        proposalParticipantDtosToBeAdded.add(proposalParticipantDto); // add as initiator

        CompetencyProposalDto competencyProposalDtoToBeAdded = new CompetencyProposalDto();
        competencyProposalDtoToBeAdded.setId(proposalParticipantDto.getId());
        competencyProposalDtoToBeAdded.setProposal(proposalDtoAdded);
        competencyProposalDtoToBeAdded.setStaff(userDto);
        competencyProposalDtoToBeAdded.setName(competencyName);
        competencyProposalDtoToBeAdded.setDescription(competencyDescription);
        competencyProposalDtoToBeAdded.setCreatedAt(now);
        competencyProposalDtoToBeAdded.setCreatedBy(userUUID);
        competencyProposalDtoToBeAdded.setUpdatedAt(now);
        competencyProposalDtoToBeAdded.setUpdatedBy(userUUID);

        competencyProposalDtosToBeAdded.add(competencyProposalDtoToBeAdded); // add competencyProposalOriginalCopy

        if (!reviewerList.isEmpty()) {
            // add proposal participant
            List<ProposalParticipantDto> proposalParticipantDtos = reviewerList.stream().map(reviewer -> {
                ProposalParticipantDto proposalReviewerDto = new ProposalParticipantDto();
                proposalReviewerDto.setId(new ProposalParticipantId(proposalDtoAdded.getId(), reviewer.getId()));
                proposalReviewerDto.setStaff(reviewer);
                proposalReviewerDto.setProposal(proposalDtoAdded);
                proposalReviewerDto.setProposalRole(ProposalRole.REVIEWER);
                proposalReviewerDto.setInitiator(false);
                proposalReviewerDto.setCreatedBy(userUUID);
                proposalReviewerDto.setCreatedAt(now);
                proposalReviewerDto.setUpdatedBy(userUUID);
                proposalReviewerDto.setUpdatedAt(now);

                return proposalReviewerDto;
            }).toList();

            proposalParticipantDtosToBeAdded.addAll(proposalParticipantDtos);
        }

        if (!proposerList.isEmpty()) {
            //grant access
            Set<RoleDto> roleDtos = proposerList.stream()
                    .map(StaffDto::getRole)
                    .collect(Collectors.toSet());

            this.updateGrantedAccess(roleDtos, userUUID, now);

            // add proposal participant
            List<ProposalParticipantDto> proposalParticipantDtos = proposerList.stream().map(proposer -> {
                ProposalParticipantDto proposalReviewerDto = new ProposalParticipantDto();
                proposalReviewerDto.setId(new ProposalParticipantId(proposalDtoAdded.getId(), proposer.getId()));
                proposalReviewerDto.setStaff(proposer);
                proposalReviewerDto.setProposal(proposalDtoAdded);
                proposalReviewerDto.setProposalRole(ProposalRole.PROPOSER);
                proposalReviewerDto.setInitiator(false);
                proposalReviewerDto.setCreatedBy(userUUID);
                proposalReviewerDto.setCreatedAt(now);
                proposalReviewerDto.setUpdatedBy(userUUID);
                proposalReviewerDto.setUpdatedAt(now);

                return proposalReviewerDto;
            }).toList();

            proposalParticipantDtosToBeAdded.addAll(proposalParticipantDtos);

            // add competency proposal
            List<CompetencyProposalDto> competencyProposalDtos = proposerList.stream().map(proposer -> {
                CompetencyProposalDto competencyProposalDto = new CompetencyProposalDto();
                competencyProposalDto.setId(new ProposalParticipantId(proposalDtoAdded.getId(), proposer.getId()));
                competencyProposalDto.setStaff(proposer);
                competencyProposalDto.setProposal(proposalDtoAdded);
                competencyProposalDto.setName(competencyName);
                competencyProposalDto.setDescription(competencyDescription);
                competencyProposalDto.setCreatedAt(now);
                competencyProposalDto.setCreatedBy(userUUID);
                competencyProposalDto.setUpdatedAt(now);
                competencyProposalDto.setUpdatedBy(userUUID);

                return competencyProposalDto;
            }).toList();

            competencyProposalDtosToBeAdded.addAll(competencyProposalDtos);
        }

        proposalParticipantService.createProposalParticipants(proposalParticipantDtosToBeAdded); // add proposal participant

        List<CompetencyProposalDto> competencyProposalDtoAdded = competencyProposalService.createCompetencyProposals(competencyProposalDtosToBeAdded);

        Map<Long, CompTagDto> compTagDtoMap = compTagService.createAll(
                        req.getCompetencyTagList().stream().map(compTag -> {
                                    CompTagDto compTagDto = new CompTagDto();
                                    compTagDto.setTag(compTag);
                                    compTagDto.setDeleted(false);
                                    compTagDto.setCreatedAt(now);
                                    compTagDto.setCreatedBy(userUUID);
                                    compTagDto.setUpdatedAt(now);
                                    compTagDto.setUpdatedBy(userUUID);
                                    return compTagDto;
                                }
                        ).toList())
                .stream().collect(Collectors.toMap(
                        CompTagDto::getId,
                        Function.identity()
                ));

        Map<UUID, StaffDto> staffDtoMap = competencyProposalDtoAdded.stream().collect(Collectors.toMap(
                competencyProposalDto -> competencyProposalDto.getId().getStaffId(),
                CompetencyProposalDto::getStaff
        ));

        Set<UUID> staffIds = staffDtoMap.keySet();

        List<CompetencyCompTagProposalDto> compTagProposalDtoToBeAdded =
                staffIds.stream()
                        .flatMap(staffId ->
                                compTagDtoMap.keySet().stream()
                                        .map(compTagId -> {
                                                    CompetencyCompTagProposalDto dto = new CompetencyCompTagProposalDto();
                                                    dto.setId(new CompetencyCompTagProposalId(
                                                            proposalDtoAdded.getId(),
                                                            staffId,
                                                            compTagId
                                                    ));
                                                    dto.setProposal(proposalDtoAdded);
                                                    dto.setCompTag(compTagDtoMap.get(compTagId));
                                                    dto.setStaff(staffDtoMap.get(staffId));
                                                    dto.setCreatedAt(now);
                                                    dto.setCreatedBy(userUUID);
                                                    dto.setUpdatedAt(now);
                                                    dto.setUpdatedBy(userUUID);

                                                    return dto;
                                                }
                                        )
                        )
                        .collect(Collectors.toList());

        if (!compTagProposalDtoToBeAdded.isEmpty()) {
            competencyCompTagProposalService.createCompetencyCompTagProposals(compTagProposalDtoToBeAdded);
        }

        //send email
        List<String> compTags = compTagDtoMap.values().stream().map(CompTagDto::getTag).toList();

        if (!reviewerList.isEmpty()) {
            reviewerList.forEach(reviewer -> {
                emailService.sendCompetencyProposalReviewInvitationEmail(
                        reviewer.getEmail(),
                        proposalDtoAdded,
                        competencyName,
                        competencyDescription,
                        userUUID,
                        compTags,
                        Locale.getDefault()
                );
            });
        }

        if (!proposerList.isEmpty()) {
            proposerList.forEach(proposer -> {
                emailService.sendCompetencyProposalProposeInvitationEmail(
                        proposer.getEmail(),
                        proposalDtoAdded,
                        competencyName,
                        competencyDescription,
                        proposer.getId(),
                        compTags,
                        Locale.getDefault()
                );
            });
        }

        return ResponseEntity.ok(Map.of(
                "message", messageSource.getMessage(COMPETENCY_PROPOSAL_CREATION_OK, null, Locale.getDefault()
                )));
    }

    @PreAuthorize("""
            hasAnyAuthority(
                T(com.tbm.careerpathlearning.enums.AuthorityName).CAN_MANAGE_COMPETENCY.getAuthorityName(),
                T(com.tbm.careerpathlearning.enums.AuthorityName).CAN_PROPOSE_ROLE_COMPETENCIES.getAuthorityName()
            )
            """)
    @PutMapping("/edit-competency-proposal")
    @Transactional
    public ResponseEntity<?> updateCompetencyProposal(@RequestBody EditCompetencyProposalRequestDto req, Authentication authentication) throws Exception {
        if (req == null || req.getProposalParticipantId() == null || validationService.isNullOrBlank(req.getCompetencyName())) {
            String errorMessage = messageSource.getMessage(INVALID_REQUEST_BODY_ERR_MSG_CODE, new String[]{COMPETENCY_PROPOSAL_EDIT_OPERATION}, Locale.getDefault());
            throw new BadRequestException(errorMessage);
        }

        String userId = authentication.getName();
        UUID userUUID = UUID.fromString(userId);

        ProposalParticipantId proposalParticipantId = req.getProposalParticipantId();
        Long proposalId = proposalParticipantId.getProposalId();
        UUID staffId = proposalParticipantId.getStaffId();

        CompetencyProposalDto competencyProposalDto = competencyProposalService.getById(proposalParticipantId);

        String competencyName = req.getCompetencyName().trim();
        competencyService.checkRedundancyByName(null, competencyName); // check name redundancy
        competencyProposalDto.setName(competencyName);

        String competencyDescription = req.getCompetencyDescription().trim();
        competencyProposalDto.setDescription(competencyDescription);

        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC).withOffsetSameInstant(ZoneOffset.ofHours(8));
        competencyProposalDto.setUpdatedBy(userUUID);
        competencyProposalDto.setUpdatedAt(now);

        CompetencyProposalDto updatedCompetencyProposalDto = competencyProposalService.updateCompetencyProposal(proposalParticipantId, competencyProposalDto);

        List<CompetencyCompTagProposalDto> existingCompetencyCompTagProposalDtos = competencyCompTagProposalService
                .getByProposalStaffId(proposalId, staffId);
        Set<Long> existingCompTagIds = existingCompetencyCompTagProposalDtos.stream()
                .map(cctp -> cctp.getId().getCompTagId())
                .collect(Collectors.toSet());

        List<CompTagDto> requestedCompTags;

        if (req.getCompTagList() != null || !req.getCompTagList().isEmpty()) {
            requestedCompTags = compTagService.createAll(
                    req.getCompTagList().stream()
                            .map(tag -> {
                                CompTagDto dto = new CompTagDto();
                                dto.setTag(tag.trim());
                                dto.setDeleted(false);
                                dto.setCreatedBy(userUUID);
                                dto.setCreatedAt(now);
                                dto.setUpdatedBy(userUUID);
                                dto.setUpdatedAt(now);
                                return dto;
                            })
                            .collect(Collectors.toList())
            );
        } else {
            requestedCompTags = Collections.emptyList();
        }

        Map<Long, CompTagDto> requestedCompTagMap = requestedCompTags.stream()
                .collect(Collectors.toMap(CompTagDto::getId, Function.identity()));

        Set<Long> requestedCompTagIds = requestedCompTags.stream()
                .map(CompTagDto::getId)
                .collect(Collectors.toSet());

        Set<Long> toAdd = new HashSet<>(requestedCompTagIds);
        toAdd.removeAll(existingCompTagIds);

        Set<Long> toRemove = new HashSet<>(existingCompTagIds);
        toRemove.removeAll(requestedCompTagIds);

        if (!toAdd.isEmpty()) {
            List<CompetencyCompTagProposalDto> competencyCompTagProposalDtos = toAdd.stream()
                    .map(compTagId -> {
                        CompetencyCompTagProposalDto cctp = new CompetencyCompTagProposalDto();
                        cctp.setId(new CompetencyCompTagProposalId(proposalId, staffId, compTagId));
                        cctp.setProposal(updatedCompetencyProposalDto.getProposal());
                        cctp.setStaff(updatedCompetencyProposalDto.getStaff());
                        cctp.setCompTag(requestedCompTagMap.get(compTagId));
                        cctp.setCreatedBy(userUUID);
                        cctp.setCreatedAt(now);
                        cctp.setUpdatedBy(userUUID);
                        cctp.setUpdatedAt(now);
                        return cctp;
                    })
                    .collect(Collectors.toList());

            competencyCompTagProposalService.createCompetencyCompTagProposals(competencyCompTagProposalDtos);
        }

        if (!toRemove.isEmpty()) {
            competencyCompTagProposalService.deleteByProposalStaffIdAndCompTagIdIn(proposalId, staffId, toRemove);

            // check if compTags are used by other competencies
            Set<Long> stillUsedInCompetency = competencyCompTagService.findAllByCompTagIdIn(toRemove)
                    .stream().map(cct -> cct.getId().getCompTagId()).collect(Collectors.toSet());

            Set<Long> stillUsedInCompetencyProposal = competencyCompTagProposalService.getByCompTagIdIn(toRemove)
                    .stream().map(cctp -> cctp.getId().getCompTagId()).collect(Collectors.toSet());

            Set<Long> compTagToBeDeleted = new HashSet<>(toRemove);
            compTagToBeDeleted.removeAll(stillUsedInCompetency);
            compTagToBeDeleted.removeAll(stillUsedInCompetencyProposal);

            // delete if no longer being used
            if (!compTagToBeDeleted.isEmpty()) {
                compTagService.deleteAllByIdIn(compTagToBeDeleted, userUUID);
            }
        }

        ProposalDto proposalDto = updatedCompetencyProposalDto.getProposal();

        Map<UUID, ProposalParticipantDto> recipentMap = proposalParticipantService.getAllByProposalId(proposalId).stream()
                .filter(dto ->
                        Objects.equals(dto.getProposalRole(), ProposalRole.REVIEWER) || //reviewer
                                Objects.equals(dto.getId().getStaffId(), updatedCompetencyProposalDto.getId().getStaffId())) // Owner
                .collect(Collectors.toMap(
                        dto -> dto.getId().getStaffId(),
                        Function.identity()
                ));

        ProposalParticipantDto updatedBy = recipentMap.get(userUUID);
        recipentMap.remove(userUUID); // avoid sending email to user who made changes

        recipentMap.values().forEach(dto -> {
            emailService.sendCompetencyProposalUpdateEmail(
                    dto.getStaff().getEmail(),
                    proposalDto,
                    updatedCompetencyProposalDto.getName().trim(),
                    updatedCompetencyProposalDto.getDescription() == null ? null : updatedCompetencyProposalDto.getDescription().trim(),
                    req.getCompTagList(),
                    updatedBy.getStaff().getEmail(),
                    staffId,
                    Locale.getDefault()
            );
        });

        return ResponseEntity.ok(Map.of(
                "message", messageSource.getMessage(COMPETENCY_PROPOSAL_EDIT_OK, null, Locale.getDefault())
        ));
    }

    @PreAuthorize("""
            hasAnyAuthority(
                T(com.tbm.careerpathlearning.enums.AuthorityName).CAN_MANAGE_COMPETENCY.getAuthorityName()
            )
            """)
    @PutMapping("/reject-competency-proposal")
    @Transactional
    public ResponseEntity<?> rejectCompetencyProposal(@RequestBody ProposalParticipantId selectedProposalParticipantId, Authentication authentication) throws Exception {
        if (selectedProposalParticipantId == null) {
            String errorMessage = messageSource.getMessage(INVALID_REQUEST_BODY_ERR_MSG_CODE, new String[]{COMPETENCY_PROPOSAL_REJECT_OPERATION}, Locale.getDefault());
            throw new BadRequestException(errorMessage);
        }

        // -- basic info --
        String userId = authentication.getName();
        UUID userUUID = UUID.fromString(userId);

        Long selectedProposalId = selectedProposalParticipantId.getProposalId();
        UUID selectedStaffId = selectedProposalParticipantId.getStaffId();

        List<ProposalParticipantDto> allParticipantDtos = proposalParticipantService.getAllProposalParticipants();
        List<CompetencyProposalDto> allCompetencyDtosWithinSameProposal = competencyProposalService.getAllByProposalId(selectedProposalId);

        CompetencyProposalDto selectedCompetencyProposalDto = allCompetencyDtosWithinSameProposal.stream()
                .filter(dto -> Objects.equals(dto.getId(), selectedProposalParticipantId))
                .findAny().orElse(null);

        List<CompetencyCompTagProposalDto> selectedCompetencyCompTagProposalDtos = competencyCompTagProposalService
                .getByProposalStaffId(selectedProposalId, selectedStaffId);
        Set<Long> selectedCompTagIds = selectedCompetencyCompTagProposalDtos.stream()
                .map(cctp -> cctp.getId().getCompTagId())
                .collect(Collectors.toSet());
        List<String> selectedCompTags = selectedCompetencyCompTagProposalDtos.stream().map(
                        dto -> dto.getCompTag().getTag())
                .toList();

        // -- delete relevant records --
        if (!selectedCompTagIds.isEmpty()) {
            competencyCompTagProposalService.deleteByProposalStaffIdAndCompTagIdIn(selectedProposalId, selectedStaffId, selectedCompTagIds); // remove comp tag assignments

            // check if compTags are used by other competencies
            Set<Long> stillUsedInCompetency = competencyCompTagService.findAllByCompTagIdIn(selectedCompTagIds)
                    .stream().map(cct -> cct.getId().getCompTagId()).collect(Collectors.toSet());

            Set<Long> stillUsedInCompetencyProposal = competencyCompTagProposalService.getByCompTagIdIn(selectedCompTagIds)
                    .stream().map(cctp -> cctp.getId().getCompTagId()).collect(Collectors.toSet());

            Set<Long> compTagToBeDeleted = new HashSet<>(selectedCompTagIds);
            compTagToBeDeleted.removeAll(stillUsedInCompetency);
            compTagToBeDeleted.removeAll(stillUsedInCompetencyProposal);

            // delete comp tag if no longer being used
            if (!compTagToBeDeleted.isEmpty()) {
                compTagService.deleteAllByIdIn(compTagToBeDeleted, userUUID);
            }
        }

        // -- update granted access
        if (selectedCompetencyProposalDto != null) {
            this.updateAccessControlForSingleRole(selectedCompetencyProposalDto.getStaff().getRole().getId(), selectedProposalId, allParticipantDtos);
        }

        // reject proposal directly if no more child records
        List<CompetencyProposalDto> remainingCompetencyProposalDto =
                new ArrayList<>(allCompetencyDtosWithinSameProposal);
        remainingCompetencyProposalDto.remove(selectedCompetencyProposalDto);

        if (remainingCompetencyProposalDto.isEmpty()) {
            proposalParticipantService.deleteAllByProposalId(selectedProposalId); // delete all participation
            proposalService.updateProposalStatusById(selectedProposalId, ProposalStatus.REJECTED, userUUID); //reject parent record directly
        } else {
            ProposalParticipantDto selectParticipant = allParticipantDtos.stream()
                    .filter(dto -> Objects.equals(dto.getId(), selectedProposalParticipantId))
                    .findAny()
                    .orElse(null);
            if (selectParticipant != null && !selectParticipant.isInitiator()
                    && !Objects.equals(selectParticipant.getProposalRole(), ProposalRole.REVIEWER)) { // not initiator
                proposalParticipantService.deleteById(selectedProposalParticipantId); // delete selected proposal participation
            }
        }

        competencyProposalService.delete(selectedProposalParticipantId); // delete competency proposal record

        // -- send email --
        Map<UUID, ProposalParticipantDto> recipentMap = allParticipantDtos.stream()
                .filter(dto -> Objects.equals(dto.getId().getProposalId(), selectedProposalId) && // same proposal
                        (Objects.equals(dto.getProposalRole(), ProposalRole.REVIEWER) || // reviewer
                                Objects.equals(dto.getId(), selectedProposalParticipantId))) // Owner
                .collect(Collectors.toMap(
                        dto -> dto.getId().getStaffId(),
                        Function.identity()
                ));

        ProposalParticipantDto updatedBy = recipentMap.get(userUUID);
        recipentMap.remove(userUUID); // avoid sending email to user who made changes

        if (!recipentMap.isEmpty()) {
            recipentMap.values().forEach(dto -> {
                emailService.sendCompetencyProposalRejectedEmail(
                        dto.getStaff().getEmail(),
                        selectedCompetencyProposalDto.getName().trim(),
                        selectedCompetencyProposalDto.getDescription() == null ? null : selectedCompetencyProposalDto.getDescription().trim(),
                        selectedCompTags,
                        updatedBy.getStaff().getEmail(),
                        Locale.getDefault()
                );
            });
        }

        return ResponseEntity.ok(Map.of(
                "message", messageSource.getMessage(COMPETENCY_PROPOSAL_REJECT_OK, null, Locale.getDefault())
        ));
    }

    @PreAuthorize("""
            hasAnyAuthority(
                T(com.tbm.careerpathlearning.enums.AuthorityName).CAN_MANAGE_COMPETENCY.getAuthorityName()
            )
            """)
    @PutMapping("/bulk-reject-competency-proposal")
    @Transactional
    public ResponseEntity<?> bulkRejectCompetencyProposal(@RequestBody Set<ProposalParticipantId> selectedProposalParticipantId, Authentication authentication) throws Exception {
        if (selectedProposalParticipantId == null) {
            String errorMessage = messageSource.getMessage(INVALID_REQUEST_BODY_ERR_MSG_CODE, new String[]{COMPETENCY_PROPOSAL_REJECT_OPERATION}, Locale.getDefault());
            throw new BadRequestException(errorMessage);
        }

        // -- basic info --
        String userId = authentication.getName();
        UUID userUUID = UUID.fromString(userId);

        Set<Long> selectedProposalIds = selectedProposalParticipantId.stream().map(ProposalParticipantId::getProposalId).collect(Collectors.toSet());
        Set<UUID> selectedStaffIds = selectedProposalParticipantId.stream().map(ProposalParticipantId::getStaffId).collect(Collectors.toSet());

        List<ProposalParticipantDto> allParticipants = proposalParticipantService.getAllProposalParticipants();

        List<CompetencyProposalDto> allCompetencyProposalDtos = competencyProposalService.getAllByProposalIdIn(selectedProposalIds);
        Map<ProposalParticipantId, CompetencyProposalDto> selectedCompetencyProposalDtoMap = allCompetencyProposalDtos.stream()
                .filter(dto -> selectedProposalParticipantId.contains(dto.getId()))
                .collect(Collectors.toMap(
                        CompetencyProposalDto::getId,
                        Function.identity()
                ));

        List<CompetencyCompTagProposalDto> selectedCompetencyCompTagProposalDtos = competencyCompTagProposalService.getByProposalStaffIdIn(selectedProposalIds, selectedStaffIds);
        Set<Long> selectedCompTagIds = selectedCompetencyCompTagProposalDtos.stream()
                .map(cctp -> cctp.getId().getCompTagId())
                .collect(Collectors.toSet());

        // -- delete relevant records --
        if (!selectedCompTagIds.isEmpty()) {
            competencyCompTagProposalService.deleteByProposalStaffIdInAndCompTagIdIn(selectedProposalIds, selectedStaffIds, selectedCompTagIds); // delete assigned compTags

            // check if compTags are used by other competencies
            Set<Long> stillUsedInCompetency = competencyCompTagService.findAllByCompTagIdIn(selectedCompTagIds)
                    .stream().map(cct -> cct.getId().getCompTagId()).collect(Collectors.toSet());

            Set<Long> stillUsedInCompetencyProposal = competencyCompTagProposalService.getByCompTagIdIn(selectedCompTagIds)
                    .stream().map(cctp -> cctp.getId().getCompTagId()).collect(Collectors.toSet());

            Set<Long> compTagToBeDeleted = new HashSet<>(selectedCompTagIds);
            compTagToBeDeleted.removeAll(stillUsedInCompetency);
            compTagToBeDeleted.removeAll(stillUsedInCompetencyProposal);

            // delete comp tag if no longer being used
            if (!compTagToBeDeleted.isEmpty()) {
                compTagService.deleteAllByIdIn(compTagToBeDeleted, userUUID);
            }
        }

        // update access control
        this.bulkRemoveAccessGranted(null, selectedProposalParticipantId, allParticipants);

        // reject proposal directly if no more child records
        Set<Long> usedProposalIds = allCompetencyProposalDtos.stream()
                .filter(dto -> !selectedProposalParticipantId.contains(dto.getId()))
                .map(dto -> dto.getProposal().getId())
                .collect(Collectors.toSet());

        Set<Long> abandonedProposalIds = new HashSet<>(selectedProposalIds);
        abandonedProposalIds.removeAll(usedProposalIds);

        if (!abandonedProposalIds.isEmpty()) {
            proposalParticipantService.deleteAllByProposalIdIn(abandonedProposalIds); // remove participation
            proposalService.updateProposalStatusByIdIn(abandonedProposalIds, ProposalStatus.REJECTED, userUUID); // update parent proposal record
        }

        Map<ProposalParticipantId, ProposalParticipantDto> selectedProposalParticipantMap
                = selectedProposalParticipantId.stream()
                .collect(Collectors.toMap(
                        Function.identity(),
                        id -> allParticipants.stream()
                                .filter(dto -> Objects.equals(id, dto.getId()))
                                .findAny().orElse(new ProposalParticipantDto())
                ));

        Set<ProposalParticipantId> proposalParticipantIdToBeRemoved = new HashSet<>(selectedProposalParticipantId);
        Set<ProposalParticipantId> proposalInitiatorIds = selectedProposalParticipantId.stream()
                .filter(id -> selectedProposalParticipantMap.get(id).isInitiator()
                        && Objects.equals(selectedProposalParticipantMap.get(id).getProposalRole(), ProposalRole.REVIEWER))
                .collect(Collectors.toSet()); // avoid deleting initiator to allow initaitor keep viewing the remaining competencyDtos
        proposalParticipantIdToBeRemoved.removeAll(proposalInitiatorIds);

        if (!proposalParticipantIdToBeRemoved.isEmpty()) {
            proposalParticipantService.deleteAllByIdIn(proposalParticipantIdToBeRemoved); // delete participation
        }
        competencyProposalService.deleteAllByIdIn(selectedProposalParticipantId); // delete competency proposal record

        // -- send email --
        selectedCompetencyProposalDtoMap.values().forEach(dto -> {
            Map<UUID, ProposalParticipantDto> recipentMap = allParticipants.stream()
                    .filter(participantDto ->
                            Objects.equals(participantDto.getId().getProposalId(), dto.getId().getProposalId()) && // same proposal
                                    (Objects.equals(participantDto.getProposalRole(), ProposalRole.REVIEWER) || // reviewer
                                            Objects.equals(participantDto.getId(), dto.getId()))) //owner
                    .collect(Collectors.toMap(
                            participantDto -> participantDto.getId().getStaffId(),
                            Function.identity()
                    ));

            ProposalParticipantDto updatedBy = recipentMap.get(userUUID);
            recipentMap.remove(userUUID); // avoid sending email to user who made changes

            List<String> assignedCompTags = selectedCompetencyCompTagProposalDtos.stream().filter(
                            cctp -> Objects.equals(cctp.getId().getProposalId(), dto.getId().getProposalId())
                                    && Objects.equals(cctp.getId().getStaffId(), dto.getId().getStaffId()))
                    .map(
                            cctp -> cctp.getCompTag().getTag())
                    .toList();

            recipentMap.values().forEach(recipient -> {
                emailService.sendCompetencyProposalRejectedEmail(
                        recipient.getStaff().getEmail(),
                        dto.getName().trim(),
                        dto.getDescription() == null ? null : dto.getDescription().trim(),
                        assignedCompTags,
                        updatedBy.getStaff().getEmail(),
                        Locale.getDefault()
                );
            });
        });

        return ResponseEntity.ok(Map.of(
                "message", messageSource.getMessage(COMPETENCY_PROPOSAL_REJECT_OK, null, Locale.getDefault())
        ));
    }

    @PreAuthorize("""
            hasAnyAuthority(
                T(com.tbm.careerpathlearning.enums.AuthorityName).CAN_MANAGE_COMPETENCY.getAuthorityName()
            )
            """)
    @PutMapping("/approve-competency-proposal")
    @Transactional
    public ResponseEntity<?> approveCompetencyProposal(@RequestBody ProposalParticipantId selectedProposalParticipantId, Authentication authentication) throws Exception {
        if (selectedProposalParticipantId == null) {
            String errorMessage = messageSource.getMessage(INVALID_REQUEST_BODY_ERR_MSG_CODE, new String[]{COMPETENCY_PROPOSAL_APPROVE_OPERATION}, Locale.getDefault());
            throw new BadRequestException(errorMessage);
        }

        // -- basic info --
        String userId = authentication.getName();
        UUID userUUID = UUID.fromString(userId);

        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC).withOffsetSameInstant(ZoneOffset.ofHours(8));

        Long selectedProposalId = selectedProposalParticipantId.getProposalId();
        UUID selectedStaffId = selectedProposalParticipantId.getStaffId();

        List<ProposalParticipantDto> allProposalParticipants = proposalParticipantService.getAllProposalParticipants();

        List<CompetencyProposalDto> competencyProposalDtosWithinTheSameProposal = competencyProposalService.getAllByProposalId(selectedProposalId);
        CompetencyProposalDto selectedCompetencyProposalDto = competencyProposalDtosWithinTheSameProposal.stream()
                .filter(dto -> dto.getId().equals(selectedProposalParticipantId))
                .findAny().orElse(null);

        // -- approve proposal to competency --
        CompetencyDto competencyDtoToBeAdded = new CompetencyDto();
        competencyDtoToBeAdded.setName(selectedCompetencyProposalDto.getName().trim());
        competencyDtoToBeAdded.setDescription(selectedCompetencyProposalDto.getDescription() == null ? null : selectedCompetencyProposalDto.getDescription().trim());

        competencyDtoToBeAdded.setDeleted(false);
        competencyDtoToBeAdded.setCreatedBy(selectedCompetencyProposalDto.getId().getStaffId());
        competencyDtoToBeAdded.setCreatedAt(now);
        competencyDtoToBeAdded.setUpdatedBy(userUUID);
        competencyDtoToBeAdded.setUpdatedAt(now);

        CompetencyDto competencyCreated = competencyService.create(competencyDtoToBeAdded); // create new competency record

        // -- assign comp tags to competencyCreated --
        List<CompetencyCompTagProposalDto> allCompetencyCompTagProposalDtosWithinSameProposal = competencyCompTagProposalService
                .findAllByProposalId(selectedProposalId);
        Map<Long, CompTagDto> compTagMap = allCompetencyCompTagProposalDtosWithinSameProposal.stream()
                .collect(Collectors.toMap(
                        dto -> dto.getId().getCompTagId(),
                        CompetencyCompTagProposalDto::getCompTag,
                        (first, second) -> first
                ));

        List<CompetencyCompTagProposalDto> selectedCompetencyCompTagProposalDtos = allCompetencyCompTagProposalDtosWithinSameProposal.stream()
                .filter(cctp -> Objects.equals(cctp.getId().getProposalId(), selectedProposalId)
                        && Objects.equals(cctp.getId().getStaffId(), selectedStaffId))
                .toList();

        List<CompetencyCompTagDto> competencyCompTagDtoToBeAdded = selectedCompetencyCompTagProposalDtos.stream()
                .map(cctp -> {
                    CompetencyCompTagDto dto = new CompetencyCompTagDto();
                    dto.setId(new CompetencyCompTagId(competencyCreated.getId(), cctp.getId().getCompTagId()));
                    dto.setCompetency(competencyCreated);
                    dto.setCompTag(compTagMap.get(cctp.getId().getCompTagId()));

                    dto.setCreatedBy(selectedCompetencyProposalDto.getId().getStaffId());
                    dto.setCreatedAt(now);
                    dto.setUpdatedBy(userUUID);
                    dto.setUpdatedAt(now);

                    return dto;
                })
                .collect(Collectors.toList());

        if (!competencyCompTagDtoToBeAdded.isEmpty()) {
            competencyCompTagService.createAll(competencyCompTagDtoToBeAdded); // assign tags to competency
        }

        // -- delete all relevant records --
        Set<Long> selectedCompTagIds = compTagMap.keySet();

        if (!allCompetencyCompTagProposalDtosWithinSameProposal.isEmpty()) {
            competencyCompTagProposalService.deleteAllByProposalId(selectedProposalId); // remove

            // check if compTags are used by other competencies
            Set<Long> stillUsedInCompetency = competencyCompTagService.findAllByCompTagIdIn(selectedCompTagIds)
                    .stream().map(cct -> cct.getId().getCompTagId()).collect(Collectors.toSet());

            Set<Long> stillUsedInCompetencyProposal = competencyCompTagProposalService.getByCompTagIdIn(selectedCompTagIds)
                    .stream().map(cctp -> cctp.getId().getCompTagId()).collect(Collectors.toSet());

            Set<Long> compTagToBeDeleted = new HashSet<>(selectedCompTagIds);
            compTagToBeDeleted.removeAll(stillUsedInCompetency);
            compTagToBeDeleted.removeAll(stillUsedInCompetencyProposal);

            // delete comp tag if no longer being used
            if (!compTagToBeDeleted.isEmpty()) {
                compTagService.deleteAllByIdIn(compTagToBeDeleted, userUUID);
            }
        }

        // update access control
        this.updateAccessControlForSingleRole(selectedCompetencyProposalDto.getStaff().getRole().getId(), selectedProposalId, allProposalParticipants);

        proposalService.updateProposalStatusById(selectedProposalId, ProposalStatus.APPROVED, userUUID); // update parent proposal record as 'APPROVED'

        competencyProposalService.deleteAllByProposalId(selectedProposalId); // delete all competency proposal record

        proposalParticipantService.deleteAllByProposalId(selectedProposalId); // remove participation


        // -- send email --
        List<ProposalParticipantDto> selectedProposalParticipants = allProposalParticipants.stream().filter(
                        dto -> Objects.equals(dto.getId().getProposalId(), selectedProposalId))
                .toList();
        StaffDto updatedBy = selectedProposalParticipants.stream()
                .filter(dto -> Objects.equals(dto.getId(), selectedProposalParticipants))
                .map(ProposalParticipantDto::getStaff)
                .findAny().orElse(null);

        competencyProposalDtosWithinTheSameProposal.forEach(dto -> {
            List<String> assignedCompTags = allCompetencyCompTagProposalDtosWithinSameProposal.stream().filter(
                            cctp -> Objects.equals(cctp.getId().getProposalId(), dto.getId().getProposalId())
                                    && Objects.equals(cctp.getId().getStaffId(), dto.getId().getStaffId()))
                    .map(cctp -> cctp.getCompTag().getTag())
                    .toList();

            for (ProposalParticipantDto recipient : selectedProposalParticipants) {
                if (Objects.equals(recipient.getId().getProposalId(), dto.getId().getProposalId())) { // same proposal
                    if (Objects.equals(recipient.getProposalRole(), ProposalRole.REVIEWER) || Objects.equals(recipient.getId(), dto.getId())) { // reviewer or owner
                        emailService.sendCompetencyProposalApprovedEmail( // send proposal approved email
                                recipient.getStaff().getEmail(),
                                dto.getName().trim(),
                                dto.getDescription() == null ? null : dto.getDescription().trim(),
                                assignedCompTags,
                                updatedBy != null ? updatedBy.getEmail() : "-",
                                Locale.getDefault()
                        );
                    } else if (Objects.equals(recipient.getProposalRole(), ProposalRole.PROPOSER) && !Objects.equals(recipient.getId(), dto.getId())) { // proposer and not owner
                        emailService.sendCompetencyProposalRejectedEmail( // proposal rejected email
                                recipient.getStaff().getEmail(),
                                dto.getName().trim(),
                                dto.getDescription() == null ? null : dto.getDescription().trim(),
                                assignedCompTags,
                                updatedBy != null ? updatedBy.getEmail() : "-",
                                Locale.getDefault()
                        );
                    }
                }
            }
        });

        return ResponseEntity.ok(Map.of(
                "message", messageSource.getMessage(COMPETENCY_PROPOSAL_APPROVE_OK, null, Locale.getDefault())
        ));
    }

    @PreAuthorize("""
            hasAnyAuthority(
                T(com.tbm.careerpathlearning.enums.AuthorityName).CAN_MANAGE_COMPETENCY.getAuthorityName()
            )
            """)
    @PutMapping("/bulk-approve-competency-proposal")
    @Transactional
    public ResponseEntity<?> bulkApproveCompetencyProposal(@RequestBody Set<ProposalParticipantId> selectedProposalParticipantIds, Authentication authentication) throws Exception {
        if (selectedProposalParticipantIds == null) {
            String errorMessage = messageSource.getMessage(INVALID_REQUEST_BODY_ERR_MSG_CODE, new String[]{COMPETENCY_PROPOSAL_APPROVE_OPERATION}, Locale.getDefault());
            throw new BadRequestException(errorMessage);
        }

        // -- Basic info --
        String userId = authentication.getName();
        UUID userUUID = UUID.fromString(userId);

        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC).withOffsetSameInstant(ZoneOffset.ofHours(8));

        List<Long> proposalIdList = selectedProposalParticipantIds.stream().map(ProposalParticipantId::getProposalId).toList();
        Set<Long> selectedProposalIds = new HashSet<>(proposalIdList);

        List<ProposalParticipantDto> allParticipantList = proposalParticipantService.getAllProposalParticipants();

        // -- Check conflicts --
        Set<Long> seen = new HashSet<>();
        Set<Long> duplicates = proposalIdList.stream()
                .filter(id -> !seen.add(id))
                .collect(Collectors.toSet());

        if (!duplicates.isEmpty()) { // multiple proposals are approved within the same collaboration
            String errorTitle = messageSource.getMessage(PROPOSAL_APPROVAL_CONFLICT_ERR_TITLE_CODE, null, Locale.getDefault());
            String errorMessage = messageSource.getMessage(PROPOSAL_APPROVAL_CONFLICT_ERR_MSG_CODE, new String[]{duplicates.toString()}, Locale.getDefault());
            throw new BadRequestException(errorTitle, errorMessage);
        }

        // -- Convert approved proposal into competency; delete all rejected --
        List<CompetencyProposalDto> selectedCompetencyProposalDtos = competencyProposalService.getAllByIdIn(selectedProposalParticipantIds);
        Map<ProposalParticipantId, CompetencyProposalDto> selectedCompetencyProposalDtoMap = selectedCompetencyProposalDtos.stream()
                .collect(Collectors.toMap(
                        CompetencyProposalDto::getId,
                        Function.identity()
                ));

        List<CompetencyDto> competencyDtoToBeAdded = selectedCompetencyProposalDtos.stream().map(dto -> {
            CompetencyDto competencyDto = new CompetencyDto();
            competencyDto.setName(dto.getName().trim());
            competencyDto.setDescription(dto.getDescription() == null ? null : dto.getDescription().trim());

            competencyDto.setDeleted(false);
            competencyDto.setCreatedAt(now);
            competencyDto.setCreatedBy(dto.getId().getStaffId());
            competencyDto.setUpdatedAt(now);
            competencyDto.setUpdatedBy(userUUID);

            return competencyDto;
        }).toList();

        List<CompetencyDto> competencyDtoCreated = competencyService.createAll(competencyDtoToBeAdded); // convert proposal to competency
        Map<String, CompetencyDto> competencyCreatedMap = competencyDtoCreated.stream()
                .collect(Collectors.toMap(
                        CompetencyDto::getName,
                        Function.identity()
                ));

        // -- assign comp tags to newly created competency dto
        List<CompetencyCompTagProposalDto> existingCompetencyCompTagProposalDtos = competencyCompTagProposalService
                .findAllByProposalIdIn(selectedProposalIds);
        Map<Long, CompTagDto> existingCompTagMap = existingCompetencyCompTagProposalDtos.stream()
                .collect(Collectors.toMap(
                        cctp -> cctp.getId().getCompTagId(),
                        CompetencyCompTagProposalDto::getCompTag,
                        (first, second) -> first
                ));
        Set<Long> existingCompTagIds = existingCompTagMap.keySet();

        Map<CompetencyDto, Set<Long>> competencyCompTagMap = selectedCompetencyProposalDtos.stream() // map assigned tag list with respective newly created competency dto
                .collect(Collectors.toMap(
                        dto -> competencyCreatedMap.get(dto.getName()),
                        dto ->
                                existingCompetencyCompTagProposalDtos.stream()
                                        .filter(cctp -> Objects.equals(cctp.getId().getProposalId(), dto.getId().getProposalId())
                                                && Objects.equals(cctp.getId().getStaffId(), dto.getId().getStaffId()))
                                        .map(cctp -> {
                                            return cctp.getId().getCompTagId();
                                        })
                                        .collect(Collectors.toSet())
                ));

        List<CompetencyCompTagDto> competencyCompTagDtos = new ArrayList<>();
        competencyCompTagMap.forEach((competencyDto, compTagIds) -> {
            List<CompetencyCompTagDto> dtos = compTagIds.stream().map(compTagId -> {
                        CompetencyCompTagDto dto = new CompetencyCompTagDto();

                        dto.setId(new CompetencyCompTagId(competencyDto.getId(), compTagId));
                        dto.setCompetency(competencyDto);
                        dto.setCompTag(existingCompTagMap.get(compTagId));

                        dto.setCreatedAt(now);
                        dto.setCreatedBy(competencyDto.getCreatedBy());
                        dto.setUpdatedAt(now);
                        dto.setUpdatedBy(userUUID);

                        return dto;
                    })
                    .toList();

            competencyCompTagDtos.addAll(dtos);
        });

        if (!competencyCompTagDtos.isEmpty()) {
            competencyCompTagService.createAll(competencyCompTagDtos); // create competency compTag records
        }

        // -- delete relevant records --
        if (!existingCompTagIds.isEmpty()) {
            //remove all competency comp tag proposal
            competencyCompTagProposalService.deleteAllByProposalIdIn(selectedProposalIds);

            // check if compTags are used by other competencies
            Set<Long> stillUsedInCompetency = competencyCompTagService.findAllByCompTagIdIn(existingCompTagIds)
                    .stream().map(cct -> cct.getId().getCompTagId()).collect(Collectors.toSet());

            Set<Long> stillUsedInCompetencyProposal = competencyCompTagProposalService.getByCompTagIdIn(existingCompTagIds)
                    .stream().map(cctp -> cctp.getId().getCompTagId()).collect(Collectors.toSet());

            Set<Long> compTagToBeDeleted = new HashSet<>(existingCompTagIds);
            compTagToBeDeleted.removeAll(stillUsedInCompetency);
            compTagToBeDeleted.removeAll(stillUsedInCompetencyProposal);

            // delete comp tag if no longer being used
            if (!compTagToBeDeleted.isEmpty()) {
                compTagService.deleteAllByIdIn(compTagToBeDeleted, userUUID);
            }
        }

        // remove granted access
        this.bulkRemoveAccessGranted(selectedProposalIds, null, allParticipantList);

        competencyProposalService.deleteAllByProposalIdIn(selectedProposalIds); // delete all competency proposal records

        proposalParticipantService.deleteAllByProposalIdIn(selectedProposalIds); // remove participation

        // approve proposal directly
        proposalService.updateProposalStatusByIdIn(selectedProposalIds, ProposalStatus.APPROVED, userUUID);

        // -- send email --
        List<ProposalParticipantDto> selectedParticipantList = allParticipantList.stream()
                .filter(participantDto -> selectedProposalIds.contains(participantDto.getId().getProposalId())).toList();
        Optional<StaffDto> updatedBy = allParticipantList.stream()
                .filter(dto -> Objects.equals(dto.getId().getStaffId(), userUUID))
                .findAny()
                .map(ProposalParticipantDto::getStaff);

        selectedCompetencyProposalDtoMap.values().forEach(dto -> {
            List<String> assignedCompTags = existingCompetencyCompTagProposalDtos.stream().filter(
                            cctp -> Objects.equals(cctp.getId().getProposalId(), dto.getId().getProposalId())
                                    && Objects.equals(cctp.getId().getStaffId(), dto.getId().getStaffId()))
                    .map(cctp -> cctp.getCompTag().getTag())
                    .toList();

            for (ProposalParticipantDto recipient : selectedParticipantList) {
                if (Objects.equals(recipient.getId().getProposalId(), dto.getId().getProposalId())) { // same proposal
                    if (Objects.equals(recipient.getProposalRole(), ProposalRole.REVIEWER) || Objects.equals(recipient.getId(), dto.getId())) { // reviewer or owner
                        emailService.sendCompetencyProposalApprovedEmail( // send proposal approved email
                                recipient.getStaff().getEmail(),
                                dto.getName().trim(),
                                dto.getDescription() == null ? null : dto.getDescription().trim(),
                                assignedCompTags,
                                updatedBy.isPresent() ? updatedBy.get().getEmail() : "-",
                                Locale.getDefault()
                        );
                    } else if (Objects.equals(recipient.getProposalRole(), ProposalRole.PROPOSER) && !Objects.equals(recipient.getId(), dto.getId())) { // proposer and not owner
                        emailService.sendCompetencyProposalRejectedEmail( // proposal rejected email
                                recipient.getStaff().getEmail(),
                                dto.getName().trim(),
                                dto.getDescription() == null ? null : dto.getDescription().trim(),
                                assignedCompTags,
                                updatedBy.isPresent() ? updatedBy.get().getEmail() : "-",
                                Locale.getDefault()
                        );
                    }
                }
            }
        });

        return ResponseEntity.ok(Map.of(
                "message", messageSource.getMessage(COMPETENCY_PROPOSAL_APPROVE_OK, null, Locale.getDefault())
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

        roleAuthorityService.createAll(roleAuthorityDtos); // assign access
    }

    private void bulkRemoveAccessGranted(Set<Long> selectedProposalIds, Set<ProposalParticipantId> proposalParticipantIds, List<ProposalParticipantDto> allParticipantList) {
        Long authorityId = authorityService.findByName(AuthorityName.CAN_PROPOSE_ROLE_COMPETENCIES).getId();

        if (selectedProposalIds != null) {
            Set<Long> roleIdsInSelectedProposals = allParticipantList.stream()
                    .filter(dto -> selectedProposalIds.contains(dto.getId().getProposalId())
                            && Objects.equals(dto.getProposalRole(), ProposalRole.PROPOSER))
                    .map(dto -> dto.getStaff().getRole().getId())
                    .collect(Collectors.toSet());

            Set<Long> roleIdsInOtherProposals = allParticipantList.stream()
                    .filter(dto -> !selectedProposalIds.contains(dto.getId().getProposalId())
                            && Objects.equals(dto.getProposalRole(), ProposalRole.PROPOSER))
                    .map(dto -> dto.getStaff().getRole().getId())
                    .collect(Collectors.toSet());

            Set<Long> uniqueRoleIds = roleIdsInSelectedProposals.stream()
                    .filter(id -> !roleIdsInOtherProposals.contains(id))
                    .collect(Collectors.toSet());

            Set<RoleAuthorityId> roleAuthorityIds = uniqueRoleIds.stream()
                    .map(id -> new RoleAuthorityId(id, authorityId))
                    .collect(Collectors.toSet());

            roleAuthorityService.deleteAllByIdIn(roleAuthorityIds); // remove granted access
        }

        if (proposalParticipantIds != null) {
            Set<Long> roleIdsInSelectedProposals = allParticipantList.stream()
                    .filter(dto -> proposalParticipantIds.contains(dto.getId())
                            && Objects.equals(dto.getProposalRole(), ProposalRole.PROPOSER))
                    .map(dto -> dto.getStaff().getRole().getId())
                    .collect(Collectors.toSet());

            Set<Long> roleIdsInOtherProposals = allParticipantList.stream()
                    .filter(dto -> !proposalParticipantIds.contains(dto.getId())
                            && Objects.equals(dto.getProposalRole(), ProposalRole.PROPOSER))
                    .map(dto -> dto.getStaff().getRole().getId())
                    .collect(Collectors.toSet());

            Set<Long> uniqueRoleIds = roleIdsInSelectedProposals.stream()
                    .filter(id -> !roleIdsInOtherProposals.contains(id))
                    .collect(Collectors.toSet());

            Set<RoleAuthorityId> roleAuthorityIds = uniqueRoleIds.stream()
                    .map(id -> new RoleAuthorityId(id, authorityId))
                    .collect(Collectors.toSet());

            roleAuthorityService.deleteAllByIdIn(roleAuthorityIds); // remove granted access
        }
    }

    private void updateAccessControlForSingleRole(Long roleId, Long selectedProposalId, List<ProposalParticipantDto> allParticipantList) {

        Set<Long> roleIdsInOtherProposals = allParticipantList.stream()
                .filter(dto -> !Objects.equals(dto.getId().getProposalId(), selectedProposalId)
                        && Objects.equals(dto.getProposalRole(), ProposalRole.PROPOSER))
                .map(dto -> dto.getStaff().getRole().getId())
                .collect(Collectors.toSet());

        Long authorityId = authorityService.findByName(AuthorityName.CAN_PROPOSE_ROLE_COMPETENCIES).getId();

        RoleAuthorityId roleAuthorityId = new RoleAuthorityId(roleId, authorityId);

        if (!roleIdsInOtherProposals.contains(roleId)) {
            roleAuthorityService.deleteById(roleAuthorityId);
        }
    }
}