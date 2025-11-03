package com.kanban.repository;

import com.kanban.domain.entity.TechnicalIndicator;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * 技術指標資料存取層
 */
@Repository
public interface TechnicalIndicatorRepository extends JpaRepository<TechnicalIndicator, String> {
    
    /**
     * 查詢指定股票的最新技術指標
     */
    @Query("SELECT t FROM TechnicalIndicator t WHERE t.stockCode = :stockCode ORDER BY t.calculationDate DESC LIMIT 1")
    Optional<TechnicalIndicator> findLatestByStockCode(@Param("stockCode") String stockCode);
    
    /**
     * 查詢指定股票在時間範圍內的技術指標
     */
    @Query("SELECT t FROM TechnicalIndicator t WHERE t.stockCode = :stockCode " +
           "AND t.calculationDate BETWEEN :startDate AND :endDate ORDER BY t.calculationDate DESC")
    List<TechnicalIndicator> findByStockCodeAndDateRange(
            @Param("stockCode") String stockCode,
            @Param("startDate") LocalDateTime startDate,
            @Param("endDate") LocalDateTime endDate);
    
    /**
     * 查詢需要更新的技術指標（超過指定時間未更新）
     */
    @Query("SELECT DISTINCT t.stockCode FROM TechnicalIndicator t WHERE t.updatedAt < :threshold")
    List<String> findStockCodesNeedingUpdate(@Param("threshold") LocalDateTime threshold);
    
    /**
     * 查詢指定股票代碼列表的最新技術指標
     */
    @Query("SELECT t FROM TechnicalIndicator t WHERE t.stockCode IN :stockCodes " +
           "AND t.calculationDate = (SELECT MAX(t2.calculationDate) FROM TechnicalIndicator t2 WHERE t2.stockCode = t.stockCode)")
    List<TechnicalIndicator> findLatestByStockCodes(@Param("stockCodes") List<String> stockCodes);
    
    /**
     * 刪除指定日期之前的舊資料
     */
    void deleteByCalculationDateBefore(LocalDateTime date);
}