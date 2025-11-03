package com.kanban.exception;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {
    
    @ExceptionHandler(BadCredentialsException.class)
    public ResponseEntity<Map<String, Object>> handleBadCredentials(BadCredentialsException ex) {
        return createErrorResponse(
                "INVALID_CREDENTIALS",
                "使用者名稱或密碼錯誤",
                "請檢查您的登入資訊",
                HttpStatus.UNAUTHORIZED
        );
    }
    
    @ExceptionHandler(UsernameNotFoundException.class)
    public ResponseEntity<Map<String, Object>> handleUserNotFound(UsernameNotFoundException ex) {
        return createErrorResponse(
                "USER_NOT_FOUND",
                "使用者不存在",
                "請檢查使用者名稱是否正確",
                HttpStatus.NOT_FOUND
        );
    }
    
    @ExceptionHandler(StockNotFoundException.class)
    public ResponseEntity<Map<String, Object>> handleStockNotFound(StockNotFoundException ex) {
        return createErrorResponse(
                "STOCK_NOT_FOUND",
                "指定的股票代碼不存在",
                "請檢查股票代碼是否正確",
                HttpStatus.NOT_FOUND
        );
    }
    
    @ExceptionHandler(TwseMcpException.class)
    public ResponseEntity<Map<String, Object>> handleTwseMcpError(TwseMcpException ex) {
        return createErrorResponse(
                ex.getErrorCode(),
                "股票資料服務暫時無法使用",
                "請稍後再試，或查看快取資料",
                HttpStatus.valueOf(ex.getHttpStatus())
        );
    }
    
    @ExceptionHandler(WatchlistNotFoundException.class)
    public ResponseEntity<Map<String, Object>> handleWatchlistNotFound(WatchlistNotFoundException ex) {
        return createErrorResponse(
                "WATCHLIST_NOT_FOUND",
                "觀察清單不存在",
                "請檢查觀察清單ID是否正確",
                HttpStatus.NOT_FOUND
        );
    }
    
    @ExceptionHandler(WatchlistCapacityExceededException.class)
    public ResponseEntity<Map<String, Object>> handleWatchlistCapacityExceeded(WatchlistCapacityExceededException ex) {
        return createErrorResponse(
                "WATCHLIST_CAPACITY_EXCEEDED",
                ex.getMessage(),
                "請移除部分股票後再新增",
                HttpStatus.BAD_REQUEST
        );
    }
    
    @ExceptionHandler(DuplicateStockException.class)
    public ResponseEntity<Map<String, Object>> handleDuplicateStock(DuplicateStockException ex) {
        return createErrorResponse(
                "DUPLICATE_STOCK",
                ex.getMessage(),
                "該股票已在您的觀察清單中",
                HttpStatus.CONFLICT
        );
    }
    
    @ExceptionHandler(RuntimeException.class)
    public ResponseEntity<Map<String, Object>> handleRuntimeException(RuntimeException ex) {
        log.error("Runtime exception occurred", ex);
        
        // 處理特定的業務邏輯錯誤
        String message = ex.getMessage();
        if (message.contains("使用者名稱已存在")) {
            return createErrorResponse(
                    "USERNAME_EXISTS",
                    "使用者名稱已存在",
                    "請選擇其他使用者名稱",
                    HttpStatus.CONFLICT
            );
        } else if (message.contains("電子郵件已存在")) {
            return createErrorResponse(
                    "EMAIL_EXISTS",
                    "電子郵件已存在",
                    "請使用其他電子郵件地址",
                    HttpStatus.CONFLICT
            );
        } else if (message.contains("無效的 refresh token") || message.contains("已過期")) {
            return createErrorResponse(
                    "INVALID_REFRESH_TOKEN",
                    "Refresh token 無效或已過期",
                    "請重新登入",
                    HttpStatus.UNAUTHORIZED
            );
        }
        
        return createErrorResponse(
                "INTERNAL_ERROR",
                "系統內部錯誤",
                "請稍後再試",
                HttpStatus.INTERNAL_SERVER_ERROR
        );
    }
    
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> handleValidationExceptions(MethodArgumentNotValidException ex) {
        Map<String, String> errors = new HashMap<>();
        ex.getBindingResult().getAllErrors().forEach((error) -> {
            String fieldName = ((FieldError) error).getField();
            String errorMessage = error.getDefaultMessage();
            errors.put(fieldName, errorMessage);
        });
        
        Map<String, Object> response = new HashMap<>();
        response.put("success", false);
        
        Map<String, Object> error = new HashMap<>();
        error.put("code", "VALIDATION_ERROR");
        error.put("message", "輸入資料驗證失敗");
        error.put("hint", "請檢查輸入資料格式");
        error.put("details", errors);
        error.put("traceId", UUID.randomUUID().toString());
        error.put("timestamp", Instant.now().toString());
        
        response.put("error", error);
        
        return ResponseEntity.badRequest().body(response);
    }
    
    private ResponseEntity<Map<String, Object>> createErrorResponse(String code, String message, String hint, HttpStatus status) {
        Map<String, Object> response = new HashMap<>();
        response.put("success", false);
        
        Map<String, Object> error = new HashMap<>();
        error.put("code", code);
        error.put("message", message);
        error.put("hint", hint);
        error.put("traceId", UUID.randomUUID().toString());
        error.put("timestamp", Instant.now().toString());
        
        response.put("error", error);
        
        return ResponseEntity.status(status).body(response);
    }
}