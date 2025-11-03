package com.kanban.service;

import com.kanban.domain.entity.AuditLog;
import com.kanban.domain.entity.Card;
import com.kanban.repository.AuditLogRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuditLogServiceTest {
    
    @Mock
    private AuditLogRepository auditLogRepository;
    
    @InjectMocks
    private AuditLogService auditLogService;
    
    private AuditLog testAuditLog;
    
    @BeforeEach
    void setUp() {
        testAuditLog = AuditLog.builder()
                .id("audit1")
                .userId("user1")
                .cardId("card1")
                .action("CARD_STATUS_CHANGE")
                .fromStatus("WATCH")
                .toStatus("READY_TO_BUY")
                .reason("手動更新")
                .createdAt(LocalDateTime.now())
                .traceId("trace123")
                .build();
    }
    
    @Test
    void logCardStatusChange_ShouldSaveAuditLog() {
        // Given
        when(auditLogRepository.save(any(AuditLog.class))).thenReturn(testAuditLog);
        
        // When
        auditLogService.logCardStatusChange(
                "user1", 
                "card1", 
                Card.CardStatus.WATCH, 
                Card.CardStatus.READY_TO_BUY, 
                "手動更新"
        );
        
        // Then
        verify(auditLogRepository).save(argThat(auditLog -> 
                auditLog.getUserId().equals("user1") &&
                auditLog.getCardId().equals("card1") &&
                auditLog.getAction().equals("CARD_STATUS_CHANGE") &&
                auditLog.getFromStatus().equals("WATCH") &&
                auditLog.getToStatus().equals("READY_TO_BUY") &&
                auditLog.getReason().equals("手動更新")
        ));
    }
    
    @Test
    void logWatchlistChange_ShouldSaveAuditLog() {
        // Given
        when(auditLogRepository.save(any(AuditLog.class))).thenReturn(testAuditLog);
        
        // When
        auditLogService.logWatchlistChange("user1", "ADD_STOCK", "2330", "新增股票至觀察清單");
        
        // Then
        verify(auditLogRepository).save(argThat(auditLog -> 
                auditLog.getUserId().equals("user1") &&
                auditLog.getAction().equals("ADD_STOCK") &&
                auditLog.getReason().equals("新增股票至觀察清單")
        ));
    }
    
    @Test
    void getUserAuditLogs_ShouldReturnPagedResults() {
        // Given
        Pageable pageable = PageRequest.of(0, 10);
        Page<AuditLog> auditLogPage = new PageImpl<>(List.of(testAuditLog));
        when(auditLogRepository.findByUserIdOrderByCreatedAtDesc("user1", pageable))
                .thenReturn(auditLogPage);
        
        // When
        Page<AuditLog> result = auditLogService.getUserAuditLogs("user1", pageable);
        
        // Then
        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().get(0).getUserId()).isEqualTo("user1");
    }
    
    @Test
    void getCardAuditLogs_ShouldReturnPagedResults() {
        // Given
        Pageable pageable = PageRequest.of(0, 10);
        Page<AuditLog> auditLogPage = new PageImpl<>(List.of(testAuditLog));
        when(auditLogRepository.findByCardIdOrderByCreatedAtDesc("card1", pageable))
                .thenReturn(auditLogPage);
        
        // When
        Page<AuditLog> result = auditLogService.getCardAuditLogs("card1", pageable);
        
        // Then
        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().get(0).getCardId()).isEqualTo("card1");
    }
    
    @Test
    void archiveOldLogs_ShouldDeleteOldEntries() {
        // Given
        LocalDateTime cutoffDate = LocalDateTime.now().minusDays(90);
        when(auditLogRepository.deleteByCreatedAtBefore(cutoffDate)).thenReturn(100L);
        
        // When
        auditLogService.archiveOldLogs(cutoffDate);
        
        // Then
        verify(auditLogRepository).deleteByCreatedAtBefore(cutoffDate);
    }
}