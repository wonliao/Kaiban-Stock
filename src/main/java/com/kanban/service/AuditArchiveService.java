package com.kanban.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
@Slf4j
@ConditionalOnProperty(name = "kanban.audit.auto-archive.enabled", havingValue = "true", matchIfMissing = true)
public class AuditArchiveService {
    
    private final AuditLogService auditLogService;
    
    /**
     * 自動封存90天前的稽核日誌
     * 每天凌晨2點執行
     */
    @Scheduled(cron = "0 0 2 * * ?")
    public void archiveOldAuditLogs() {
        log.info("Starting automatic audit log archiving...");
        
        try {
            LocalDateTime cutoffDate = LocalDateTime.now().minusDays(90);
            auditLogService.archiveOldLogs(cutoffDate);
            log.info("Automatic audit log archiving completed successfully");
        } catch (Exception e) {
            log.error("Failed to archive old audit logs", e);
        }
    }
    
    /**
     * 每週日凌晨3點檢查稽核日誌統計
     */
    @Scheduled(cron = "0 0 3 ? * SUN")
    public void generateAuditStatistics() {
        log.info("Generating audit log statistics...");
        
        try {
            LocalDateTime oneWeekAgo = LocalDateTime.now().minusDays(7);
            // 這裡可以添加統計邏輯，例如計算一週內的操作次數等
            log.info("Audit log statistics generation completed");
        } catch (Exception e) {
            log.error("Failed to generate audit statistics", e);
        }
    }
}