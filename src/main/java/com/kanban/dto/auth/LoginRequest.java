package com.kanban.dto.auth;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.AssertTrue;
import lombok.Data;

@Data
public class LoginRequest {
    
    private String username; // 可以是 username 或 email
    
    @NotBlank(message = "密碼不能為空")
    private String password;
    
    // 為了相容前端，也接受 email 欄位
    private String email;
    
    // 自訂驗證：username 或 email 至少要有一個
    @AssertTrue(message = "使用者名稱或電子郵件不能為空")
    public boolean isLoginIdentifierValid() {
        return (username != null && !username.trim().isEmpty()) || 
               (email != null && !email.trim().isEmpty());
    }
    
    // 取得實際的登入識別符（優先使用 email，否則使用 username）
    public String getLoginIdentifier() {
        return email != null && !email.trim().isEmpty() ? email : username;
    }
}