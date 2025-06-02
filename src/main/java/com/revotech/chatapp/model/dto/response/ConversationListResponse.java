package com.revotech.chatapp.model.dto.response;

import com.revotech.chatapp.model.dto.ConversationDTO;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data @NoArgsConstructor @AllArgsConstructor @Builder
public class ConversationListResponse {
    private List<ConversationDTO> conversations;
    private Long totalUnreadCount;
    private Integer currentPage;
    private Integer totalPages;
    private Long totalElements;
}