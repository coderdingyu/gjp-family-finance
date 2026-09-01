package com.gjp.common;

/**
 * 全局统一返回体。前后端约定：code=0 表示成功，非 0 表示业务失败。
 * 前端 axios 响应拦截器统一按这个结构解包，各模块不要自己另造返回格式。
 */
public class Result<T> {

    /** 状态码：0=成功，400=参数/业务错误，401=未登录，500=系统异常 */
    private int code;
    /** 提示信息，失败时前端直接弹这个字段 */
    private String msg;
    /** 业务数据 */
    private T data;

    public Result() {
    }

    public Result(int code, String msg, T data) {
        this.code = code;
        this.msg = msg;
        this.data = data;
    }

    public static <T> Result<T> ok(T data) {
        return new Result<>(0, "success", data);
    }

    public static <T> Result<T> ok() {
        return new Result<>(0, "success", null);
    }

    public static <T> Result<T> fail(String msg) {
        return new Result<>(400, msg, null);
    }

    public static <T> Result<T> fail(int code, String msg) {
        return new Result<>(code, msg, null);
    }

    public int getCode() {
        return code;
    }

    public void setCode(int code) {
        this.code = code;
    }

    public String getMsg() {
        return msg;
    }

    public void setMsg(String msg) {
        this.msg = msg;
    }

    public T getData() {
        return data;
    }

    public void setData(T data) {
        this.data = data;
    }
}
