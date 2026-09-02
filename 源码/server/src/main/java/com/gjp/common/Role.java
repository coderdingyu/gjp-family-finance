package com.gjp.common;

/**
 * 角色常量与数据可见范围的定义。
 *
 * 三个角色的划分依据是"能看到谁的钱"：
 *   普通成员   只看自己名下的流水与统计。家庭里孩子、父母各有账号时，
 *              各自记账互不可见，避免隐私问题。
 *   户主       家庭的财务负责人，能看全家所有成员的明细，
 *              也是唯一能管理成员、分类、资产负债的角色。
 *   系统管理员 不属于任何家庭、不参与记账，只做网站维护与日志排查。
 *              按需求只设一个，跨家庭可见但看不到具体金额之外的业务操作入口。
 */
public class Role {

    /** 普通成员：只能看自己的数据 */
    public static final int MEMBER = 0;
    /** 户主：可看全家数据，可管理成员与分类 */
    public static final int OWNER = 1;
    /** 系统管理员：跨家庭，仅用于维护 */
    public static final int ADMIN = 2;

    public static String name(Integer role) {
        if (role == null) {
            return "未知";
        }
        switch (role) {
            case OWNER:
                return "户主";
            case ADMIN:
                return "系统管理员";
            default:
                return "普通成员";
        }
    }

    private Role() {
    }
}
