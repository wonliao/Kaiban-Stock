package com.kanban.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kanban.domain.entity.Notification;
import com.kanban.domain.entity.User;
import com.kanban.dto.PagedResponse;
import com.kanban.dto.notification.NotificationDto;
import com.kanban.dto.notification.RuleNotificationEvent;
import com.kanban.exception.ResourceNotFoundException;
import com.kanban.repository.NotificationRepository;
import com.kanban.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class NotificationService {

    private final NotificationRepository notificationRepository;
    private final UserRepository userRepository;
    private final WebSocketNotificationService webSocketService;
    private final ObjectMapper objectMapper;

    /**
     * 建立規則觸發通知
     */
    @Transactional
    @Async
    public void createRuleTriggeredNotification(RuleNotificationEvent event) {
        try {
            User user = userRepository.findById(event.getUserId())
                .orElseThrow(() -> new ResourceNotFoundException("使用者不存在"));

            String title = String.format("規則觸發: %s", event.getRuleName());
            String message = String.format(
                "股票 %s (%s) 觸發規則 '%s'，狀態從 %s 變更為 %s",
                event.getStockName(),
                event.getStockCode(),
                event.getRuleName(),
                event.getPreviousStatus() != null ? event.getPreviousStatus().getDisplayName() : "無",
                event.getNewStatus().getDisplayName()
            );

            String metadata = objectMapper.writeValueAsString(event);

            Notification notification = Notification.builder()
                .user(user)
                .title(title)
                .message(message)
                .type(Notification.NotificationType.RULE_TRIGGERED)
                .ruleId(event.getRuleId())
                .cardId(event.getCardId())
                .stockCode(event.getStockCode())
                .metadata(metadata)
                .isRead(false)
                .build();

            Notification savedNotification = notificationRepository.save(notification);

            log.info("建立規則觸發通知: userId={}, ruleId={}, notificationId={}",
                    event.getUserId(), event.getRuleId(), savedNotification.getId());

            // 透過 WebSocket 發送即時通知
            webSocketService.sendNotificationToUser(
                event.getUserId(),
                convertToDto(savedNotification)
            );

        } catch (Exception e) {
            log.error("建立規則觸發通知失敗: userId={}, ruleId={}, error={}",
                     event.getUserId(), event.getRuleId(), e.getMessage(), e);
        }
    }

    /**
     * 建立一般通知
     */
    @Transactional
    public NotificationDto createNotification(
            String userId,
            String title,
            String message,
            Notification.NotificationType type) {

        User user = userRepository.findById(userId)
            .orElseThrow(() -> new ResourceNotFoundException("使用者不存在"));

        Notification notification = Notification.builder()
            .user(user)
            .title(title)
            .message(message)
            .type(type)
            .isRead(false)
            .build();

        Notification savedNotification = notificationRepository.save(notification);

        log.info("建立通知: userId={}, type={}, notificationId={}",
                userId, type, savedNotification.getId());

        NotificationDto dto = convertToDto(savedNotification);

        // 透過 WebSocket 發送即時通知
        webSocketService.sendNotificationToUser(userId, dto);

        return dto;
    }

    /**
     * 取得使用者的通知（分頁）
     */
    @Transactional(readOnly = true)
    public PagedResponse<NotificationDto> getUserNotifications(String userId, Pageable pageable) {
        User user = userRepository.findById(userId)
            .orElseThrow(() -> new ResourceNotFoundException("使用者不存在"));

        Page<Notification> notifications = notificationRepository
            .findByUserOrderByCreatedAtDesc(user, pageable);

        List<NotificationDto> dtos = notifications.getContent().stream()
            .map(this::convertToDto)
            .collect(Collectors.toList());

        return PagedResponse.<NotificationDto>builder()
            .success(true)
            .data(dtos)
            .pagination(PagedResponse.PaginationInfo.builder()
                .page(notifications.getNumber())
                .size(notifications.getSize())
                .totalElements(notifications.getTotalElements())
                .totalPages(notifications.getTotalPages())
                .hasNext(!notifications.isLast())
                .hasPrevious(!notifications.isFirst())
                .build())
            .build();
    }

    /**
     * 取得使用者的未讀通知
     */
    @Transactional(readOnly = true)
    public List<NotificationDto> getUnreadNotifications(String userId) {
        User user = userRepository.findById(userId)
            .orElseThrow(() -> new ResourceNotFoundException("使用者不存在"));

        return notificationRepository.findByUserAndIsReadFalseOrderByCreatedAtDesc(user)
            .stream()
            .map(this::convertToDto)
            .collect(Collectors.toList());
    }

    /**
     * 取得未讀通知數量
     */
    @Transactional(readOnly = true)
    public long getUnreadCount(String userId) {
        User user = userRepository.findById(userId)
            .orElseThrow(() -> new ResourceNotFoundException("使用者不存在"));

        return notificationRepository.countByUserAndIsReadFalse(user);
    }

    /**
     * 標記通知為已讀
     */
    @Transactional
    public NotificationDto markAsRead(String userId, String notificationId) {
        Notification notification = notificationRepository.findById(notificationId)
            .orElseThrow(() -> new ResourceNotFoundException("通知不存在"));

        if (!notification.getUser().getId().equals(userId)) {
            throw new IllegalArgumentException("無權限存取此通知");
        }

        notification.setIsRead(true);
        notification.setReadAt(LocalDateTime.now());

        Notification updated = notificationRepository.save(notification);

        log.info("標記通知為已讀: userId={}, notificationId={}", userId, notificationId);

        return convertToDto(updated);
    }

    /**
     * 標記所有通知為已讀
     */
    @Transactional
    public int markAllAsRead(String userId) {
        User user = userRepository.findById(userId)
            .orElseThrow(() -> new ResourceNotFoundException("使用者不存在"));

        int count = notificationRepository.markAllAsRead(user, LocalDateTime.now());

        log.info("標記所有通知為已讀: userId={}, count={}", userId, count);

        return count;
    }

    /**
     * 刪除通知
     */
    @Transactional
    public void deleteNotification(String userId, String notificationId) {
        Notification notification = notificationRepository.findById(notificationId)
            .orElseThrow(() -> new ResourceNotFoundException("通知不存在"));

        if (!notification.getUser().getId().equals(userId)) {
            throw new IllegalArgumentException("無權限刪除此通知");
        }

        notificationRepository.delete(notification);

        log.info("刪除通知: userId={}, notificationId={}", userId, notificationId);
    }

    /**
     * 轉換為 DTO
     */
    private NotificationDto convertToDto(Notification notification) {
        return NotificationDto.builder()
            .id(notification.getId())
            .title(notification.getTitle())
            .message(notification.getMessage())
            .type(notification.getType())
            .ruleId(notification.getRuleId())
            .cardId(notification.getCardId())
            .stockCode(notification.getStockCode())
            .metadata(notification.getMetadata())
            .isRead(notification.getIsRead())
            .readAt(notification.getReadAt())
            .createdAt(notification.getCreatedAt())
            .build();
    }
}
