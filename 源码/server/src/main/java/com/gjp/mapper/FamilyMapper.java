package com.gjp.mapper;

import com.gjp.entity.Family;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

public interface FamilyMapper {

    @Insert("INSERT INTO t_family (family_name) VALUES (#{familyName})")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(Family family);

    @Select("SELECT * FROM t_family WHERE id = #{id}")
    Family selectById(@Param("id") Long id);
}
