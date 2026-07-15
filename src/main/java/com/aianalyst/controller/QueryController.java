package com.aianalyst.controller;

import com.aianalyst.common.Result;
import com.aianalyst.config.OpenApiConfig;
import com.aianalyst.dto.QueryRequest;
import com.aianalyst.dto.SqlGenerationRequest;
import com.aianalyst.security.SecurityUser;
import com.aianalyst.service.DataQueryService;
import com.aianalyst.service.SqlGenerationService;
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

/** AI query entry point. It only maps HTTP requests to application services. */
@RestController
@RequestMapping("/queries")
@Tag(name = "AI 数据查询", description = "自然语言生成 SQL、安全审核、只读执行与 AI 结果总结")
@SecurityRequirement(name = OpenApiConfig.BEARER_AUTH_SCHEME)
public class QueryController {

    private final SqlGenerationService sqlGenerationService;
    private final DataQueryService dataQueryService;

    public QueryController(SqlGenerationService sqlGenerationService, DataQueryService dataQueryService) { // 构造器注入，没有使用@Autowired注解
        this.sqlGenerationService = sqlGenerationService; // 注入SQL生成服务
        this.dataQueryService = dataQueryService; // 注入数据查询服务
    }

    @PostMapping("/generate-sql")
    @Operation(
            summary = "生成 SQL",
            description = "调用 AI 根据业务元数据生成 SQL；本接口不会执行任何 SQL",
            security = @SecurityRequirement(name = OpenApiConfig.BEARER_AUTH_SCHEME))
    public Result<SqlGenerationVO> generateSql(@Valid @RequestBody SqlGenerationRequest request,
                                               @AuthenticationPrincipal SecurityUser securityUser) {
        return Result.success(sqlGenerationService.generate(securityUser.getId(), request.question()));
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
