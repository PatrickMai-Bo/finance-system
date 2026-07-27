package com.finance.common;

import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * 全局异常处理,统一返回体
 */
@RestControllerAdvice
public class GlobalExceptionHandler {
    @ExceptionHandler(Exception.class)
    public R<?> handle(Exception e) {
        return R.fail(500, e.getMessage() == null ? "服务器内部错误" : e.getMessage());
    }
}
