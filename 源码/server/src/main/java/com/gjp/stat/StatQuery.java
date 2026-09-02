package com.gjp.stat;

import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDate;

/**
 * 统计接口的公共查询参数。
 *
 * 原先每个统计接口都要重复写 startDate / endDate / year / month 四个 @RequestParam，
 * 十个接口就是四十行几乎一样的签名；加上 memberId 之后更长。
 * 收成一个对象后用 @ModelAttribute 绑定，Controller 每个方法只剩一行。
 */
public class StatQuery {

    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate startDate;

    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate endDate;

    private Integer year;
    private Integer month;

    /** 1=收入 2=支出，多数接口默认 2 */
    private Integer type;

    /** 想查看的成员；普通成员传了也会被强制成自己 */
    private Long memberId;

    /** 排行榜取前几条 */
    private Integer limit;

    /** 钻取时的父分类ID */
    private Long parentId;

    /** 预算查询的月份，格式 yyyy-MM */
    private String ym;

    /** 解析成统一的日期区间 */
    public DateRange range() {
        return DateRange.of(startDate, endDate, year, month);
    }

    /** 收支类型，未传时按支出 */
    public int typeOrExpense() {
        return type == null ? 2 : type;
    }

    public int limitOrDefault(int fallback) {
        return limit == null || limit < 1 ? fallback : Math.min(limit, 100);
    }

    public LocalDate getStartDate() {
        return startDate;
    }

    public void setStartDate(LocalDate startDate) {
        this.startDate = startDate;
    }

    public LocalDate getEndDate() {
        return endDate;
    }

    public void setEndDate(LocalDate endDate) {
        this.endDate = endDate;
    }

    public Integer getYear() {
        return year;
    }

    public void setYear(Integer year) {
        this.year = year;
    }

    public Integer getMonth() {
        return month;
    }

    public void setMonth(Integer month) {
        this.month = month;
    }

    public Integer getType() {
        return type;
    }

    public void setType(Integer type) {
        this.type = type;
    }

    public Long getMemberId() {
        return memberId;
    }

    public void setMemberId(Long memberId) {
        this.memberId = memberId;
    }

    public Integer getLimit() {
        return limit;
    }

    public void setLimit(Integer limit) {
        this.limit = limit;
    }

    public Long getParentId() {
        return parentId;
    }

    public void setParentId(Long parentId) {
        this.parentId = parentId;
    }

    public String getYm() {
        return ym;
    }

    public void setYm(String ym) {
        this.ym = ym;
    }
}
