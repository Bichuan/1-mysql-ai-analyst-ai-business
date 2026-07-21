package com.aianalyst.config;

import org.slf4j.MDC;
import org.springframework.core.task.TaskDecorator;

import java.util.Map;

/** Propagates the caller MDC, including Request ID, without leaking it between pooled threads. */
public class MdcTaskDecorator implements TaskDecorator {

    @Override
    public Runnable decorate(Runnable runnable) {
        Map<String, String> callerContext = MDC.getCopyOfContextMap();
        return () -> {
            Map<String, String> executorContext = MDC.getCopyOfContextMap();
            try {
                replaceContext(callerContext);
                runnable.run();
            } finally {
                replaceContext(executorContext);
            }
        };
    }

    private void replaceContext(Map<String, String> context) {
        if (context == null || context.isEmpty()) {
            MDC.clear();
            return;
        }
        MDC.setContextMap(context);
    }
}
