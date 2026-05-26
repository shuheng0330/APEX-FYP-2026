package com.tbm.careerpathlearning.service.impl;

import com.googlecode.mp4parser.authoring.Movie;
import com.googlecode.mp4parser.authoring.Track;
import com.googlecode.mp4parser.authoring.container.mp4.MovieCreator;
import com.tbm.careerpathlearning.dto.LearningDocumentDto;
import com.tbm.careerpathlearning.dto.LearningMaterialDto;
import com.tbm.careerpathlearning.dto.LearningMaterialRequestDto;
import com.tbm.careerpathlearning.exception.BadRequestException;
import com.tbm.careerpathlearning.mapper.AppMapper;
import com.tbm.careerpathlearning.model.*;
import com.tbm.careerpathlearning.repository.CompetencyRepository;
import com.tbm.careerpathlearning.repository.LearningMaterialRepository;
import com.tbm.careerpathlearning.repository.OrgChartRepository;
import com.tbm.careerpathlearning.service.FileService;
import com.tbm.careerpathlearning.service.LearningMaterialService;
import jakarta.transaction.Transactional;
import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.MessageSource;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;


import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
@Service
public class LearningMaterialServiceImpl implements LearningMaterialService {


    @Autowired
    private LearningMaterialRepository learningMaterialRepository;

    @Autowired
    private FileService fileService;

    @Autowired
    private CompetencyRepository competencyRepository;

    @Autowired
    OrgChartRepository orgChartRepository;

    @Autowired
    private AppMapper appMapper;

    @Autowired
    private MessageSource messageSource;

    @Value("${file.upload-dir}")
    private String FILE_UPLOAD_DIR;

    private static final String IMPORT_FILE_TOO_LARGE_ERR_MSG_CODE = "import.file.too.large.err.msg";

    @Override
    @Transactional
    public LearningMaterialDto createLearningMaterial(LearningMaterialRequestDto request, UUID userId) {
        // TODO: replace with actual logged-in user

        LearningMaterial entity = new LearningMaterial();

        // Basic fields
        entity.setTitle(request.getTitle());
        entity.setDescription(request.getDescription());
        entity.setLearningOutcomes(request.getLearningOutcomes());
        List<OrgChart> orgCharts = orgChartRepository.findAllById(request.getDepartmentIds());

        List<LearningMaterialOrgChart> learningMaterialDepartments = orgCharts.stream()
                .map(orgChart -> {
                    LearningMaterialOrgChart lmDept = new LearningMaterialOrgChart();
                    lmDept.setId(new LearningMaterialOrgChartId());
                    lmDept.setLearningMaterial(entity);
                    lmDept.setOrgChart(orgChart);
                    return lmDept;
                }).collect(Collectors.toList());

        entity.setDepartments(learningMaterialDepartments);
        entity.setMaterialType(request.getMaterialType());
        entity.setCreatedBy(userId);

        List<LearningDocument> documents = request.getLearningDocuments()
                        .stream()
                                .map(docReq -> {
                                    LearningDocument doc = appMapper.toEntity(docReq);
                                    doc.setLearningMaterial(entity);

//                                    String absolutePath = FILE_UPLOAD_DIR + "/" + docReq.getFileUrl();
//                                    Path path = Paths.get(FILE_UPLOAD_DIR, docReq.getFileUrl());
//                                    // Auto calculate based on type
//                                    if ("PDF".equalsIgnoreCase(docReq.getFileType())) {
//                                        File pdfFile = new File(absolutePath);
//                                        int pages = extractPdfPageCount(new File(absolutePath));
//                                        doc.setTotalPages(pages);
//                                        processPdfMetadataFromFile(pdfFile, doc);
//                                    }
//
//                                    if ("VIDEO".equalsIgnoreCase(docReq.getFileType())) {
//                                        double duration = extractVideoDuration(path);
//                                        doc.setTotalDuration(duration);
//                                    }

                                    return doc;
                                }).collect(Collectors.toList());

        entity.setLearningDocuments(documents);

        // 🔥 Sum up all document durations
//        double totalSum = documents.stream()
//                .mapToDouble(doc -> doc.getTotalDuration() != null ? doc.getTotalDuration() : 0.0)
//                .sum();
//        double roundedTotal = Math.round(totalSum * 100.0) / 100.0;
//        entity.setTotalDurationAllDoc(roundedTotal);

        // Competencies - fetch by ID
        List<Competency> competencies = competencyRepository.findAllById(request.getCompetencyIds());
        entity.setCompetency(competencies);

        // Metadata
        entity.setCreatedAt(LocalDateTime.now());

        LearningMaterial saved = learningMaterialRepository.save(entity);
        this.updateMetadataAsync(saved.getMaterialId());

        return appMapper.toDto(saved);
    }


    @Async
    public void updateMetadataAsync(Long materialId) {
        log.info("Async: Processing metadata for material ID {}", materialId);

        // Fetch the material in this new thread
        LearningMaterial material = learningMaterialRepository.findById(materialId).orElse(null);
        if (material == null) return;

        try {

        for (LearningDocument doc : material.getLearningDocuments()) {
            try {
                calculateMetadata(doc);
            } catch (Exception docEx) {
                log.error("Failed to process document: {}", doc.getTitle(), docEx);
            }
        }

        // Recalculate total duration
        double totalSum = material.getLearningDocuments().stream()
                .mapToDouble(doc -> doc.getTotalDuration() != null ? doc.getTotalDuration() : 0.0)
                .sum();
        material.setTotalDurationAllDoc(Math.round(totalSum * 100.0) / 100.0);

        // Save one last time with the new metadata
        learningMaterialRepository.save(material);
        log.info("Async: Processing complete for material ID {}", materialId);

        } catch (Exception e){
            log.error("CRITICAL: Async processing failed for material ID {}", materialId, e);
        }
    }

    @Override
    public List<LearningMaterialDto> getAllLearningMaterials() {
        return learningMaterialRepository.findAll().stream()
                .map(this::mapMaterialWithSignedDocs)
                .toList();
    }

    @Override
    public LearningMaterialDto getLearningMaterialById(Long id) {
        return learningMaterialRepository.findById(id)
                .map(this::mapMaterialWithSignedDocs)
                .orElse(null);
    }

    @Transactional
    @Override
    public void deleteLearningMaterial(Long id) {
        LearningMaterial material = learningMaterialRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Learning material not found with Id" + id));

        if (material.getLearningDocuments() != null) {
            for (LearningDocument doc : material.getLearningDocuments()) {
                String filePath = doc.getFileUrl();
                fileService.deleteFile(filePath);
            }
        }
        learningMaterialRepository.deleteById(id);
    }

    @Transactional
    @Override
    public void bulkDeleteLearningMaterial(List<Long> materialIds) {
        if (materialIds == null || materialIds.isEmpty()) {
            return;
        }

        for (Long id : materialIds) {
            deleteLearningMaterial(id); // reuse existing logic
        }
    }

    @Transactional
    @Override
    public LearningMaterialDto updateLearningMaterial(
            Long id,
            LearningMaterialRequestDto request) {

        // 1️⃣ Load managed parent entity
        LearningMaterial material = learningMaterialRepository.findById(id)
                .orElseThrow(() ->
                        new RuntimeException("Learning material not found with id " + id));

        // 2️⃣ Fetch references
        List<Competency> competencies =
                competencyRepository.findAllById(request.getCompetencyIds());

        List<OrgChart> departments =
                orgChartRepository.findAllById(request.getDepartmentIds());

        Map<Long, LearningMaterialOrgChart> existingDepartmentsMap = material.getDepartments()
                .stream()
                .collect(Collectors.toMap(
                        d -> d.getOrgChart().getId(),
                        Function.identity()
                ));

        material.getDepartments().removeIf(d -> !request.getDepartmentIds().contains(d.getOrgChart().getId()));

        for (OrgChart orgChart : departments) {
            if (!existingDepartmentsMap.containsKey(orgChart.getId())) {
                LearningMaterialOrgChart lmDept = new LearningMaterialOrgChart();
                lmDept.setId(new LearningMaterialOrgChartId());
                lmDept.setLearningMaterial(material);
                lmDept.setOrgChart(orgChart);
                material.getDepartments().add(lmDept);
            }
        }

        // 3️⃣ Update simple fields
        material.setTitle(request.getTitle());
        material.setDescription(request.getDescription());
        material.setMaterialType(request.getMaterialType());
        material.setLearningOutcomes(request.getLearningOutcomes());
        material.setCompetency(competencies);
        material.setUpdatedAt(LocalDateTime.now());

        // 4️⃣ Map existing documents by ID
        List<LearningDocument> documents = material.getLearningDocuments();

        Map<Long, LearningDocument> existingDocsById = documents.stream()
                .filter(d -> d.getDocumentId() != null)
                .collect(Collectors.toMap(
                        LearningDocument::getDocumentId,
                        Function.identity()
                ));

        // 5️⃣ Track incoming document IDs
        Set<Long> incomingIds = request.getLearningDocuments().stream()
                .map(LearningDocumentDto::getDocumentId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());

        // 6️⃣ Remove orphaned documents
        documents.removeIf(doc -> {
            if (doc.getDocumentId() != null && !incomingIds.contains(doc.getDocumentId())) {
                fileService.deleteFile(doc.getFileUrl());
                doc.setLearningMaterial(null); // orphanRemoval deletes it
                return true;
            }
            return false;
        });

        // 7️⃣ Add / update documents
        for (LearningDocumentDto dto : request.getLearningDocuments()) {

            if (dto.getDocumentId() != null &&
                    existingDocsById.containsKey(dto.getDocumentId())) {

                LearningDocument doc = existingDocsById.get(dto.getDocumentId());

                // Detect file replacement
                if (!Objects.equals(dto.getFileUrl(), doc.getFileUrl())) {
                    fileService.deleteFile(doc.getFileUrl());
                    documents.remove(doc);

                    // Create new document
                    LearningDocument newDoc = appMapper.toEntity(dto);
                    newDoc.setLearningMaterial(material);
                    documents.add(newDoc);
                    calculateMetadata(newDoc);
                } else {
                    // Metadata update only
                    appMapper.updateEntityFromDto(dto, doc);
                    if (doc.getTotalDuration() == null || doc.getTotalDuration() == 0) {
                        calculateMetadata(doc);
                    }
                }

            } else {
                LearningDocument newDoc = appMapper.toEntity(dto);
                newDoc.setLearningMaterial(material);
                documents.add(newDoc);
                calculateMetadata(newDoc);
            }
        }

        double updatedTotal = material.getLearningDocuments().stream()
                .mapToDouble(doc -> doc.getTotalDuration() != null ? doc.getTotalDuration() : 0.0)
                .sum();

        double roundedUpdatedTotal = Math.round(updatedTotal * 100.0) / 100.0;

        material.setTotalDurationAllDoc(roundedUpdatedTotal);
        LearningMaterial saved = learningMaterialRepository.save(material);

        return appMapper.toDto(saved);
    }

    /**
     * Upload multiple documents to storage and create LearningDocument entities.
     */
    @Override
    public List<LearningDocument> uploadDocuments(List<MultipartFile> files, List<String> titles, String folderName) {
        List<LearningDocument> learningDocuments = new ArrayList<>();

        long maxSizeBytes = 500 * 1024 * 1024;

        for (int i = 0; i < files.size(); i++) {
            MultipartFile file = files.get(i);

            if (file.getSize() > maxSizeBytes) {
                String errorMessage = messageSource.getMessage(IMPORT_FILE_TOO_LARGE_ERR_MSG_CODE, null, Locale.getDefault());
                throw new BadRequestException(errorMessage);
            }

            String storedPath = fileService.uploadFile(file, folderName);

            LearningDocument doc = new LearningDocument();
            doc.setTitle(titles.get(i));
            doc.setFileUrl(storedPath);

            learningDocuments.add(doc);
        }
        return learningDocuments;
    }

    private LearningMaterialDto mapMaterialWithSignedDocs(LearningMaterial material) {
        LearningMaterialDto dto = appMapper.toDto(material);

        if (dto.getLearningDocuments() != null) {
            dto.getLearningDocuments().forEach(doc -> {
                String storePath = doc.getFileUrl();
                String fileDownloadUrl = "/files/" + storePath;
                doc.setSignedUrl(fileDownloadUrl);
            });
        }

        return dto;
    }

    protected double extractVideoDuration(Path absolutePath) {
        try {
            File file = absolutePath.toFile();
            Movie movie = MovieCreator.build(file.getAbsolutePath());

            double duration = 0;

            for (Track track : movie.getTracks()) {
                if ("vide".equals(track.getHandler())) {
                    long timeScale = track.getTrackMetaData().getTimescale();
                    long durationTicks = track.getDuration();
                    double rawDuration = (double) durationTicks / timeScale;

                    duration = Math.round(rawDuration * 10000.0) / 10000.0;
                }

            }

            return duration; // seconds
        } catch (Exception e) {
            throw new RuntimeException("Failed to read MP4 duration", e);
        }
    }

    protected int extractPdfPageCount(File file) {
        try (PDDocument document = PDDocument.load(file)) {
            return document.getNumberOfPages();
        } catch (Exception e) {
            throw new RuntimeException("Failed to read PDF pages", e);
        }
    }

    protected void processPdfMetadataFromFile(File file, LearningDocument doc) {
        try (PDDocument document = PDDocument.load(file)) {
            int pageCount = document.getNumberOfPages();
            PDFTextStripper stripper = new PDFTextStripper();
            String text = stripper.getText(document);

            int wordCount = (text == null || text.trim().isEmpty()) ? 0 : text.trim().split("\\s+").length;

            log.info("Calculated word count: {}", wordCount);
            double durationInMinutes = (wordCount > 0) ? (wordCount / 238.0) : (pageCount * 1.25);
            doc.setTotalDuration(durationInMinutes * 60);

            log.info("Saved Duration: {} seconds for file: {}", doc.getTotalDuration(), file.getName());
        } catch (IOException e) {
            log.error("Failed to process stored PDF", e);
        }
    }

    protected void calculateMetadata(LearningDocument doc) {
        if (doc.getFileUrl() == null || doc.getFileType() == null) return;

        String absolutePath = FILE_UPLOAD_DIR + "/" + doc.getFileUrl();
        Path path = Paths.get(FILE_UPLOAD_DIR, doc.getFileUrl());
        File file = new File(absolutePath);

        if (!file.exists()) {
            log.warn("File not found for metadata calculation: {}", absolutePath);
            return;
        }

        if ("PDF".equalsIgnoreCase(doc.getFileType())) {
            doc.setTotalPages(extractPdfPageCount(file));
            processPdfMetadataFromFile(file, doc);
        } else if ("VIDEO".equalsIgnoreCase(doc.getFileType())) {
            doc.setTotalDuration(extractVideoDuration(path));
        }
    }

}
