package com.tbm.careerpathlearning.service.impl;

import com.tbm.careerpathlearning.dto.*;
import com.tbm.careerpathlearning.exception.BadRequestException;
import com.tbm.careerpathlearning.exception.DataAccessException;
import com.tbm.careerpathlearning.mapper.AppMapper;
import com.tbm.careerpathlearning.model.RoleJobScopeProposal;
import com.tbm.careerpathlearning.model.RoleJobScopeProposalId;
import com.tbm.careerpathlearning.repository.RoleJobScopeProposalRepository;
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
public class RoleJobScopeProposalServiceImpl implements RoleJobScopeProposalService {

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
    private JobScopeService jobScopeService;

    @Autowired
    private RoleJobScopeProposalRepository roleJobScopeProposalRepository;

    private static final String RESULT_NOT_FOUND_ERR_MSG_CODE = "database.result.not.found.err.msg";

    private static final String RECORD_REDUNDANT_ERR_TITLE_CODE = "database.record.redundant.err.title";

    private static final String RECORD_REDUNDANT_ERR_MSG_CODE = "database.record.redundant.err.msg";

    private static final String INVALID_DATA_ERR_MSG_CODE = "invalid.data.err.msg";

    private static final String CREATE_OPERATION = "Assigning job Scope to the role";

    @Override
    public List<RoleJobScopeProposalDto> findAllByIdIn(Set<RoleJobScopeProposalId> ids) {
        return roleJobScopeProposalRepository.findAllById(ids).stream()
                .map(appMapper::toDto).collect(Collectors.toList());
    }

    @Override
    public List<RoleJobScopeProposalDto> findAllByProposalIdIn(Set<Long> proposalIds) {
        return roleJobScopeProposalRepository.findAllByProposal_IdIn(proposalIds).stream()
                .map(appMapper::toDto).collect(Collectors.toList());
    }

    @Override
    public List<RoleJobScopeProposalDto> findAllByJobScopeIdIn(Set<Long> jobScopeIds) {
        return roleJobScopeProposalRepository.findAllByJobScope_IdIn(jobScopeIds).stream()
                .map(appMapper::toDto).collect(Collectors.toList());
    }

    @Override
    public List<RoleJobScopeProposalDto> getByRoleCompetencyProposalId(Long proposalId, Long roleId, UUID staffId) {
        return roleJobScopeProposalRepository.findAllByProposal_IdAndRole_IdAndStaff_Id(proposalId, roleId, staffId).stream()
                .map(appMapper::toDto).collect(Collectors.toList());
    }

    @Transactional
    @Override
    public List<RoleJobScopeProposalDto> createAll(List<RoleJobScopeProposalDto> dtos) {
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

        Set<Long> jobScopeIds = dtos.stream()
                .map(dto -> dto.getId().getJobScopeId()).collect(Collectors.toSet());

        Set<RoleJobScopeProposalId> ids = dtos.stream()
                .map(RoleJobScopeProposalDto::getId).collect(Collectors.toSet());

        Set<Long> existingProposalIds = proposalService.getAllProposalsByIdIn(proposalIds).stream()
                .map(ProposalDto::getId).collect(Collectors.toSet());

        Set<Long> existingRoleIds = roleService.getAllByIsDeletedIsFalseAndIdIn(roleIds).stream()
                .map(RoleDto::getId).collect(Collectors.toSet());

        Set<UUID> existingStaffIds = staffService.findAllByIdIn(staffIds).stream()
                .map(StaffDto::getId).collect(Collectors.toSet());

        Set<Long> existingJobScopeIds = jobScopeService.findAllByIsDeletedIsFalseAndIdIn(jobScopeIds)
                .stream().map(JobScopeDto::getId).collect(Collectors.toSet());

        Set<RoleJobScopeProposalId> existingIds = this.findAllByIdIn(ids).stream()
                .map(RoleJobScopeProposalDto::getId).collect(Collectors.toSet());

        List<RoleJobScopeProposal> entities = dtos.stream().map(dto -> {
            Long proposalId = dto.getId().getProposalId();
            Long roleId = dto.getId().getRoleId();
            UUID staffId = dto.getId().getStaffId();
            Long jobScopeId = dto.getId().getJobScopeId();

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

            if (!existingJobScopeIds.contains(jobScopeId)) {
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

        return roleJobScopeProposalRepository.saveAll(entities).stream()
                .map(appMapper::toDto).collect(Collectors.toList());
    }

    @Transactional
    @Override
    public List<RoleJobScopeProposalDto> findAndDeleteAllByProposalId(Long proposalId) {
        List<RoleJobScopeProposal> toDelete = roleJobScopeProposalRepository.findAllByProposal_Id(proposalId);
        roleJobScopeProposalRepository.deleteAll(toDelete);

        return toDelete.stream().map(appMapper::toDto).collect(Collectors.toList());
    }

    @Transactional
    @Override
    public List<RoleJobScopeProposalDto> findAndDeleteAllByProposalStaffId(Long proposalId, UUID staffId) {
        List<RoleJobScopeProposal> toDelete = roleJobScopeProposalRepository.findAllByProposal_IdAndStaff_Id(proposalId, staffId);
        roleJobScopeProposalRepository.deleteAll(toDelete);

        return toDelete.stream().map(appMapper::toDto).collect(Collectors.toList());
    }

    @Transactional
    @Override
    public List<RoleJobScopeProposalDto> findAndDeleteAllByProposalStaffIdIn(Set<Long> proposalIds, Set<UUID> staffIds) {
        List<RoleJobScopeProposal> toDelete = roleJobScopeProposalRepository.findAllByProposal_IdInAndStaff_IdIn(proposalIds, staffIds);
        roleJobScopeProposalRepository.deleteAll(toDelete);

        return toDelete.stream().map(appMapper::toDto).collect(Collectors.toList());
    }

    @Transactional
    @Override
    public void deleteAllByIdIn(Set<RoleJobScopeProposalId> ids) {
        roleJobScopeProposalRepository.deleteAllById(ids);
    }
}
