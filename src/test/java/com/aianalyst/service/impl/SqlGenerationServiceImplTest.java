package com.aianalyst.service.impl;

import com.aianalyst.service.QueryRequestGuard;
import com.aianalyst.service.TextToSqlService;
import com.aianalyst.vo.SqlGenerationVO;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SqlGenerationServiceImplTest {

    @Mock
    private QueryRequestGuard queryRequestGuard;

    @Mock
    private TextToSqlService textToSqlService;

    @InjectMocks
    private SqlGenerationServiceImpl sqlGenerationService;

    @Test
    void shouldGuardThenGenerateSql() {
        SqlGenerationVO expected = new SqlGenerationVO("查询客户", "SELECT id FROM biz_customer LIMIT 10");
        when(textToSqlService.generateSql("查询客户")).thenReturn(expected);

        SqlGenerationVO result = sqlGenerationService.generate(7L, "查询客户");

        assertThat(result).isEqualTo(expected);
        verify(queryRequestGuard).validateAndAcquire(7L, "查询客户");
        verify(textToSqlService).generateSql("查询客户");
    }
}
