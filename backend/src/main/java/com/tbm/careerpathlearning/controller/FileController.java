package com.tbm.careerpathlearning.controller;

import com.tbm.careerpathlearning.exception.BadRequestException;
import com.tbm.careerpathlearning.service.FileService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.MessageSource;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.InputStreamResource;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;

@RestController
@RequestMapping("/api/files")
public class FileController {

    private final FileService fileService;

    @Value("${file.template-dir}")
    private String FILE_TEMPLATE_DIR;

    @Autowired
    private MessageSource messageSource;

    private static final Logger logger = LoggerFactory.getLogger(FileController.class);

    private static final String TEMPLATE_NOT_FOUND_ERR_TITLE_CODE = "file.template.not.found.err.title";

    private static final String TEMPLATE_NOT_FOUND_ERR_MSG_CODE = "file.template.not.found.err.msg";

    public FileController(FileService fileService) {
        this.fileService = fileService;
    }

    @GetMapping("/{folderName}/{filename}")
    public ResponseEntity<Resource> downloadFile(@PathVariable String folderName, @PathVariable String filename) {
        try {

            String relativePath = folderName + "/" + filename;
            Path filePath = fileService.getFilePath(relativePath);
            Resource resource = new UrlResource(filePath.toUri());

            if (!resource.exists()) {
                return ResponseEntity.notFound().build();
            }

            String contentType = Files.probeContentType(filePath);
            if (contentType == null) {
                contentType = "application/octet-stream";
            }

            return ResponseEntity.ok()
                    .contentType(MediaType.parseMediaType(contentType))
                    .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + resource.getFilename() + "\"")
                    .body(resource);
        } catch (Exception e) {
            return ResponseEntity.internalServerError().build();
        }
    }

    @PreAuthorize("""
            hasAnyAuthority(
                T(com.tbm.careerpathlearning.enums.AuthorityName).CAN_MANAGE_ORG_CHART.getAuthorityName()
            )
            """)
    @Transactional
    @GetMapping("/download-template")
    public ResponseEntity<InputStreamResource> downloadTemplate(@RequestParam String templateName, Authentication authentication) {
        try {
            ClassPathResource resource = new ClassPathResource(FILE_TEMPLATE_DIR + templateName);
            if (!resource.exists()) {
                String errorTitle = messageSource.getMessage(TEMPLATE_NOT_FOUND_ERR_TITLE_CODE, null, Locale.getDefault());
                String errorMessage = messageSource.getMessage(TEMPLATE_NOT_FOUND_ERR_MSG_CODE, null, Locale.getDefault());
                throw new BadRequestException(errorTitle, errorMessage);
            }

            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + templateName + "\"")
                    .contentType(MediaType.APPLICATION_OCTET_STREAM)
                    .body(new InputStreamResource(resource.getInputStream()));
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.internalServerError().build();
        }
    }
}
