package com.jixiaotong.performance.exception;

import com.jixiaotong.performance.common.Result;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(BusinessException.class)
    public Result<?> handleBusinessException(BusinessException e) {
        log.warn("业务异常: {}", e.getMessage());
        return Result.error(500, e.getMessage());
    }

    @ExceptionHandler(UnauthorizedException.class)
    @ResponseStatus(HttpStatus.UNAUTHORIZED)
    public Result<?> handleUnauthorizedException(UnauthorizedException e) {
        return Result.error(401, e.getMessage());
    }

    @ExceptionHandler(IllegalStateException.class)
    public Result<?> handleIllegalStateException(IllegalStateException e) {
        log.warn("状态异常: {}", e.getMessage());
        return Result.error(500, e.getMessage());
    }

    @ExceptionHandler(Exception.class)
    public Result<?> handleException(Exception e) {
        log.error("系统内部异常", e);
        return Result.error(500, "系统开小差了，请稍后再试: " + resolveMessage(e));
    }

    private String resolveMessage(Throwable e) {
        Throwable cur = e;
        while (cur != null) {
            if (cur.getMessage() != null && !cur.getMessage().isBlank()) {
                return cur.getMessage();
            }
            cur = cur.getCause();
        }
        return e.getClass().getSimpleName();
    }
}
