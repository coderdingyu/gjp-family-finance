package com.gjp.dedup;

import com.gjp.entity.Record;
import com.gjp.record.RecordService;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.annotation.Transactional;

import java.lang.reflect.Method;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OrderNosTest {

    @Test
    void bothBlankIsNotStrongDuplicate() {
        assertFalse(OrderNos.same(null, "A"));
        assertFalse(OrderNos.same("A", "  "));
        assertFalse(OrderNos.same("", ""));
    }

    @Test
    void trimAndIgnoreCase() {
        assertTrue(OrderNos.same("  AbC001  ", "abc001"));
        assertFalse(OrderNos.same("abc001", "abc002"));
    }

    @Test
    void groupDetectsSharedOrderNo() {
        Record a = new Record();
        a.setOrderNo("WX-1");
        Record b = new Record();
        b.setOrderNo("wx-1");
        Record c = new Record();
        c.setOrderNo("WX-2");
        assertTrue(DedupService.allSameOrderNo(List.of(a, b)));
        assertFalse(DedupService.allSameOrderNo(List.of(a, c)));
        Record blank = new Record();
        assertFalse(DedupService.allSameOrderNo(List.of(a, blank)));
    }

    @Test
    void batchDeleteIsTransactional() throws Exception {
        Method m = RecordService.class.getMethod("deleteBatch", List.class);
        Transactional tx = m.getAnnotation(Transactional.class);
        assertNotNull(tx);
        assertEquals(Exception.class, tx.rollbackFor()[0]);
        Method d = DedupService.class.getMethod("deleteSelected", List.class);
        assertNotNull(d.getAnnotation(Transactional.class));
    }

    @Test
    void normalizeOrderNoTrims() {
        assertEquals("A1", RecordService.normalizeOrderNo("  A1  "));
        assertNull(RecordService.normalizeOrderNo("   "));
        assertNull(RecordService.normalizeOrderNo(null));
    }
}
