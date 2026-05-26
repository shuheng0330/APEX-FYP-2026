package com.tbm.careerpathlearning.service.impl;

import com.tbm.careerpathlearning.dto.StaffSelfDeclaredSkillDto;
import com.tbm.careerpathlearning.exception.BadRequestException;
import com.tbm.careerpathlearning.mapper.AppMapper;
import com.tbm.careerpathlearning.repository.StaffSelfDeclaredSkillRepository;
import com.tbm.careerpathlearning.service.StaffSelfDeclaredSkillService;
import com.tbm.careerpathlearning.service.ValidationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.MessageSource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

@Service
public class StaffSelfSelfDeclaredSkillServiceImpl implements StaffSelfDeclaredSkillService {

    @Autowired
    private StaffSelfDeclaredSkillRepository staffSelfDeclaredSkillRepository;

    @Autowired
    private MessageSource messageSource;

    @Autowired
    private ValidationService validationService;

    @Autowired
    private AppMapper appMapper;

    private static final Logger logger = LoggerFactory.getLogger(StaffSelfSelfDeclaredSkillServiceImpl.class);

    private static final String RESULT_NOT_FOUND_ERR_MSG_CODE = "database.result.not.found.err.msg";

    private static final String INVALID_DATA_ERR_MSG_CODE = "invalid.data.err.msg";

    private static final String UNIQUE_ATTRIBUTE_REQUIRED_ERR_TITLE_CODE = "attribute.unique.err.title";

    private static final String UNIQUE_ATTRIBUTE_REQUIRED_ERR_MSG_CODE = "attribute.unique.err.msg";

    private static final String SKILL_ATTRIBUTE = "Skill Name";

    private static final String CREATE_OPERATION = "Create Self Declared Skill";

    private static final String UPDATE_OPERATION = "Update Staff Profile";

    @Override
    public StaffSelfDeclaredSkillDto findById(Long id) {
        return this.staffSelfDeclaredSkillRepository.findById(id).map(appMapper::toDto)
                .orElseThrow(() ->
                        new BadRequestException(messageSource.getMessage(RESULT_NOT_FOUND_ERR_MSG_CODE, null, Locale.getDefault())));
    }

    @Override
    public Optional<StaffSelfDeclaredSkillDto> getByStaffIdAndSkill(UUID staffId, String skill) {
        return staffSelfDeclaredSkillRepository.findByStaff_IdAndSkillIgnoreCase(staffId, skill)
                .map(appMapper::toDto);
    }

    @Override
    public List<StaffSelfDeclaredSkillDto> findByStaffId(UUID staffId) {
        return staffSelfDeclaredSkillRepository.findAllByStaff_Id(staffId).stream().map(appMapper::toDto).collect(Collectors.toList());
    }

    @Transactional
    @Override
    public StaffSelfDeclaredSkillDto update(Long id, StaffSelfDeclaredSkillDto dto) {
        if (id == null || validationService.isNullOrBlank(dto.getSkill().trim()) || dto.getProficiency() == null
                || dto.getSkill().trim().length() > 255 || (dto.getDescription() != null && dto.getDescription().trim().length() > 1000)) {
            throw new BadRequestException(messageSource.getMessage(INVALID_DATA_ERR_MSG_CODE, new String[]{UPDATE_OPERATION}, Locale.getDefault()));
        }

        Optional<StaffSelfDeclaredSkillDto> exitedProfile = this.getByStaffIdAndSkill(dto.getStaff().getId(), dto.getSkill().trim());

        if (exitedProfile.isPresent() && !Objects.equals(exitedProfile.get().getId(), dto.getId())) {
            throw new BadRequestException(messageSource.getMessage(UNIQUE_ATTRIBUTE_REQUIRED_ERR_TITLE_CODE, new String[]{SKILL_ATTRIBUTE}, Locale.getDefault()),
                    messageSource.getMessage(UNIQUE_ATTRIBUTE_REQUIRED_ERR_MSG_CODE, new String[]{SKILL_ATTRIBUTE, CREATE_OPERATION}, Locale.getDefault()));
        }

        StaffSelfDeclaredSkillDto toBeUpdated = this.findById(id);

        toBeUpdated.setSkill(dto.getSkill().trim());
        toBeUpdated.setProficiency(dto.getProficiency());
        toBeUpdated.setDescription(dto.getDescription());
        toBeUpdated.setUpdatedAt(dto.getUpdatedAt());
        toBeUpdated.setUpdatedBy(dto.getUpdatedBy());

        return appMapper.toDto(staffSelfDeclaredSkillRepository.save(appMapper.toEntity(toBeUpdated)));
    }

    @Transactional
    @Override
    public StaffSelfDeclaredSkillDto create(StaffSelfDeclaredSkillDto dto) {
        if (dto.getStaff() == null || validationService.isNullOrBlank(dto.getSkill()) || dto.getProficiency() == null
                || dto.getSkill().trim().length() > 255 || (dto.getDescription() != null && dto.getDescription().trim().length() > 1000)) {
            throw new BadRequestException(messageSource.getMessage(INVALID_DATA_ERR_MSG_CODE, new String[]{CREATE_OPERATION}, Locale.getDefault()));
        }

        Optional<StaffSelfDeclaredSkillDto> exitedProfile = this.getByStaffIdAndSkill(dto.getStaff().getId(), dto.getSkill().trim());

        if (exitedProfile.isPresent()) {
            throw new BadRequestException(messageSource.getMessage(UNIQUE_ATTRIBUTE_REQUIRED_ERR_TITLE_CODE, new String[]{SKILL_ATTRIBUTE}, Locale.getDefault()),
                    messageSource.getMessage(UNIQUE_ATTRIBUTE_REQUIRED_ERR_MSG_CODE, new String[]{SKILL_ATTRIBUTE, CREATE_OPERATION}, Locale.getDefault()));
        }

        return appMapper.toDto(staffSelfDeclaredSkillRepository.save(appMapper.toEntity(dto)));
    }

    @Transactional
    @Override
    public void delete(Long id) {
        staffSelfDeclaredSkillRepository.deleteById(id);
    }

    @Transactional
    @Override
    public void deleteAllById(Set<Long> ids) {
        staffSelfDeclaredSkillRepository.deleteAllById(ids);
    }

    @Transactional
    @Override
    public void deleteAllByStaffId(UUID staffId) {
        staffSelfDeclaredSkillRepository.deleteAllByStaff_Id(staffId);
    }

    @Transactional
    @Override
    public void deleteAllByStaffIdIn(Set<UUID> ids) {
        staffSelfDeclaredSkillRepository.deleteAllByStaff_IdIn(ids);
    }
}
