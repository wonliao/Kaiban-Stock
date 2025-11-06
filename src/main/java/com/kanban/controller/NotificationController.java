package com.kanban.controller;

import com.kanban.dto.PagedResponse;
import com.kanban.dto.SuccessResponse;
import com.kanban.dto.notification.NotificationDto;
import com.kanban.security.UserPrincipal;
import com.kanban.service.NotificationService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/notifications")
@RequiredArgsConstructor
public class NotificationController {

    private final NotificationService notificationService;

    /**
     * 取得使用者的通知（分頁）
     */
    @GetMapping
    public ResponseEntity<PagedResponse<NotificationDto>> getNotifications(
            @AuthenticationPrincipal UserPrincipal userPrincipal,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        Pageable pageable = PageRequest.of(page, size,
                                          Sort.by(Sort.Direction.DESC, "createdAt"));
        PagedResponse<NotificationDto> notifications =
            notificationService.getUserNotifications(userPrincipal.getId(), pageable);
        return ResponseEntity.ok(notifications);
    }

    /**
     * 取得未讀通知
     */
    @GetMapping("/unread")
    public ResponseEntity<List<NotificationDto>> getUnreadNotifications(
            @AuthenticationPrincipal UserPrincipal userPrincipal) {
        List<NotificationDto> notifications =
            notificationService.getUnreadNotifications(userPrincipal.getId());
        return ResponseEntity.ok(notifications);
    }

    /**
     * 取得未讀通知數量
     */
    @GetMapping("/unread/count")
    public ResponseEntity<Map<String, Long>> getUnreadCount(
            @AuthenticationPrincipal UserPrincipal userPrincipal) {
        long count = notificationService.getUnreadCount(userPrincipal.getId());
        return ResponseEntity.ok(Map.of("count", count));
    }

    /**
     * 標記通知為已讀
     */
    @PutMapping("/{notificationId}/read")
    public ResponseEntity<NotificationDto> markAsRead(
            @AuthenticationPrincipal UserPrincipal userPrincipal,
            @PathVariable String notificationId) {
        NotificationDto notification =
            notificationService.markAsRead(userPrincipal.getId(), notificationId);
        return ResponseEntity.ok(notification);
    }

    /**
     * 標記所有通知為已讀
     */
    @PutMapping("/read-all")
    public ResponseEntity<SuccessResponse> markAllAsRead(
            @AuthenticationPrincipal UserPrincipal userPrincipal) {
        int count = notificationService.markAllAsRead(userPrincipal.getId());
        return ResponseEntity.ok(
            SuccessResponse.of(String.format("已標記 %d 則通知為已讀", count))
        );
    }

    /**
     * 刪除通知
     */
    @DeleteMapping("/{notificationId}")
    public ResponseEntity<SuccessResponse> deleteNotification(
            @AuthenticationPrincipal UserPrincipal userPrincipal,
            @PathVariable String notificationId) {
        notificationService.deleteNotification(userPrincipal.getId(), notificationId);
        return ResponseEntity.ok(SuccessResponse.of("通知已刪除"));
    }
}
