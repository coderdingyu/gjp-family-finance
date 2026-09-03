package com.gjp.mapper;

import com.gjp.entity.User;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

public interface UserMapper {

    @Select("SELECT * FROM t_user WHERE username = #{username}")
    User selectByUsername(@Param("username") String username);

    @Select("SELECT * FROM t_user WHERE id = #{id}")
    User selectById(@Param("id") Long id);

    @Insert("INSERT INTO t_user (username, password, real_name, family_id, member_id, role, status) "
            + "VALUES (#{username}, #{password}, #{realName}, #{familyId}, #{memberId}, #{role}, #{status})")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(User user);

    @Select("SELECT COUNT(*) FROM t_user WHERE username = #{username}")
    int countByUsername(@Param("username") String username);

    @Update("UPDATE t_user SET last_login = NOW() WHERE id = #{id}")
    int touchLastLogin(@Param("id") Long id);

    /** 户主给家庭成员开账号后，把账号绑定到成员上 */
    @Update("UPDATE t_user SET member_id = #{memberId}, real_name = #{realName} "
            + "WHERE id = #{id} AND family_id = #{familyId}")
    int updateBinding(@Param("id") Long id, @Param("familyId") Long familyId,
                      @Param("memberId") Long memberId, @Param("realName") String realName);

    @Update("UPDATE t_user SET password = #{password} WHERE id = #{id}")
    int updatePassword(@Param("id") Long id, @Param("password") String password);

    @Update("UPDATE t_user SET status = #{status}, "
            + "session_version = CASE WHEN #{status} = 0 THEN IFNULL(session_version, 0) + 1 "
            + "ELSE IFNULL(session_version, 0) END WHERE id = #{id}")
    int updateStatus(@Param("id") Long id, @Param("status") Integer status);

    /** 本家庭的账号列表，户主管理成员账号用 */
    @Select("SELECT u.*, m.member_name AS member_name FROM t_user u "
            + "LEFT JOIN t_member m ON u.member_id = m.id "
            + "WHERE u.family_id = #{familyId} ORDER BY u.role DESC, u.id")
    List<User> selectByFamily(@Param("familyId") Long familyId);

    /** 全部账号（跨家庭），系统管理员用 */
    @Select("<script>"
            + "SELECT u.*, m.member_name AS member_name, f.family_name AS family_name FROM t_user u "
            + "LEFT JOIN t_member m ON u.member_id = m.id "
            + "LEFT JOIN t_family f ON u.family_id = f.id "
            + "<where>"
            + " <if test='keyword != null and keyword != \"\"'> AND (u.username LIKE CONCAT('%', #{keyword}, '%') ESCAPE '\\\\' "
            + "     OR u.real_name LIKE CONCAT('%', #{keyword}, '%') ESCAPE '\\\\' "
            + "     OR f.family_name LIKE CONCAT('%', #{keyword}, '%') ESCAPE '\\\\') </if>"
            + " <if test='role != null'> AND u.role = #{role} </if>"
            + "</where>"
            + " ORDER BY u.family_id, u.role DESC, u.id"
            + "</script>")
    List<User> selectAll(@Param("keyword") String keyword, @Param("role") Integer role);

    @Select("SELECT COUNT(*) FROM t_user")
    long countAll();

    @Select("SELECT COUNT(*) FROM t_user WHERE status = 0")
    long countDisabled();

    /** 该成员是否已被某个账号绑定，避免两个账号指向同一个成员 */
    @Select("<script>SELECT COUNT(*) FROM t_user WHERE member_id = #{memberId} "
            + "<if test='excludeUserId != null'> AND id <![CDATA[<>]]> #{excludeUserId} </if></script>")
    int countByMemberId(@Param("memberId") Long memberId, @Param("excludeUserId") Long excludeUserId);

    @Delete("DELETE FROM t_user WHERE id = #{id}")
    int deleteById(@Param("id") Long id);
}
