package com.aianalyst.filter;

import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;

class RequestIdFilterTest {

    private final RequestIdFilter requestIdFilter = new RequestIdFilter();

    @Test
    void shouldReturnValidCallerSuppliedRequestId() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/health");
        request.addHeader(RequestIdFilter.REQUEST_ID_HEADER, "trace-20260714-0001");
        MockHttpServletResponse response = new MockHttpServletResponse();

        requestIdFilter.doFilter(request, response, (servletRequest, servletResponse) ->
                ((MockHttpServletResponse) servletResponse).setStatus(204));

        assertThat(response.getHeader(RequestIdFilter.REQUEST_ID_HEADER)).isEqualTo("trace-20260714-0001");
        assertThat(MDC.get("requestId")).isNull();
    }

    @Test
    void shouldReplaceUnsafeRequestId() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/health");
        request.addHeader(RequestIdFilter.REQUEST_ID_HEADER, "unsafe id!");
        MockHttpServletResponse response = new MockHttpServletResponse();

        requestIdFilter.doFilter(request, response, (servletRequest, servletResponse) -> {
        });

        assertThat(response.getHeader(RequestIdFilter.REQUEST_ID_HEADER))
                .matches("[A-Za-z0-9-]{8,64}")
                .isNotEqualTo("unsafe id!");
    }
}
