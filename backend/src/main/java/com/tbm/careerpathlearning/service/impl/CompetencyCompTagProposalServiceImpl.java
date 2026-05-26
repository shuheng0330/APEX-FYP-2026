package com.tbm.careerpathlearning.service.impl;

import com.tbm.careerpathlearning.dto.*;
import com.tbm.careerpathlearning.exception.BadRequestException;
import com.tbm.careerpathlearning.exception.DataAccessException;
import com.tbm.careerpathlearning.mapper.AppMapper;
import com.tbm.careerpathlearning.model.*;
import com.tbm.careerpathlearning.repository.CompetencyCompTagProposalRepository;
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
public class CompetencyCompTagProposalServiceImpl implements CompetencyCompTagProposalService {

    @Autowired
    private CompTagService compTagService;

    @Autowired
    private AppMapper appMapper;

    @Autowired
    private MessageSource messageSource;

    @Autowired
    private CompetencyCompTagProposalRepository competencyCompTagProposalRepository;

    @Autowired
    private ProposalService proposalService;

    @Autowired
    private StaffService staffService;

    private static final String RESULT_NOT_FOUND_ERR_MSG_CODE = "database.result.not.found.err.msg";

    private static final String RECORD_REDUNDANT_ERR_TITLE_CODE = "database.record.redundant.err.title";

    private static final String RECORD_REDUNDANT_ERR_MSG_CODE = "database.record.redundant.err.msg";

    private static final String INVALID_DATA_ERR_MSG_CODE = "invalid.data.err.msg";

    private static final String ASSIGN_COMP_TAG = "Assigning tags to the competency";

    @Override
    public List<CompetencyCompTagProposalDto> getAll() {
        return competencyCompTagProposalRepository.findAll().stream().map(appMapper::toDto).toList();
    }

    @Override
    public List<CompetencyCompTagProposalDto> findAllByIdIn(Set<CompetencyCompTagProposalId> ids) {
        return competencyCompTagProposalRepository.findAllById(ids).stream()
                .map(appMapper::toDto).collect(Collectors.toList());
    }

    @Override
    public List<CompetencyCompTagProposalDto> findAllByProposalId(Long proposalId) {
        return competencyCompTagProposalRepository.findAllByProposal_Id(proposalId).stream()
                .map(appMapper::toDto).collect(Collectors.toList());
    }

    @Override
    public List<CompetencyCompTagProposalDto> findAllByProposalIdIn(Set<Long> proposalIds) {
        return competencyCompTagProposalRepository.findAllByProposal_IdIn(proposalIds).stream()
                .map(appMapper::toDto).collect(Collectors.toList());
    }

    @Override
    public List<CompetencyCompTagProposalDto> getByCompTagIdIn(Set<Long> compTagIds) {
        return competencyCompTagProposalRepository.findAllByCompTagIdIn(compTagIds).stream()
                .map(appMapper::toDto).collect(Collectors.toList());
    }

    @Override
    public List<CompetencyCompTagProposalDto> getByProposalStaffId(Long proposalId, UUID staffId) {
        return competencyCompTagProposalRepository.findByProposalIdAndStaffId(proposalId, staffId).stream()
                .map(appMapper::toDto).collect(Collectors.toList());
    }

    @Override
    public List<CompetencyCompTagProposalDto> getByProposalStaffIdIn(Set<Long> proposalIds, Set<UUID> staffIds) {
        return competencyCompTagProposalRepository.findByProposalIdInAndStaffIdIn(proposalIds, staffIds).stream()
                .map(appMapper::toDto).collect(Collectors.toList());
    }

    @Transactional
    @Override
    public List<CompetencyCompTagProposalDto> createCompetencyCompTagProposals(List<CompetencyCompTagProposalDto> dtos) {
        if (dtos == null || dtos.isEmpty()) {
            String errorMessage = messageSource.getMessage(INVALID_DATA_ERR_MSG_CODE, new String[]{ASSIGN_COMP_TAG}, Locale.getDefault());

            throw new BadRequestException(errorMessage);
        }

        Set<Long> proposalIds = dtos.stream()
                .map(dto -> dto.getId().getProposalId()).collect(Collectors.toSet());

        Set<UUID> staffIds = dtos.stream()
                .map(dto -> dto.getId().getStaffId()).collect(Collectors.toSet());

        Set<Long> compTagIds = dtos.stream()
                .map(dto -> dto.getId().getCompTagId()).collect(Collectors.toSet());

        Set<CompetencyCompTagProposalId> ids = dtos.stream()
                .map(CompetencyCompTagProposalDto::getId).collect(Collectors.toSet());

        Set<Long> existingProposalIds = proposalService.getAllProposalsByIdIn(proposalIds).stream()
                .map(ProposalDto::getId).collect(Collectors.toSet());

        Set<UUID> existingStaffIds = staffService.findAllByIdIn(staffIds).stream()
                .map(StaffDto::getId).collect(Collectors.toSet());

        Set<Long> existingCompTagIds = compTagService.findAllByIsDeletedIsFalseAndIdIn(compTagIds)
                .stream().map(CompTagDto::getId).collect(Collectors.toSet());

        Set<CompetencyCompTagProposalId> existingIds = this.findAllByIdIn(ids).stream()
                .map(CompetencyCompTagProposalDto::getId).collect(Collectors.toSet());

        for (CompetencyCompTagProposalDto dto : dtos) {
            Long proposalId = dto.getId().getProposalId();
            UUID staffId = dto.getId().getStaffId();
            Long compTagId = dto.getId().getCompTagId();

            if (!existingProposalIds.contains(proposalId)) {
                String errorMessage = messageSource.getMessage(RESULT_NOT_FOUND_ERR_MSG_CODE, null, Locale.getDefault());
                throw new DataAccessException(errorMessage);
            }

            if (!existingStaffIds.contains(staffId)) {
                String errorMessage = messageSource.getMessage(RESULT_NOT_FOUND_ERR_MSG_CODE, null, Locale.getDefault());
                throw new DataAccessException(errorMessage);
            }

            if (!existingCompTagIds.contains(compTagId)) {
                String errorMessage = messageSource.getMessage(RESULT_NOT_FOUND_ERR_MSG_CODE, null, Locale.getDefault());
                throw new DataAccessException(errorMessage);
            }

            if (existingIds.contains(dto.getId())) {
                String errorTitle = messageSource.getMessage(RECORD_REDUNDANT_ERR_TITLE_CODE, null, Locale.getDefault());
                String errorMessage = messageSource.getMessage(RECORD_REDUNDANT_ERR_MSG_CODE,
                        null,
                        Locale.getDefault());

                throw new DataAccessException(errorTitle, errorMessage);
            }
        }

        List<CompetencyCompTagProposal> entities = dtos.stream()
                .map(appMapper::toEntity).collect(Collectors.toList());

        return competencyCompTagProposalRepository.saveAll(entities).stream()
                .map(appMapper::toDto).collect(Collectors.toList());
    }


    @Transactional
    @Override
    public void deleteByProposalStaffIdAndCompTagIdIn(Long proposalId, UUID staffId, Set<Long> compTagIds) {
        competencyCompTagProposalRepository.deleteByProposalIdAndStaffIdAndCompTagIdIn(proposalId, staffId, compTagIds);
    }

    @Transactional
    @Override
    public void deleteByProposalStaffIdInAndCompTagIdIn(Set<Long> proposalIds, Set<UUID> staffIds, Set<Long> compTagIds) {
        competencyCompTagProposalRepository.deleteByProposalIdInAndStaffIdInAndCompTagIdIn(proposalIds, staffIds, compTagIds);
    }

    @Transactional
    @Override
    public void deleteAllByIdIn(Set<CompetencyCompTagProposalId> ids) {
        competencyCompTagProposalRepository.deleteAllById(ids);
    }

    @Transactional
    @Override
    public void deleteAllByProposalId(Long proposalId) {
        competencyCompTagProposalRepository.deleteAllByProposal_Id(proposalId);
    }

    @Transactional
    @Override
    public void deleteAllByProposalIdIn(Set<Long> proposalIds) {
        competencyCompTagProposalRepository.deleteAllByProposal_IdIn(proposalIds);
    }

    @Transactional
    @Override
    public List<CompetencyCompTagProposalDto> findAndDeleteAllByProposalIdIn(Set<Long> proposalIds) {
        List<CompetencyCompTagProposal> toDelete = competencyCompTagProposalRepository.findAllByProposal_IdIn(proposalIds);
        competencyCompTagProposalRepository.deleteAll(toDelete);
        return toDelete.stream().map(appMapper::toDto).collect(Collectors.toList());
    }
}
