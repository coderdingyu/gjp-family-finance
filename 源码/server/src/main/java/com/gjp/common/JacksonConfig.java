package com.gjp.common;

import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.datatype.jsr310.deser.LocalDateDeserializer;
import com.fasterxml.jackson.datatype.jsr310.deser.LocalDateTimeDeserializer;
import com.fasterxml.jackson.datatype.jsr310.ser.LocalDateSerializer;
import com.fasterxml.jackson.datatype.jsr310.ser.LocalDateTimeSerializer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * JSON 序列化配置。
 *
 * 默认情况下 LocalDateTime 会被序列化成 "2026-09-01T14:35:46" 这种带 T 的 ISO 格式，
 * 前端表格直接显示不好看；这里统一成 "yyyy-MM-dd HH:mm:ss"，日期统一成 "yyyy-MM-dd"。
 * 前端所有日期参数也按这两种格式传，前后端不再各自做格式转换。
 */
@Configuration
public class JacksonConfig {

    private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    @Bean
    public SimpleModule javaTimeModule() {
        SimpleModule module = new SimpleModule();
        module.addSerializer(LocalDateTime.class, new LocalDateTimeSerializer(DATE_TIME));
        module.addDeserializer(LocalDateTime.class, new LocalDateTimeDeserializer(DATE_TIME));
        module.addSerializer(LocalDate.class, new LocalDateSerializer(DATE));
        module.addDeserializer(LocalDate.class, new LocalDateDeserializer(DATE));
        return module;
    }
}
