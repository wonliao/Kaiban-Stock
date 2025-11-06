package com.kanban.controller;

import com.kanban.domain.entity.AuditLog;
import com.kanban.dto.AuditLogDto;
import com.kanban.dto.PagedResponse;
import com.kanban.security.UserPrincipal;
import com.kanban.service.AuditLogService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/audit")
@RequiredArgsConstructor
@Slf4j
public class AuditLogController {
    
    private final AuditLogService auditLogService;
    
    @GetMapping("/logs")
    public ResponseEntity<PagedResponse<AuditLogDto>> getUserAuditLogs(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size,
            Authentication authentication) {
        
        UserPrincipal userPrincipal = (UserPrincipal) authentication.getPrincipal();
        String userId = userPrincipal.getId();
        log.debug("GET /api/audit/logs - user: {}, page: {}, size: {}", userId, page, size);
        
        // Validate page size
        if (size > 100) {
            size = 100;
        }
        
        Pageable pageable = PageRequest.of(page, size);
        Page<AuditLog> auditLogPage = auditLogService.getUserAuditLogs(userId, pageable);
        
        List<AuditLogDto> auditLogDtos = auditLogPage.getContent().stream()
                .map(this::convertToDto)
                .collect(Collectors.toList());
        
        PagedResponse.PaginationInfo paginationInfo = PagedResponse.PaginationInfo.builder()
                .page(auditLogPage.getNumber())
                .size(auditLogPage.getSize())
                .totalElements(auditLogPage.getTotalElements())
                .totalPages(auditLogPage.getTotalPages())
                .hasNext(auditLogPage.hasNext())
                .hasPrevious(auditLogPage.hasPrevious())
                .build();
        
        PagedResponse.MetaInfo metaInfo = PagedResponse.MetaInfo.builder()
                .timestamp(Instant.now())
                .traceId(UUID.randomUUID().toString())
                .version("1.0.0")
                .build();
        
        PagedResponse<AuditLogDto> response = PagedResponse.<AuditLogDto>builder()
                .success(true)
                .data(auditLogDtos)
                .pagination(paginationInfo)
                .meta(metaInfo)
                .build();
        
        return ResponseEntity.ok(response);
    }
    
    @GetMapping("/cards/{cardId}/logs")
    public ResponseEntity<PagedResponse<AuditLogDto>> getCardAuditLogs(
            @PathVariable String cardId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size,
            Authentication authentication) {
        
        UserPrincipal userPrincipal = (UserPrincipal) authentication.getPrincipal();
        String userId = userPrincipal.getId();
        log.debug("GET /api/audit/cards/{}/logs - user: {}, page: {}, size: {}", cardId, userId, page, size);
        
        // Validate page size
        if (size > 100) {
            size = 100;
        }
        
        Pageable pageable = PageRequest.of(page, size);
        Page<AuditLog> auditLogPage = auditLogService.getCardAuditLogs(cardId, pageable);
        
        List<AuditLogDto> auditLogDtos = auditLogPage.getContent().stream()
                .map(this::convertToDto)
                .collect(Collectors.toList());
        
        PagedResponse.PaginationInfo paginationInfo = PagedResponse.PaginationInfo.builder()
                .page(auditLogPage.getNumber())
                .size(auditLogPage.getSize())
                .totalElements(auditLogPage.getTotalElements())
                .totalPages(auditLogPage.getTotalPages())
                .hasNext(auditLogPage.hasNext())
                .hasPrevious(auditLogPage.hasPrevious())
                .build();
        
        PagedResponse.MetaInfo metaInfo = PagedResponse.MetaInfo.builder()
                .timestamp(Instant.now())
                .traceId(UUID.randomUUID().toString())
                .version("1.0.0")
                .build();
        
        PagedResponse<AuditLogDto> response = PagedResponse.<AuditLogDto>builder()
                .success(true)
                .data(auditLogDtos)
                .pagination(paginationInfo)
                .meta(metaInfo)
                .build();
        
        return ResponseEntity.ok(response);
    }
    
    @PostMapping("/archive")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<String> archiveOldLogs(Authentication authentication) {
        UserPrincipal userPrincipal = (UserPrincipal) authentication.getPrincipal();
        String userId = userPrincipal.getId();
        log.info("POST /api/audit/archive - admin user: {}", userId);
        
        // Archive logs older than 90 days
        java.time.LocalDateTime cutoffDate = java.time.LocalDateTime.now().minusDays(90);
        auditLogService.archiveOldLogs(cutoffDate);
        
        return ResponseEntity.ok("稽核日誌封存完成");
    }
    
    private AuditLogDto convertToDto(AuditLog auditLog) {
        return AuditLogDto.builder()
                .id(auditLog.getId())
                .userId(auditLog.getUserId())
                .cardId(auditLog.getCardId())
                .action(auditLog.getAction())
                .fromStatus(auditLog.getFromStatus())
                .toStatus(auditLog.getToStatus())
                .reason(auditLog.getReason())
                .createdAt(auditLog.getCreatedAt())
                .traceId(auditLog.getTraceId())
                .ipAddress(auditLog.getIpAddress())
                .build();
    }
}