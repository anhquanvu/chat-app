package com.revotech.chatapp.service;

import com.revotech.chatapp.model.dto.FileMessage;
import org.springframework.core.io.Resource;
import org.springframework.web.multipart.MultipartFile;

public interface FileService {
    FileMessage uploadFile(MultipartFile file, Long userId);
    Resource downloadFile(String fileName);
    void deleteFile(String fileName, Long userId);
    String generateThumbnail(String filePath, String fileName);
}