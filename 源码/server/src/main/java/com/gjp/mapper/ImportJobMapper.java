package com.gjp.mapper;

import com.gjp.imp.ImportJob;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

public interface ImportJobMapper {

    String JOB_COLUMNS = "j.*, m.member_name AS member_name, "
            + "(SELECT COUNT(*) FROM t_import_job x "
            + "WHERE x.family_id = j.family_id AND x.user_id = j.user_id AND NOT (x.id > j.id)) AS seq_no";

    @Insert("INSERT INTO t_import_job (family_id, user_id, member_id, status, total_files, done_files, "
            + "extracted, imported, rejected, message) VALUES (#{familyId}, #{userId}, #{memberId}, #{status}, "
            + "#{totalFiles}, #{doneFiles}, #{extracted}, #{imported}, #{rejected}, #{message})")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(ImportJob job);

    @Select("SELECT " + JOB_COLUMNS + " FROM t_import_job j "
            + "LEFT JOIN t_member m ON j.member_id = m.id "
            + "WHERE j.id = #{id} AND j.family_id = #{familyId}")
    ImportJob selectById(@Param("id") Long id, @Param("familyId") Long familyId);

    @Select("<script>SELECT " + JOB_COLUMNS + " FROM t_import_job j "
            + "LEFT JOIN t_member m ON j.member_id = m.id "
            + "WHERE j.family_id = #{familyId} "
            + "<if test='userId != null'> AND j.user_id = #{userId} </if> "
            + "ORDER BY j.id DESC LIMIT #{limit}</script>")
    java.util.List<ImportJob> selectRecent(@Param("familyId") Long familyId,
                                           @Param("userId") Long userId,
                                           @Param("limit") int limit);

    @Select("SELECT j.*, m.member_name AS member_name FROM t_import_job j "
            + "LEFT JOIN t_member m ON j.member_id = m.id "
            + "WHERE j.status IN ('queued', 'running') ORDER BY j.id")
    java.util.List<ImportJob> selectUnfinished();

    /**
     * 卡在入库中的任务。确认入库不是后台任务，进程在这一步被杀就没人再改它的状态，
     * 而 importing 状态下 confirm 和 cancel 都会被拒绝，这批待确认流水会永远入不了库。
     * 启动时单独捞出来复位，不能并进 selectUnfinished —— 那条路会重新丢给线程池解析。
     */
    @Select("SELECT j.*, m.member_name AS member_name FROM t_import_job j "
            + "LEFT JOIN t_member m ON j.member_id = m.id "
            + "WHERE j.status = 'importing' ORDER BY j.id")
    java.util.List<ImportJob> selectStuckImporting();

    @Update("UPDATE t_import_job SET status=#{status}, done_files=#{doneFiles}, extracted=#{extracted}, "
            + "imported=#{imported}, rejected=#{rejected}, message=#{message}, "
            + "finish_time=#{finishTime} WHERE id=#{id}")
    int update(ImportJob job);
}
