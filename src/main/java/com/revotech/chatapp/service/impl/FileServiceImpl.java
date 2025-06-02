package com.revotech.chatapp.service.impl;

import com.revotech.chatapp.exception.AppException;
import com.revotech.chatapp.model.dto.FileMessage;
import com.revotech.chatapp.model.entity.FileAttachment;
import com.revotech.chatapp.model.entity.User;
import com.revotech.chatapp.model.enums.FileType;
import com.revotech.chatapp.repository.FileAttachmentRepository;
import com.revotech.chatapp.repository.UserRepository;
import com.revotech.chatapp.service.FileService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.tika.Tika;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.net.MalformedURLException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class FileServiceImpl implements FileService {

    private final FileAttachmentRepository fileAttachmentRepository;
    private final UserRepository userRepository;

    @Value("${app.storage.upload-dir}")
    private String uploadDir;

    @Value("${app.storage.max-file-size}")
    private long maxFileSize;

    @Value("${app.storage.allowed-types}")
    private String[] allowedTypes;

    private final Tika tika = new Tika();

    @Override
    public FileMessage uploadFile(MultipartFile file, Long userId) {
        validateFile(file);

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new AppException("User not found"));

        try {
            // Create upload directory if it doesn't exist
            Path uploadPath = Paths.get(uploadDir);
            if (!Files.exists(uploadPath)) {
                Files.createDirectories(uploadPath);
            }

            // Generate unique filename
            String originalFilename = StringUtils.cleanPath(file.getOriginalFilename());
            String fileExtension = getFileExtension(originalFilename);
            String uniqueFilename = UUID.randomUUID().toString() + fileExtension;

            // Determine file type
            String mimeType = tika.detect(file.getInputStream(), originalFilename);
            FileType fileType = determineFileType(mimeType);

            // Save file to disk
            Path targetLocation = uploadPath.resolve(uniqueFilename);
            Files.copy(file.getInputStream(), targetLocation, StandardCopyOption.REPLACE_EXISTING);

            // Create file attachment record (without message for now)
            FileAttachment fileAttachment = FileAttachment.builder()
                    .fileName(uniqueFilename)
                    .originalFileName(originalFilename)
                    .filePath(targetLocation.toString())
                    .fileType(fileType)
                    .fileSize(file.getSize())
                    .mimeType(mimeType)
                    .uploadedBy(user)
                    .build();

            // Generate thumbnail for images
            if (fileType == FileType.IMAGE) {
                String thumbnailPath = generateThumbnail(targetLocation.toString(), uniqueFilename);
                fileAttachment.setThumbnailPath(thumbnailPath);
            }

            fileAttachment = fileAttachmentRepository.save(fileAttachment);

            log.info("File {} uploaded by user {}", uniqueFilename, userId);

            return convertToFileMessage(fileAttachment);

        } catch (IOException e) {
            log.error("Failed to upload file", e);
            throw new AppException("Failed to upload file: " + e.getMessage());
        }
    }

    @Override
    @Transactional(readOnly = true)
    public Resource downloadFile(String fileName) {
        try {
            Path filePath = Paths.get(uploadDir).resolve(fileName).normalize();
            Resource resource = new UrlResource(filePath.toUri());

            if (resource.exists() && resource.isReadable()) {
                return resource;
            } else {
                throw new AppException("File not found: " + fileName);
            }
        } catch (MalformedURLException e) {
            log.error("Invalid file path: {}", fileName, e);
            throw new AppException("Invalid file path: " + fileName);
        }
    }

    @Override
    public void deleteFile(String fileName, Long userId) {
        FileAttachment fileAttachment = fileAttachmentRepository.findByFileName(fileName)
                .orElseThrow(() -> new AppException("File not found"));

        // Check if user owns the file
        if (!fileAttachment.getUploadedBy().getId().equals(userId)) {
            throw new AppException("You don't have permission to delete this file");
        }

        try {
            // Delete file from disk
            Path filePath = Paths.get(fileAttachment.getFilePath());
            Files.deleteIfExists(filePath);

            // Delete thumbnail if exists
            if (fileAttachment.getThumbnailPath() != null) {
                Path thumbnailPath = Paths.get(fileAttachment.getThumbnailPath());
                Files.deleteIfExists(thumbnailPath);
            }

            // Delete database record
            fileAttachmentRepository.delete(fileAttachment);

            log.info("File {} deleted by user {}", fileName, userId);

        } catch (IOException e) {
            log.error("Failed to delete file: {}", fileName, e);
            throw new AppException("Failed to delete file: " + e.getMessage());
        }
    }

    @Override
    public String generateThumbnail(String filePath, String fileName) {
        // This is a placeholder implementation
        // In a real application, you would use image processing libraries
        // like ImageIO, Thumbnailator, or integrate with services like ImageMagick

        try {
            Path originalPath = Paths.get(filePath);
            String thumbnailFileName = "thumb_" + fileName;
            Path thumbnailPath = originalPath.getParent().resolve(thumbnailFileName);

            // For now, just copy the original file as thumbnail
            // In production, you would resize the image here
            Files.copy(originalPath, thumbnailPath, StandardCopyOption.REPLACE_EXISTING);

            return thumbnailPath.toString();

        } catch (IOException e) {
            log.warn("Failed to generate thumbnail for {}", fileName, e);
            return null;
        }
    }

    // Helper methods
    private void validateFile(MultipartFile file) {
        if (file.isEmpty()) {
            throw new AppException("File is empty");
        }

        if (file.getSize() > maxFileSize) {
            throw new AppException("File size exceeds maximum allowed size");
        }

        String contentType = file.getContentType();
        if (contentType == null || !isAllowedFileType(contentType)) {
            throw new AppException("File type not allowed");
        }

        String filename = file.getOriginalFilename();
        if (filename == null || filename.contains("..")) {
            throw new AppException("Invalid filename");
        }
    }

    private boolean isAllowedFileType(String contentType) {
        for (String allowedType : allowedTypes) {
            if (contentType.startsWith(allowedType.trim())) {
                return true;
            }
        }
        return false;
    }

    private String getFileExtension(String filename) {
        if (filename == null || !filename.contains(".")) {
            return "";
        }
        return filename.substring(filename.lastIndexOf("."));
    }

    private FileType determineFileType(String mimeType) {
        if (mimeType.startsWith("image/")) {
            return FileType.IMAGE;
        } else if (mimeType.startsWith("audio/")) {
            return FileType.VOICE;
        } else if (mimeType.startsWith("video/")) {
            return FileType.VIDEO;
        } else if (mimeType.equals("application/pdf") ||
                mimeType.startsWith("application/msword") ||
                mimeType.startsWith("application/vnd.openxmlformats-officedocument")) {
            return FileType.DOCUMENT;
        } else {
            return FileType.OTHER;
        }
    }

    private FileMessage convertToFileMessage(FileAttachment fileAttachment) {
        String downloadUrl = "/api/files/" + fileAttachment.getId() + "/download";
        String thumbnailUrl = fileAttachment.getThumbnailPath() != null ?
                "/api/files/" + fileAttachment.getId() + "/thumbnail" : null;

        return FileMessage.builder()
                .id(fileAttachment.getId())
                .fileName(fileAttachment.getFileName())
                .originalFileName(fileAttachment.getOriginalFileName())
                .downloadUrl(downloadUrl)
                .fileType(fileAttachment.getFileType())
                .fileSize(fileAttachment.getFileSize())
                .mimeType(fileAttachment.getMimeType())
                .thumbnailUrl(thumbnailUrl)
                .build();
    }
}