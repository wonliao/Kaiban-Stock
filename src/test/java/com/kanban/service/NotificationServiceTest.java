package com.kanban.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kanban.domain.entity.Card;
import com.kanban.domain.entity.Notification;
import com.kanban.domain.entity.User;
import com.kanban.dto.PagedResponse;
import com.kanban.dto.notification.NotificationDto;
import com.kanban.dto.notification.RuleNotificationEvent;
import com.kanban.exception.ResourceNotFoundException;
import com.kanban.repository.NotificationRepository;
import com.kanban.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("NotificationService 單元測試")
class NotificationServiceTest {

    @Mock
    private NotificationRepository notificationRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private WebSocketNotificationService webSocketService;

    @Mock
    private ObjectMapper objectMapper;

    @InjectMocks
    private NotificationService notificationService;

    private User testUser;
    private Notification testNotification;

    @BeforeEach
    void setUp() {
        testUser = User.builder()
            .id("user-1")
            .username("testuser")
            .email("test@example.com")
            .build();

        testNotification = Notification.builder()
            .id("notif-1")
            .user(testUser)
            .title("規則觸發")
            .message("股票 2330 觸發規則")
            .type(Notification.NotificationType.RULE_TRIGGERED)
            .ruleId("rule-1")
            .cardId("card-1")
            .stockCode("2330")
            .isRead(false)
            .createdAt(LocalDateTime.now())
            .build();
    }

    @Test
    @DisplayName("建立規則觸發通知 - 成功")
    void createRuleTriggeredNotification_Success() throws Exception {
        // Given
        RuleNotificationEvent event = RuleNotificationEvent.builder()
            .userId("user-1")
            .ruleId("rule-1")
            .ruleName("價格警示")
            .cardId("card-1")
            .stockCode("2330")
            .stockName("台積電")
            .previousStatus(Card.CardStatus.WATCH)
            .newStatus(Card.CardStatus.ALERTS)
            .message("價格超過設定值")
            .triggeredAt(LocalDateTime.now())
            .build();

        when(userRepository.findById("user-1")).thenReturn(Optional.of(testUser));
        when(objectMapper.writeValueAsString(any())).thenReturn("{}");
        when(notificationRepository.save(any(Notification.class))).thenReturn(testNotification);

        // When
        notificationService.createRuleTriggeredNotification(event);

        // Then
        verify(notificationRepository).save(any(Notification.class));
        verify(webSocketService).sendNotificationToUser(eq("user-1"), any(NotificationDto.class));
    }

    @Test
    @DisplayName("建立一般通知 - 成功")
    void createNotification_Success() {
        // Given
        when(userRepository.findById("user-1")).thenReturn(Optional.of(testUser));
        when(notificationRepository.save(any(Notification.class))).thenReturn(testNotification);

        // When
        NotificationDto result = notificationService.createNotification(
            "user-1",
            "測試通知",
            "這是一則測試通知",
            Notification.NotificationType.INFO
        );

        // Then
        assertThat(result).isNotNull();
        verify(notificationRepository).save(any(Notification.class));
        verify(webSocketService).sendNotificationToUser(eq("user-1"), any(NotificationDto.class));
    }

    @Test
    @DisplayName("建立通知 - 使用者不存在")
    void createNotification_UserNotFound() {
        // Given
        when(userRepository.findById("user-1")).thenReturn(Optional.empty());

        // When & Then
        assertThatThrownBy(() -> notificationService.createNotification(
            "user-1",
            "測試通知",
            "這是一則測試通知",
            Notification.NotificationType.INFO
        )).isInstanceOf(ResourceNotFoundException.class)
          .hasMessage("使用者不存在");
    }

    @Test
    @DisplayName("取得使用者通知 - 成功")
    void getUserNotifications_Success() {
        // Given
        List<Notification> notifications = Arrays.asList(testNotification);
        Page<Notification> notificationPage = new PageImpl<>(notifications);
        Pageable pageable = PageRequest.of(0, 10);

        when(userRepository.findById("user-1")).thenReturn(Optional.of(testUser));
        when(notificationRepository.findByUserOrderByCreatedAtDesc(testUser, pageable))
            .thenReturn(notificationPage);

        // When
        PagedResponse<NotificationDto> result =
            notificationService.getUserNotifications("user-1", pageable);

        // Then
        assertThat(result).isNotNull();
        assertThat(result.getData()).hasSize(1);
        assertThat(result.getData().get(0).getTitle()).isEqualTo("規則觸發");
    }

    @Test
    @DisplayName("取得未讀通知 - 成功")
    void getUnreadNotifications_Success() {
        // Given
        when(userRepository.findById("user-1")).thenReturn(Optional.of(testUser));
        when(notificationRepository.findByUserAndIsReadFalseOrderByCreatedAtDesc(testUser))
            .thenReturn(Arrays.asList(testNotification));

        // When
        List<NotificationDto> result = notificationService.getUnreadNotifications("user-1");

        // Then
        assertThat(result).hasSize(1);
        assertThat(result.get(0).getIsRead()).isFalse();
    }

    @Test
    @DisplayName("取得未讀通知數量 - 成功")
    void getUnreadCount_Success() {
        // Given
        when(userRepository.findById("user-1")).thenReturn(Optional.of(testUser));
        when(notificationRepository.countByUserAndIsReadFalse(testUser)).thenReturn(5L);

        // When
        long count = notificationService.getUnreadCount("user-1");

        // Then
        assertThat(count).isEqualTo(5L);
    }

    @Test
    @DisplayName("標記通知為已讀 - 成功")
    void markAsRead_Success() {
        // Given
        when(notificationRepository.findById("notif-1")).thenReturn(Optional.of(testNotification));
        when(notificationRepository.save(any(Notification.class))).thenReturn(testNotification);

        // When
        NotificationDto result = notificationService.markAsRead("user-1", "notif-1");

        // Then
        assertThat(result).isNotNull();
        verify(notificationRepository).save(any(Notification.class));
    }

    @Test
    @DisplayName("標記通知為已讀 - 通知不存在")
    void markAsRead_NotificationNotFound() {
        // Given
        when(notificationRepository.findById("notif-1")).thenReturn(Optional.empty());

        // When & Then
        assertThatThrownBy(() -> notificationService.markAsRead("user-1", "notif-1"))
            .isInstanceOf(ResourceNotFoundException.class)
            .hasMessage("通知不存在");
    }

    @Test
    @DisplayName("標記通知為已讀 - 無權限")
    void markAsRead_AccessDenied() {
        // Given
        when(notificationRepository.findById("notif-1")).thenReturn(Optional.of(testNotification));

        // When & Then
        assertThatThrownBy(() -> notificationService.markAsRead("user-2", "notif-1"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("無權限存取此通知");
    }

    @Test
    @DisplayName("標記所有通知為已讀 - 成功")
    void markAllAsRead_Success() {
        // Given
        when(userRepository.findById("user-1")).thenReturn(Optional.of(testUser));
        when(notificationRepository.markAllAsRead(eq(testUser), any(LocalDateTime.class)))
            .thenReturn(5);

        // When
        int count = notificationService.markAllAsRead("user-1");

        // Then
        assertThat(count).isEqualTo(5);
        verify(notificationRepository).markAllAsRead(eq(testUser), any(LocalDateTime.class));
    }

    @Test
    @DisplayName("刪除通知 - 成功")
    void deleteNotification_Success() {
        // Given
        when(notificationRepository.findById("notif-1")).thenReturn(Optional.of(testNotification));

        // When
        notificationService.deleteNotification("user-1", "notif-1");

        // Then
        verify(notificationRepository).delete(testNotification);
    }

    @Test
    @DisplayName("刪除通知 - 無權限")
    void deleteNotification_AccessDenied() {
        // Given
        when(notificationRepository.findById("notif-1")).thenReturn(Optional.of(testNotification));

        // When & Then
        assertThatThrownBy(() -> notificationService.deleteNotification("user-2", "notif-1"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("無權限刪除此通知");
    }
}
