package com.zxp.dormrepair.common;

import java.util.LinkedHashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.NoHandlerFoundException;

/**
 * 异常统一出口。前端的所有失败都从这里拿码，别在 controller 里各写一套。
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(BizException.class)
    public ResponseEntity<Map<String, Object>> onBiz(BizException e) {
        return ResponseEntity.status(e.status())
                .body(ApiResponses.error(e.code(), e.getMessage(), e.extra()));
    }

    @ExceptionHandler(org.springframework.web.bind.MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> onInvalid(
            org.springframework.web.bind.MethodArgumentNotValidException e) {
        Map<String, Object> extra = new LinkedHashMap<>();
        extra.put("fields", e.getBindingResult().getFieldErrors().stream()
                .map(f -> f.getField() + " " + f.getDefaultMessage()).toList());
        return ResponseEntity.badRequest()
                .body(ApiResponses.error("BAD_REQUEST", "参数不合法", extra));
    }

    @ExceptionHandler(org.springframework.http.converter.HttpMessageNotReadableException.class)
    public ResponseEntity<Map<String, Object>> onUnreadable(Exception e) {
        return ResponseEntity.badRequest()
                .body(ApiResponses.error("BAD_REQUEST", "请求体读不出来，检查 JSON 格式"));
    }

    @ExceptionHandler(NoHandlerFoundException.class)
    public ResponseEntity<Map<String, Object>> onNoHandler(NoHandlerFoundException e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ApiResponses.error("NOT_FOUND", "没有这个接口：" + e.getRequestURL()));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> onOther(Exception e) {
        log.error("未预料的异常", e);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiResponses.error("INTERNAL_ERROR", "服务端出错了，看日志"));
    }
}
