package com.aianalyst.service.impl;

import com.aianalyst.common.SqlExecutionException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentMatchers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.PreparedStatementSetter;
import org.springframework.jdbc.core.RowMapper;

import java.sql.PreparedStatement;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SqlExecutionServiceImplTest {

    @Mock
    private JdbcTemplate businessJdbcTemplate;

    @Mock
    private PreparedStatement preparedStatement;

    @Test
    void shouldSetQueryTimeoutAndReturnRows() throws Exception {
        String sql = "SELECT id FROM biz_customer LIMIT 1";
        List<Map<String, Object>> expected = List.of(Map.of("id", 1L));
        SqlExecutionServiceImpl service = new SqlExecutionServiceImpl(businessJdbcTemplate);

        doAnswer(invocation -> {
            PreparedStatementSetter setter = invocation.getArgument(1);
            setter.setValues(preparedStatement);
            return expected;
        }).when(businessJdbcTemplate).query(eq(sql), any(PreparedStatementSetter.class),
                ArgumentMatchers.<RowMapper<Map<String, Object>>>any());

        assertThat(service.executeAuditedSelect(sql)).containsExactlyElementsOf(expected);

        verify(preparedStatement).setQueryTimeout(10);
    }

    @Test
    void shouldWrapDatabaseExceptionWithoutExposingIt() {
        String sql = "SELECT id FROM biz_customer LIMIT 1";
        DataAccessResourceFailureException cause = new DataAccessResourceFailureException("connection refused");
        SqlExecutionServiceImpl service = new SqlExecutionServiceImpl(businessJdbcTemplate);
        when(businessJdbcTemplate.query(eq(sql), any(PreparedStatementSetter.class),
                ArgumentMatchers.<RowMapper<Map<String, Object>>>any())).thenThrow(cause);

        assertThatThrownBy(() -> service.executeAuditedSelect(sql))
                .isInstanceOf(SqlExecutionException.class)
                .hasCause(cause);
    }
}
