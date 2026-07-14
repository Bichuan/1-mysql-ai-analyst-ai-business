package com.aianalyst.service.impl;

import com.aianalyst.service.QueryRequestGuard;
import com.aianalyst.service.SqlGenerationService;
import com.aianalyst.service.TextToSqlService;
import com.aianalyst.vo.SqlGenerationVO;
import org.springframework.stereotype.Service;

/** Keeps request quota handling out of the Controller and reuses the same guard as full queries. */
@Service
public class SqlGenerationServiceImpl implements SqlGenerationService {

    private final QueryRequestGuard queryRequestGuard;
    private final TextToSqlService textToSqlService;

    public SqlGenerationServiceImpl(QueryRequestGuard queryRequestGuard, TextToSqlService textToSqlService) {
        this.queryRequestGuard = queryRequestGuard;
        this.textToSqlService = textToSqlService;
    }

    @Override
    public SqlGenerationVO generate(Long userId, String question) {
        queryRequestGuard.validateAndAcquire(userId, question);
        return textToSqlService.generateSql(question);
    }
}
