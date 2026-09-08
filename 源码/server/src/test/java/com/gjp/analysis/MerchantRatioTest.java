package com.gjp.analysis;

import com.gjp.common.UserContext;
import com.gjp.mapper.StatMapper;
import com.gjp.stat.DateRange;
import com.gjp.stat.StatService;
import com.gjp.stat.vo.AmountItem;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class MerchantRatioTest {
    @Test void topNMustNotRenormalizeFullPopulationRatio() {
        AnalysisServiceTest.login(1,1,1,null);
        try {
            var mapper=mock(StatMapper.class); var service=new StatService(); ReflectionTestUtils.setField(service,"statMapper",mapper);
            var item=new AmountItem();item.setName("商家");item.setAmount(new BigDecimal("100"));item.setRatio(new BigDecimal("10"));
            when(mapper.selectMerchantRank(any(),any(),any(),any(),anyInt())).thenReturn(List.of(item));
            var range=DateRange.of(LocalDate.of(2026,1,1),LocalDate.of(2026,8,31),null,null);
            assertEquals(new BigDecimal("10"),service.merchantRank(range,5,null).get(0).getRatio());
            assertEquals(new BigDecimal("10"),service.merchantRank(range,8,null).get(0).getRatio());
        } finally {UserContext.clear();}
    }
}
