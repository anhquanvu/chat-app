package com.revotech.chatapp.model.dto.response;

import com.revotech.chatapp.model.dto.UserSummaryDTO;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data @NoArgsConstructor @AllArgsConstructor @Builder
public class UserListResponse {
    private List<UserSummaryDTO> users;
    private Integer currentPage;
    private Integer totalPages;
    private Long totalElements;
}
