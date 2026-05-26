package com.tbm.careerpathlearning.service.impl;

import com.tbm.careerpathlearning.dto.*;
import com.tbm.careerpathlearning.exception.BadRequestException;
import com.tbm.careerpathlearning.exception.DataAccessException;
import com.tbm.careerpathlearning.mapper.AppMapper;
import com.tbm.careerpathlearning.model.RoleCompetencyItem;
import com.tbm.careerpathlearning.model.RoleCompetencyItemId;
import com.tbm.careerpathlearning.repository.RoleCompetencyItemRepository;
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
public class RoleCompetencyItemServiceImpl implements RoleCompetencyItemService {

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
    private CompetencyService competencyService;

    @Autowired
    private RoleCompetencyItemRepository roleCompetencyItemRepository;

    private static final String RESULT_NOT_FOUND_ERR_MSG_CODE = "database.result.not.found.err.msg";

    private static final String RECORD_REDUNDANT_ERR_TITLE_CODE = "database.record.redundant.err.title";

    private static final String RECORD_REDUNDANT_ERR_MSG_CODE = "database.record.redundant.err.msg";

    private static final String INVALID_DATA_ERR_MSG_CODE = "invalid.data.err.msg";

    private static final String CREATE_OPERATION = "Assigning competency to role";

    @Override
    public List<RoleCompetencyItemDto> findAllByIdIn(Set<RoleCompetencyItemId> ids) {
        return roleCompetencyItemRepository.findAllById(ids).stream()
                .map(appMapper::toDto).collect(Collectors.toList());
    }

    @Override
    public List<RoleCompetencyItemDto> findAllByProposalStaffId(Long proposalId, UUID staffId) {
        return roleCompetencyItemRepository.findAllByProposal_IdAndStaff_Id(proposalId, staffId).stream()
                .map(appMapper::toDto).collect(Collectors.toList());
    }

    @Override
    public List<RoleCompetencyItemDto> findAllByProposalIdIn(Set<Long> proposalIds) {
        return roleCompetencyItemRepository.findAllByProposal_IdIn(proposalIds).stream()
                .map(appMapper::toDto).collect(Collectors.toList());
    }

    @Transactional
    @Override
    public List<RoleCompetencyItemDto> createAll(List<RoleCompetencyItemDto> dtos) {
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

        Set<Long> competencyIds = dtos.stream()
                .map(dto -> dto.getId().getCompetencyId()).collect(Collectors.toSet());

        Set<RoleCompetencyItemId> ids = dtos.stream()
                .map(RoleCompetencyItemDto::getId).collect(Collectors.toSet());

        Set<Long> existingProposalIds = proposalService.getAllProposalsByIdIn(proposalIds).stream()
                .map(ProposalDto::getId).collect(Collectors.toSet());

        Set<Long> existingRoleIds = roleService.getAllByIsDeletedIsFalseAndIdIn(roleIds).stream()
                .map(RoleDto::getId).collect(Collectors.toSet());

        Set<UUID> existingStaffIds = staffService.findAllByIdIn(staffIds).stream()
                .map(StaffDto::getId).collect(Collectors.toSet());

        Set<Long> existingCompetencyIds = competencyService.findAllByIsDeletedIsFalseAndIdIn(competencyIds)
                .stream().map(CompetencyDto::getId).collect(Collectors.toSet());

        Set<RoleCompetencyItemId> existingIds = this.findAllByIdIn(ids).stream()
                .map(RoleCompetencyItemDto::getId).collect(Collectors.toSet());

        List<RoleCompetencyItem> entities = dtos.stream().map(dto -> {
            Long proposalId = dto.getId().getProposalId();
            Long roleId = dto.getId().getRoleId();
            UUID staffId = dto.getId().getStaffId();
            Long competencyId = dto.getId().getCompetencyId();

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

            if (!existingCompetencyIds.contains(competencyId)) {
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

        return roleCompetencyItemRepository.saveAll(entities).stream()
                .map(appMapper::toDto).collect(Collectors.toList());
    }

    @Override
    @Transactional
    public List<RoleCompetencyItemDto> updateAll
            (Set<RoleCompetencyItemId> ids, List<RoleCompetencyItemDto> dtos) {
        if (dtos.isEmpty() || ids.isEmpty()) {
            String errorMessage = messageSource.getMessage(INVALID_DATA_ERR_MSG_CODE, new String[]{CREATE_OPERATION}, Locale.getDefault());

            throw new BadRequestException(errorMessage);
        }

        Set<Long> proposalIds = ids.stream().map(RoleCompetencyItemId::getProposalId).collect(Collectors.toSet());

        Set<Long> roleIds = ids.stream().map(RoleCompetencyItemId::getRoleId).collect(Collectors.toSet());

        Set<Long> competencyIds = ids.stream().map(RoleCompetencyItemId::getCompetencyId).collect(Collectors.toSet());

        Set<Long> existingProposalIds = proposalService.getAllProposalsByIdIn(proposalIds).stream().map(ProposalDto::getId).collect(Collectors.toSet());

        Set<Long> existingRoleIds = roleService.getAllByIsDeletedIsFalseAndIdIn(roleIds).stream()
                .map(RoleDto::getId).collect(Collectors.toSet());

        Set<Long> existingCompetencyIds = competencyService.findAllByIsDeletedIsFalseAndIdIn(competencyIds)
                .stream().map(CompetencyDto::getId).collect(Collectors.toSet());

        List<RoleCompetencyItem> entities = dtos.stream().map(dto -> {
            Long proposalId = dto.getId().getProposalId();
            Long roleId = dto.getId().getRoleId();
            Long competencyId = dto.getId().getCompetencyId();

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

        return roleCompetencyItemRepository.saveAll(entities).stream()
                .map(appMapper::toDto).collect(Collectors.toList());
    }

    @Transactional
    @Override
    public List<RoleCompetencyItemDto> findAndDeleteAllByProposalStaffId(Long proposalId, UUID staffId) {
        List<RoleCompetencyItem> toDelete = roleCompetencyItemRepository.findAllByProposal_IdAndStaff_Id(proposalId, staffId);
        roleCompetencyItemRepository.deleteAll(toDelete);
        return toDelete.stream().map(appMapper::toDto).collect(Collectors.toList());
    }

    @Transactional
    @Override
    public List<RoleCompetencyItemDto> findAndDeleteAllByProposalStaffIdIn(Set<Long> proposalIds, Set<UUID> staffIds) {
        List<RoleCompetencyItem> toDelete = roleCompetencyItemRepository.findAllByProposal_IdInAndStaff_IdIn(proposalIds, staffIds);
        roleCompetencyItemRepository.deleteAll(toDelete);
        return toDelete.stream().map(appMapper::toDto).collect(Collectors.toList());
    }

    @Transactional
    @Override
    public List<RoleCompetencyItemDto> findAndDeleteAllByProposalId(Long proposalId) {
        List<RoleCompetencyItem> toDelete = roleCompetencyItemRepository.findAllByProposal_Id(proposalId);
        roleCompetencyItemRepository.deleteAll(toDelete);

        return toDelete.stream().map(appMapper::toDto).collect(Collectors.toList());
    }

    @Transactional
    @Override
    public void deleteAllByIdIn(Set<RoleCompetencyItemId> ids) {
        roleCompetencyItemRepository.deleteAllById(ids);
    }

}
