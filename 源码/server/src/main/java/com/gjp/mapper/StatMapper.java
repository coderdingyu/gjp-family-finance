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
 * 统计口径统一约定：日期区间为闭区间 [startDate, endDate]，type 1=收入 2=支出。
 * 所有聚合都用 COALESCE 兜底，保证没有数据时返回 0 而不是 null，前端不用再判空。
 */
public interface StatMapper {

    /** 区间内某一类型（收入/支出）的金额合计 */
    @Select("SELECT COALESCE(SUM(amount), 0) FROM t_record "
            + "WHERE family_id = #{familyId} AND type = #{type} "
            + "AND record_date BETWEEN #{startDate} AND #{endDate}")
    BigDecimal sumAmount(@Param("familyId") Long familyId, @Param("type") Integer type,
                         @Param("startDate") LocalDate startDate, @Param("endDate") LocalDate endDate);

    /** 区间内流水笔数 */
    @Select("SELECT COUNT(*) FROM t_record WHERE family_id = #{familyId} "
            + "AND record_date BETWEEN #{startDate} AND #{endDate}")
    int countRecords(@Param("familyId") Long familyId,
                     @Param("startDate") LocalDate startDate, @Param("endDate") LocalDate endDate);

    /** 区间内单笔最大支出金额 */
    @Select("SELECT COALESCE(MAX(amount), 0) FROM t_record WHERE family_id = #{familyId} "
            + "AND type = 2 AND record_date BETWEEN #{startDate} AND #{endDate}")
    BigDecimal maxExpense(@Param("familyId") Long familyId,
                          @Param("startDate") LocalDate startDate, @Param("endDate") LocalDate endDate);

    /** 区间内人情往来支出合计（is_gift = 1），对应课程要求"朋友间礼尚往来的消费有多少" */
    @Select("SELECT COALESCE(SUM(amount), 0) FROM t_record WHERE family_id = #{familyId} "
            + "AND type = 2 AND is_gift = 1 AND record_date BETWEEN #{startDate} AND #{endDate}")
    BigDecimal sumGiftExpense(@Param("familyId") Long familyId,
                              @Param("startDate") LocalDate startDate, @Param("endDate") LocalDate endDate);

    /** 按月汇总收入与支出，收支趋势折线图数据源 */
    @Select("SELECT DATE_FORMAT(record_date, '%Y-%m') AS ym, "
            + "       COALESCE(SUM(CASE WHEN type = 1 THEN amount ELSE 0 END), 0) AS income, "
            + "       COALESCE(SUM(CASE WHEN type = 2 THEN amount ELSE 0 END), 0) AS expense "
            + "FROM t_record WHERE family_id = #{familyId} "
            + "AND record_date BETWEEN #{startDate} AND #{endDate} "
            + "GROUP BY DATE_FORMAT(record_date, '%Y-%m') ORDER BY ym")
    List<MonthAmount> selectMonthlyTrend(@Param("familyId") Long familyId,
                                         @Param("startDate") LocalDate startDate,
                                         @Param("endDate") LocalDate endDate);

    /**
     * 按一级分类汇总。二级分类的流水会归到它的父分类上（用 IFNULL 把一级分类自身也覆盖掉），
     * 这样饼图上不会出现十几个碎片化的二级分类。
     */
    @Select("SELECT COALESCE(pc.id, c.id) AS id, "
            + "       COALESCE(pc.category_name, c.category_name) AS name, "
            + "       COALESCE(SUM(r.amount), 0) AS amount, COUNT(*) AS count "
            + "FROM t_record r "
            + "JOIN t_category c ON r.category_id = c.id "
            + "LEFT JOIN t_category pc ON c.parent_id = pc.id AND c.parent_id <> 0 "
            + "WHERE r.family_id = #{familyId} AND r.type = #{type} "
            + "AND r.record_date BETWEEN #{startDate} AND #{endDate} "
            + "GROUP BY COALESCE(pc.id, c.id), COALESCE(pc.category_name, c.category_name) "
            + "ORDER BY amount DESC")
    List<AmountItem> selectCategoryStat(@Param("familyId") Long familyId, @Param("type") Integer type,
                                        @Param("startDate") LocalDate startDate,
                                        @Param("endDate") LocalDate endDate);

    /** 按二级分类汇总（钻取用）：给定一级分类，看它下面各二级分类的构成 */
    @Select("SELECT c.id AS id, c.category_name AS name, "
            + "       COALESCE(SUM(r.amount), 0) AS amount, COUNT(*) AS count "
            + "FROM t_record r JOIN t_category c ON r.category_id = c.id "
            + "WHERE r.family_id = #{familyId} AND c.parent_id = #{parentId} "
            + "AND r.record_date BETWEEN #{startDate} AND #{endDate} "
            + "GROUP BY c.id, c.category_name ORDER BY amount DESC")
    List<AmountItem> selectSubCategoryStat(@Param("familyId") Long familyId, @Param("parentId") Long parentId,
                                           @Param("startDate") LocalDate startDate,
                                           @Param("endDate") LocalDate endDate);

    /** 按家庭成员汇总，成员收支对比柱状图数据源 */
    @Select("SELECT m.id AS id, m.member_name AS name, "
            + "       COALESCE(SUM(r.amount), 0) AS amount, COUNT(*) AS count "
            + "FROM t_record r JOIN t_member m ON r.member_id = m.id "
            + "WHERE r.family_id = #{familyId} AND r.type = #{type} "
            + "AND r.record_date BETWEEN #{startDate} AND #{endDate} "
            + "GROUP BY m.id, m.member_name ORDER BY amount DESC")
    List<AmountItem> selectMemberStat(@Param("familyId") Long familyId, @Param("type") Integer type,
                                      @Param("startDate") LocalDate startDate,
                                      @Param("endDate") LocalDate endDate);

    /** 商家消费排行：回答"年度外部餐饮主要在哪些商家消费" */
    @Select("SELECT r.merchant AS name, COALESCE(SUM(r.amount), 0) AS amount, COUNT(*) AS count "
            + "FROM t_record r WHERE r.family_id = #{familyId} AND r.type = 2 "
            + "AND r.merchant IS NOT NULL AND r.merchant <> '' "
            + "AND r.record_date BETWEEN #{startDate} AND #{endDate} "
            + "GROUP BY r.merchant ORDER BY amount DESC LIMIT #{limit}")
    List<AmountItem> selectMerchantRank(@Param("familyId") Long familyId,
                                        @Param("startDate") LocalDate startDate,
                                        @Param("endDate") LocalDate endDate, @Param("limit") int limit);

    /** 片区消费分布：回答"主要消费集中在哪个片区" */
    @Select("SELECT r.area AS name, COALESCE(SUM(r.amount), 0) AS amount, COUNT(*) AS count "
            + "FROM t_record r WHERE r.family_id = #{familyId} AND r.type = 2 "
            + "AND r.area IS NOT NULL AND r.area <> '' "
            + "AND r.record_date BETWEEN #{startDate} AND #{endDate} "
            + "GROUP BY r.area ORDER BY amount DESC")
    List<AmountItem> selectAreaStat(@Param("familyId") Long familyId,
                                    @Param("startDate") LocalDate startDate,
                                    @Param("endDate") LocalDate endDate);

    /** 支付方式构成 */
    @Select("SELECT COALESCE(NULLIF(r.pay_method, ''), '未填写') AS name, "
            + "       COALESCE(SUM(r.amount), 0) AS amount, COUNT(*) AS count "
            + "FROM t_record r WHERE r.family_id = #{familyId} AND r.type = #{type} "
            + "AND r.record_date BETWEEN #{startDate} AND #{endDate} "
            + "GROUP BY COALESCE(NULLIF(r.pay_method, ''), '未填写') ORDER BY amount DESC")
    List<AmountItem> selectPayMethodStat(@Param("familyId") Long familyId, @Param("type") Integer type,
                                         @Param("startDate") LocalDate startDate,
                                         @Param("endDate") LocalDate endDate);

    /** 指定成员在指定月份的支出合计，预算执行率用 */
    @Select("SELECT COALESCE(SUM(amount), 0) FROM t_record WHERE family_id = #{familyId} "
            + "AND member_id = #{memberId} AND type = 2 "
            + "AND record_date BETWEEN #{startDate} AND #{endDate}")
    BigDecimal sumMemberExpense(@Param("familyId") Long familyId, @Param("memberId") Long memberId,
                                @Param("startDate") LocalDate startDate,
                                @Param("endDate") LocalDate endDate);

    /** 某月某一级分类的支出金额，异常月份归因时用来做同比/环比对照 */
    @Select("SELECT COALESCE(pc.id, c.id) AS id, "
            + "       COALESCE(pc.category_name, c.category_name) AS name, "
            + "       COALESCE(SUM(r.amount), 0) AS amount, COUNT(*) AS count "
            + "FROM t_record r "
            + "JOIN t_category c ON r.category_id = c.id "
            + "LEFT JOIN t_category pc ON c.parent_id = pc.id AND c.parent_id <> 0 "
            + "WHERE r.family_id = #{familyId} AND r.type = 2 "
            + "AND r.record_date BETWEEN #{startDate} AND #{endDate} "
            + "GROUP BY COALESCE(pc.id, c.id), COALESCE(pc.category_name, c.category_name) "
            + "ORDER BY amount DESC")
    List<AmountItem> selectExpenseByCategory(@Param("familyId") Long familyId,
                                             @Param("startDate") LocalDate startDate,
                                             @Param("endDate") LocalDate endDate);

    /** 家庭最早一笔流水日期，用于"全部年份"这种不指定区间的默认查询 */
    @Select("SELECT MIN(record_date) FROM t_record WHERE family_id = #{familyId}")
    LocalDate selectMinDate(@Param("familyId") Long familyId);

    @Select("SELECT MAX(record_date) FROM t_record WHERE family_id = #{familyId}")
    LocalDate selectMaxDate(@Param("familyId") Long familyId);
}
