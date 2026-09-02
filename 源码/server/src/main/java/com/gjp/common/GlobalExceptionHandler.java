package com.gjp.common;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.validation.BindException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.MultipartException;
import org.springframework.web.servlet.NoHandlerFoundException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

/**
 * 全局异常处理。保证任何情况下前端拿到的都是标准 Result 结构，不会收到一坨 500 堆栈。
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    /** 业务异常：金额非法、账号已存在这类可预期的错误 */
    @ExceptionHandler(BizException.class)
    public Result<Void> handleBiz(BizException e) {
        log.warn("业务异常：{}", e.getMessage());
        return Result.fail(e.getCode(), e.getMessage());
    }

    /** @Valid 校验失败：取第一条校验信息返回，够前端提示用 */
    @ExceptionHandler({MethodArgumentNotValidException.class, BindException.class})
    public Result<Void> handleValid(BindException e) {
        String msg = e.getBindingResult().getFieldErrors().isEmpty()
                ? "参数校验失败"
                : e.getBindingResult().getFieldErrors().get(0).getDefaultMessage();
        return Result.fail(msg);
    }

    @ExceptionHandler({MaxUploadSizeExceededException.class, MultipartException.class})
    public Result<Void> handleUpload(Exception e) {
        log.warn("上传失败：{}", e.getMessage());
        return Result.fail("文件太大：单个不超过 12MB，一次不超过 80MB");
    }

    /** /api/record/abc 这类路径变量类型对不上，不应落到 500 */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public Result<Void> handleTypeMismatch(MethodArgumentTypeMismatchException e) {
        log.warn("参数类型不匹配：{}", e.getMessage());
        return Result.fail("请求参数不正确");
    }

    /** 已登录后访问不存在的接口（如 /api/auth/me）原先会被收成系统异常 */
    @ExceptionHandler({NoHandlerFoundException.class, NoResourceFoundException.class})
    public Result<Void> handleNotFound(Exception e) {
        log.warn("接口不存在：{}", e.getMessage());
        return Result.fail(404, "接口不存在");
    }

    /** 兜底：未预料到的异常，日志打全栈，返回给前端的信息不暴露内部细节 */
    @ExceptionHandler(Exception.class)
    public Result<Void> handleOther(Exception e) {
        log.error("系统异常", e);
        return Result.fail(500, "系统异常，请联系管理员");
    }
}
