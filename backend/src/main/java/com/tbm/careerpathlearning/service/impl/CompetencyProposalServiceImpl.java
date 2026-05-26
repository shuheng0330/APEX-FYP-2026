package com.tbm.careerpathlearning.service.impl;

import com.tbm.careerpathlearning.dto.*;
import com.tbm.careerpathlearning.exception.BadRequestException;
import com.tbm.careerpathlearning.exception.DataAccessException;
import com.tbm.careerpathlearning.mapper.AppMapper;
import com.tbm.careerpathlearning.model.CompetencyProposal;
import com.tbm.careerpathlearning.model.ProposalParticipantId;
import com.tbm.careerpathlearning.repository.CompetencyProposalRepository;
import com.tbm.careerpathlearning.service.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.MessageSource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

@Service
public class CompetencyProposalServiceImpl implements CompetencyProposalService {

    @Autowired
    private AppMapper appMapper;

    @Autowired
    private MessageSource messageSource;

    @Autowired
    private ValidationService validationService;

    @Autowired
    private CompetencyProposalRepository competencyProposalRepository;

    @Autowired
    private ProposalService proposalService;

    @Autowired
    private StaffService staffService;

    private static final String RESULT_NOT_FOUND_ERR_MSG_CODE = "database.result.not.found.err.msg";

    private static final String INVALID_DATA_ERR_MSG_CODE = "invalid.data.err.msg";

    private static final String RECORD_REDUNDANT_ERR_TITLE_CODE = "database.record.redundant.err.title";

    private static final String RECORD_REDUNDANT_ERR_MSG_CODE = "database.record.redundant.err.msg";

    private static final String CREATE_COMPETENCY_PROPOSAL = "Competency Proposal Creation";

    private static final String UPDATE_OPERATION = "Update proposal";

    @Override
    public List<CompetencyProposalDto> getAll() {
        return competencyProposalRepository.findAll().stream().map(appMapper::toDto).collect(Collectors.toList());
    }

    @Override
    public CompetencyProposalDto getById(ProposalParticipantId id) {
        CompetencyProposal competency = competencyProposalRepository.findById(id)
                .orElseThrow(() -> new DataAccessException(
                        messageSource.getMessage(RESULT_NOT_FOUND_ERR_MSG_CODE, null, Locale.getDefault())
                ));
        return appMapper.toDto(competency);
    }

    @Override
    public List<CompetencyProposalDto> getAllByProposalId(Long proposalId) {
        return competencyProposalRepository.findAllByProposal_Id(proposalId).stream().map(appMapper::toDto).collect(Collectors.toList());
    }

    @Override
    public List<CompetencyProposalDto> getAllByStaffId(UUID staffId) {
        return competencyProposalRepository.findAllByStaff_Id(staffId).stream().map(appMapper::toDto).collect(Collectors.toList());
    }

    @Override
    public List<CompetencyProposalDto> getAllByProposalIdIn(Set<Long> proposalId) {
        return competencyProposalRepository.findAllByProposalIdIn(proposalId).stream().map(appMapper::toDto).collect(Collectors.toList());
    }

    @Override
    public List<CompetencyProposalDto> getAllByIdIn(Set<ProposalParticipantId> ids) {
        return competencyProposalRepository.findAllById(ids).stream()
                .map(appMapper::toDto).collect(Collectors.toList());
    }

    @Transactional
    @Override
    public List<CompetencyProposalDto> createCompetencyProposals(List<CompetencyProposalDto> dtos) {
        if (dtos == null || dtos.isEmpty()) {
            String errorMessage = messageSource.getMessage(INVALID_DATA_ERR_MSG_CODE, new String[]{CREATE_COMPETENCY_PROPOSAL}, Locale.getDefault());

            throw new BadRequestException(errorMessage);
        }

        Set<Long> proposalIds = dtos.stream().map(dto ->
                dto.getId().getProposalId()).collect(Collectors.toSet());

        Set<UUID> staffIds = dtos.stream().map(dto ->
                dto.getId().getStaffId()).collect(Collectors.toSet());

        Set<ProposalParticipantId> proposalParticipantIds = dtos.stream()
                .map(CompetencyProposalDto::getId).collect(Collectors.toSet());

        Set<Long> existingProposalIds = proposalService.getAllProposalsByIdIn(proposalIds).stream()
                .map(ProposalDto::getId).collect(Collectors.toSet());

        Set<UUID> existingStaffIds = staffService.findAllByIdIn(staffIds)
                .stream().map(StaffDto::getId).collect(Collectors.toSet());

        Set<ProposalParticipantId> existingProposalParticipantIds = this.getAllByIdIn(proposalParticipantIds).stream()
                .map(CompetencyProposalDto::getId).collect(Collectors.toSet());

        for (CompetencyProposalDto dto : dtos) {
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

            if (validationService.isNullOrBlank(dto.getName())
                    || dto.getName().trim().length() > 255
                    || (dto.getDescription() != null && dto.getDescription().trim().length() > 1000)) {
                String errorMessage = messageSource.getMessage(INVALID_DATA_ERR_MSG_CODE,
                        new String[]{UPDATE_OPERATION}, Locale.getDefault());

                throw new BadRequestException(errorMessage);
            }
        }

        List<CompetencyProposal> entities = dtos.stream().map(dto -> appMapper.toEntity(dto)).toList();

        return competencyProposalRepository.saveAll(entities).stream().map(appMapper::toDto).collect(Collectors.toList());

    }

    @Transactional
    @Override
    public CompetencyProposalDto updateCompetencyProposal(ProposalParticipantId id, CompetencyProposalDto dto) {
        CompetencyProposalDto competencyProposalDtoToBeUpdated = this.getById(id);

        if (validationService.isNullOrBlank(dto.getName())
                || dto.getName().trim().length() > 255
                || (dto.getDescription() != null && dto.getDescription().trim().length() > 1000)) {
            String errorMessage = messageSource.getMessage(INVALID_DATA_ERR_MSG_CODE,
                    new String[]{UPDATE_OPERATION}, Locale.getDefault());

            throw new BadRequestException(errorMessage);
        }

        competencyProposalDtoToBeUpdated.setName(dto.getName());
        competencyProposalDtoToBeUpdated.setDescription(dto.getDescription());
        competencyProposalDtoToBeUpdated.setUpdatedAt(dto.getUpdatedAt());
        competencyProposalDtoToBeUpdated.setUpdatedBy(dto.getUpdatedBy());

        return appMapper.toDto(
                competencyProposalRepository.save(appMapper.toEntity(competencyProposalDtoToBeUpdated))
        );
    }

    @Transactional
    @Override
    public void delete(ProposalParticipantId id) {
        competencyProposalRepository.deleteById(id);
    }

    @Transactional
    @Override
    public void deleteAllByIdIn(Set<ProposalParticipantId> proposalParticipantIds) {
        competencyProposalRepository.deleteAllById(proposalParticipantIds);
    }

    @Transactional
    @Override
    public void deleteAllByProposalId(Long proposalId) {
        competencyProposalRepository.deleteByProposal_Id(proposalId);
    }

    @Transactional
    @Override
    public List<CompetencyProposalDto> findAndDeleteAllByProposalIdIn(Set<Long> proposalIds) {
        List<CompetencyProposal> toDelete = competencyProposalRepository.findAllByProposal_IdIn(proposalIds);
        competencyProposalRepository.deleteAll(toDelete);
        return toDelete.stream().map(appMapper::toDto).collect(Collectors.toList());
    }

    @Transactional
    @Override
    public void deleteAllByProposalIdIn(Set<Long> proposalId) {
        competencyProposalRepository.deleteAllByProposalIdIn(proposalId);
    }

}
