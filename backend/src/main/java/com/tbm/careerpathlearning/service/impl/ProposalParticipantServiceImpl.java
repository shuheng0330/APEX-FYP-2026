package com.tbm.careerpathlearning.service.impl;

import com.tbm.careerpathlearning.dto.*;
import com.tbm.careerpathlearning.exception.BadRequestException;
import com.tbm.careerpathlearning.exception.DataAccessException;
import com.tbm.careerpathlearning.mapper.AppMapper;
import com.tbm.careerpathlearning.model.ProposalParticipant;
import com.tbm.careerpathlearning.model.ProposalParticipantId;
import com.tbm.careerpathlearning.repository.ProposalParticipantRepository;
import com.tbm.careerpathlearning.service.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.MessageSource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class ProposalParticipantServiceImpl implements ProposalParticipantService {

    @Autowired
    private ProposalParticipantRepository proposalParticipantRepository;

    @Autowired
    private AppMapper appMapper;

    @Autowired
    private MessageSource messageSource;

    @Autowired
    private ProposalService proposalService;

    @Autowired
    private StaffService staffService;

    private static final String RESULT_NOT_FOUND_ERR_MSG_CODE = "database.result.not.found.err.msg";

    private static final String RECORD_REDUNDANT_ERR_TITLE_CODE = "database.record.redundant.err.title";

    private static final String RECORD_REDUNDANT_ERR_MSG_CODE = "database.record.redundant.err.msg";

    private static final String INVALID_DATA_ERR_MSG_CODE = "invalid.data.err.msg";

    private static final String ASSIGN_PARTICIPANT = "Assigning collaborator to collaboration";

    @Override
    public List<ProposalParticipantDto> getAllProposalParticipants() {
        return proposalParticipantRepository.findAll().stream().map(appMapper::toDto).toList();
    }

    @Override
    public List<ProposalParticipantDto> getAllByIdIn(Set<ProposalParticipantId> proposalParticipantIds) {
        return proposalParticipantRepository.findAllById(proposalParticipantIds)
                .stream().map(appMapper::toDto).toList();
    }

    @Override
    public List<ProposalParticipantDto> getAllByProposalId(Long proposalId) {
        return proposalParticipantRepository.findAllByProposal_Id(proposalId).stream()
                .map(appMapper::toDto).collect(Collectors.toList());
    }

    @Override
    public List<ProposalParticipantDto> getAllByStaffId(UUID staffId) {
        return proposalParticipantRepository.findAllByStaff_Id(staffId).stream()
                .map(appMapper::toDto).collect(Collectors.toList());
    }

    @Override
    public List<ProposalParticipantDto> getAllByProposalIdIn(Set<Long> proposalIds) {
        return proposalParticipantRepository.findAllByProposal_IdIn(proposalIds).stream()
                .map(appMapper::toDto).collect(Collectors.toList());
    }

    @Override
    public List<ProposalParticipantDto> getByStaffRoleIdIn(Set<Long> roleIds) {
        return proposalParticipantRepository.findAllByStaff_Role_IdIn(roleIds).stream().map(appMapper::toDto).toList();
    }

    @Transactional
    @Override
    public List<ProposalParticipantDto> createProposalParticipants(List<ProposalParticipantDto> proposalParticipantDtoList) {
        if (proposalParticipantDtoList == null || proposalParticipantDtoList.isEmpty()) {
            String errorMessage = messageSource.getMessage(INVALID_DATA_ERR_MSG_CODE, new String[]{ASSIGN_PARTICIPANT}, Locale.getDefault());

            throw new BadRequestException(errorMessage);
        }

        Set<Long> proposalIds = proposalParticipantDtoList.stream()
                .map(dto -> dto.getId().getProposalId()).collect(Collectors.toSet());

        Set<UUID> staffIds = proposalParticipantDtoList.stream()
                .map(dto -> dto.getId().getStaffId()).collect(Collectors.toSet());

        Set<ProposalParticipantId> proposalParticipantIds = proposalParticipantDtoList.stream()
                .map(ProposalParticipantDto::getId).collect(Collectors.toSet());

        Set<Long> existingProposalIds = proposalService.getAllProposalsByIdIn(proposalIds).stream()
                .map(ProposalDto::getId).collect(Collectors.toSet());

        Set<UUID> existingStaffIds = staffService.findAllByIdIn(staffIds)
                .stream().map(StaffDto::getId).collect(Collectors.toSet());

        Set<ProposalParticipantId> existingProposalParticipantIds = this.getAllByIdIn(proposalParticipantIds).stream()
                .map(ProposalParticipantDto::getId).collect(Collectors.toSet());

        for (ProposalParticipantDto dto : proposalParticipantDtoList) {
            Long proposalId = dto.getId().getProposalId();
            UUID staffId = dto.getId().getStaffId();

            if (!existingProposalIds.contains(proposalId)) {
                String errorMessage = messageSource.getMessage(RESULT_NOT_FOUND_ERR_MSG_CODE, null, Locale.getDefault());
                throw new DataAccessException(errorMessage);
            }

            if (!existingStaffIds.contains(staffId)) {
                String errorMessage = messageSource.getMessage(RESULT_NOT_FOUND_ERR_MSG_CODE, null, Locale.getDefault());
                throw new DataAccessException(errorMessage);
            }

            if (existingProposalParticipantIds.contains(dto.getId())) {
                String errorTitle = messageSource.getMessage(RECORD_REDUNDANT_ERR_TITLE_CODE, null, Locale.getDefault());
                String errorMessage = messageSource.getMessage(RECORD_REDUNDANT_ERR_MSG_CODE,
                        null,
                        Locale.getDefault());

                throw new DataAccessException(errorTitle, errorMessage);
            }
        }

        List<ProposalParticipant> entities = proposalParticipantDtoList.stream()
                .map(appMapper::toEntity).collect(Collectors.toList());

        return proposalParticipantRepository.saveAll(entities).stream().map(appMapper::toDto).collect(Collectors.toList());
    }

    @Transactional
    @Override
    public void deleteById(ProposalParticipantId proposalParticipantId) {
        proposalParticipantRepository.deleteById(proposalParticipantId);
    }

    @Transactional
    @Override
    public ProposalParticipantDto findAndDeleteById(ProposalParticipantId proposalParticipantId) {
        ProposalParticipant participant = proposalParticipantRepository.getReferenceById(proposalParticipantId);
        proposalParticipantRepository.delete(participant);

        return appMapper.toDto(participant);
    }

    @Transactional
    @Override
    public List<ProposalParticipantDto> findAndDeleteByProposalId(Long proposalId) {
        List<ProposalParticipant> participant = proposalParticipantRepository.findAllByProposal_Id(proposalId);
        proposalParticipantRepository.deleteAll(participant);

        return participant.stream().map(appMapper::toDto).collect(Collectors.toList());
    }

    @Transactional
    @Override
    public List<ProposalParticipantDto> findAndDeleteByProposalIdIn(Set<Long> proposalIds) {
        List<ProposalParticipant> participant = proposalParticipantRepository.findAllByProposal_IdIn(proposalIds);
        proposalParticipantRepository.deleteAll(participant);

        return participant.stream().map(appMapper::toDto).collect(Collectors.toList());
    }

    @Transactional
    @Override
    public List<ProposalParticipantDto> findAndDeleteByIdIn(Set<ProposalParticipantId> ids) {
        List<ProposalParticipant> participant = proposalParticipantRepository.findAllById(ids);
        proposalParticipantRepository.deleteAll(participant);

        return participant.stream().map(appMapper::toDto).collect(Collectors.toList());
    }

    @Transactional
    @Override
    public void deleteAllByIdIn(Set<ProposalParticipantId> proposalParticipantIds) {
        proposalParticipantRepository.deleteAllByIdIn(proposalParticipantIds);
    }

    @Transactional
    @Override
    public void deleteAllByProposalId(Long proposalId) {
        proposalParticipantRepository.deleteAllByProposal_Id(proposalId);
    }

    @Transactional
    @Override
    public void deleteAllByProposalIdIn(Set<Long> proposalIds) {
        proposalParticipantRepository.deleteAllByProposalIdIn(proposalIds);
    }
}
