package com.gjp.mapper;

import com.gjp.entity.Family;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;
import java.util.Map;

public interface FamilyMapper {

    @Insert("INSERT INTO t_family (family_name) VALUES (#{familyName})")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(Family family);

    @Select("SELECT * FROM t_family WHERE id = #{id}")
    Family selectById(@Param("id") Long id);

    @Select("SELECT * FROM t_family ORDER BY id")
    List<Family> selectAll();

    /** 管理员面板：每个家庭的规模概览 */
    @Select("SELECT f.id, f.family_name AS familyName, f.create_time AS createTime, "
            + "  (SELECT COUNT(*) FROM t_user u WHERE u.family_id = f.id)   AS userCount, "
            + "  (SELECT COUNT(*) FROM t_member m WHERE m.family_id = f.id) AS memberCount, "
            + "  (SELECT COUNT(*) FROM t_record r WHERE r.family_id = f.id) AS recordCount, "
            + "  (SELECT COUNT(*) FROM t_asset a WHERE a.family_id = f.id)  AS assetCount "
            + "FROM t_family f ORDER BY f.id")
    List<Map<String, Object>> selectOverview();

    @Select("SELECT COUNT(*) FROM t_family")
    long countAll();
}
