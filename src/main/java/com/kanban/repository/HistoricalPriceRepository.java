package com.kanban.repository;

import com.kanban.domain.entity.HistoricalPrice;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * 歷史價格資料存取層
 */
@Repository
public interface HistoricalPriceRepository extends JpaRepository<HistoricalPrice, String> {
    
    /**
     * 查詢指定股票的歷史價格（按日期降序）
     */
    @Query("SELECT h FROM HistoricalPrice h WHERE h.stockCode = :stockCode ORDER BY h.tradeDate DESC")
    List<HistoricalPrice> findByStockCodeOrderByTradeDateDesc(@Param("stockCode") String stockCode);
    
    /**
     * 查詢指定股票在日期範圍內的歷史價格
     */
    @Query("SELECT h FROM HistoricalPrice h WHERE h.stockCode = :stockCode " +
           "AND h.tradeDate BETWEEN :startDate AND :endDate ORDER BY h.tradeDate DESC")
    List<HistoricalPrice> findByStockCodeAndDateRange(
            @Param("stockCode") String stockCode,
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate);
    
    /**
     * 查詢指定股票最近 N 天的歷史價格
     */
    @Query("SELECT h FROM HistoricalPrice h WHERE h.stockCode = :stockCode ORDER BY h.tradeDate DESC LIMIT :limit")
    List<HistoricalPrice> findRecentByStockCode(@Param("stockCode") String stockCode, @Param("limit") int limit);
    
    /**
     * 查詢指定股票的最新價格
     */
    @Query("SELECT h FROM HistoricalPrice h WHERE h.stockCode = :stockCode ORDER BY h.tradeDate DESC LIMIT 1")
    Optional<HistoricalPrice> findLatestByStockCode(@Param("stockCode") String stockCode);
    
    /**
     * 檢查指定股票在指定日期是否有資料
     */
    boolean existsByStockCodeAndTradeDate(String stockCode, LocalDate tradeDate);
    
    /**
     * 刪除指定日期之前的舊資料
     */
    void deleteByTradeDateBefore(LocalDate date);
    
    /**
     * 查詢所有有歷史資料的股票代碼
     */
    @Query("SELECT DISTINCT h.stockCode FROM HistoricalPrice h")
    List<String> findAllStockCodes();
}