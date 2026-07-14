package com.aianalyst.service.impl;

import com.aianalyst.service.DeepSeekChatService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ResultAnalysisServiceImplTest {

    @Mock
    private DeepSeekChatService deepSeekChatService;

    @Test
    void shouldAnalyzeOnlyProvidedMaskedRows() {
        ResultAnalysisServiceImpl service = new ResultAnalysisServiceImpl(deepSeekChatService, new ObjectMapper());
        List<Map<String, Object>> maskedRows = List.of(
                Map.of("customer_name", "企业客户001", "email", "t***@gmail.com", "sales", 19081.2));
        when(deepSeekChatService.generate(anyString())).thenReturn("- 企业客户001销售额最高。\n- 共返回1条数据。");

        String summary = service.analyze("查询销售额", "SELECT ...", maskedRows, 1);

        assertThat(summary).contains("销售额最高");
        ArgumentCaptor<String> promptCaptor = ArgumentCaptor.forClass(String.class);
        verify(deepSeekChatService).generate(promptCaptor.capture());
        assertThat(promptCaptor.getValue())
                .contains("t***@gmail.com")
                .doesNotContain("test@gmail.com")
                .contains("不得据此推断数据完整性")
                .contains("不得使用“全部订单”“完整数据”“所有记录”等绝对性表述");
    }

    @Test
    void shouldReturnFallbackWhenAiCallFails() {
        ResultAnalysisServiceImpl service = new ResultAnalysisServiceImpl(deepSeekChatService, new ObjectMapper());
        when(deepSeekChatService.generate(anyString())).thenThrow(new IllegalStateException("temporary failure"));

        String summary = service.analyze("查询客户", "SELECT ...", List.of(), 0);

        assertThat(summary).isEqualTo("查询成功，共返回 0 条数据。AI 总结暂不可用，请查看下方明细数据。");
    }
}
