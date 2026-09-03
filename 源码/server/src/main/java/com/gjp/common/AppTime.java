package com.gjp.common;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.TimeZone;

/**
 * 全系统统一的时区与「今天」。
 *
 * 为什么需要这个类：记账系统里「今天」是业务规则的一部分 —— 录入时校验
 * 「发生日期不能晚于今天」、个人看板的今日/本周合计、导入时给缺日期的账单补当天，
 * 都要拿到同一个日期。之前这些地方一部分写 {@code LocalDate.now()}（跟着 JVM 默认时区走），
 * 个人看板又写死 Asia/Shanghai，机器时区不是东八区时两者会差一天：
 * 云主机默认 UTC 时，北京时间 0 点到 8 点之间，看板显示的「今日」录进去会被
 * 「发生日期不能晚于今天」直接拒掉。
 *
 * 做法是把时区固定成 Asia/Shanghai（数据库连接串里本来就是这个时区），
 * 并且所有取「今天」的地方都走这里，不再依赖运行机器的时区设置。
 */
public final class AppTime {

    /** 业务时区。与 application.yml 里 jdbc 的 serverTimezone 保持一致 */
    public static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");

    private AppTime() {
    }

    /**
     * 把 JVM 默认时区钉死，在 Spring 启动前调用。
     * 除了本类以外，MySQL 驱动、日志时间戳等也会跟着用默认时区，统一设置能少一类偏差。
     */
    public static void pinDefaultZone() {
        TimeZone.setDefault(TimeZone.getTimeZone(ZONE));
    }

    /** 业务口径的「今天」 */
    public static LocalDate today() {
        return LocalDate.now(ZONE);
    }

    /** 业务口径的当前时刻 */
    public static LocalDateTime now() {
        return LocalDateTime.now(ZONE);
    }
}
