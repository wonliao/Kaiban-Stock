package com.kanban.controller;

import com.kanban.dto.PagedResponse;
import com.kanban.dto.SuccessResponse;
import com.kanban.dto.rule.RuleExecutionDto;
import com.kanban.security.UserPrincipal;
import com.kanban.service.RuleExecutionService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/rule-executions")
@RequiredArgsConstructor
public class RuleExecutionController {

    private final RuleExecutionService executionService;

    /**
     * 取得規則的執行歷史
     */
    @GetMapping("/rule/{ruleId}")
    public ResponseEntity<PagedResponse<RuleExecutionDto>> getRuleExecutions(
            @PathVariable String ruleId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        Pageable pageable = PageRequest.of(page, size,
                                          Sort.by(Sort.Direction.DESC, "executedAt"));
        PagedResponse<RuleExecutionDto> executions =
            executionService.getRuleExecutions(ruleId, pageable);
        return ResponseEntity.ok(executions);
    }

    /**
     * 取得卡片的規則執行歷史
     */
    @GetMapping("/card/{cardId}")
    public ResponseEntity<PagedResponse<RuleExecutionDto>> getCardExecutions(
            @PathVariable String cardId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        Pageable pageable = PageRequest.of(page, size,
                                          Sort.by(Sort.Direction.DESC, "executedAt"));
        PagedResponse<RuleExecutionDto> executions =
            executionService.getCardExecutions(cardId, pageable);
        return ResponseEntity.ok(executions);
    }

    /**
     * 手動觸發規則評估（僅管理員）
     */
    @PostMapping("/trigger")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<SuccessResponse> triggerRuleEvaluation(
            @AuthenticationPrincipal UserPrincipal userPrincipal) {
        executionService.executeAllActiveRules();
        return ResponseEntity.ok(SuccessResponse.of("規則評估已觸發"));
    }
}
