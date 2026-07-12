package com.aianalyst.controller;

import com.aianalyst.common.Result;
import com.aianalyst.vo.HealthVO;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Basic availability endpoint. It deliberately contains no business logic.
 */
@RestController
public class HealthController {

    @GetMapping("/health")
    public Result<HealthVO> health() {
        return Result.success(new HealthVO("UP", "ai-data-analyst"));
    }
}
