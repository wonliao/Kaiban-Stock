package com.kanban.repository;

import com.kanban.domain.entity.Card;
import com.kanban.domain.entity.Rule;
import com.kanban.domain.entity.User;
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
public interface RuleRepository extends JpaRepository<Rule, String> {

    /**
     * 查詢使用者的所有規則（分頁）
     */
    Page<Rule> findByUser(User user, Pageable pageable);

    /**
     * 查詢使用者的啟用規則
     */
    List<Rule> findByUserAndEnabledTrue(User user);

    /**
     * 查詢特定類型和觸發事件的啟用規則
     */
    @Query("SELECT r FROM Rule r WHERE r.user = :user AND r.enabled = true " +
           "AND r.triggerEvent = :triggerEvent ORDER BY r.priority ASC, r.createdAt ASC")
    List<Rule> findActiveRulesByTriggerEvent(
        @Param("user") User user,
        @Param("triggerEvent") Rule.TriggerEvent triggerEvent
    );

    /**
     * 查詢所有需要執行的規則（排除冷卻期內的規則）
     */
    @Query("SELECT r FROM Rule r WHERE r.enabled = true " +
           "AND (r.lastExecutedAt IS NULL OR r.lastExecutedAt < :cooldownThreshold) " +
           "ORDER BY r.priority ASC, r.createdAt ASC")
    List<Rule> findRulesReadyForExecution(@Param("cooldownThreshold") LocalDateTime cooldownThreshold);

    /**
     * 查詢使用者特定類型的規則
     */
    List<Rule> findByUserAndRuleType(User user, Rule.RuleType ruleType);

    /**
     * 查詢使用者特定名稱的規則
     */
    Optional<Rule> findByUserAndName(User user, String name);

    /**
     * 統計使用者的規則數量
     */
    long countByUser(User user);

    /**
     * 統計使用者的啟用規則數量
     */
    long countByUserAndEnabledTrue(User user);
}
