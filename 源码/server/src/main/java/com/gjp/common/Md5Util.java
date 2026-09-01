package com.gjp.common;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * 密码摘要工具。课程项目按库表设计说明用 MD5 存储；
 * 注意：真实生产环境应使用 BCrypt 加盐，MD5 不具备抗撞库能力，此处仅为课程演示。
 */
public class Md5Util {

    public static String md5(String text) {
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] bytes = md.digest(text.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : bytes) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("当前 JDK 不支持 MD5", e);
        }
    }
}
