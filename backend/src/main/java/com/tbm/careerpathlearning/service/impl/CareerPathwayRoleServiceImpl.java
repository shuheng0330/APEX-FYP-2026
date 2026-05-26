package com.tbm.careerpathlearning.service.impl;

import com.tbm.careerpathlearning.dto.*;
import com.tbm.careerpathlearning.exception.BadRequestException;
import com.tbm.careerpathlearning.exception.DataAccessException;
import com.tbm.careerpathlearning.mapper.AppMapper;
import com.tbm.careerpathlearning.model.*;
import com.tbm.careerpathlearning.repository.CareerPathwayRoleRepository;
import com.tbm.careerpathlearning.service.CareerPathwayService;
import com.tbm.careerpathlearning.service.CareerPathwayRoleService;
import com.tbm.careerpathlearning.service.RoleService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.MessageSource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

@Service
public class CareerPathwayRoleServiceImpl implements CareerPathwayRoleService {

    @Autowired
    private CareerPathwayRoleRepository careerPathwayRoleRepository;

    @Autowired
    private CareerPathwayService careerPathwayService;

    @Autowired
    private AppMapper appMapper;

    @Autowired
    private MessageSource messageSource;

    @Autowired
    private RoleService roleService;

    private static final String INVALID_DATA_ERR_MSG_CODE = "invalid.data.err.msg";

    private static final String RESULT_NOT_FOUND_ERR_MSG_CODE = "database.result.not.found.err.msg";

    private static final String RECORD_REDUNDANT_ERR_TITLE_CODE = "database.record.redundant.err.title";

    private static final String RECORD_REDUNDANT_ERR_MSG_CODE = "database.record.redundant.err.msg";

    private static final String RECURSIVE_ERR_MSG_CODE = "recursive.reference.err.msg";

    private static final String ASSIGN_ROLE = "Assigning roles to the career pathway";


    @Override
    public List<CareerPathwayRoleDto> getAll() {
        return careerPathwayRoleRepository.findAll().stream().map(appMapper::toDto).collect(Collectors.toList());
    }

    @Override
    public List<CareerPathwayRoleDto> getAllByIdIn(Set<CareerPathwayRoleId> ids) {
        return careerPathwayRoleRepository.findAllByIdIn(ids).stream().map(appMapper::toDto).collect(Collectors.toList());
    }

    @Override
    public List<CareerPathwayRoleDto> getAllByCareerPathwayId(Long careerPathwayId) {
        return careerPathwayRoleRepository.findByCareerPathwayId(careerPathwayId).stream().map(appMapper::toDto).collect(Collectors.toList());
    }

    @Override
    @Transactional
    public List<CareerPathwayRoleDto> createAll(List<CareerPathwayRoleDto> dtos) {
        if (dtos == null || dtos.isEmpty()) {
            String errorMessage = messageSource.getMessage(INVALID_DATA_ERR_MSG_CODE, new String[]{ASSIGN_ROLE}, Locale.getDefault());

            throw new BadRequestException(errorMessage);
        }

        Set<Long> careerPathwayIds = dtos.stream()
                .map(dto -> dto.getId().getCareerPathwayId()).collect(Collectors.toSet());

        Set<Long> childRoleIds = dtos.stream()
                .map(dto -> dto.getId().getChildId()).collect(Collectors.toSet());

        Set<Long> parentRoleIds = dtos.stream()
                .map(dto -> dto.getId().getParentId()).collect(Collectors.toSet());

        Set<Long> roleIds = new HashSet<>(childRoleIds);
        roleIds.addAll(parentRoleIds);

        Set<CareerPathwayRoleId> careerPathwayRoleIds = dtos.stream()
                .map(CareerPathwayRoleDto::getId).collect(Collectors.toSet());

        Set<Long> existingCareerPathwayIds = careerPathwayService.getAllByIsDeletedIsFalseAndIdIn(careerPathwayIds).stream()
                .map(CareerPathwayDto::getId).collect(Collectors.toSet());

        Set<Long> existingRoleIds = roleService.getAllByIsDeletedIsFalseAndIdIn(roleIds)
                .stream().map(RoleDto::getId).collect(Collectors.toSet());

        Set<CareerPathwayRoleId> existingCareerPathwayRoleIds = this.getAllByIdIn(careerPathwayRoleIds).stream()
                .map(CareerPathwayRoleDto::getId).collect(Collectors.toSet());

        List<CareerPathwayRole> entities = dtos.stream().map(dto -> {
            Long careerPathwayId = dto.getId().getCareerPathwayId();
            Long childRoleId = dto.getId().getChildId();
            Long parentRoleId = dto.getId().getParentId();
            Set<Long> parentChildId = new HashSet<>();
            parentChildId.add(parentRoleId);
            parentChildId.add(childRoleId);

            if (!existingCareerPathwayIds.contains(careerPathwayId)) {
                String errorMessage = messageSource.getMessage(RESULT_NOT_FOUND_ERR_MSG_CODE, new String[]{}, Locale.getDefault());
                throw new DataAccessException(errorMessage);
            }

            if (!existingRoleIds.containsAll(parentChildId)) {
                String errorMessage = messageSource.getMessage(RESULT_NOT_FOUND_ERR_MSG_CODE, new String[]{}, Locale.getDefault());
                throw new DataAccessException(errorMessage);
            }

            if (existingCareerPathwayRoleIds.contains(dto.getId())) {
                String errorTitle = messageSource.getMessage(RECORD_REDUNDANT_ERR_TITLE_CODE, null, Locale.getDefault());
                String errorMessage = messageSource.getMessage(RECORD_REDUNDANT_ERR_MSG_CODE, null, Locale.getDefault());

                throw new DataAccessException(errorTitle, errorMessage);
            }

            if (Objects.equals(parentRoleId, childRoleId)) {
                String errorMessage = messageSource.getMessage(RECURSIVE_ERR_MSG_CODE, null, Locale.getDefault());

                throw new BadRequestException(errorMessage);
            }

            return appMapper.toEntity(dto);
        }).toList();

        return careerPathwayRoleRepository.saveAll(entities).stream().map(appMapper::toDto).collect(Collectors.toList());
    }

    @Transactional
    @Override
    public void deleteAllByIdIn(Set<CareerPathwayRoleId> ids) {
        careerPathwayRoleRepository.deleteAllById(ids);
    }

    @Transactional
    @Override
    public void deleteByCareerPathwayId(Long careerPathwayId) {
        careerPathwayRoleRepository.deleteAllByCareerPathwayId(careerPathwayId);
    }

    @Transactional
    @Override
    public void deleteAllByCareerPathwayIdIn(Set<Long> careerPathwayIds) {
        careerPathwayRoleRepository.deleteAllByCareerPathway_IdIn(careerPathwayIds);
    }
}
