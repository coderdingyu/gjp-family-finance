package com.gjp.common;

import java.util.List;

/**
 * 分页返回结构，用于流水列表等需要翻页的查询。
 */
public class PageResult<T> {

    /** 符合条件的总记录数 */
    private long total;
    /** 当前页数据 */
    private List<T> list;

    public PageResult() {
    }

    public PageResult(long total, List<T> list) {
        this.total = total;
        this.list = list;
    }

    public long getTotal() {
        return total;
    }

    public void setTotal(long total) {
        this.total = total;
    }

    public List<T> getList() {
        return list;
    }

    public void setList(List<T> list) {
        this.list = list;
    }
}
