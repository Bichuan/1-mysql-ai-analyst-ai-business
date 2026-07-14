package com.aianalyst.controller;

import com.aianalyst.common.Result;
import com.aianalyst.config.OpenApiConfig;
import com.aianalyst.security.SecurityUser;
import com.aianalyst.service.QueryHistoryService;
import com.aianalyst.vo.PageResultVO;
import com.aianalyst.vo.QueryHistoryVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Current-user query audit history APIs. */
@Validated
@RestController
@RequestMapping("/query-histories")
@Tag(name = "查询历史", description = "当前登录用户的自然语言查询审计记录")
@SecurityRequirement(name = OpenApiConfig.BEARER_AUTH_SCHEME)
public class QueryHistoryController {

    private final QueryHistoryService queryHistoryService;

    public QueryHistoryController(QueryHistoryService queryHistoryService) {
        this.queryHistoryService = queryHistoryService;
    }

    @GetMapping
    @Operation(summary = "分页查询我的查询历史",
            description = "仅返回当前登录用户的审计记录摘要，不返回完整查询结果 JSON",
            security = @SecurityRequirement(name = OpenApiConfig.BEARER_AUTH_SCHEME))
    public Result<PageResultVO<QueryHistoryVO>> pageMyHistory(
            @RequestParam(defaultValue = "1") @Min(value = 1, message = "页码必须大于等于 1") long page,
            @RequestParam(defaultValue = "10") @Min(value = 1, message = "每页数量必须大于等于 1")
            @Max(value = 100, message = "每页数量不能超过 100") long size,
            @AuthenticationPrincipal SecurityUser securityUser) {
        return Result.success(queryHistoryService.pageMyHistory(securityUser.getId(), page, size));
    }
}
