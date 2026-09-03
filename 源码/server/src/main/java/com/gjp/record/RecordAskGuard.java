package com.gjp.record;

import java.util.Set;

/**
 * 账单问答的权限收敛：智能体给出的 memberId 不能越权。
 */
public final class RecordAskGuard {

    private RecordAskGuard() {
    }

    /**
     * @param llmMemberId        智能体想查的成员，可能为 null
     * @param requestedMemberId  前端传入的成员（户主可空=全家）
     * @param owner              当前用户是否户主
     * @param selfMemberId       普通成员自己的成员ID
     * @param visibleMemberIds   当前用户可见的本家庭成员ID
     */
    public static Long sanitizeMemberId(Long llmMemberId, Long requestedMemberId,
                                        boolean owner, Long selfMemberId,
                                        Set<Long> visibleMemberIds) {
        if (!owner) {
            return selfMemberId;
        }
        if (llmMemberId == null) {
            return requestedMemberId;
        }
        if (visibleMemberIds != null && visibleMemberIds.contains(llmMemberId)) {
            return llmMemberId;
        }
        return requestedMemberId;
    }
}
