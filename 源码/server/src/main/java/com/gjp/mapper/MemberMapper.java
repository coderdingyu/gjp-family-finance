package com.gjp.mapper;

import com.gjp.entity.Member;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

/**
 * 家庭成员数据访问。所有查询都带 family_id 条件，保证家庭之间数据隔离。
 */
public interface MemberMapper {

    @Select("SELECT * FROM t_member WHERE family_id = #{familyId} ORDER BY id")
    List<Member> selectByFamily(@Param("familyId") Long familyId);

    @Select("SELECT * FROM t_member WHERE id = #{id} AND family_id = #{familyId}")
    Member selectById(@Param("id") Long id, @Param("familyId") Long familyId);

    @Select("<script>"
            + "SELECT COUNT(*) FROM t_member WHERE family_id = #{familyId} AND member_name = #{memberName} "
            + "<if test='excludeId != null'> AND id <![CDATA[<>]]> #{excludeId} </if>"
            + "</script>")
    int countByName(@Param("familyId") Long familyId, @Param("memberName") String memberName,
                    @Param("excludeId") Long excludeId);

    @Insert("INSERT INTO t_member (family_id, member_name, relation, monthly_budget) "
            + "VALUES (#{familyId}, #{memberName}, #{relation}, #{monthlyBudget})")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(Member member);

    @Update("UPDATE t_member SET member_name = #{memberName}, relation = #{relation}, "
            + "monthly_budget = #{monthlyBudget} WHERE id = #{id} AND family_id = #{familyId}")
    int update(Member member);

    @Delete("DELETE FROM t_member WHERE id = #{id} AND family_id = #{familyId}")
    int deleteById(@Param("id") Long id, @Param("familyId") Long familyId);

    /** 删除前检查该成员名下是否还有流水，有则不允许删除，避免流水变成孤儿数据 */
    @Select("SELECT COUNT(*) FROM t_record WHERE member_id = #{memberId}")
    int countRecords(@Param("memberId") Long memberId);
}
