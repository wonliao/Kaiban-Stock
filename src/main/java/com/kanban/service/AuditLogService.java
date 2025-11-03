package com.kanban.service;

import com.kanban.domain.entity.AuditLog;
import com.kanban.domain.entity.Card;
import com.kanban.repository.AuditLogRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import jakarta.servlet.http.HttpServletRequest;
import java.time.LocalDateTime;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class AuditLogService {
    
    private final AuditLogRepository auditLogRepository;
    
    @Transactional
    public void logCardStatusChange(String userId, String cardId, Card.CardStatus fromStatus, 
                                   Card.CardStatus toStatus, String reason) {
        log.debug("Logging card status change: user={}, card={}, {}→{}", userId, cardId, fromStatus, toStatus);
        
        AuditLog auditLog = AuditLog.builder()
                .userId(userId)
                .cardId(cardId)
                .action("CARD_STATUS_CHANGE")
                .fromStatus(fromStatus != null ? fromStatus.name() : null)
                .toStatus(toStatus != null ? toStatus.name() : null)
                .reason(reason)
                .traceId(UUID.randomUUID().toString())
                .build();
        
        // Try to get request context for IP and User-Agent
        try {
            ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
            if (attributes != null) {
                HttpServletRequest request = attributes.getRequest();
                auditLog.setIpAddress(getClientIpAddress(request));
                auditLog.setUserAgent(request.getHeader("User-Agent"));
            }
        } catch (Exception e) {
            log.debug("Could not get request context for audit log: {}", e.getMessage());
        }
        
        auditLogRepository.save(auditLog);
        log.info("Logged audit event: {}", auditLog.getId());
    }
    
    @Transactional
    public void logWatchlistChange(String userId, String action, String stockCode, String reason) {
        log.debug("Logging watchlist change: user={}, action={}, stock={}", userId, action, stockCode);
        
        AuditLog auditLog = AuditLog.builder()
                .userId(userId)
                .action(action) // ADD_STOCK, REMOVE_STOCK, CREATE_WATCHLIST, DELETE_WATCHLIST
                .reason(reason != null ? reason : stockCode)
                .traceId(UUID.randomUUID().toString())
                .build();
        
        // Try to get request context
        try {
            ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
            if (attributes != null) {
                HttpServletRequest request = attributes.getRequest();
                auditLog.setIpAddress(getClientIpAddress(request));
                auditLog.setUserAgent(request.getHeader("User-Agent"));
            }
        } catch (Exception e) {
            log.debug("Could not get request context for audit log: {}", e.getMessage());
        }
        
        auditLogRepository.save(auditLog);
        log.info("Logged audit event: {}", auditLog.getId());
    }
    
    @Transactional(readOnly = true)
    public Page<AuditLog> getUserAuditLogs(String userId, Pageable pageable) {
        return auditLogRepository.findByUserIdOrderByCreatedAtDesc(userId, pageable);
    }
    
    @Transactional(readOnly = true)
    public Page<AuditLog> getCardAuditLogs(String cardId, Pageable pageable) {
        return auditLogRepository.findByCardIdOrderByCreatedAtDesc(cardId, pageable);
    }
    
    @Transactional
    public void archiveOldLogs(LocalDateTime cutoffDate) {
        log.info("Archiving audit logs older than: {}", cutoffDate);
        long archivedCount = auditLogRepository.deleteByCreatedAtBefore(cutoffDate);
        log.info("Archived {} audit log entries", archivedCount);
    }
    
    private String getClientIpAddress(HttpServletRequest request) {
        String xForwardedFor = request.getHeader("X-Forwarded-For");
        if (xForwardedFor != null && !xForwardedFor.isEmpty()) {
            return xForwardedFor.split(",")[0].trim();
        }
        
        String xRealIp = request.getHeader("X-Real-IP");
        if (xRealIp != null && !xRealIp.isEmpty()) {
            return xRealIp;
        }
        
        return request.getRemoteAddr();
    }
}