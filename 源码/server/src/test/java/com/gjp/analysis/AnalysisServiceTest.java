package com.gjp.analysis;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gjp.common.BizException;
import com.gjp.common.UserContext;
import com.gjp.dify.DifyProperties;
import com.gjp.dify.DifyWorkflowClient;
import org.junit.jupiter.api.*;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class AnalysisServiceTest {
    AnalysisDataService data;
    DifyWorkflowClient workflow;
    AnalysisService service;
    AnalysisData snapshot;
    @BeforeEach void setUp() {
        login(1,1,1,null);
        data=mock(AnalysisDataService.class); workflow=mock(DifyWorkflowClient.class);
        DifyProperties config=new DifyProperties(); config.setAnalysisApiKey("unit-test-not-a-real-key");
        service=new AnalysisService(data,config,workflow,new ObjectMapper().findAndRegisterModules());
        snapshot=AnalysisRulesTest.data("2026-07-01","2026-08-31",AnalysisRulesTest.month("2026-07","100",1),AnalysisRulesTest.month("2026-08","300",1));
        when(data.load(any(),any())).thenReturn(snapshot);
        when(workflow.run(any(),any(),any(),anyInt(),any())).thenReturn("{\"insights\":[{\"code\":\"S0\",\"evidenceIds\":[\"A3\"],\"focus\":\"INCOME\"}]}");
    }
    static void login(long user,long family,int role,Long member) {
        var u=new UserContext.LoginUser(user,"test","test",family,"family"); u.setRole(role);u.setMemberId(member);u.setSessionVersion(1);UserContext.set(u);
    }
    @AfterEach void tearDown() { UserContext.clear(); }
    @Test void localReportNeverCallsAiAndEnhancementIsCached() {
        var local=service.report(snapshot.range,null,false); assertEquals("rules",local.aiStatus); verifyNoInteractions(workflow);
        var enhanced=service.enhance(local.reportId); assertEquals("enhanced",enhanced.aiStatus);
        assertEquals(local.items,enhanced.items); assertEquals("rules",local.aiStatus);
        service.enhance(local.reportId); verify(workflow,times(1)).run(any(),any(),any(),anyInt(),any());
        assertEquals(local.reportId,service.report(snapshot.range,null,false).reportId);
    }
    @Test void changedAggregatesInvalidateCache() {
        var old=service.report(snapshot.range,null,false);
        snapshot.trend.get(1).setExpense(AnalysisRulesTest.n("400"));
        assertNotEquals(old.reportId,service.report(snapshot.range,null,false).reportId);
    }
    @Test void invalidOutputFallsBackWithoutChangingFacts() {
        when(workflow.run(any(),any(),any(),anyInt(),any())).thenReturn("{\"items\":[{\"code\":\"A3\",\"basis\":\"999999\"}]}");
        var local=service.report(snapshot.range,null,false);
        var result=service.enhance(local.reportId);
        assertEquals("fallback",result.aiStatus);assertEquals(local.items,result.items);assertTrue(result.aiInsights.isEmpty());
    }
    @Test void reportIdCannotBeSharedAcrossUsersFamiliesOrChangedPermissions() {
        var local=service.report(snapshot.range,null,false);
        login(2,1,1,null); assertThrows(BizException.class,()->service.enhance(local.reportId));
        login(1,2,1,null); assertThrows(BizException.class,()->service.enhance(local.reportId));
        login(1,1,0,5L); assertThrows(BizException.class,()->service.enhance(local.reportId));
        verifyNoInteractions(workflow);
    }
    @Test void refreshCreatesNewSnapshot() {
        var first=service.report(snapshot.range,null,false);var refreshed=service.report(snapshot.range,null,true);
        assertNotEquals(first.reportId,refreshed.reportId);
    }
}
