package com.tbm.careerpathlearning.service.impl;

import com.tbm.careerpathlearning.dto.*;
import com.tbm.careerpathlearning.exception.BadRequestException;
import com.tbm.careerpathlearning.exception.DataAccessException;
import com.tbm.careerpathlearning.mapper.AppMapper;
import com.tbm.careerpathlearning.model.*;
import com.tbm.careerpathlearning.repository.RoleCompetencyProposalItemRepository;
import com.tbm.careerpathlearning.service.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
public class RoleCompetencyProposalItemServiceImpl implements RoleCompetencyProposalItemService {

    private static final Logger logger = LoggerFactory.getLogger(RoleCompetencyProposalItemServiceImpl.class);

    @Autowired
    private AppMapper appMapper;

    @Autowired
    private MessageSource messageSource;

    @Autowired
    private ProposalService proposalService;

    @Autowired
    private StaffService staffService;

    @Autowired
    private RoleService roleService;

    @Autowired
    private RoleCompetencyProposalItemRepository roleCompetencyProposalItemRepository;

    @Autowired
    private CompetencyProposalService competencyProposalService;

    private static final String RESULT_NOT_FOUND_ERR_MSG_CODE = "database.result.not.found.err.msg";

    private static final String RECORD_REDUNDANT_ERR_TITLE_CODE = "database.record.redundant.err.title";

    private static final String RECORD_REDUNDANT_ERR_MSG_CODE = "database.record.redundant.err.msg";

    private static final String INVALID_DATA_ERR_MSG_CODE = "invalid.data.err.msg";

    private static final String CREATE_OPERATION = "Assigning competency to role";

    @Override
    public List<RoleCompetencyProposalItemDto> findAllByIdIn(Set<RoleCompetencyProposalItemId> ids) {
        return roleCompetencyProposalItemRepository.findAllById(ids).stream()
                .map(appMapper::toDto).collect(Collectors.toList());
    }

    @Override
    public List<RoleCompetencyProposalItemDto> findAllByProposalStaffId(Long proposalId, UUID staffId) {
        return roleCompetencyProposalItemRepository.findAllByProposal_IdAndStaff_Id(proposalId, staffId)
                .stream().map(appMapper::toDto).collect(Collectors.toList());
    }

    @Override
    public List<RoleCompetencyProposalItemDto> findAllByProposalIdIn(Set<Long> proposalIds) {
        return roleCompetencyProposalItemRepository.findAllByProposal_IdIn(proposalIds).stream()
                .map(appMapper::toDto).collect(Collectors.toList());
    }

    @Override
    public List<RoleCompetencyProposalItemDto> findAllByRoleCompetencyProposalId(Long proposalId, Long roleId, UUID staffId) {
        return roleCompetencyProposalItemRepository.findAllByProposal_IdAndRole_IdAndStaff_Id(proposalId, roleId, staffId)
                .stream().map(appMapper::toDto).collect(Collectors.toList());
    }

    @Transactional
    @Override
    public List<RoleCompetencyProposalItemDto> createAll(List<RoleCompetencyProposalItemDto> dtos) {
        if (dtos == null || dtos.isEmpty()) {
            String errorMessage = messageSource.getMessage(INVALID_DATA_ERR_MSG_CODE, new String[]{CREATE_OPERATION}, Locale.getDefault());

            throw new BadRequestException(errorMessage);
        }

        Set<Long> proposalIds = dtos.stream()
                .map(dto -> dto.getId().getProposalId()).collect(Collectors.toSet());

        Set<Long> roleIds = dtos.stream()
                .map(dto -> dto.getId().getRoleId()).collect(Collectors.toSet());

        Set<UUID> staffIds = dtos.stream()
                .map(dto -> dto.getId().getStaffId()).collect(Collectors.toSet());

        Set<ProposalParticipantId> competencyProposalIds = dtos.stream()
                .map(dto -> dto.getId().getCompetencyProposalId()).collect(Collectors.toSet());

        Set<RoleCompetencyProposalItemId> ids = dtos.stream()
                .map(RoleCompetencyProposalItemDto::getId).collect(Collectors.toSet());

        Set<Long> existingProposalIds = proposalService.getAllProposalsByIdIn(proposalIds).stream()
                .map(ProposalDto::getId).collect(Collectors.toSet());

        Set<Long> existingRoleIds = roleService.getAllByIsDeletedIsFalseAndIdIn(roleIds).stream()
                .map(RoleDto::getId).collect(Collectors.toSet());

        Set<UUID> existingStaffIds = staffService.findAllByIdIn(staffIds).stream()
                .map(StaffDto::getId).collect(Collectors.toSet());

        Set<ProposalParticipantId> existingCompetencyIds = competencyProposalService.getAllByIdIn(competencyProposalIds)
                .stream().map(CompetencyProposalDto::getId).collect(Collectors.toSet());

        Set<RoleCompetencyProposalItemId> existingIds = this.findAllByIdIn(ids).stream()
                .map(RoleCompetencyProposalItemDto::getId).collect(Collectors.toSet());

        List<RoleCompetencyProposalItem> entities = dtos.stream().map(dto -> {
            Long proposalId = dto.getId().getProposalId();
            Long roleId = dto.getId().getRoleId();
            UUID staffId = dto.getId().getStaffId();
            ProposalParticipantId competencyProposalId = dto.getId().getCompetencyProposalId();

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

            if (!existingCompetencyIds.contains(competencyProposalId)) {
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

            return appMapper.toEntity(dto);
        }).toList();

        return roleCompetencyProposalItemRepository.saveAll(entities).stream()
                .map(appMapper::toDto).collect(Collectors.toList());
    }

    @Override
    @Transactional
    public List<RoleCompetencyProposalItemDto> updateAll
            (Set<RoleCompetencyProposalItemId> ids, List<RoleCompetencyProposalItemDto> dtos) {
        if (dtos.isEmpty() || ids.isEmpty()) {
            String errorMessage = messageSource.getMessage(INVALID_DATA_ERR_MSG_CODE, new String[]{CREATE_OPERATION}, Locale.getDefault());

            throw new BadRequestException(errorMessage);
        }

        Set<Long> proposalIds = ids.stream().map(RoleCompetencyProposalItemId::getProposalId).collect(Collectors.toSet());

        Set<Long> roleIds = ids.stream().map(RoleCompetencyProposalItemId::getRoleId).collect(Collectors.toSet());

        Set<ProposalParticipantId> competencyIds = ids.stream().map(RoleCompetencyProposalItemId::getCompetencyProposalId).collect(Collectors.toSet());

        Set<Long> existingProposalIds = proposalService.getAllProposalsByIdIn(proposalIds).stream().map(ProposalDto::getId).collect(Collectors.toSet());

        Set<Long> existingRoleIds = roleService.getAllByIsDeletedIsFalseAndIdIn(roleIds).stream()
                .map(RoleDto::getId).collect(Collectors.toSet());

        Set<ProposalParticipantId> existingCompetencyIds = competencyProposalService.getAllByIdIn(competencyIds)
                .stream().map(CompetencyProposalDto::getId).collect(Collectors.toSet());

        List<RoleCompetencyProposalItem> entities = dtos.stream().map(dto -> {
            Long proposalId = dto.getId().getProposalId();
            Long roleId = dto.getId().getRoleId();
            ProposalParticipantId competencyId = dto.getId().getCompetencyProposalId();

            if (!existingProposalIds.contains(proposalId)) {
                String errorMessage = messageSource.getMessage(RESULT_NOT_FOUND_ERR_MSG_CODE, null, Locale.getDefault());
                throw new DataAccessException(errorMessage);
            }

            if (!existingRoleIds.contains(roleId)) {
                String errorMessage = messageSource.getMessage(RESULT_NOT_FOUND_ERR_MSG_CODE, null, Locale.getDefault());
                throw new DataAccessException(errorMessage);
            }

            if (!existingCompetencyIds.contains(competencyId)) {
                String errorMessage = messageSource.getMessage(RESULT_NOT_FOUND_ERR_MSG_CODE, null, Locale.getDefault());
                throw new DataAccessException(errorMessage);
            }

            return appMapper.toEntity(dto);
        }).toList();

        return roleCompetencyProposalItemRepository.saveAll(entities).stream()
                .map(appMapper::toDto).collect(Collectors.toList());
    }

    @Transactional
    @Override
    public List<RoleCompetencyProposalItemDto> findAndDeleteAllByProposalId(Long proposalId) {
        List<RoleCompetencyProposalItem> toDelete = roleCompetencyProposalItemRepository.findAllByProposal_Id(proposalId);
        roleCompetencyProposalItemRepository.deleteAll(toDelete);
        return toDelete.stream().map(appMapper::toDto).collect(Collectors.toList());
    }

    @Transactional
    @Override
    public List<RoleCompetencyProposalItemDto> findAndDeleteAllByProposalStaffId(Long proposalId, UUID staffId) {
        List<RoleCompetencyProposalItem> toDelete = roleCompetencyProposalItemRepository.findAllByProposal_IdAndStaff_Id(proposalId, staffId);
        roleCompetencyProposalItemRepository.deleteAll(toDelete);
        return toDelete.stream().map(appMapper::toDto).collect(Collectors.toList());
    }

    @Transactional
    @Override
    public List<RoleCompetencyProposalItemDto> findAndDeleteAllByProposalStaffIdIn(Set<Long> proposalIds, Set<UUID> staffIds) {
        List<RoleCompetencyProposalItem> toDelete = roleCompetencyProposalItemRepository
                .findAllByProposal_IdInAndStaff_IdIn(proposalIds, staffIds);
        roleCompetencyProposalItemRepository.deleteAll(toDelete);
        return toDelete.stream().map(appMapper::toDto).collect(Collectors.toList());
    }

    @Transactional
    @Override
    public void deleteAllByIdIn(Set<RoleCompetencyProposalItemId> ids) {
        roleCompetencyProposalItemRepository.deleteAllById(ids);
    }
}
