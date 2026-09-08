package com.gjp.mapper;

import com.gjp.analysis.AnalysisData;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import java.time.LocalDate;
import java.util.List;

public interface AnalysisMapper {
    String FILTER = " WHERE r.family_id = #{familyId} AND r.record_date BETWEEN #{start} AND #{end} "
            + StatMapper.MEMBER_COND;

    @Select("<script>SELECT DATE_FORMAT(r.record_date, '%Y-%m') AS ym, rc.id AS categoryId, "
            + "rc.category_name AS name, SUM(r.amount) AS amount, COUNT(*) AS count FROM t_record r "
            + "JOIN t_category c ON c.id=r.category_id JOIN t_category rc ON rc.id=c.root_id "
            + FILTER + " AND r.type=2 GROUP BY ym, rc.id, rc.category_name ORDER BY ym, rc.id</script>")
    List<AnalysisData.CategoryMonth> categoryMonths(@Param("familyId") Long familyId,
            @Param("memberId") Long memberId, @Param("start") LocalDate start, @Param("end") LocalDate end);

    @Select("<script>SELECT DATE_FORMAT(r.record_date, '%Y-%m') AS ym, r.member_id AS memberId, "
            + "SUM(r.amount) AS amount FROM t_record r " + FILTER
            + " AND r.type=2 GROUP BY ym, r.member_id ORDER BY ym, r.member_id</script>")
    List<AnalysisData.MemberMonth> memberMonths(@Param("familyId") Long familyId,
            @Param("memberId") Long memberId, @Param("start") LocalDate start, @Param("end") LocalDate end);

    @Select("<script>SELECT COALESCE(SUM(r.type=2),0) AS expenseCount, "
            + "COALESCE(SUM(r.type=1),0) AS incomeCount, "
            + "COALESCE(SUM(r.type=2 AND TRIM(COALESCE(r.merchant,'')) != ''),0) AS merchantFilledCount "
            + "FROM t_record r " + FILTER + "</script>")
    AnalysisData.Quality quality(@Param("familyId") Long familyId,
            @Param("memberId") Long memberId, @Param("start") LocalDate start, @Param("end") LocalDate end);
}
