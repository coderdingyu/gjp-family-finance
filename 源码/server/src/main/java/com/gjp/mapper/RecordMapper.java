package com.gjp.mapper;

import com.gjp.entity.Record;
import com.gjp.record.RecordQuery;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.math.BigDecimal;
import java.util.List;

/**
 * 收支流水数据访问（核心表）。
 * 列表查询统一联出成员姓名与分类名称，避免前端为了显示名称再发 N 次请求。
 */
public interface RecordMapper {

    /** 列表查询与统计共用的 FROM + 联表片段，改动这里要同时确认 countByQuery */
    String JOIN = " FROM t_record r "
            + " LEFT JOIN t_member m ON r.member_id = m.id "
            + " LEFT JOIN t_category c ON r.category_id = c.id "
            + " LEFT JOIN t_category pc ON c.parent_id = pc.id "
            // rc 是所属的一级分类。三级分类改造后，只联父级只能显示"外出就餐/快餐"，
            // 联上一级分类才能显示完整路径"餐饮支出/外出就餐/快餐"
            + " LEFT JOIN t_category rc ON c.root_id = rc.id ";

    /** 动态 where 片段：与 countByQuery 完全一致，保证列表与总数口径相同 */
    String WHERE = " <where> r.family_id = #{familyId} "
            + " <if test='q.type != null'> AND r.type = #{q.type} </if>"
            + " <if test='q.memberId != null'> AND r.member_id = #{q.memberId} </if>"
            // 分类条件：匹配自身、直接子分类，以及 root_id（选一级时含全部三级明细）
            + " <if test='q.categoryId != null'> AND (r.category_id = #{q.categoryId} "
            + "     OR c.parent_id = #{q.categoryId} OR c.root_id = #{q.categoryId}) </if>"
            + " <if test='q.startDate != null'> AND r.record_date <![CDATA[>=]]> #{q.startDate} </if>"
            + " <if test='q.endDate != null'> AND r.record_date <![CDATA[<=]]> #{q.endDate} </if>"
            + " <if test='q.payMethod != null and q.payMethod != \"\"'> AND r.pay_method = #{q.payMethod} </if>"
            + " <if test='q.area != null and q.area != \"\"'> AND r.area = #{q.area} </if>"
            + " <if test='q.isGift != null'> AND r.is_gift = #{q.isGift} </if>"
            + " <if test='q.minAmount != null'> AND r.amount <![CDATA[>=]]> #{q.minAmount} </if>"
            + " <if test='q.maxAmount != null'> AND r.amount <![CDATA[<=]]> #{q.maxAmount} </if>"
            + " <if test='q.keyword != null and q.keyword != \"\"'> AND (r.merchant LIKE CONCAT('%', #{q.keyword}, '%') ESCAPE '\\\\' "
            + "     OR r.remark LIKE CONCAT('%', #{q.keyword}, '%') ESCAPE '\\\\') </if>"
            + " </where> ";

    @Select("<script>"
            + "SELECT r.*, m.member_name AS member_name, c.category_name AS category_name, "
            + "       pc.category_name AS parent_category_name, rc.category_name AS root_category_name, "
            + "       c.level AS category_level "
            + JOIN
            + WHERE
            + " ORDER BY r.record_date DESC, r.id DESC "
            + " LIMIT #{q.offset}, #{q.pageSize}"
            + "</script>")
    List<Record> selectByQuery(@Param("familyId") Long familyId, @Param("q") RecordQuery q);

    @Select("<script>"
            + "SELECT COUNT(*) " + JOIN + WHERE
            + "</script>")
    long countByQuery(@Param("familyId") Long familyId, @Param("q") RecordQuery q);

    /** 当前查询条件下的收入合计与支出合计，用于列表页顶部的汇总条 */
    @Select("<script>"
            + "SELECT COALESCE(SUM(CASE WHEN r.type = #{type} THEN r.amount ELSE 0 END), 0) "
            + JOIN + WHERE
            + "</script>")
    BigDecimal sumAmountByQuery(@Param("familyId") Long familyId, @Param("q") RecordQuery q,
                                @Param("type") Integer type);

    @Select("SELECT r.*, m.member_name AS member_name, c.category_name AS category_name, "
            + "       pc.category_name AS parent_category_name, rc.category_name AS root_category_name, "
            + "       c.level AS category_level "
            + JOIN
            + " WHERE r.id = #{id} AND r.family_id = #{familyId}")
    Record selectById(@Param("id") Long id, @Param("familyId") Long familyId);

    @Insert("INSERT INTO t_record (family_id, member_id, category_id, type, amount, record_date, "
            + "merchant, area, pay_method, is_gift, remark) "
            + "VALUES (#{familyId}, #{memberId}, #{categoryId}, #{type}, #{amount}, #{recordDate}, "
            + "#{merchant}, #{area}, #{payMethod}, #{isGift}, #{remark})")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(Record record);

    @Update("UPDATE t_record SET member_id = #{memberId}, category_id = #{categoryId}, type = #{type}, "
            + "amount = #{amount}, record_date = #{recordDate}, merchant = #{merchant}, area = #{area}, "
            + "pay_method = #{payMethod}, is_gift = #{isGift}, remark = #{remark} "
            + "WHERE id = #{id} AND family_id = #{familyId}")
    int update(Record record);

    @Delete("DELETE FROM t_record WHERE id = #{id} AND family_id = #{familyId}")
    int deleteById(@Param("id") Long id, @Param("familyId") Long familyId);

    /** 下拉候选：已经录过的商家 / 片区，录入时给出联想，减少同一商家写成多个名字 */
    @Select("SELECT DISTINCT merchant FROM t_record WHERE family_id = #{familyId} "
            + "AND merchant IS NOT NULL AND merchant <> '' ORDER BY merchant")
    List<String> selectMerchants(@Param("familyId") Long familyId);

    @Select("SELECT DISTINCT area FROM t_record WHERE family_id = #{familyId} "
            + "AND area IS NOT NULL AND area <> '' ORDER BY area")
    List<String> selectAreas(@Param("familyId") Long familyId);
}
