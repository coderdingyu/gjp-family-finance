package com.gjp.mapper;

import com.gjp.imp.ImportJob;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

public interface ImportJobMapper {

    @Insert("INSERT INTO t_import_job (family_id, user_id, member_id, status, total_files, done_files, "
            + "extracted, imported, rejected, message) VALUES (#{familyId}, #{userId}, #{memberId}, #{status}, "
            + "#{totalFiles}, #{doneFiles}, #{extracted}, #{imported}, #{rejected}, #{message})")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(ImportJob job);

    @Select("SELECT j.*, m.member_name AS member_name FROM t_import_job j "
            + "LEFT JOIN t_member m ON j.member_id = m.id "
            + "WHERE j.id = #{id} AND j.family_id = #{familyId}")
    ImportJob selectById(@Param("id") Long id, @Param("familyId") Long familyId);

    @Update("UPDATE t_import_job SET status=#{status}, done_files=#{doneFiles}, extracted=#{extracted}, "
            + "imported=#{imported}, rejected=#{rejected}, message=#{message}, "
            + "finish_time=#{finishTime} WHERE id=#{id}")
    int update(ImportJob job);
}
