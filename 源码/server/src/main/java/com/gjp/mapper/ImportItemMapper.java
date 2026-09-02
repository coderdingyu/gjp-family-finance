package com.gjp.mapper;

import com.gjp.imp.ImportItem;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

public interface ImportItemMapper {

    @Insert("INSERT INTO t_import_item (job_id, file_id, family_id, status, reject_reason, type, "
            + "category_name, category_id, amount, record_date, merchant, area, pay_method, is_gift, remark) "
            + "VALUES (#{jobId}, #{fileId}, #{familyId}, #{status}, #{rejectReason}, #{type}, "
            + "#{categoryName}, #{categoryId}, #{amount}, #{recordDate}, #{merchant}, #{area}, "
            + "#{payMethod}, #{isGift}, #{remark})")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(ImportItem item);

    @Select("SELECT i.*, f.original_name AS source_name FROM t_import_item i "
            + "JOIN t_import_file f ON i.file_id = f.id "
            + "WHERE i.job_id = #{jobId} ORDER BY i.id")
    List<ImportItem> selectByJob(@Param("jobId") Long jobId);

    @Select("SELECT * FROM t_import_item WHERE id = #{id} AND job_id = #{jobId}")
    ImportItem selectById(@Param("id") Long id, @Param("jobId") Long jobId);

    @Select("SELECT COUNT(*) FROM t_import_item WHERE file_id = #{fileId}")
    int countByFile(@Param("fileId") Long fileId);

    @Update("UPDATE t_import_item SET status=#{status}, reject_reason=#{rejectReason} WHERE id=#{id}")
    int updateStatus(ImportItem item);
}
