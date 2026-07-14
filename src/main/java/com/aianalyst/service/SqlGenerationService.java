package com.aianalyst.service;

import com.aianalyst.vo.SqlGenerationVO;

/** Application service for the standalone “generate SQL” endpoint. */
public interface SqlGenerationService {

    SqlGenerationVO generate(Long userId, String question);
}
