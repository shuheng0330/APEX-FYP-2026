package com.tbm.careerpathlearning.service.impl;

import com.tbm.careerpathlearning.service.FileService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.UUID;

@Service
public class FileServiceImpl implements FileService {

    @Value("${file.upload-dir}")
    private String FILE_UPLOAD_DIR;

    @Override
    public String uploadFile(MultipartFile file, String folderName) {
        try {
            Path targetDir = getFilePath(folderName);

            String originalFileName = file.getOriginalFilename();
            String uniqueFileName = UUID.randomUUID() + "-" + originalFileName;

            Path targetPath = getFilePath(targetDir.resolve(uniqueFileName).toString());
            Files.createDirectories(targetDir);
            try(var input=file.getInputStream()) {
                Files.copy(input, targetPath, StandardCopyOption.REPLACE_EXISTING);
            }

            return folderName + "/" + uniqueFileName;

        } catch (IOException e) {
            throw new RuntimeException("Failed to store file", e);
        }
    }

    @Override
    public Path getFilePath(String relativePath) {
        Path root=Paths.get(FILE_UPLOAD_DIR).toAbsolutePath().normalize();
        Path target=root.resolve(relativePath).normalize();
        if(!target.startsWith(root) || target.startsWith(root.resolve(
                com.tbm.careerpathlearning.service.KpiAssessmentEvidenceStorage.PRIVATE_FOLDER)))
            throw new org.springframework.security.access.AccessDeniedException("Assessment evidence requires scoped access");
        Path current=root;
        if(Files.isSymbolicLink(current))
            throw new org.springframework.security.access.AccessDeniedException("Symbolic file paths are not allowed");
        for(Path part:root.relativize(target)) {
            current=current.resolve(part);
            if(Files.isSymbolicLink(current))
                throw new org.springframework.security.access.AccessDeniedException("Symbolic file paths are not allowed");
        }
        return target;
    }

    @Override
    public void deleteFile(String filePath) {
        try {
            Path targetPath = getFilePath(filePath);
            Files.deleteIfExists(targetPath);
        } catch (IOException e) {
            throw new RuntimeException("Failed to delete file", e);
        }
    }


}
