package com.gjp.imp;

import java.util.List;

public class ConfirmRequest {

    /** 要入库的待确认项；空或省略表示全部 pending */
    private List<Long> itemIds;
    /** true=合并后入库，与账本或本次其他文件重复的只留一份 */
    private Boolean merge;

    public List<Long> getItemIds() {
        return itemIds;
    }

    public void setItemIds(List<Long> itemIds) {
        this.itemIds = itemIds;
    }

    public Boolean getMerge() {
        return merge;
    }

    public void setMerge(Boolean merge) {
        this.merge = merge;
    }
}
