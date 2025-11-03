package com.kanban.exception;

/**
 * TWSE-MCP 服務相關異常
 */
public class TwseMcpException extends RuntimeException {
    
    private final String errorCode;
    private final int httpStatus;
    
    public TwseMcpException(String message) {
        super(message);
        this.errorCode = "TWSE_MCP_ERROR";
        this.httpStatus = 503;
    }
    
    public TwseMcpException(String message, Throwable cause) {
        super(message, cause);
        this.errorCode = "TWSE_MCP_ERROR";
        this.httpStatus = 503;
    }
    
    public TwseMcpException(String errorCode, String message, int httpStatus) {
        super(message);
        this.errorCode = errorCode;
        this.httpStatus = httpStatus;
    }
    
    public TwseMcpException(String errorCode, String message, int httpStatus, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
        this.httpStatus = httpStatus;
    }
    
    public String getErrorCode() {
        return errorCode;
    }
    
    public int getHttpStatus() {
        return httpStatus;
    }
}