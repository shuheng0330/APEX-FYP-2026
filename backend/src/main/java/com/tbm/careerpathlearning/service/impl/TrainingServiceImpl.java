package com.tbm.careerpathlearning.service.impl;

import com.tbm.careerpathlearning.dto.CascaderChildrenDTO;
import com.tbm.careerpathlearning.dto.CascaderOptionDTO;
import com.tbm.careerpathlearning.dto.TrainingProgramDTO;
import com.tbm.careerpathlearning.enums.Status;
import com.tbm.careerpathlearning.mapper.AppMapper;
import com.tbm.careerpathlearning.model.*;
import com.tbm.careerpathlearning.repository.*;
import com.tbm.careerpathlearning.service.TrainingService;
import jakarta.transaction.Transactional;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
public class TrainingServiceImpl implements TrainingService {

    @Autowired
    private TrainingProgramRepository trainingProgramRepository;

    @Autowired
    private TrainingRegistrationRepository trainingRegistrationRepository;

    @Autowired
    private TrainingTargetRoleRepository trainingTargetRoleRepository;

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private CompetencyRepository competencyRepository;

    @Autowired
    private OrgChartRepository orgChartRepository;

    @Autowired
    private AppMapper appMapper;


    @Override
    public List<TrainingProgramDTO> getAllTrainingPrograms() {
        List<TrainingProgramDTO> dtos = trainingProgramRepository.findByIsDeletedFalse().stream()
                .map(appMapper::toDto)
                .collect(Collectors.toList());
        populateRegisteredCounts(dtos);
        return dtos;
    }

    @Override
    public Page<TrainingProgramDTO> getTrainingPrograms(Pageable pageable) {
        Page<TrainingProgramDTO> page = trainingProgramRepository.findByIsDeletedFalse(pageable)
                .map(appMapper::toDto);
        populateRegisteredCounts(page.getContent());
        return page;
    }

    @Override
    public TrainingProgramDTO getTrainingProgramById(Long id) {
        return trainingProgramRepository.findByTrainingIdAndIsDeletedFalse(id)
                .map(appMapper::toDto)
                .orElse(null);
    }

    @Transactional
    @Override
    public TrainingProgramDTO createTrainingProgram(TrainingProgramDTO dto, UUID userId) {
        TrainingProgram program = appMapper.toEntity(dto);
        program.setCreatedAt(LocalDateTime.now());
        program.setCreatedBy(userId);
        program.setStatus(Status.UPCOMING);
        program.setIsDeleted(false);
        if(program.getIsPublic() == null){
            program.setIsPublic(false);
        }
        if(program.getIsMandatory() == null){
            program.setIsMandatory(false);
        }

        List<Competency> competencies = competencyRepository.findAllById(dto.getCompetencyIds());
        program.setCompetencies(competencies);

        List<OrgChart> department = orgChartRepository.findAllById(dto.getDepartmentIds());
        program.setDepartments(department);

        TrainingProgram savedProgram = trainingProgramRepository.save(program);
        List<TrainingTargetRole> targetRoles = new ArrayList<>();
        if (dto.getRoleIds() != null){
            for (Long roleId : dto.getRoleIds()) {
                Role role = this.roleRepository.findById(roleId)
                        .orElseThrow(() -> new RuntimeException("Role not found"));

                TrainingTargetRole trainingTargetRole = new TrainingTargetRole();
                trainingTargetRole.setRole(role);
                trainingTargetRole.setTraining(savedProgram);
                targetRoles.add(trainingTargetRole);
                trainingTargetRoleRepository.save(trainingTargetRole);
            }
        }
        savedProgram.setTrainingTargetRoles(targetRoles);
        return appMapper.toDto(savedProgram);
    }

    @Transactional
    @Override
    public TrainingProgramDTO updateTrainingProgram(Long id, TrainingProgramDTO dto, UUID userId) {

        return trainingProgramRepository.findById(id).map(existing -> {

            List<Competency> competencies =
                    competencyRepository.findAllById(dto.getCompetencyIds());

            List<OrgChart> departments =
                    orgChartRepository.findAllById(dto.getDepartmentIds());

            existing.setTitle(dto.getTitle());
            existing.setDescription(dto.getDescription());
            existing.setStartDate(dto.getStartDate());
            existing.setEndDate(dto.getEndDate());
            existing.setStartTime(dto.getStartTime());
            existing.setEndTime(dto.getEndTime());
            existing.setVenue(dto.getVenue());
            existing.setDepartments(departments);
            existing.setCompetencies(competencies);
            existing.setLocationName(dto.getLocationName());
            existing.setLatitude(dto.getLatitude());
            existing.setLongitude(dto.getLongitude());

            if(existing.getIsPublic() == null){
                existing.setIsPublic(false);
            }else{
                existing.setIsPublic(dto.getIsPublic());
            }

            if(existing.getIsMandatory() == null){
                existing.setIsMandatory(false);
            }else{
                existing.setIsMandatory(dto.getIsMandatory());
            }

            existing.setCapacity(dto.getCapacity());
            existing.setImportantNotes(dto.getImportantNotes());
            existing.setUpdatedAt(LocalDateTime.now());
            existing.setUpdatedBy(userId);

            TrainingProgram saved = trainingProgramRepository.save(existing);
            TrainingProgramDTO result = appMapper.toDto(saved);
            populateRegisteredCounts(Collections.singletonList(result));
            return result;
        }).orElse(null);
    }

    @Override
    public List<TrainingProgramDTO> getTrainingProgramsByStaffId(UUID staffId) {
        List<TrainingProgramDTO> dtos = trainingRegistrationRepository.findActiveTrainingsByStaffId(staffId).stream()
                .map(appMapper::toDto)
                .collect(Collectors.toList());
        populateRegisteredCounts(dtos);
        return dtos;
    }

    @Override
    public List<TrainingProgramDTO> getTrainingProgramsByRoleId(Long roleId) {
        List<Long> trainingIds = trainingTargetRoleRepository.findTrainingIdsByRoleIds(roleId);

        if (trainingIds.isEmpty()) {
            return Collections.emptyList();
        }

        List<TrainingProgram> trainingPrograms = trainingProgramRepository.findAllById(trainingIds);
        List<TrainingProgramDTO> dtos = trainingPrograms.stream()
                .map(appMapper::toDto)
                .collect(Collectors.toList());
        populateRegisteredCounts(dtos);
        return dtos;
    }


    @Override
    @Transactional
    public void deleteTrainingProgramById(Long trainingId, UUID deletedBy) {
        TrainingProgram training = trainingProgramRepository.findById(trainingId)
                .orElseThrow(() -> new RuntimeException("Training not found"));

        training.setIsDeleted(true);
//        training.setStatus(Status.CANCELLED);
        training.setUpdatedBy(deletedBy);
        training.setUpdatedAt(LocalDateTime.now());

        trainingProgramRepository.save(training);
    }


    @Override
    public List<CascaderOptionDTO> getRoleCascaderOptions() {
        List<Role> roles = roleRepository.findAllVisibleAndNotDeletedWithOrgChartTypeD();

        Map<OrgChart, List<Role>> grouped = roles.stream()
                .collect(Collectors.groupingBy(Role::getOrgChart));

        List<CascaderOptionDTO> result = new ArrayList<>();

        for (Map.Entry<OrgChart, List<Role>> entry : grouped.entrySet()) {
            OrgChart org = entry.getKey();
            List<Role> orgRoles = entry.getValue();

            CascaderOptionDTO parent = new CascaderOptionDTO();
            parent.setLabel(org.getName());
            parent.setValue(org.getId());

            List<CascaderChildrenDTO> children = orgRoles.stream().map(role -> {
                CascaderChildrenDTO child = new CascaderChildrenDTO();
                child.setLabel(role.getName());
                child.setValue(role.getId());
                child.setIsLeaf(true);
                return child;
            }).collect(Collectors.toList());

            parent.setChildren(children);
            result.add(parent);
        }

        return result;
    }

    private TrainingProgramDTO mapToDTO(TrainingProgram program) {
        TrainingProgramDTO dto = new TrainingProgramDTO();
        dto.setTrainingId(program.getTrainingId());
        dto.setTitle(program.getTitle());
        dto.setDescription(program.getDescription());
        dto.setStartDate(program.getStartDate());
        dto.setEndDate(program.getEndDate());
        dto.setStartTime(program.getStartTime());
        dto.setEndTime(program.getEndTime());
        dto.setVenue(program.getVenue());
        dto.setCapacity(program.getCapacity());
        dto.setIsPublic(program.getIsPublic());
        dto.setImportantNotes(program.getImportantNotes());
        dto.setIsMandatory(program.getIsMandatory());
        List<Long> roleIds = program.getTrainingTargetRoles().stream()
                .map(ttr -> ttr.getRole().getId())
                .collect(Collectors.toList());
        dto.setRoleIds(roleIds);


        return dto;
    }

    private TrainingProgram mapToEntity(TrainingProgramDTO dto) {
        TrainingProgram program = new TrainingProgram();
        program.setTitle(dto.getTitle());
        program.setDescription(dto.getDescription());
        program.setStartDate(dto.getStartDate());
        program.setEndDate(dto.getEndDate());
        program.setStartTime(dto.getStartTime());
        program.setEndTime(dto.getEndTime());
        program.setVenue(dto.getVenue());
        program.setCapacity(dto.getCapacity());
        program.setImportantNotes(dto.getImportantNotes());
        program.setIsPublic(dto.getIsPublic());
        program.setIsMandatory(dto.getIsMandatory());

        return program;
    }

    private void populateRegisteredCounts(List<TrainingProgramDTO> dtos) {
        if (dtos.isEmpty()) return;
        List<Long> ids = dtos.stream().map(TrainingProgramDTO::getTrainingId).collect(Collectors.toList());
        Map<Long, Long> counts = trainingRegistrationRepository.countRegistrationsByTrainingIds(ids).stream()
                .collect(Collectors.toMap(
                        row -> (Long) row[0],
                        row -> (Long) row[1]
                ));
        dtos.forEach(dto -> dto.setRegisteredCount(counts.getOrDefault(dto.getTrainingId(), 0L).intValue()));
    }
}
