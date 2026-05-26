package com.tbm.careerpathlearning.service.impl;

import com.tbm.careerpathlearning.dto.*;
import com.tbm.careerpathlearning.exception.BadRequestException;
import com.tbm.careerpathlearning.exception.DataAccessException;
import com.tbm.careerpathlearning.mapper.AppMapper;
import com.tbm.careerpathlearning.model.RoleCompetencyProposal;
import com.tbm.careerpathlearning.model.RoleCompetencyProposalId;
import com.tbm.careerpathlearning.repository.RoleCompetencyProposalRepository;
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
public class RoleCompetencyProposalServiceImpl implements RoleCompetencyProposalService {

    @Autowired
    private AppMapper appMapper;

    @Autowired
    private MessageSource messageSource;

    @Autowired
    private RoleCompetencyProposalRepository roleCompetencyProposalRepository;

    @Autowired
    private ProposalService proposalService;

    @Autowired
    private StaffService staffService;

    @Autowired
    private RoleService roleService;

    private static final String RESULT_NOT_FOUND_ERR_MSG_CODE = "database.result.not.found.err.msg";

    private static final String INVALID_DATA_ERR_MSG_CODE = "invalid.data.err.msg";

    private static final String RECORD_REDUNDANT_ERR_TITLE_CODE = "database.record.redundant.err.title";

    private static final String RECORD_REDUNDANT_ERR_MSG_CODE = "database.record.redundant.err.msg";

    private static final String CREATE_ROLE_COMPETENCY_PROPOSAL = "Competency Assignment Proposal Creation";

    private static final String UPDATE_OPERATION = "Update Proposal";

    @Override
    public RoleCompetencyProposalDto getById(RoleCompetencyProposalId id) {
        RoleCompetencyProposal competency = roleCompetencyProposalRepository.findById(id)
                .orElseThrow(() -> new DataAccessException(
                        messageSource.getMessage(RESULT_NOT_FOUND_ERR_MSG_CODE, null, Locale.getDefault())
                ));
        return appMapper.toDto(competency);
    }


    @Override
    public List<RoleCompetencyProposalDto> getAllByProposalId(Long proposalId) {
        return roleCompetencyProposalRepository.findAllByProposal_Id(proposalId).stream().map(appMapper::toDto).collect(Collectors.toList());
    }

    @Override
    public List<RoleCompetencyProposalDto> getAllByProposalIdIn(Set<Long> proposalId) {
        return roleCompetencyProposalRepository.findAllByProposal_IdIn(proposalId).stream().map(appMapper::toDto).collect(Collectors.toList());
    }

    @Override
    public List<RoleCompetencyProposalDto> getAllByIdIn(Set<RoleCompetencyProposalId> ids) {
        return roleCompetencyProposalRepository.findAllById(ids).stream()
                .map(dto -> appMapper.toDto(dto)).collect(Collectors.toList());
    }

    @Transactional
    @Override
    public List<RoleCompetencyProposalDto> createAll(List<RoleCompetencyProposalDto> dtos) {
        if (dtos == null || dtos.isEmpty()) {
            String errorMessage = messageSource.getMessage(INVALID_DATA_ERR_MSG_CODE, new String[]{CREATE_ROLE_COMPETENCY_PROPOSAL}, Locale.getDefault());

            throw new BadRequestException(errorMessage);
        }

        Set<Long> proposalIds = dtos.stream().map(dto ->
                dto.getId().getProposalId()).collect(Collectors.toSet());

        Set<Long> roleIds = dtos.stream().map(dto -> dto.getId().getRoleId()).collect(Collectors.toSet());

        Set<UUID> staffIds = dtos.stream().map(dto ->
                dto.getId().getStaffId()).collect(Collectors.toSet());

        Set<RoleCompetencyProposalId> roleCompetencyProposalIds = dtos.stream()
                .map(RoleCompetencyProposalDto::getId).collect(Collectors.toSet());

        Set<Long> existingProposalIds = proposalService.getAllProposalsByIdIn(proposalIds).stream()
                .map(ProposalDto::getId).collect(Collectors.toSet());

        Set<Long> existingRoleIds = roleService.getAllByIsDeletedIsFalseAndIdIn(roleIds)
                .stream().map(RoleDto::getId).collect(Collectors.toSet());

        Set<UUID> existingStaffIds = staffService.findAllByIdIn(staffIds)
                .stream().map(StaffDto::getId).collect(Collectors.toSet());

        Set<RoleCompetencyProposalId> existingRoleCompetencyProposalIds = this.getAllByIdIn(roleCompetencyProposalIds).stream()
                .map(RoleCompetencyProposalDto::getId).collect(Collectors.toSet());

        List<RoleCompetencyProposal> entities = dtos.stream().map(dto -> {
            Long proposalId = dto.getId().getProposalId();
            Long roleId = dto.getId().getRoleId();
            UUID staffId = dto.getId().getStaffId();

            if (!existingProposalIds.contains(proposalId)) {
                String errorMessage = messageSource.getMessage(RESULT_NOT_FOUND_ERR_MSG_CODE, null, Locale.getDefault());
                throw new DataAccessException(errorMessage);
            }

            if (!existingRoleIds.contains(roleId)) {
                String errorMessage = messageSource.getMessage(RESULT_NOT_FOUND_ERR_MSG_CODE, null, Locale.getDefault());
                throw new DataAccessException(errorMessage);
            }

            if (!existingStaffIds.contains(staffId)) {
                String errorMessage = messageSource.getMessage(RESULT_NOT_FOUND_ERR_MSG_CODE, null, Locale.getDefault());
                throw new DataAccessException(errorMessage);
            }

            if (existingRoleCompetencyProposalIds.contains(dto.getId())) {
                String errorTitle = messageSource.getMessage(RECORD_REDUNDANT_ERR_TITLE_CODE, null, Locale.getDefault());
                String errorMessage = messageSource.getMessage(RECORD_REDUNDANT_ERR_MSG_CODE,
                        null,
                        Locale.getDefault());

                throw new DataAccessException(errorTitle, errorMessage);
            }

            if (dto.getDescription() != null && dto.getDescription().trim().length() > 1000) {
                String errorMessage = messageSource.getMessage(INVALID_DATA_ERR_MSG_CODE, new String[]{CREATE_ROLE_COMPETENCY_PROPOSAL}, Locale.getDefault());

                throw new BadRequestException(errorMessage);
            }

            return appMapper.toEntity(dto);
        }).toList();

        return roleCompetencyProposalRepository.saveAll(entities).stream().map(appMapper::toDto).collect(Collectors.toList());
    }

    @Transactional
    @Override
    public RoleCompetencyProposalDto update(RoleCompetencyProposalId id, RoleCompetencyProposalDto dto) {
        RoleCompetencyProposalDto dtoToBeUpdated = this.getById(id);

        if (id == null
                || dto.getDescription() != null && dto.getDescription().trim().length() > 1000) {
            String errorMessage = messageSource.getMessage(INVALID_DATA_ERR_MSG_CODE,
                    new String[]{UPDATE_OPERATION}, Locale.getDefault());

            throw new BadRequestException(errorMessage);
        }

        dtoToBeUpdated.setDescription(dto.getDescription() == null ? null : dto.getDescription().trim());
        dtoToBeUpdated.setUpdatedAt(dto.getUpdatedAt());
        dtoToBeUpdated.setUpdatedBy(dto.getUpdatedBy());

        return appMapper.toDto(
                roleCompetencyProposalRepository.save(appMapper.toEntity(dtoToBeUpdated))
        );
    }

    @Transactional
    @Override
    public RoleCompetencyProposalDto findAndDeleteById(RoleCompetencyProposalId id) {
        RoleCompetencyProposalDto dtoToBeDeleted = this.getById(id);
        roleCompetencyProposalRepository.deleteById(id);

        return dtoToBeDeleted;
    }

    @Transactional
    @Override
    public List<RoleCompetencyProposalDto> findAndDeleteByIdIn(Set<RoleCompetencyProposalId> ids) {
        List<RoleCompetencyProposalDto> dtoToBeDeleted = this.getAllByIdIn(ids);
        roleCompetencyProposalRepository.deleteAllById(ids);

        return dtoToBeDeleted;
    }

    @Transactional
    @Override
    public List<RoleCompetencyProposalDto> findAndDeleteByProposalId(Long proposalId) {
        List<RoleCompetencyProposal> dtoToBeDeleted = roleCompetencyProposalRepository.findAllByProposal_Id(proposalId);
        roleCompetencyProposalRepository.deleteAll(dtoToBeDeleted);

        return dtoToBeDeleted.stream().map(appMapper::toDto).collect(Collectors.toList());
    }

    @Transactional
    @Override
    public List<RoleCompetencyProposalDto> findAndDeleteByProposalIdIn(Set<Long> proposalIds) {
        List<RoleCompetencyProposal> dtoToBeDeleted = roleCompetencyProposalRepository.findAllByProposal_IdIn(proposalIds);
        roleCompetencyProposalRepository.deleteAll(dtoToBeDeleted);

        return dtoToBeDeleted.stream().map(appMapper::toDto).collect(Collectors.toList());
    }
}
