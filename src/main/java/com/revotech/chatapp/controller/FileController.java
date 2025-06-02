package com.revotech.chatapp.controller;

import com.revotech.chatapp.model.dto.FileMessage;
import com.revotech.chatapp.security.UserPrincipal;
import com.revotech.chatapp.service.FileService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import jakarta.servlet.http.HttpServletRequest;
import java.io.IOException;

@RestController
@RequestMapping("/api/files")
@RequiredArgsConstructor
@Slf4j
public class FileController {

    private final FileService fileService;

    @PostMapping("/upload")
    public ResponseEntity<FileMessage> uploadFile(
            @RequestParam("file") MultipartFile file,
            @AuthenticationPrincipal UserPrincipal currentUser) {
        FileMessage fileMessage = fileService.uploadFile(file, currentUser.getId());
        return ResponseEntity.ok(fileMessage);
    }

    @GetMapping("/{fileId}")
    public ResponseEntity<FileMessage> getFileInfo(
            @PathVariable Long fileId) {
        // This would require a method in FileService to get file info by ID
        // For now, return a basic response
        return ResponseEntity.ok().build();
    }

    @GetMapping("/{fileName}/download")
    public ResponseEntity<Resource> downloadFile(
            @PathVariable String fileName,
            HttpServletRequest request) {

        Resource resource = fileService.downloadFile(fileName);

        // Try to determine file's content type
        String contentType = null;
        try {
            contentType = request.getServletContext().getMimeType(resource.getFile().getAbsolutePath());
        } catch (IOException ex) {
            log.info("Could not determine file type.");
        }

        // Fallback to the default content type if type could not be determined
        if (contentType == null) {
            contentType = "application/octet-stream";
        }

        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(contentType))
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + resource.getFilename() + "\"")
                .body(resource);
    }

    @DeleteMapping("/{fileName}")
    public ResponseEntity<Void> deleteFile(
            @PathVariable String fileName,
            @AuthenticationPrincipal UserPrincipal currentUser) {
        fileService.deleteFile(fileName, currentUser.getId());
        return ResponseEntity.ok().build();
    }

    @GetMapping("/{fileName}/thumbnail")
    public ResponseEntity<Resource> getThumbnail(
            @PathVariable String fileName,
            HttpServletRequest request) {

        // This would require modifications to FileService to handle thumbnails
        String thumbnailFileName = "thumb_" + fileName;
        Resource resource = fileService.downloadFile(thumbnailFileName);

        return ResponseEntity.ok()
                .contentType(MediaType.IMAGE_JPEG)
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + thumbnailFileName + "\"")
                .body(resource);
    }
}