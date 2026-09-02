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
 * 收支分类数据访问。分类为三级结构：
 *   parent_id = 0 且 level = 1  一级分类，如「文化娱乐」
 *   level = 2                   二级分类，如「影音娱乐」
 *   level = 3                   三级分类，如「游戏充值」「KTV」
 * root_id 始终指向所属的一级分类，统计按一级汇总时只需一次等值条件。
 */
public interface CategoryMapper {

    /** 按类型查全部分类（含三级），type 传 null 表示收入支出都查 */
    @Select("<script>"
            + "SELECT c.*, p.category_name AS parent_name "
            + "FROM t_category c LEFT JOIN t_category p ON c.parent_id = p.id "
            + "WHERE c.family_id = #{familyId} "
            + "<if test='type != null'> AND c.type = #{type} </if>"
            + "ORDER BY c.type, c.level, c.parent_id, c.sort_no, c.id"
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

    @Insert("INSERT INTO t_category (family_id, parent_id, root_id, level, category_name, type, is_default, sort_no) "
            + "VALUES (#{familyId}, #{parentId}, #{rootId}, #{level}, #{categoryName}, #{type}, #{isDefault}, #{sortNo})")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(Category category);

    /** 一级分类插入后要把 root_id 回填成自己的ID */
    @Update("UPDATE t_category SET root_id = id WHERE id = #{id}")
    int fixRootIdToSelf(@Param("id") Long id);

    @Update("UPDATE t_category SET category_name = #{categoryName}, sort_no = #{sortNo} "
            + "WHERE id = #{id} AND family_id = #{familyId}")
    int update(Category category);

    @Delete("DELETE FROM t_category WHERE id = #{id} AND family_id = #{familyId} AND is_default = 0")
    int deleteById(@Param("id") Long id, @Param("familyId") Long familyId);

    /** 分类下还有子分类时不允许直接删除 */
    @Select("SELECT COUNT(*) FROM t_category WHERE parent_id = #{id}")
    int countChildren(@Param("id") Long id);

    /** 分类被流水引用时不允许删除 */
    @Select("SELECT COUNT(*) FROM t_record WHERE category_id = #{categoryId}")
    int countRecords(@Param("categoryId") Long categoryId);

    /** 某个家庭的分类总数，管理员面板与预置分类初始化判空用 */
    @Select("SELECT COUNT(*) FROM t_category WHERE family_id = #{familyId}")
    int countByFamily(@Param("familyId") Long familyId);
}
