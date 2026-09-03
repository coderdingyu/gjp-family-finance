package com.gjp.mapper;

import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;
import java.util.Map;

/**
 * 账单查重数据访问（需求第 6 条）。
 *
 * 实现思路：先用一条自连接 SQL 找出**两两可疑的配对**，再在 Java 里把配对合并成组。
 *
 * 为什么不直接 GROUP BY 出组？
 *   完全一致的重复可以用 GROUP BY amount, record_date 找出来，
 *   但"消费时间高度相似"（比如 3 号和 5 号各录了一笔 268 元）跨不同日期，
 *   GROUP BY 分不到一组。自连接 + 日期差判断能同时覆盖这两种情况，
 *   代价是要在应用层做一次并查集合并。
 *
 * 性能上靠 a.amount = b.amount 这个等值条件先把候选集压到很小，
 * 再配合 a.id < b.id 去掉重复配对和自身配对。
 */
public interface DedupMapper {

    /**
     * 找出可疑的重复配对。
     *
     * @param dayTolerance   日期容差（天）。0 表示只找同一天的；3 表示相差 3 天内都算可疑
     * @param sameMember     是否要求同一成员
     * @param sameCategory   是否要求同一分类
     */
    @Select("<script>"
            + "SELECT a.id AS idA, b.id AS idB, "
            + "       ABS(DATEDIFF(a.record_date, b.record_date)) AS dayDiff, "
            + "       a.amount AS amount "
            + "FROM t_record a JOIN t_record b "
            + "  ON a.family_id = b.family_id "
            + " AND a.id <![CDATA[<]]> b.id "
            + " AND a.amount = b.amount "
            + " AND a.type = b.type "
            + " AND ABS(DATEDIFF(a.record_date, b.record_date)) <![CDATA[<=]]> #{dayTolerance} "
            + "<if test='sameMember'> AND a.member_id = b.member_id </if>"
            + "<if test='sameCategory'> AND a.category_id = b.category_id </if>"
            + "WHERE a.family_id = #{familyId} "
            + "<if test='memberId != null'> AND a.member_id = #{memberId} AND b.member_id = #{memberId} </if>"
            + "<if test='startDate != null'> AND a.record_date <![CDATA[>=]]> #{startDate} "
            + "     AND b.record_date <![CDATA[>=]]> #{startDate} </if>"
            + "<if test='endDate != null'> AND a.record_date <![CDATA[<=]]> #{endDate} "
            + "     AND b.record_date <![CDATA[<=]]> #{endDate} </if>"
            + "ORDER BY a.amount DESC, a.id "
            + "LIMIT 2000"
            + "</script>")
    List<Map<String, Object>> findPairs(@Param("familyId") Long familyId,
                                        @Param("memberId") Long memberId,
                                        @Param("dayTolerance") int dayTolerance,
                                        @Param("sameMember") boolean sameMember,
                                        @Param("sameCategory") boolean sameCategory,
                                        @Param("startDate") String startDate,
                                        @Param("endDate") String endDate);

    /**
     * 两边都有非空订单号且忽略大小写相同：强重复，不看金额/日期/商家。
     * 只有一边有订单号的不在这里，仍走 {@link #findPairs}。
     */
    @Select("<script>"
            + "SELECT a.id AS idA, b.id AS idB, "
            + "       ABS(DATEDIFF(a.record_date, b.record_date)) AS dayDiff, "
            + "       a.amount AS amount "
            + "FROM t_record a JOIN t_record b "
            + "  ON a.family_id = b.family_id "
            + " AND a.id <![CDATA[<]]> b.id "
            + " AND a.order_no IS NOT NULL AND TRIM(a.order_no) <![CDATA[<>]]> '' "
            + " AND b.order_no IS NOT NULL AND TRIM(b.order_no) <![CDATA[<>]]> '' "
            + " AND LOWER(TRIM(a.order_no)) = LOWER(TRIM(b.order_no)) "
            + "<if test='sameMember'> AND a.member_id = b.member_id </if>"
            + "WHERE a.family_id = #{familyId} "
            + "<if test='memberId != null'> AND a.member_id = #{memberId} AND b.member_id = #{memberId} </if>"
            + "<if test='startDate != null'> AND a.record_date <![CDATA[>=]]> #{startDate} "
            + "     AND b.record_date <![CDATA[>=]]> #{startDate} </if>"
            + "<if test='endDate != null'> AND a.record_date <![CDATA[<=]]> #{endDate} "
            + "     AND b.record_date <![CDATA[<=]]> #{endDate} </if>"
            + "ORDER BY a.id "
            + "LIMIT 2000"
            + "</script>")
    List<Map<String, Object>> findOrderNoPairs(@Param("familyId") Long familyId,
                                               @Param("memberId") Long memberId,
                                               @Param("sameMember") boolean sameMember,
                                               @Param("startDate") String startDate,
                                               @Param("endDate") String endDate);
}
