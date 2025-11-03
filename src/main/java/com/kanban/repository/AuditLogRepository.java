package com.kanban.repository;

import com.kanban.domain.entity.AuditLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;

@Repository
public interface AuditLogRepository extends JpaRepository<AuditLog, String> {
    
    Page<AuditLog> findByUserIdOrderByCreatedAtDesc(String userId, Pageable pageable);
    
    Page<AuditLog> findByCardIdOrderByCreatedAtDesc(String cardId, Pageable pageable);
    
    @Modifying
    @Query("DELETE FROM AuditLog a WHERE a.createdAt < :cutoffDate")
    long deleteByCreatedAtBefore(@Param("cutoffDate") LocalDateTime cutoffDate);
    
    @Query("SELECT COUNT(a) FROM AuditLog a WHERE a.createdAt < :cutoffDate")
    long countByCreatedAtBefore(@Param("cutoffDate") LocalDateTime cutoffDate);
}