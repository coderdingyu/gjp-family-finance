package com.gjp.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 注册请求参数。一次注册同时完成三件事：建家庭、建账号、初始化该家庭的预置收支分类。
 */
public class RegisterDTO {

    @NotBlank(message = "请输入账号")
    @Size(min = 3, max = 20, message = "账号长度需在 3-20 个字符之间")
    private String username;

    @NotBlank(message = "请输入密码")
    @Size(min = 6, max = 20, message = "密码长度需在 6-20 个字符之间")
    private String password;

    @NotBlank(message = "请输入姓名")
    private String realName;

    @NotBlank(message = "请输入家庭名称")
    private String familyName;

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public String getRealName() {
        return realName;
    }

    public void setRealName(String realName) {
        this.realName = realName;
    }

    public String getFamilyName() {
        return familyName;
    }

    public void setFamilyName(String familyName) {
        this.familyName = familyName;
    }
}
