package com.revotech.chatapp.model.dto;

import com.revotech.chatapp.model.enums.FileType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data @NoArgsConstructor @AllArgsConstructor @Builder
public class FileMessage {
    private Long id;
    private String fileName;
    private String originalFileName;
    private String downloadUrl;
    private FileType fileType;
    private Long fileSize;
    private String mimeType;
    private String thumbnailUrl;
}