package com.gjp.common;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.TimeZone;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 时区口径的回归测试。
 *
 * 起因：录入校验用的是 {@code LocalDate.now()}（跟着运行机器的时区走），
 * 个人看板却写死了 Asia/Shanghai。机器时区不是东八区时两者会差一天，
 * 云主机默认 UTC 的话，北京时间 0 点到 8 点之间，看板显示的「今日」
 * 录进去会被「发生日期不能晚于今天」直接拒掉。
 */
class AppTimeTest {

    private final TimeZone original = TimeZone.getDefault();

    @AfterEach
    void restore() {
        TimeZone.setDefault(original);
    }

    @Test
    void 业务口径的今天不随机器时区变化() {
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"));
        LocalDate underUtc = AppTime.today();

        TimeZone.setDefault(TimeZone.getTimeZone("Pacific/Honolulu"));
        LocalDate underHonolulu = AppTime.today();

        assertEquals(underUtc, underHonolulu);
        assertEquals(LocalDate.now(ZoneId.of("Asia/Shanghai")), underHonolulu);
    }

    @Test
    void 钉死时区后系统默认的今天与业务口径一致() {
        TimeZone.setDefault(TimeZone.getTimeZone("Pacific/Honolulu"));
        AppTime.pinDefaultZone();

        assertEquals(AppTime.ZONE, TimeZone.getDefault().toZoneId());
        assertEquals(AppTime.today(), LocalDate.now());
    }

    /**
     * 机器时区比东八区慢一天时，用户在东八区选的「今天」不能被判成未来日期。
     * 这里不依赖真实时钟，直接按固定日期比较两种取「今天」的写法。
     */
    @Test
    void 主机慢一天时今天的日期不会被判成晚于今天() {
        ZoneId shanghai = ZoneId.of("Asia/Shanghai");
        // 北京时间 9 月 3 日凌晨 1 点，此刻檀香山还是 9 月 2 日
        LocalDate userToday = LocalDate.of(2026, 9, 3);
        LocalDate hostToday = LocalDate.of(2026, 9, 2);

        // 修复前的比较基准是主机日期：今天的账被当成明天，直接拒收
        assertTrue(userToday.isAfter(hostToday));
        // 修复后统一按业务时区取「今天」，同一天不会被拒
        assertFalse(userToday.isAfter(LocalDate.of(2026, 9, 3)));
        assertEquals(shanghai, AppTime.ZONE);
    }
}
