package com.jixiaotong.performance.common;

import lombok.Data;

@Data
public class Result<T> {
    private Integer code;
    private String message;
    private T data;

    public static <T> Result<T> success(T data) {
        Result<T> res = new Result<>();
        res.setCode(200);
        res.setMessage("操作成功");
        res.setData(data);
        return res;
    }

    public static <T> Result<T> success() {
        return success(null);
    }

    public static <T> Result<T> error(Integer code, String message) {
        Result<T> res = new Result<>();
        res.setCode(code);
        res.setMessage(message);
        return res;
    }
}
