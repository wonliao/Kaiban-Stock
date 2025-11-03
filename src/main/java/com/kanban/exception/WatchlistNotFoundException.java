package com.kanban.exception;

public class WatchlistNotFoundException extends RuntimeException {
    
    public WatchlistNotFoundException(String message) {
        super(message);
    }
    
    public WatchlistNotFoundException(String message, Throwable cause) {
        super(message, cause);
    }
}