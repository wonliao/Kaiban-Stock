package com.kanban.exception;

public class WatchlistCapacityExceededException extends RuntimeException {
    
    public WatchlistCapacityExceededException(String message) {
        super(message);
    }
    
    public WatchlistCapacityExceededException(String message, Throwable cause) {
        super(message, cause);
    }
}