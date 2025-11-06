package com.kanban.controller;

import com.kanban.dto.PagedResponse;
import com.kanban.dto.SuccessResponse;
import com.kanban.dto.rule.RuleCreateRequest;
import com.kanban.dto.rule.RuleDto;
import com.kanban.dto.rule.RuleUpdateRequest;
import com.kanban.security.UserPrincipal;
import com.kanban.service.RuleService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/rules")
@RequiredArgsConstructor
public class RuleController {

    private final RuleService ruleService;

    /**
     * 建立新規則
     */
    @PostMapping
    public ResponseEntity<RuleDto> createRule(
            @AuthenticationPrincipal UserPrincipal userPrincipal,
            @Valid @RequestBody RuleCreateRequest request) {
        RuleDto rule = ruleService.createRule(userPrincipal.getId(), request);
        return ResponseEntity.status(HttpStatus.CREATED).body(rule);
    }

    /**
     * 更新規則
     */
    @PutMapping("/{ruleId}")
    public ResponseEntity<RuleDto> updateRule(
            @AuthenticationPrincipal UserPrincipal userPrincipal,
            @PathVariable String ruleId,
            @Valid @RequestBody RuleUpdateRequest request) {
        RuleDto rule = ruleService.updateRule(userPrincipal.getId(), ruleId, request);
        return ResponseEntity.ok(rule);
    }

    /**
     * 刪除規則
     */
    @DeleteMapping("/{ruleId}")
    public ResponseEntity<SuccessResponse> deleteRule(
            @AuthenticationPrincipal UserPrincipal userPrincipal,
            @PathVariable String ruleId) {
        ruleService.deleteRule(userPrincipal.getId(), ruleId);
        return ResponseEntity.ok(SuccessResponse.of("規則已刪除"));
    }

    /**
     * 取得規則詳情
     */
    @GetMapping("/{ruleId}")
    public ResponseEntity<RuleDto> getRule(
            @AuthenticationPrincipal UserPrincipal userPrincipal,
            @PathVariable String ruleId) {
        RuleDto rule = ruleService.getRule(userPrincipal.getId(), ruleId);
        return ResponseEntity.ok(rule);
    }

    /**
     * 取得使用者的所有規則（分頁）
     */
    @GetMapping
    public ResponseEntity<PagedResponse<RuleDto>> getUserRules(
            @AuthenticationPrincipal UserPrincipal userPrincipal,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "priority,asc") String[] sort) {

        // 解析排序參數
        Sort.Direction direction = sort.length > 1 && sort[1].equalsIgnoreCase("desc")
            ? Sort.Direction.DESC
            : Sort.Direction.ASC;
        String sortBy = sort[0];

        Pageable pageable = PageRequest.of(page, size, Sort.by(direction, sortBy));
        PagedResponse<RuleDto> rules = ruleService.getUserRules(userPrincipal.getId(), pageable);
        return ResponseEntity.ok(rules);
    }

    /**
     * 取得使用者的啟用規則
     */
    @GetMapping("/active")
    public ResponseEntity<List<RuleDto>> getActiveRules(
            @AuthenticationPrincipal UserPrincipal userPrincipal) {
        List<RuleDto> rules = ruleService.getActiveRules(userPrincipal.getId());
        return ResponseEntity.ok(rules);
    }

    /**
     * 啟用規則
     */
    @PostMapping("/{ruleId}/enable")
    public ResponseEntity<RuleDto> enableRule(
            @AuthenticationPrincipal UserPrincipal userPrincipal,
            @PathVariable String ruleId) {
        RuleDto rule = ruleService.toggleRuleStatus(userPrincipal.getId(), ruleId, true);
        return ResponseEntity.ok(rule);
    }

    /**
     * 停用規則
     */
    @PostMapping("/{ruleId}/disable")
    public ResponseEntity<RuleDto> disableRule(
            @AuthenticationPrincipal UserPrincipal userPrincipal,
            @PathVariable String ruleId) {
        RuleDto rule = ruleService.toggleRuleStatus(userPrincipal.getId(), ruleId, false);
        return ResponseEntity.ok(rule);
    }

    /**
     * 取得預設規則模板
     */
    @GetMapping("/templates")
    public ResponseEntity<List<RuleDto>> getRuleTemplates() {
        List<RuleDto> templates = ruleService.getDefaultRuleTemplates();
        return ResponseEntity.ok(templates);
    }

    /**
     * 從模板建立規則
     */
    @PostMapping("/templates/{templateName}")
    public ResponseEntity<RuleDto> createRuleFromTemplate(
            @AuthenticationPrincipal UserPrincipal userPrincipal,
            @PathVariable String templateName) {
        RuleDto rule = ruleService.createRuleFromTemplate(userPrincipal.getId(), templateName);
        return ResponseEntity.status(HttpStatus.CREATED).body(rule);
    }
}
