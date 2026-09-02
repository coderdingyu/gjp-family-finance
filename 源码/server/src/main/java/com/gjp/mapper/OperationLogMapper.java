package com.gjp.mapper;

import com.gjp.entity.OperationLog;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;
import java.util.Map;

/**
 * 操作日志数据访问。
 *
 * 查询条件里的 familyId / userId 由 Service 按角色算好后传入：
 *   普通成员 → familyId + 自己的 userId
 *   户主     → familyId，userId 为空
 *   管理员   → 两者都为空（看全部家庭）
 * 这样权限收敛只发生在 Service 一处，Mapper 只管拼条件。
 */
public interface OperationLogMapper {

    @Insert("INSERT INTO t_operation_log (family_id, user_id, username, real_name, module, action, "
            + "target_id, summary, detail, ip, success, error_msg, cost_ms) VALUES "
            + "(#{familyId}, #{userId}, #{username}, #{realName}, #{module}, #{action}, "
            + "#{targetId}, #{summary}, #{detail}, #{ip}, #{success}, #{errorMsg}, #{costMs})")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(OperationLog log);

    String WHERE = "<where>"
            + " <if test='familyId != null'> AND family_id = #{familyId} </if>"
            + " <if test='userId != null'> AND user_id = #{userId} </if>"
            + " <if test='module != null and module != \"\"'> AND module = #{module} </if>"
            + " <if test='action != null and action != \"\"'> AND action = #{action} </if>"
            + " <if test='success != null'> AND success = #{success} </if>"
            + " <if test='keyword != null and keyword != \"\"'> AND (summary LIKE CONCAT('%', #{keyword}, '%') "
            + "     OR username LIKE CONCAT('%', #{keyword}, '%') OR real_name LIKE CONCAT('%', #{keyword}, '%')) </if>"
            + " <if test='startTime != null'> AND create_time <![CDATA[>=]]> #{startTime} </if>"
            + " <if test='endTime != null'> AND create_time <![CDATA[<=]]> #{endTime} </if>"
            + "</where>";

    @Select("<script>SELECT * FROM t_operation_log " + WHERE
            + " ORDER BY id DESC LIMIT #{offset}, #{pageSize}</script>")
    List<OperationLog> selectByQuery(@Param("familyId") Long familyId, @Param("userId") Long userId,
                                     @Param("module") String module, @Param("action") String action,
                                     @Param("success") Integer success, @Param("keyword") String keyword,
                                     @Param("startTime") String startTime, @Param("endTime") String endTime,
                                     @Param("offset") int offset, @Param("pageSize") int pageSize);

    @Select("<script>SELECT COUNT(*) FROM t_operation_log " + WHERE + "</script>")
    long countByQuery(@Param("familyId") Long familyId, @Param("userId") Long userId,
                      @Param("module") String module, @Param("action") String action,
                      @Param("success") Integer success, @Param("keyword") String keyword,
                      @Param("startTime") String startTime, @Param("endTime") String endTime);

    /** 按模块统计条数，管理员面板的分布图用 */
    @Select("<script>SELECT module AS name, COUNT(*) AS value FROM t_operation_log "
            + "<where><if test='familyId != null'> AND family_id = #{familyId} </if></where>"
            + " GROUP BY module ORDER BY value DESC</script>")
    List<Map<String, Object>> countByModule(@Param("familyId") Long familyId);

    /** 最近 N 天每天的操作条数，管理员面板的趋势图用 */
    @Select("SELECT DATE_FORMAT(create_time, '%Y-%m-%d') AS name, COUNT(*) AS value "
            + "FROM t_operation_log WHERE create_time >= DATE_SUB(CURDATE(), INTERVAL #{days} DAY) "
            + "GROUP BY DATE_FORMAT(create_time, '%Y-%m-%d') ORDER BY name")
    List<Map<String, Object>> countByDay(@Param("days") int days);

    /** 失败操作条数，用于管理员面板的健康度指标 */
    @Select("SELECT COUNT(*) FROM t_operation_log WHERE success = 0")
    long countFailed();

    @Select("SELECT COUNT(*) FROM t_operation_log")
    long countAll();
}
