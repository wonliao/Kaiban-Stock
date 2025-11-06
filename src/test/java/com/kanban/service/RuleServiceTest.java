package com.kanban.service;

import com.kanban.domain.entity.Card;
import com.kanban.domain.entity.Rule;
import com.kanban.domain.entity.User;
import com.kanban.dto.PagedResponse;
import com.kanban.dto.rule.RuleCreateRequest;
import com.kanban.dto.rule.RuleDto;
import com.kanban.dto.rule.RuleUpdateRequest;
import com.kanban.exception.ResourceNotFoundException;
import com.kanban.repository.RuleRepository;
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
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("RuleService 單元測試")
class RuleServiceTest {

    @Mock
    private RuleRepository ruleRepository;

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private RuleService ruleService;

    private User testUser;
    private Rule testRule;

    @BeforeEach
    void setUp() {
        testUser = User.builder()
            .id("user-1")
            .username("testuser")
            .email("test@example.com")
            .build();

        testRule = Rule.builder()
            .id("rule-1")
            .user(testUser)
            .name("價格警示")
            .description("當價格超過100時觸發")
            .ruleType(Rule.RuleType.CUSTOM)
            .conditionExpression("price > 100")
            .triggerEvent(Rule.TriggerEvent.PRICE_CHANGE)
            .targetStatus(Card.CardStatus.ALERTS)
            .enabled(true)
            .cooldownSeconds(3600)
            .priority(5)
            .sendNotification(true)
            .triggerCount(0L)
            .createdAt(LocalDateTime.now())
            .build();
    }

    @Test
    @DisplayName("建立規則 - 成功")
    void createRule_Success() {
        // Given
        RuleCreateRequest request = RuleCreateRequest.builder()
            .name("價格警示")
            .description("當價格超過100時觸發")
            .ruleType(Rule.RuleType.CUSTOM)
            .conditionExpression("price > 100")
            .triggerEvent(Rule.TriggerEvent.PRICE_CHANGE)
            .targetStatus(Card.CardStatus.ALERTS)
            .enabled(true)
            .cooldownSeconds(3600)
            .priority(5)
            .sendNotification(true)
            .build();

        when(userRepository.findById("user-1")).thenReturn(Optional.of(testUser));
        when(ruleRepository.findByUserAndName(testUser, "價格警示")).thenReturn(Optional.empty());
        when(ruleRepository.save(any(Rule.class))).thenReturn(testRule);

        // When
        RuleDto result = ruleService.createRule("user-1", request);

        // Then
        assertThat(result).isNotNull();
        assertThat(result.getName()).isEqualTo("價格警示");
        assertThat(result.getConditionExpression()).isEqualTo("price > 100");
        verify(ruleRepository).save(any(Rule.class));
    }

    @Test
    @DisplayName("建立規則 - 使用者不存在")
    void createRule_UserNotFound() {
        // Given
        RuleCreateRequest request = RuleCreateRequest.builder()
            .name("價格警示")
            .conditionExpression("price > 100")
            .triggerEvent(Rule.TriggerEvent.PRICE_CHANGE)
            .targetStatus(Card.CardStatus.ALERTS)
            .ruleType(Rule.RuleType.CUSTOM)
            .cooldownSeconds(3600)
            .build();

        when(userRepository.findById("user-1")).thenReturn(Optional.empty());

        // When & Then
        assertThatThrownBy(() -> ruleService.createRule("user-1", request))
            .isInstanceOf(ResourceNotFoundException.class)
            .hasMessage("使用者不存在");
    }

    @Test
    @DisplayName("建立規則 - 名稱重複")
    void createRule_DuplicateName() {
        // Given
        RuleCreateRequest request = RuleCreateRequest.builder()
            .name("價格警示")
            .conditionExpression("price > 100")
            .triggerEvent(Rule.TriggerEvent.PRICE_CHANGE)
            .targetStatus(Card.CardStatus.ALERTS)
            .ruleType(Rule.RuleType.CUSTOM)
            .cooldownSeconds(3600)
            .build();

        when(userRepository.findById("user-1")).thenReturn(Optional.of(testUser));
        when(ruleRepository.findByUserAndName(testUser, "價格警示"))
            .thenReturn(Optional.of(testRule));

        // When & Then
        assertThatThrownBy(() -> ruleService.createRule("user-1", request))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("規則名稱已存在");
    }

    @Test
    @DisplayName("更新規則 - 成功")
    void updateRule_Success() {
        // Given
        RuleUpdateRequest request = RuleUpdateRequest.builder()
            .description("更新後的描述")
            .enabled(false)
            .build();

        when(ruleRepository.findById("rule-1")).thenReturn(Optional.of(testRule));
        when(ruleRepository.save(any(Rule.class))).thenReturn(testRule);

        // When
        RuleDto result = ruleService.updateRule("user-1", "rule-1", request);

        // Then
        assertThat(result).isNotNull();
        verify(ruleRepository).save(any(Rule.class));
    }

    @Test
    @DisplayName("刪除規則 - 成功")
    void deleteRule_Success() {
        // Given
        when(ruleRepository.findById("rule-1")).thenReturn(Optional.of(testRule));

        // When
        ruleService.deleteRule("user-1", "rule-1");

        // Then
        verify(ruleRepository).delete(testRule);
    }

    @Test
    @DisplayName("取得規則詳情 - 成功")
    void getRule_Success() {
        // Given
        when(ruleRepository.findById("rule-1")).thenReturn(Optional.of(testRule));

        // When
        RuleDto result = ruleService.getRule("user-1", "rule-1");

        // Then
        assertThat(result).isNotNull();
        assertThat(result.getId()).isEqualTo("rule-1");
        assertThat(result.getName()).isEqualTo("價格警示");
    }

    @Test
    @DisplayName("取得使用者的規則列表 - 成功")
    void getUserRules_Success() {
        // Given
        List<Rule> rules = Arrays.asList(testRule);
        Page<Rule> rulePage = new PageImpl<>(rules);
        Pageable pageable = PageRequest.of(0, 10);

        when(userRepository.findById("user-1")).thenReturn(Optional.of(testUser));
        when(ruleRepository.findByUser(testUser, pageable)).thenReturn(rulePage);

        // When
        PagedResponse<RuleDto> result = ruleService.getUserRules("user-1", pageable);

        // Then
        assertThat(result).isNotNull();
        assertThat(result.getData()).hasSize(1);
        assertThat(result.getData().get(0).getName()).isEqualTo("價格警示");
    }

    @Test
    @DisplayName("取得啟用的規則 - 成功")
    void getActiveRules_Success() {
        // Given
        when(userRepository.findById("user-1")).thenReturn(Optional.of(testUser));
        when(ruleRepository.findByUserAndEnabledTrue(testUser))
            .thenReturn(Arrays.asList(testRule));

        // When
        List<RuleDto> result = ruleService.getActiveRules("user-1");

        // Then
        assertThat(result).hasSize(1);
        assertThat(result.get(0).getEnabled()).isTrue();
    }

    @Test
    @DisplayName("啟用/停用規則 - 成功")
    void toggleRuleStatus_Success() {
        // Given
        when(ruleRepository.findById("rule-1")).thenReturn(Optional.of(testRule));
        when(ruleRepository.save(any(Rule.class))).thenReturn(testRule);

        // When
        RuleDto result = ruleService.toggleRuleStatus("user-1", "rule-1", false);

        // Then
        assertThat(result).isNotNull();
        verify(ruleRepository).save(any(Rule.class));
    }

    @Test
    @DisplayName("取得預設規則模板 - 成功")
    void getDefaultRuleTemplates_Success() {
        // When
        List<RuleDto> templates = ruleService.getDefaultRuleTemplates();

        // Then
        assertThat(templates).isNotEmpty();
        assertThat(templates).hasSizeGreaterThanOrEqualTo(5);
    }

    @Test
    @DisplayName("從模板建立規則 - 成功")
    void createRuleFromTemplate_Success() {
        // Given
        when(userRepository.findById("user-1")).thenReturn(Optional.of(testUser));
        when(ruleRepository.save(any(Rule.class))).thenReturn(testRule);

        // When
        RuleDto result = ruleService.createRuleFromTemplate("user-1", "價格警示");

        // Then
        assertThat(result).isNotNull();
        verify(ruleRepository).save(any(Rule.class));
    }
}
