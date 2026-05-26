package com.tbm.careerpathlearning.controller;

import com.tbm.careerpathlearning.dto.LearningMaterialDto;
import com.tbm.careerpathlearning.dto.MaterialProgressDto;
import com.tbm.careerpathlearning.dto.StaffLearningMaterialDto;
import com.tbm.careerpathlearning.service.StaffLearningMaterialService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@Slf4j
@RestController
@RequestMapping("/api/learning-material/enroll")
@CrossOrigin
public class StaffLearningMaterialController {

    @Autowired
    private StaffLearningMaterialService staffLearningMaterialService;

    @PostMapping
    public ResponseEntity<?> enrollLearningMaterial(@RequestBody StaffLearningMaterialDto staffLearningMaterialDto){
        StaffLearningMaterialDto staffLearningMaterialDto1 = staffLearningMaterialService.enroll(staffLearningMaterialDto);
        return ResponseEntity.ok(staffLearningMaterialDto1);
    }

    @GetMapping("/my-course/{staffId}")
    public ResponseEntity<List<LearningMaterialDto>> getEnrolledCourses(@PathVariable UUID staffId){
        return ResponseEntity.ok(staffLearningMaterialService.getEnrolledCourses(staffId));
    }


    @GetMapping("/list/{materialId}")
    public List<StaffLearningMaterialDto> getEnrollmentsByMaterialId(@PathVariable Long materialId){
        return staffLearningMaterialService.getEnrollmentsByMaterialId(materialId);
    }

    @GetMapping("/all/progress/{staffId}")
    public List<MaterialProgressDto> getAllProgress(@PathVariable UUID staffId){
        return staffLearningMaterialService.getProgressList(staffId);
    }

    @GetMapping("/{staffId}/{materialId}")
    public StaffLearningMaterialDto getLearningMaterialByStaffId(@PathVariable UUID staffId, @PathVariable Long materialId){
        return staffLearningMaterialService.getLearningMaterialByStaffId(staffId, materialId);
    }

    @DeleteMapping("/unenroll/{staffId}/{materialId}")
    public ResponseEntity<Void> unenroll(@PathVariable UUID staffId, @PathVariable Long materialId) {
        staffLearningMaterialService.unenroll(staffId, materialId);
        return ResponseEntity.noContent().build();
    }
}
