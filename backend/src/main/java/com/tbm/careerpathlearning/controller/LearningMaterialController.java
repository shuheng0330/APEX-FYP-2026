package com.tbm.careerpathlearning.controller;

import com.tbm.careerpathlearning.dto.LearningMaterialDto;
import com.tbm.careerpathlearning.dto.LearningMaterialRequestDto;
import com.tbm.careerpathlearning.model.LearningDocument;
import com.tbm.careerpathlearning.service.FileService;
import com.tbm.careerpathlearning.service.LearningMaterialRecommendationService;
import com.tbm.careerpathlearning.service.LearningMaterialService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.ResponseEntity;
import org.springframework.http.HttpStatus;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/learning-material")
@CrossOrigin
public class LearningMaterialController {

    @Autowired
	private LearningMaterialService learningMaterialService;

    @Autowired
    private LearningMaterialRecommendationService learningMaterialRecommendationService;

    @Autowired
    private FileService fileService;

    private static final String FOLDER_NAME = "LMS Material";

	@Autowired
	public LearningMaterialController(LearningMaterialService learningMaterialService) {
		this.learningMaterialService = learningMaterialService;
	}

    @PreAuthorize("""
            hasAnyAuthority(
                T(com.tbm.careerpathlearning.enums.AuthorityName).CAN_MANAGE_LEARNING_MATERIAL.getAuthorityName()
            )
            """)
    @PostMapping("/create")
    public ResponseEntity<LearningMaterialDto> createLearningMaterial(@Valid @RequestBody LearningMaterialRequestDto request, Authentication authentication) {
        String userId = authentication.getPrincipal().toString();
        UUID userUUID = UUID.fromString(userId);
        LearningMaterialDto created = learningMaterialService.createLearningMaterial(request, userUUID);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @PreAuthorize("""
            hasAnyAuthority(
                T(com.tbm.careerpathlearning.enums.AuthorityName).CAN_MANAGE_LEARNING_MATERIAL.getAuthorityName()
            )
            """)
    @PostMapping("/upload")
    public ResponseEntity<Map<String, String>> uploadFile(@RequestPart("file") MultipartFile file) {
        String fileUrl = fileService.uploadFile(file, FOLDER_NAME);
        return ResponseEntity.ok(Map.of("fileUrl", fileUrl));
    }

    @PreAuthorize("""
            hasAnyAuthority(
                T(com.tbm.careerpathlearning.enums.AuthorityName).CAN_MANAGE_LEARNING_MATERIAL.getAuthorityName()
            )
            """)
    @PostMapping("/upload-document")
    public ResponseEntity<List<LearningDocument>> uploadDocuments(@RequestPart("files") List<MultipartFile> files, @RequestPart("titles") List<String> titles) {
        if (files.size() != titles.size()) {
            return ResponseEntity.badRequest().build();
        }
        List<LearningDocument> uploadedDocs = learningMaterialService.uploadDocuments(files, titles, FOLDER_NAME);
        return ResponseEntity.ok(uploadedDocs);
    }

    @GetMapping
	public ResponseEntity<List<LearningMaterialDto>> getAllLearningMaterials() {
		List<LearningMaterialDto> materials = learningMaterialService.getAllLearningMaterials();
		return ResponseEntity.ok(materials);
	}

    @GetMapping("/recommendation/{staffId}")
    public ResponseEntity<List<LearningMaterialDto>> getRecommendationMaterialList(@PathVariable("staffId") UUID staffId) {
        List<LearningMaterialDto> recommendedMaterialList = learningMaterialRecommendationService.recommendForUser(staffId);
        return ResponseEntity.ok(recommendedMaterialList);
    }

	@GetMapping("/{id}")
	public ResponseEntity<LearningMaterialDto> getLearningMaterialById(@PathVariable("id") Long id) {
		LearningMaterialDto material = learningMaterialService.getLearningMaterialById(id);
		if (material == null) {
			return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
		}
		return ResponseEntity.ok(material);
	}

    @PreAuthorize("""
            hasAnyAuthority(
                T(com.tbm.careerpathlearning.enums.AuthorityName).CAN_MANAGE_LEARNING_MATERIAL.getAuthorityName()
            )
            """)
    @PutMapping("/update/{materialId}")
    public ResponseEntity<LearningMaterialDto> updateLearningMaterial(@PathVariable("materialId") Long materialId,@Valid @RequestBody LearningMaterialRequestDto dto) {
        LearningMaterialDto updated = learningMaterialService.updateLearningMaterial(materialId, dto);
        return updated != null ? ResponseEntity.ok(updated) : ResponseEntity.notFound().build();
    }

    @PreAuthorize("""
            hasAnyAuthority(
                T(com.tbm.careerpathlearning.enums.AuthorityName).CAN_MANAGE_LEARNING_MATERIAL.getAuthorityName()
            )
            """)
    @DeleteMapping("/delete/{materialId}")
    public ResponseEntity<Void>  deleteLearningMaterial(@PathVariable("materialId") Long materialId) {
        learningMaterialService.deleteLearningMaterial(materialId);
        return ResponseEntity.ok().build();
    }

    @PreAuthorize("""
            hasAnyAuthority(
                T(com.tbm.careerpathlearning.enums.AuthorityName).CAN_MANAGE_LEARNING_MATERIAL.getAuthorityName()
            )
            """)
    @DeleteMapping("/bulk-delete")
    public ResponseEntity<Void> bulkDeleteLearningMaterial(@RequestParam("materialIds") List<Long> materialIds) {
        if (materialIds == null || materialIds.isEmpty()) {
            return ResponseEntity.badRequest().build();
        }
        learningMaterialService.bulkDeleteLearningMaterial(materialIds);
        return ResponseEntity.ok().build();
    }

}
