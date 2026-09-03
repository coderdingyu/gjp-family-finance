package com.gjp.record;

/**
 * POST /api/record/ask 入参。
 */
public class RecordAskRequest {

    /** 自然语言问题 */
    private String q;
    /** 户主可指定成员范围；普通成员传了也会被强制成自己 */
    private Long memberId;

    public String getQ() {
        return q;
    }

    public void setQ(String q) {
        this.q = q;
    }

    public Long getMemberId() {
        return memberId;
    }

    public void setMemberId(Long memberId) {
        this.memberId = memberId;
    }
}
