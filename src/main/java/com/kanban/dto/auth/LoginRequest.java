package com.kanban.dto.auth;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class LoginRequest {
    
    @NotBlank(message = "使用者名稱不能為空")
    private String username;
    
    @NotBlank(message = "密碼不能為空")
    private String password;
}