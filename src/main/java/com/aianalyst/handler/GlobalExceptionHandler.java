package com.aianalyst.handler;

import com.aianalyst.common.BusinessException;
import com.aianalyst.common.Result;
import com.aianalyst.common.ResultCode;
import com.aianalyst.common.SqlExecutionException;
import com.aianalyst.common.ValidationError;
import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.http.converter.HttpMessageNotReadableException;

import java.util.List;

/**
 * 将框架与业务异常统一转换为 API 响应。
 * 对外返回稳定、无敏感信息的错误码；对内日志保留必要堆栈，避免暴露数据库结构和连接细节。
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Result<List<ValidationError>>> handleMethodArgumentNotValid(
            MethodArgumentNotValidException exception) {
        List<ValidationError> errors = exception.getBindingResult().getFieldErrors().stream()
                .map(this::toValidationError)
                .toList();
        log.warn("Request validation failed: {}", errors);
        return ResponseEntity.badRequest()
                .body(Result.error(ResultCode.PARAM_ERROR, "请求参数校验失败", errors));
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<Result<List<ValidationError>>> handleConstraintViolation(
            ConstraintViolationException exception) {
        List<ValidationError> errors = exception.getConstraintViolations().stream()
                .map(violation -> new ValidationError(
                        violation.getPropertyPath().toString(), violation.getMessage()))
                .toList();
        log.warn("Constraint validation failed: {}", errors);
        return ResponseEntity.badRequest()
                .body(Result.error(ResultCode.PARAM_ERROR, "请求参数校验失败", errors));
    }

    @ExceptionHandler({MissingServletRequestParameterException.class, HttpMessageNotReadableException.class})
    public ResponseEntity<Result<Void>> handleBadRequest(Exception exception) {
        log.warn("Malformed request: {}", exception.getMessage());
        return ResponseEntity.badRequest().body(Result.error(ResultCode.PARAM_ERROR));
    }

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<Result<Void>> handleBusinessException(BusinessException exception) {
        log.warn("Business exception, code={}, message={}",
                exception.getResultCode().getCode(), exception.getMessage());
        HttpStatus status = switch (exception.getResultCode()) {
            case TOO_MANY_REQUESTS -> HttpStatus.TOO_MANY_REQUESTS;
            case FORBIDDEN -> HttpStatus.FORBIDDEN;
            case NOT_FOUND -> HttpStatus.NOT_FOUND;
            default -> HttpStatus.BAD_REQUEST;
        };
        return ResponseEntity.status(status)
                .body(Result.error(exception.getResultCode(), exception.getMessage()));
    }

    @ExceptionHandler(SqlExecutionException.class)
    public ResponseEntity<Result<Void>> handleSqlExecutionException(SqlExecutionException exception) {
        // 原始数据库异常只记录在服务端，前端只能看到通用执行失败提示。
        log.error("Business SQL execution failed", exception.getCause());
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Result.error(ResultCode.SQL_EXECUTION_FAILED));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Result<Void>> handleUnexpectedException(Exception exception) {
        log.error("Unhandled system exception", exception);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Result.error(ResultCode.SYSTEM_ERROR));
    }

    private ValidationError toValidationError(FieldError fieldError) {
        return new ValidationError(fieldError.getField(), fieldError.getDefaultMessage());
    }
}
