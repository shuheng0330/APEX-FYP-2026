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
            Path targetDir = Paths.get(FILE_UPLOAD_DIR, folderName); // file dir with folder name ( join safely)
            Files.createDirectories(targetDir);

            String originalFileName = file.getOriginalFilename();
            String uniqueFileName = UUID.randomUUID() + "-" + originalFileName;

            Path targetPath = targetDir.resolve(uniqueFileName);
            Files.copy(file.getInputStream(), targetPath, StandardCopyOption.REPLACE_EXISTING);

            return folderName + "/" + uniqueFileName;

        } catch (IOException e) {
            throw new RuntimeException("Failed to store file", e);
        }
    }

    @Override
    public Path getFilePath(String relativePath) {
        return Paths.get(FILE_UPLOAD_DIR).resolve(relativePath).normalize();
    }

    @Override
    public void deleteFile(String filePath) {
        try {
            Path targetPath = Paths.get(FILE_UPLOAD_DIR).resolve(filePath).normalize();
            Files.deleteIfExists(targetPath);
        } catch (IOException e) {
            throw new RuntimeException("Failed to delete file", e);
        }
    }


}
