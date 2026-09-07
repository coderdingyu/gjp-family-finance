package com.gjp.analysis;

import com.gjp.mapper.AnalysisMapper;
import com.gjp.mapper.StatMapper;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.Test;
import java.util.HashMap;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

class AnalysisMapperTest {
    @Test void aggregatesBindFamilyAndOptionalMemberScope() {
        var config = new Configuration(); config.addMapper(AnalysisMapper.class);
        for (String method : new String[]{"categoryMonths", "memberMonths", "quality"}) {
            var params = new HashMap<String,Object>(); params.put("familyId", 7L); params.put("memberId", 9L);
            var statement = config.getMappedStatement(AnalysisMapper.class.getName()+"."+method);
            var scoped = statement.getBoundSql(params);
            assertTrue(scoped.getSql().contains("r.family_id = ?"));
            assertTrue(scoped.getSql().contains("r.member_id = ?"));
            assertTrue(scoped.getParameterMappings().stream().anyMatch(p -> p.getProperty().equals("memberId")));
            params.put("memberId", null);
            assertFalse(statement.getBoundSql(params).getSql().contains("r.member_id = ?"));
        }
    }
    @Test void merchantWindowIsAppliedBeforeLimitAndRootCategoryIsUsed() {
        var config = new Configuration(); config.addMapper(StatMapper.class);
        var sql = config.getMappedStatement(StatMapper.class.getName()+".selectMerchantRank")
                .getBoundSql(Map.of("familyId",1L,"memberId",2L,"limit",5)).getSql();
        assertTrue(sql.contains("SUM(SUM(r.amount)) OVER ()"));
        assertTrue(sql.contains("c.root_id = p.id"));
        assertTrue(sql.toUpperCase().contains("LIMIT ?"));
    }
}
