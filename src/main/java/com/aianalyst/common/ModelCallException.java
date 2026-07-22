package com.aianalyst.common;

import com.aianalyst.service.ModelCallType;

/** Stable outward-facing failure for a timed-out, open, or otherwise unavailable model stage. */
public class ModelCallException extends BusinessException {

    private final ModelCallType callType;

    public ModelCallException(ModelCallType callType, Throwable cause) {
        super(ResultCode.MODEL_SERVICE_UNAVAILABLE,
                ResultCode.MODEL_SERVICE_UNAVAILABLE.getMessage(), cause);
        this.callType = callType;
    }

    public ModelCallType getCallType() {
        return callType;
    }
}
