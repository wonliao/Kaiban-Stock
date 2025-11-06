package com.kanban.repository;

import com.kanban.domain.entity.Notification;
import com.kanban.domain.entity.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface NotificationRepository extends JpaRepository<Notification, String> {

    /**
     * 查詢使用者的通知（分頁）
     */
    Page<Notification> findByUserOrderByCreatedAtDesc(User user, Pageable pageable);

    /**
     * 查詢使用者的未讀通知
     */
    List<Notification> findByUserAndIsReadFalseOrderByCreatedAtDesc(User user);

    /**
     * 統計使用者的未讀通知數量
     */
    long countByUserAndIsReadFalse(User user);

    /**
     * 查詢特定類型的通知
     */
    Page<Notification> findByUserAndTypeOrderByCreatedAtDesc(
        User user,
        Notification.NotificationType type,
        Pageable pageable
    );

    /**
     * 查詢特定時間後的通知
     */
    List<Notification> findByUserAndCreatedAtAfterOrderByCreatedAtDesc(
        User user,
        LocalDateTime after
    );

    /**
     * 標記所有通知為已讀
     */
    @Modifying
    @Query("UPDATE Notification n SET n.isRead = true, n.readAt = :readAt " +
           "WHERE n.user = :user AND n.isRead = false")
    int markAllAsRead(@Param("user") User user, @Param("readAt") LocalDateTime readAt);

    /**
     * 刪除舊的通知
     */
    void deleteByCreatedAtBefore(LocalDateTime before);
}
