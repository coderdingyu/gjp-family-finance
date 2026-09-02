package com.gjp.mapper;

import com.gjp.stat.vo.AmountItem;
import com.gjp.stat.vo.MonthAmount;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * 统计分析数据访问。
 *
 * 统计口径统一约定：
 *   · 日期区间为闭区间 [startDate, endDate]
 *   · type：1=收入 2=支出
 *   · memberId 为空表示全家汇总，非空表示只统计该成员
 *     —— 这个参数由 Service 按登录角色算好（普通成员会被强制成自己），
 *        Mapper 只负责拼条件，不做权限判断。
 *   · 所有聚合都用 COALESCE 兜底，没有数据时返回 0 而不是 null，前端不用再判空。
 *
 * 分类汇总统一用 root_id 关联到一级分类。三级分类改造前这里要连续 JOIN 两次父表，
 * 现在 t_category 冗余了 root_id，一次等值 JOIN 就能拿到顶级分类，SQL 明显更短也更快。
 */
public interface StatMapper {

    /** 成员过滤片段，各查询复用，保证口径一致 */
    String MEMBER_COND = " <if test='memberId != null'> AND r.member_id = #{memberId} </if>";

    /** 区间内某一类型（收入/支出）的金额合计 */
    @Select("<script>SELECT COALESCE(SUM(r.amount), 0) FROM t_record r "
            + "WHERE r.family_id = #{familyId} AND r.type = #{type} "
            + "AND r.record_date BETWEEN #{startDate} AND #{endDate} " + MEMBER_COND + "</script>")
    BigDecimal sumAmount(@Param("familyId") Long familyId, @Param("type") Integer type,
                         @Param("startDate") LocalDate startDate, @Param("endDate") LocalDate endDate,
                         @Param("memberId") Long memberId);

    /** 区间内流水笔数 */
    @Select("<script>SELECT COUNT(*) FROM t_record r WHERE r.family_id = #{familyId} "
            + "AND r.record_date BETWEEN #{startDate} AND #{endDate} " + MEMBER_COND + "</script>")
    int countRecords(@Param("familyId") Long familyId,
                     @Param("startDate") LocalDate startDate, @Param("endDate") LocalDate endDate,
                     @Param("memberId") Long memberId);

    /** 区间内单笔最大支出金额 */
    @Select("<script>SELECT COALESCE(MAX(r.amount), 0) FROM t_record r WHERE r.family_id = #{familyId} "
            + "AND r.type = 2 AND r.record_date BETWEEN #{startDate} AND #{endDate} " + MEMBER_COND + "</script>")
    BigDecimal maxExpense(@Param("familyId") Long familyId,
                          @Param("startDate") LocalDate startDate, @Param("endDate") LocalDate endDate,
                          @Param("memberId") Long memberId);

    /** 区间内人情往来支出合计（is_gift = 1），对应课程要求"朋友间礼尚往来的消费有多少" */
    @Select("<script>SELECT COALESCE(SUM(r.amount), 0) FROM t_record r WHERE r.family_id = #{familyId} "
            + "AND r.type = 2 AND r.is_gift = 1 AND r.record_date BETWEEN #{startDate} AND #{endDate} "
            + MEMBER_COND + "</script>")
    BigDecimal sumGiftExpense(@Param("familyId") Long familyId,
                              @Param("startDate") LocalDate startDate, @Param("endDate") LocalDate endDate,
                              @Param("memberId") Long memberId);

    /** 按月汇总收入与支出，收支趋势折线图数据源 */
    @Select("<script>SELECT DATE_FORMAT(r.record_date, '%Y-%m') AS ym, "
            + "       COALESCE(SUM(CASE WHEN r.type = 1 THEN r.amount ELSE 0 END), 0) AS income, "
            + "       COALESCE(SUM(CASE WHEN r.type = 2 THEN r.amount ELSE 0 END), 0) AS expense "
            + "FROM t_record r WHERE r.family_id = #{familyId} "
            + "AND r.record_date BETWEEN #{startDate} AND #{endDate} " + MEMBER_COND
            + " GROUP BY DATE_FORMAT(r.record_date, '%Y-%m') ORDER BY ym</script>")
    List<MonthAmount> selectMonthlyTrend(@Param("familyId") Long familyId,
                                         @Param("startDate") LocalDate startDate,
                                         @Param("endDate") LocalDate endDate,
                                         @Param("memberId") Long memberId);

    /**
     * 按一级分类汇总。二三级分类的流水都会归到它所属的一级分类上（靠 root_id），
     * 这样饼图上不会出现几十个碎片化的下级分类。
     */
    @Select("<script>SELECT rc.id AS id, rc.category_name AS name, "
            + "       COALESCE(SUM(r.amount), 0) AS amount, COUNT(*) AS count "
            + "FROM t_record r "
            + "JOIN t_category c  ON r.category_id = c.id "
            + "JOIN t_category rc ON c.root_id = rc.id "
            + "WHERE r.family_id = #{familyId} AND r.type = #{type} "
            + "AND r.record_date BETWEEN #{startDate} AND #{endDate} " + MEMBER_COND
            + " GROUP BY rc.id, rc.category_name ORDER BY amount DESC</script>")
    List<AmountItem> selectCategoryStat(@Param("familyId") Long familyId, @Param("type") Integer type,
                                        @Param("startDate") LocalDate startDate,
                                        @Param("endDate") LocalDate endDate,
                                        @Param("memberId") Long memberId);

    /**
     * 按直接子分类汇总（钻取用）。给定一个分类，看它下面各直接子分类的构成。
     * 注意这里统计的是"挂在该子分类及其后代上"的金额：
     * 二级分类下可能还有三级，如果只算直接挂在二级上的流水，钻取结果会漏掉三级的钱。
     * 实现方式是先把每条流水映射到它在目标层级下的祖先分类。
     */
    @Select("<script>SELECT sub.id AS id, sub.category_name AS name, "
            + "       COALESCE(SUM(r.amount), 0) AS amount, COUNT(*) AS count "
            + "FROM t_record r "
            + "JOIN t_category c ON r.category_id = c.id "
            + "JOIN t_category sub ON sub.parent_id = #{parentId} "
            + "     AND (c.id = sub.id OR c.parent_id = sub.id) "
            + "WHERE r.family_id = #{familyId} "
            + "AND r.record_date BETWEEN #{startDate} AND #{endDate} " + MEMBER_COND
            + " GROUP BY sub.id, sub.category_name ORDER BY amount DESC</script>")
    List<AmountItem> selectSubCategoryStat(@Param("familyId") Long familyId, @Param("parentId") Long parentId,
                                           @Param("startDate") LocalDate startDate,
                                           @Param("endDate") LocalDate endDate,
                                           @Param("memberId") Long memberId);

    /** 按家庭成员汇总，成员收支对比柱状图数据源。这个查询本身就是分成员的，不再叠加 memberId 过滤 */
    @Select("<script>SELECT m.id AS id, m.member_name AS name, "
            + "       COALESCE(SUM(r.amount), 0) AS amount, COUNT(*) AS count "
            + "FROM t_record r JOIN t_member m ON r.member_id = m.id "
            + "WHERE r.family_id = #{familyId} AND r.type = #{type} "
            + "AND r.record_date BETWEEN #{startDate} AND #{endDate} " + MEMBER_COND
            + " GROUP BY m.id, m.member_name ORDER BY amount DESC</script>")
    List<AmountItem> selectMemberStat(@Param("familyId") Long familyId, @Param("type") Integer type,
                                      @Param("startDate") LocalDate startDate,
                                      @Param("endDate") LocalDate endDate,
                                      @Param("memberId") Long memberId);

    /** 商家消费排行。房租房贷月供（本分类或其下级）不进榜，避免把还款银行当成消费商家 */
    @Select("<script>SELECT r.merchant AS name, COALESCE(SUM(r.amount), 0) AS amount, COUNT(*) AS count "
            + "FROM t_record r "
            + "JOIN t_category c ON r.category_id = c.id "
            + "LEFT JOIN t_category p ON c.parent_id = p.id "
            + "WHERE r.family_id = #{familyId} AND r.type = 2 "
            + "AND r.merchant IS NOT NULL AND r.merchant <![CDATA[<>]]> '' "
            + "AND c.category_name <![CDATA[<>]]> '房租房贷' "
            + "AND (p.category_name IS NULL OR p.category_name <![CDATA[<>]]> '房租房贷') "
            + "AND r.record_date BETWEEN #{startDate} AND #{endDate} " + MEMBER_COND
            + " GROUP BY r.merchant ORDER BY amount DESC LIMIT #{limit}</script>")
    List<AmountItem> selectMerchantRank(@Param("familyId") Long familyId,
                                        @Param("startDate") LocalDate startDate,
                                        @Param("endDate") LocalDate endDate,
                                        @Param("memberId") Long memberId, @Param("limit") int limit);

    /** 片区消费分布：回答"主要消费集中在哪个片区" */
    @Select("<script>SELECT r.area AS name, COALESCE(SUM(r.amount), 0) AS amount, COUNT(*) AS count "
            + "FROM t_record r WHERE r.family_id = #{familyId} AND r.type = 2 "
            + "AND r.area IS NOT NULL AND r.area <![CDATA[<>]]> '' "
            + "AND r.record_date BETWEEN #{startDate} AND #{endDate} " + MEMBER_COND
            + " GROUP BY r.area ORDER BY amount DESC</script>")
    List<AmountItem> selectAreaStat(@Param("familyId") Long familyId,
                                    @Param("startDate") LocalDate startDate,
                                    @Param("endDate") LocalDate endDate,
                                    @Param("memberId") Long memberId);

    /** 支付方式构成 */
    @Select("<script>SELECT COALESCE(NULLIF(r.pay_method, ''), '未填写') AS name, "
            + "       COALESCE(SUM(r.amount), 0) AS amount, COUNT(*) AS count "
            + "FROM t_record r WHERE r.family_id = #{familyId} AND r.type = #{type} "
            + "AND r.record_date BETWEEN #{startDate} AND #{endDate} " + MEMBER_COND
            + " GROUP BY COALESCE(NULLIF(r.pay_method, ''), '未填写') ORDER BY amount DESC</script>")
    List<AmountItem> selectPayMethodStat(@Param("familyId") Long familyId, @Param("type") Integer type,
                                         @Param("startDate") LocalDate startDate,
                                         @Param("endDate") LocalDate endDate,
                                         @Param("memberId") Long memberId);

    /** 指定成员在指定月份的支出合计，预算执行率用 */
    @Select("SELECT COALESCE(SUM(amount), 0) FROM t_record WHERE family_id = #{familyId} "
            + "AND member_id = #{memberId} AND type = 2 "
            + "AND record_date BETWEEN #{startDate} AND #{endDate}")
    BigDecimal sumMemberExpense(@Param("familyId") Long familyId, @Param("memberId") Long memberId,
                                @Param("startDate") LocalDate startDate,
                                @Param("endDate") LocalDate endDate);

    /** 某区间各一级分类的支出金额，异常月份归因时用来做对照 */
    @Select("<script>SELECT rc.id AS id, rc.category_name AS name, "
            + "       COALESCE(SUM(r.amount), 0) AS amount, COUNT(*) AS count "
            + "FROM t_record r "
            + "JOIN t_category c  ON r.category_id = c.id "
            + "JOIN t_category rc ON c.root_id = rc.id "
            + "WHERE r.family_id = #{familyId} AND r.type = 2 "
            + "AND r.record_date BETWEEN #{startDate} AND #{endDate} " + MEMBER_COND
            + " GROUP BY rc.id, rc.category_name ORDER BY amount DESC</script>")
    List<AmountItem> selectExpenseByCategory(@Param("familyId") Long familyId,
                                             @Param("startDate") LocalDate startDate,
                                             @Param("endDate") LocalDate endDate,
                                             @Param("memberId") Long memberId);

    /** 家庭最早/最晚一笔流水日期 */
    @Select("SELECT MIN(record_date) FROM t_record WHERE family_id = #{familyId}")
    LocalDate selectMinDate(@Param("familyId") Long familyId);

    @Select("SELECT MAX(record_date) FROM t_record WHERE family_id = #{familyId}")
    LocalDate selectMaxDate(@Param("familyId") Long familyId);
}
