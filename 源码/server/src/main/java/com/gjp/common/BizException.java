package com.gjp.common;

/**
 * 业务异常。Service 层校验不通过时直接抛出，由 GlobalExceptionHandler 统一转成 Result.fail。
 * 好处是 Controller 里不用写一堆 if-else 判断返回值。
 */
public class BizException extends RuntimeException {

    private final int code;

    public BizException(String msg) {
        this(400, msg);
    }

    public BizException(int code, String msg) {
        super(msg);
        this.code = code;
    }

    public int getCode() {
        return code;
    }
}
