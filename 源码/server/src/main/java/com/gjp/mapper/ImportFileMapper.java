package com.gjp.mapper;

import com.gjp.imp.ImportFileRow;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

public interface ImportFileMapper {

    @Insert("INSERT INTO t_import_file (job_id, family_id, original_name, stored_path, content_type, "
            + "file_size, kind, status, progress, reject_reason, extracted) VALUES (#{jobId}, #{familyId}, "
            + "#{originalName}, #{storedPath}, #{contentType}, #{fileSize}, #{kind}, #{status}, "
            + "#{progress}, #{rejectReason}, #{extracted})")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(ImportFileRow row);

    @Select("SELECT * FROM t_import_file WHERE job_id = #{jobId} ORDER BY id")
    List<ImportFileRow> selectByJob(@Param("jobId") Long jobId);

    @Select("SELECT * FROM t_import_file WHERE id = #{id}")
    ImportFileRow selectById(@Param("id") Long id);

    @Select("SELECT * FROM t_import_file WHERE status = 'parsing'")
    List<ImportFileRow> selectParsing();

    @Update("UPDATE t_import_file SET status=#{status}, progress=#{progress}, reject_reason=#{rejectReason}, "
            + "extracted=#{extracted} WHERE id=#{id}")
    int update(ImportFileRow row);

    @Update("UPDATE t_import_file SET status='queued', progress=0, reject_reason=NULL "
            + "WHERE status='parsing'")
    int resetParsingToQueued();
}
