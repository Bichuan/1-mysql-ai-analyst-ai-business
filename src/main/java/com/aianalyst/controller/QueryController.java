package com.aianalyst.controller;

import com.aianalyst.common.Result;
import com.aianalyst.config.OpenApiConfig;
import com.aianalyst.dto.QueryRequest;
import com.aianalyst.dto.SqlGenerationRequest;
import com.aianalyst.security.SecurityUser;
import com.aianalyst.service.DataQueryService;
import com.aianalyst.service.TextToSqlService;
import com.aianalyst.vo.QueryResultVO;
import com.aianalyst.vo.SqlGenerationVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** AI query entry point. This phase generates SQL only and never executes dynamic SQL. */
@RestController
@RequestMapping("/queries")
@Tag(name = "AI 数据查询", description = "自然语言转 SQL；生成结果将在后续流程中审核和执行")
@SecurityRequirement(name = OpenApiConfig.BEARER_AUTH_SCHEME)
public class QueryController {

    private final TextToSqlService textToSqlService;
    private final DataQueryService dataQueryService;

    public QueryController(TextToSqlService textToSqlService, DataQueryService dataQueryService) {
        this.textToSqlService = textToSqlService;
        this.dataQueryService = dataQueryService;
    }

    @PostMapping("/generate-sql")
    @Operation(
            summary = "生成 SQL",
            description = "调用 AI 根据业务元数据生成 SQL；本接口不会执行任何 SQL",
            security = @SecurityRequirement(name = OpenApiConfig.BEARER_AUTH_SCHEME))
    public Result<SqlGenerationVO> generateSql(@Valid @RequestBody SqlGenerationRequest request,
                                               @AuthenticationPrincipal SecurityUser securityUser) {
        return Result.success(textToSqlService.generateSql(securityUser.getId(), request.question()));
    }

    @PostMapping("/query")
    @Operation(
            summary = "执行自然语言数据查询",
            description = "服务端依次完成 AI 生成 SQL、安全审核和只读查询；客户端不能提交原始 SQL",
            security = @SecurityRequirement(name = OpenApiConfig.BEARER_AUTH_SCHEME))
    public Result<QueryResultVO> query(@Valid @RequestBody QueryRequest request,
                                       @AuthenticationPrincipal SecurityUser securityUser) {
        return Result.success(dataQueryService.query(securityUser.getId(), request.question()));
    }
}
