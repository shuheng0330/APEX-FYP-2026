package com.tbm.careerpathlearning.service;

import org.springframework.web.multipart.MultipartFile;

import java.nio.file.Path;

public interface FileService {

    String uploadFile(MultipartFile file, String folderName);

    void deleteFile(String filePath);

    Path getFilePath(String relativePath);
}
