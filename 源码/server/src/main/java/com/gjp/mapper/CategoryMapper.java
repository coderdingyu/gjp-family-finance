package com.gjp.mapper;

import com.gjp.entity.Category;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

/**
 * 收支分类数据访问。分类为两级结构，parent_id = 0 表示一级分类。
 */
public interface CategoryMapper {

    /** 按类型查全部分类（含一二级），type 传 null 表示收入支出都查 */
    @Select("<script>"
            + "SELECT c.*, p.category_name AS parent_name "
            + "FROM t_category c LEFT JOIN t_category p ON c.parent_id = p.id "
            + "WHERE c.family_id = #{familyId} "
            + "<if test='type != null'> AND c.type = #{type} </if>"
            + "ORDER BY c.type, c.parent_id, c.sort_no, c.id"
            + "</script>")
    List<Category> selectByFamily(@Param("familyId") Long familyId, @Param("type") Integer type);

    @Select("SELECT * FROM t_category WHERE id = #{id} AND family_id = #{familyId}")
    Category selectById(@Param("id") Long id, @Param("familyId") Long familyId);

    @Select("<script>"
            + "SELECT COUNT(*) FROM t_category WHERE family_id = #{familyId} AND parent_id = #{parentId} "
            + "AND category_name = #{categoryName} "
            + "<if test='excludeId != null'> AND id <![CDATA[<>]]> #{excludeId} </if>"
            + "</script>")
    int countByName(@Param("familyId") Long familyId, @Param("parentId") Long parentId,
                    @Param("categoryName") String categoryName, @Param("excludeId") Long excludeId);

    @Insert("INSERT INTO t_category (family_id, parent_id, category_name, type, is_default, sort_no) "
            + "VALUES (#{familyId}, #{parentId}, #{categoryName}, #{type}, #{isDefault}, #{sortNo})")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(Category category);

    @Update("UPDATE t_category SET category_name = #{categoryName}, sort_no = #{sortNo} "
            + "WHERE id = #{id} AND family_id = #{familyId}")
    int update(Category category);

    @Delete("DELETE FROM t_category WHERE id = #{id} AND family_id = #{familyId} AND is_default = 0")
    int deleteById(@Param("id") Long id, @Param("familyId") Long familyId);

    /** 一级分类下还有子分类时不允许直接删除 */
    @Select("SELECT COUNT(*) FROM t_category WHERE parent_id = #{id}")
    int countChildren(@Param("id") Long id);

    /** 分类被流水引用时不允许删除 */
    @Select("SELECT COUNT(*) FROM t_record WHERE category_id = #{categoryId}")
    int countRecords(@Param("categoryId") Long categoryId);
}
