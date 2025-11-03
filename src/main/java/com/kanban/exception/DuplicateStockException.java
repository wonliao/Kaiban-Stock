package com.kanban.exception;

public class DuplicateStockException extends RuntimeException {
    
    public DuplicateStockException(String message) {
        super(message);
    }
    
    public DuplicateStockException(String message, Throwable cause) {
        super(message, cause);
    }
}