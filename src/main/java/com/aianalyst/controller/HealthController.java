package com.aianalyst.controller;

import com.aianalyst.common.Result;
import com.aianalyst.vo.HealthVO;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

/**
 * Basic availability endpoint. It deliberately contains no business logic.
 */
@RestController
@Tag(name = "系统状态", description = "基础连通性检查")
public class HealthController {

    @GetMapping("/health")
    @Operation(summary = "健康检查")
    public Result<HealthVO> health() {
        return Result.success(new HealthVO("UP", "ai-data-analyst"));
    }
}
