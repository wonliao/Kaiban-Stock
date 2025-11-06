package com.kanban.service;

import com.kanban.dto.notification.NotificationDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

/**
 * WebSocket 通知服務
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class WebSocketNotificationService {

    private final SimpMessagingTemplate messagingTemplate;

    /**
     * 發送通知給特定使用者
     */
    public void sendNotificationToUser(String userId, NotificationDto notification) {
        try {
            String destination = "/queue/notifications";
            messagingTemplate.convertAndSendToUser(
                userId,
                destination,
                notification
            );
            log.debug("WebSocket 通知已發送: userId={}, notificationId={}",
                     userId, notification.getId());
        } catch (Exception e) {
            log.error("發送 WebSocket 通知失敗: userId={}, error={}",
                     userId, e.getMessage(), e);
        }
    }

    /**
     * 發送規則觸發事件給特定使用者
     */
    public void sendRuleEventToUser(String userId, Object event) {
        try {
            String destination = "/queue/rule-events";
            messagingTemplate.convertAndSendToUser(
                userId,
                destination,
                event
            );
            log.debug("WebSocket 規則事件已發送: userId={}", userId);
        } catch (Exception e) {
            log.error("發送 WebSocket 規則事件失敗: userId={}, error={}",
                     userId, e.getMessage(), e);
        }
    }

    /**
     * 廣播系統訊息給所有使用者
     */
    public void broadcastSystemMessage(String message) {
        try {
            messagingTemplate.convertAndSend("/topic/system", message);
            log.info("系統訊息已廣播: {}", message);
        } catch (Exception e) {
            log.error("廣播系統訊息失敗: error={}", e.getMessage(), e);
        }
    }

    /**
     * 發送卡片狀態更新事件
     */
    public void sendCardUpdateToUser(String userId, Object cardUpdate) {
        try {
            String destination = "/queue/card-updates";
            messagingTemplate.convertAndSendToUser(
                userId,
                destination,
                cardUpdate
            );
            log.debug("WebSocket 卡片更新已發送: userId={}", userId);
        } catch (Exception e) {
            log.error("發送 WebSocket 卡片更新失敗: userId={}, error={}",
                     userId, e.getMessage(), e);
        }
    }
}
