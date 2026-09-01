package com.gjp.mapper;

import com.gjp.entity.User;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

public interface UserMapper {

    @Select("SELECT * FROM t_user WHERE username = #{username}")
    User selectByUsername(@Param("username") String username);

    @Insert("INSERT INTO t_user (username, password, real_name, family_id) "
            + "VALUES (#{username}, #{password}, #{realName}, #{familyId})")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(User user);

    @Select("SELECT COUNT(*) FROM t_user WHERE username = #{username}")
    int countByUsername(@Param("username") String username);
}
