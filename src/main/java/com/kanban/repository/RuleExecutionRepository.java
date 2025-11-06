package com.kanban.repository;

import com.kanban.domain.entity.Card;
import com.kanban.domain.entity.Rule;
import com.kanban.domain.entity.RuleExecution;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface RuleExecutionRepository extends JpaRepository<RuleExecution, String> {

    /**
     * 查詢規則的執行歷史（分頁）
     */
    Page<RuleExecution> findByRule(Rule rule, Pageable pageable);

    /**
     * 查詢卡片的執行歷史（分頁）
     */
    Page<RuleExecution> findByCard(Card card, Pageable pageable);

    /**
     * 查詢規則的最近一次執行
     */
    Optional<RuleExecution> findFirstByRuleOrderByExecutedAtDesc(Rule rule);

    /**
     * 查詢規則對特定卡片的最近一次執行
     */
    Optional<RuleExecution> findFirstByRuleAndCardOrderByExecutedAtDesc(Rule rule, Card card);

    /**
     * 查詢規則在特定時間後的執行記錄
     */
    List<RuleExecution> findByRuleAndExecutedAtAfter(Rule rule, LocalDateTime after);

    /**
     * 統計規則的成功執行次數
     */
    @Query("SELECT COUNT(e) FROM RuleExecution e WHERE e.rule = :rule AND e.status = 'SUCCESS'")
    long countSuccessfulExecutions(@Param("rule") Rule rule);

    /**
     * 統計規則的失敗執行次數
     */
    @Query("SELECT COUNT(e) FROM RuleExecution e WHERE e.rule = :rule AND e.status = 'FAILED'")
    long countFailedExecutions(@Param("rule") Rule rule);

    /**
     * 查詢特定時間範圍內的執行記錄
     */
    @Query("SELECT e FROM RuleExecution e WHERE e.executedAt BETWEEN :startTime AND :endTime " +
           "ORDER BY e.executedAt DESC")
    List<RuleExecution> findExecutionsBetween(
        @Param("startTime") LocalDateTime startTime,
        @Param("endTime") LocalDateTime endTime
    );

    /**
     * 查詢待發送通知的執行記錄
     */
    @Query("SELECT e FROM RuleExecution e WHERE e.status = 'SUCCESS' " +
           "AND e.notificationSent = false AND e.rule.sendNotification = true " +
           "ORDER BY e.executedAt ASC")
    List<RuleExecution> findPendingNotifications();

    /**
     * 刪除舊的執行記錄
     */
    void deleteByExecutedAtBefore(LocalDateTime before);
}
