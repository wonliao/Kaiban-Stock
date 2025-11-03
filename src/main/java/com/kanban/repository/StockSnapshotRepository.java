package com.kanban.repository;

import com.kanban.domain.entity.StockSnapshot;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * 股票快照資料存取層
 */
@Repository
public interface StockSnapshotRepository extends JpaRepository<StockSnapshot, String> {
    
    /**
     * 根據更新時間查詢股票快照
     */
    @Query("SELECT s FROM StockSnapshot s WHERE s.updatedAt >= :since ORDER BY s.updatedAt DESC")
    List<StockSnapshot> findByUpdatedAtAfter(@Param("since") LocalDateTime since);
    
    /**
     * 查詢指定時間內更新的股票快照
     */
    @Query("SELECT s FROM StockSnapshot s WHERE s.updatedAt BETWEEN :start AND :end ORDER BY s.updatedAt DESC")
    List<StockSnapshot> findByUpdatedAtBetween(@Param("start") LocalDateTime start, @Param("end") LocalDateTime end);
    
    /**
     * 查詢過時的股票快照（超過指定分鐘數未更新）
     */
    @Query("SELECT s FROM StockSnapshot s WHERE s.updatedAt < :threshold")
    List<StockSnapshot> findStaleSnapshots(@Param("threshold") LocalDateTime threshold);
    
    /**
     * 根據股票代碼列表查詢快照
     */
    @Query("SELECT s FROM StockSnapshot s WHERE s.code IN :codes ORDER BY s.code")
    List<StockSnapshot> findByCodeIn(@Param("codes") List<String> codes);
    
    /**
     * 查詢最近更新的股票快照
     */
    @Query("SELECT s FROM StockSnapshot s ORDER BY s.updatedAt DESC")
    List<StockSnapshot> findRecentSnapshots();
    
    /**
     * 根據資料來源查詢
     */
    List<StockSnapshot> findByDataSource(String dataSource);
    
    /**
     * 查詢特定股票的最新快照
     */
    @Query("SELECT s FROM StockSnapshot s WHERE s.code = :code ORDER BY s.updatedAt DESC LIMIT 1")
    Optional<StockSnapshot> findLatestByCode(@Param("code") String code);
}