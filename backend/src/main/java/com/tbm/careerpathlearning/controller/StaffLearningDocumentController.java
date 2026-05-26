package com.tbm.careerpathlearning.controller;

import com.tbm.careerpathlearning.dto.*;
import com.tbm.careerpathlearning.service.StaffLearningDocumentService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@Slf4j
@RestController
@RequestMapping("/api/learning-document/progress")
@CrossOrigin
public class StaffLearningDocumentController {

    @Autowired
    private StaffLearningDocumentService staffLearningDocumentService;


    @PostMapping("/update")
    public ResponseEntity<Void> updateDocumentProgress(@RequestBody UpdateDocumentProgressDto dto) {
        staffLearningDocumentService.updateDocumentProgress(dto);
        return ResponseEntity.ok().build();
    }

    @GetMapping("/{staffId}/{materialId}/{documentId}")
    public ResponseEntity<StaffLearningDocumentProgressDto> getProgress( @PathVariable UUID staffId,
                                                                          @PathVariable Long materialId,
                                                                          @PathVariable Long documentId){
        return ResponseEntity.ok(staffLearningDocumentService.getProgress(staffId, materialId, documentId));
    }

    @GetMapping("/all/{staffId}/{materialId}")
    public List<StaffLearningDocumentProgressDto> getAllProgressByMaterial(@PathVariable UUID staffId, @PathVariable Long materialId){
        return staffLearningDocumentService.getAllProgressByMaterial(staffId, materialId);
    }
}