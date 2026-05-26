package com.tbm.careerpathlearning.controller;
import com.tbm.careerpathlearning.dto.CascaderOptionDTO;
import com.tbm.careerpathlearning.dto.PageResponse;
import com.tbm.careerpathlearning.dto.TrainingProgramDTO;
import com.tbm.careerpathlearning.service.TrainingService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/trainings")
@CrossOrigin
public class TrainingProgramController {

    @Autowired
    private TrainingService trainingProgramService;

    @GetMapping
    public List<TrainingProgramDTO> getAllTrainingPrograms() {
        return trainingProgramService.getAllTrainingPrograms();
    }

    @GetMapping("/paged")
    public ResponseEntity<PageResponse<TrainingProgramDTO>> getTrainingPrograms(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size
    ) {
        Pageable pageable = PageRequest.of(page, size);
        Page<TrainingProgramDTO> result =
                trainingProgramService.getTrainingPrograms(pageable);

        return ResponseEntity.ok(
                new PageResponse<>(
                        result.getContent(),
                        result.getNumber(),
                        result.getSize(),
                        result.getTotalElements(),
                        result.getTotalPages()
                )
        );
    }

    @GetMapping("/{trainingId}")
    public ResponseEntity<TrainingProgramDTO> getTrainingProgramById(@PathVariable Long trainingId){
        TrainingProgramDTO trainingProgram = trainingProgramService.getTrainingProgramById(trainingId);
        return trainingProgram != null ? ResponseEntity.ok(trainingProgram) : ResponseEntity.notFound().build();
    }

    @GetMapping("/registered/{staffId}")
    public List<TrainingProgramDTO> getTrainingProgramsByStaffId(@PathVariable UUID staffId){
        return trainingProgramService.getTrainingProgramsByStaffId(staffId);
    }

    @GetMapping("/role-options")
    public List<CascaderOptionDTO> getRoleCascaderOptions() {
        return trainingProgramService.getRoleCascaderOptions();
    }

    @PreAuthorize("""
            hasAnyAuthority(
                T(com.tbm.careerpathlearning.enums.AuthorityName).CAN_MANAGE_TRAINING.getAuthorityName()
            )
            """)
    @PostMapping
    public TrainingProgramDTO createTrainingProgram(@RequestBody TrainingProgramDTO trainingProgram, Authentication authentication){
        String userId = authentication.getPrincipal().toString();
        UUID userUUID = UUID.fromString(userId);
        return  trainingProgramService.createTrainingProgram(trainingProgram, userUUID);
    }

    @PreAuthorize("""
            hasAnyAuthority(
                T(com.tbm.careerpathlearning.enums.AuthorityName).CAN_MANAGE_TRAINING.getAuthorityName()
            )
            """)
    @PutMapping("/{trainingId}")
    public ResponseEntity<TrainingProgramDTO> updateTrainingProgram(@PathVariable Long trainingId, @RequestBody TrainingProgramDTO trainingProgram, Authentication authentication){
        String userId = authentication.getPrincipal().toString();
        UUID userUUID = UUID.fromString(userId);
        TrainingProgramDTO updated = trainingProgramService.updateTrainingProgram(trainingId, trainingProgram, userUUID);
        return updated != null ? ResponseEntity.ok(updated) : ResponseEntity.notFound().build();
    }

    @PreAuthorize("""
            hasAnyAuthority(
                T(com.tbm.careerpathlearning.enums.AuthorityName).CAN_MANAGE_TRAINING.getAuthorityName()
            )
            """)
    @DeleteMapping("/{trainingId}")
    public ResponseEntity<Void> deleteTrainingProgram(@PathVariable Long trainingId, Authentication authentication){
        String userId = authentication.getPrincipal().toString();
        UUID userUUID = UUID.fromString(userId);
        trainingProgramService.deleteTrainingProgramById(trainingId, userUUID);
        return ResponseEntity.ok().build();
    }


}
