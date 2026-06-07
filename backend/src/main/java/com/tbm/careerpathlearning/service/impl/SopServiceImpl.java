package com.tbm.careerpathlearning.service.impl;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tbm.careerpathlearning.dto.SopDetailDto;
import com.tbm.careerpathlearning.dto.SopDocumentDto;
import com.tbm.careerpathlearning.dto.SopModuleDto;
import com.tbm.careerpathlearning.dto.SopQuizQuestionDto;
import com.tbm.careerpathlearning.enums.ReviewStatus;
import com.tbm.careerpathlearning.enums.SopGenerationStatus;
import com.tbm.careerpathlearning.exception.BadRequestException;
import com.tbm.careerpathlearning.model.SopDocument;
import com.tbm.careerpathlearning.model.SopModule;
import com.tbm.careerpathlearning.model.SopQuizQuestion;
import com.tbm.careerpathlearning.repository.SopDocumentRepository;
import com.tbm.careerpathlearning.repository.SopModuleRepository;
import com.tbm.careerpathlearning.repository.SopQuizQuestionRepository;
import com.tbm.careerpathlearning.service.FileService;
import com.tbm.careerpathlearning.service.SopGenerationService;
import com.tbm.careerpathlearning.service.SopService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@Service
public class SopServiceImpl implements SopService {

    @Autowired private SopDocumentRepository sopDocumentRepository;
    @Autowired private SopModuleRepository sopModuleRepository;
    @Autowired private SopQuizQuestionRepository sopQuizQuestionRepository;
    @Autowired private FileService fileService;
    @Autowired private SopGenerationService sopGenerationService;

    private final ObjectMapper objectMapper = new ObjectMapper();

    // === Module 1: upload + generation ======================================

    @Override
    @Transactional
    public SopDocumentDto upload(MultipartFile file, String title, String version, String departmentTag, UUID userId) {
        if (file == null || file.isEmpty()) {
            throw new BadRequestException("Please select a document to upload.");
        }
        String filename = file.getOriginalFilename();
        String lower = filename == null ? "" : filename.toLowerCase();
        if (!lower.endsWith(".doc") && !lower.endsWith(".docx") && !lower.endsWith(".pdf")) {
            throw new BadRequestException("Only Word (.doc, .docx) or PDF (.pdf) documents are supported.");
        }

        String storedPath = fileService.uploadFile(file, "sop");

        OffsetDateTime now = OffsetDateTime.now();
        SopDocument doc = new SopDocument();
        doc.setTitle(resolveTitle(title, filename)); // FR-08-03: filename as title when blank
        doc.setVersion(version);
        doc.setOriginalFilename(filename);
        doc.setFilePath(storedPath);
        doc.setDepartmentTag(departmentTag);
        doc.setUploadedBy(userId);
        doc.setGenerationStatus(SopGenerationStatus.PENDING);
        doc.setCreatedAt(now);
        doc.setUpdatedAt(now);
        SopDocument saved = sopDocumentRepository.save(doc);

        // Kick off parse + AI generation in the background (FR-08-04/05).
        sopGenerationService.generateForDocument(saved.getId());

        return toDocDto(saved, 0);
    }

    @Override
    public List<SopDocumentDto> listDocuments() {
        return sopDocumentRepository.findAllByIsDeletedFalseOrderByCreatedAtDesc().stream()
                .map(d -> toDocDto(d, (int) sopModuleRepository
                        .findAllBySopDocumentIdOrderByModuleOrderAsc(d.getId()).size()))
                .toList();
    }

    @Override
    public SopDetailDto getDetail(Long sopDocumentId) {
        SopDocument doc = getDocOrThrow(sopDocumentId);
        List<SopModule> modules = sopModuleRepository.findAllBySopDocumentIdOrderByModuleOrderAsc(sopDocumentId);

        SopDetailDto detail = new SopDetailDto();
        detail.setDocument(toDocDto(doc, modules.size()));
        detail.setModules(modules.stream().map(this::toModuleDto).toList());
        return detail;
    }

    @Override
    public SopDocumentDto getStatus(Long sopDocumentId) {
        SopDocument doc = getDocOrThrow(sopDocumentId);
        int moduleCount = sopModuleRepository.findAllBySopDocumentIdOrderByModuleOrderAsc(sopDocumentId).size();
        return toDocDto(doc, moduleCount);
    }

    @Override
    @Transactional
    public SopDocumentDto regenerateDocument(Long sopDocumentId) {
        SopDocument doc = getDocOrThrow(sopDocumentId);
        doc.setGenerationStatus(SopGenerationStatus.PENDING);
        doc.setStatusMessage(null);
        doc.setUpdatedAt(OffsetDateTime.now());
        sopDocumentRepository.save(doc);
        sopGenerationService.generateForDocument(sopDocumentId);
        return toDocDto(doc, 0);
    }

    // === Module 2: material review (FR-09) ==================================

    @Override
    public SopModuleDto getModule(Long moduleId) {
        return toModuleDto(getModuleOrThrow(moduleId));
    }

    @Override
    @Transactional
    public SopModuleDto updateModule(Long moduleId, String title, String content) {
        SopModule module = getModuleOrThrow(moduleId);
        if (title != null && !title.isBlank()) {
            module.setTitle(title);
        }
        module.setContent(content);
        module.setUpdatedAt(OffsetDateTime.now());
        return toModuleDto(sopModuleRepository.save(module));
    }

    @Override
    @Transactional
    public SopModuleDto approveModule(Long moduleId) {
        SopModule module = getModuleOrThrow(moduleId);
        module.setReviewStatus(ReviewStatus.APPROVED);
        module.setUpdatedAt(OffsetDateTime.now());
        return toModuleDto(sopModuleRepository.save(module));
    }

    @Override
    public SopModuleDto rejectModule(Long moduleId, String reason) {
        if (reason == null || reason.isBlank()) {
            throw new BadRequestException("A reason is required to reject and regenerate the module."); // FR-09-05
        }
        SopModule module = getModuleOrThrow(moduleId);
        sopGenerationService.regenerateModule(module, reason); // sets status REGENERATED + refreshes quiz
        return toModuleDto(getModuleOrThrow(moduleId));
    }

    // === Module 2: quiz review (FR-10) ======================================

    @Override
    @Transactional
    public SopQuizQuestionDto updateQuestion(Long questionId, SopQuizQuestionDto dto) {
        SopQuizQuestion q = sopQuizQuestionRepository.findById(questionId)
                .orElseThrow(() -> new BadRequestException("Quiz question not found."));
        if (dto.getQuestionType() != null) {
            q.setQuestionType(dto.getQuestionType());
        }
        if (dto.getQuestionText() != null) {
            q.setQuestionText(dto.getQuestionText());
        }
        q.setOptions(writeOptions(dto.getOptions()));
        q.setCorrectAnswer(dto.getCorrectAnswer());
        q.setExplanation(dto.getExplanation());
        q.setUpdatedAt(OffsetDateTime.now());
        return toQuizDto(sopQuizQuestionRepository.save(q));
    }

    @Override
    @Transactional
    public SopModuleDto approveModuleQuiz(Long moduleId) {
        SopModule module = getModuleOrThrow(moduleId);
        OffsetDateTime now = OffsetDateTime.now();
        List<SopQuizQuestion> questions = sopQuizQuestionRepository.findAllBySopModuleIdOrderByIdAsc(moduleId);
        for (SopQuizQuestion q : questions) {
            q.setReviewStatus(ReviewStatus.APPROVED);
            q.setUpdatedAt(now);
        }
        sopQuizQuestionRepository.saveAll(questions);
        return toModuleDto(module);
    }

    @Override
    public SopModuleDto rejectModuleQuiz(Long moduleId, String reason) {
        if (reason == null || reason.isBlank()) {
            throw new BadRequestException("A reason is required to reject and regenerate the quiz."); // FR-10-03
        }
        SopModule module = getModuleOrThrow(moduleId);
        sopGenerationService.regenerateQuiz(module, reason);
        return toModuleDto(getModuleOrThrow(moduleId));
    }

    // === helpers ============================================================

    private SopDocument getDocOrThrow(Long id) {
        SopDocument doc = sopDocumentRepository.findById(id).orElse(null);
        if (doc == null || doc.isDeleted()) {
            throw new BadRequestException("SOP document not found.");
        }
        return doc;
    }

    private SopModule getModuleOrThrow(Long id) {
        return sopModuleRepository.findById(id)
                .orElseThrow(() -> new BadRequestException("Module not found."));
    }

    private String resolveTitle(String title, String filename) {
        if (title != null && !title.isBlank()) {
            return title.trim();
        }
        if (filename == null) {
            return "Untitled SOP";
        }
        int dot = filename.lastIndexOf('.');
        return dot > 0 ? filename.substring(0, dot) : filename;
    }

    private SopDocumentDto toDocDto(SopDocument d, int moduleCount) {
        SopDocumentDto dto = new SopDocumentDto();
        dto.setId(d.getId());
        dto.setTitle(d.getTitle());
        dto.setVersion(d.getVersion());
        dto.setOriginalFilename(d.getOriginalFilename());
        dto.setDepartmentTag(d.getDepartmentTag());
        dto.setGenerationStatus(d.getGenerationStatus());
        dto.setStatusMessage(d.getStatusMessage());
        dto.setModuleCount(moduleCount);
        dto.setCreatedAt(d.getCreatedAt());
        return dto;
    }

    private SopModuleDto toModuleDto(SopModule m) {
        SopModuleDto dto = new SopModuleDto();
        dto.setId(m.getId());
        dto.setSopDocumentId(m.getSopDocumentId());
        dto.setModuleOrder(m.getModuleOrder());
        dto.setTitle(m.getTitle());
        dto.setContent(m.getContent());
        dto.setReviewStatus(m.getReviewStatus());
        dto.setRejectionReason(m.getRejectionReason());
        dto.setQuiz(sopQuizQuestionRepository.findAllBySopModuleIdOrderByIdAsc(m.getId())
                .stream().map(this::toQuizDto).toList());
        return dto;
    }

    private SopQuizQuestionDto toQuizDto(SopQuizQuestion q) {
        SopQuizQuestionDto dto = new SopQuizQuestionDto();
        dto.setId(q.getId());
        dto.setSopModuleId(q.getSopModuleId());
        dto.setQuestionType(q.getQuestionType());
        dto.setQuestionText(q.getQuestionText());
        dto.setOptions(readOptions(q.getOptions()));
        dto.setCorrectAnswer(q.getCorrectAnswer());
        dto.setExplanation(q.getExplanation());
        dto.setReviewStatus(q.getReviewStatus());
        dto.setRejectionReason(q.getRejectionReason());
        return dto;
    }

    private List<String> readOptions(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<List<String>>() {});
        } catch (Exception e) {
            return List.of();
        }
    }

    private String writeOptions(List<String> options) {
        if (options == null || options.isEmpty()) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(options);
        } catch (Exception e) {
            return null;
        }
    }
}
