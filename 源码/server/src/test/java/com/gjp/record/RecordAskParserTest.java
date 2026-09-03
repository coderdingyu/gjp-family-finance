package com.gjp.record;

import com.gjp.entity.Category;
import com.gjp.entity.Member;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class RecordAskParserTest {

    private List<Category> cats() {
        Category c = new Category();
        c.setId(11L);
        c.setCategoryName("正餐");
        c.setType(2);
        Category c2 = new Category();
        c2.setId(12L);
        c2.setCategoryName("餐饮支出");
        c2.setType(2);
        return List.of(c, c2);
    }

    private List<Member> members() {
        Member a = new Member();
        a.setId(1L);
        a.setMemberName("张伟");
        Member b = new Member();
        b.setId(2L);
        b.setMemberName("李娟");
        return List.of(a, b);
    }

    @Test
    void clipHugeQuestion() {
        String huge = "海底捞".repeat(3000);
        String clipped = RecordAskParser.clip(huge);
        assertEquals(RecordAskParser.MAX_QUESTION_CHARS, clipped.length());
    }

    @Test
    void emptyClip() {
        assertEquals("", RecordAskParser.clip("   "));
        assertEquals("", RecordAskParser.clip(null));
    }

    @Test
    void localParsesAmountDateCategoryPay() {
        RecordAskParser.AskDraft d = RecordAskParser.parseLocal(
                "上个月超过100元的正餐微信支出，海底捞", cats(), members());
        assertEquals(2, d.query.getType());
        assertEquals(new BigDecimal("100"), d.query.getMinAmount());
        assertEquals("微信", d.query.getPayMethod());
        assertEquals(11L, d.query.getCategoryId());
        YearMonth prev = YearMonth.now().minusMonths(1);
        assertEquals(prev.atDay(1), d.query.getStartDate());
        assertEquals(prev.atEndOfMonth(), d.query.getEndDate());
        assertEquals("海底捞", d.query.getKeyword());
    }

    @Test
    void localParsesGiftAndCnMonth() {
        RecordAskParser.AskDraft d = RecordAskParser.parseLocal("8月人情往来", cats(), members());
        assertEquals(1, d.query.getIsGift());
        assertEquals(LocalDate.of(YearMonth.now().getYear(), 8, 1), d.query.getStartDate());
        assertEquals(LocalDate.of(YearMonth.now().getYear(), 8, 31), d.query.getEndDate());
    }

    @Test
    void difyJsonAndBadJson() throws Exception {
        String raw = "{\"answer\":\"找到3笔\",\"query\":{\"type\":2,\"keyword\":\"星巴克\",\"minAmount\":20,\"startDate\":\"2026-08-01\",\"endDate\":\"2026-08-31\"}}";
        RecordAskParser.AskDraft d = RecordAskParser.parseDify(raw);
        assertEquals("找到3笔", d.answer);
        assertEquals(2, d.query.getType());
        assertEquals("星巴克", d.query.getKeyword());
        assertEquals(new BigDecimal("20"), d.query.getMinAmount());
        assertEquals(LocalDate.of(2026, 8, 1), d.query.getStartDate());
        assertThrows(Exception.class, () -> RecordAskParser.parseDify("{"));
        assertThrows(Exception.class, () -> RecordAskParser.parseDify("{\"query\":{}}"));
    }

    @Test
    void bindCategoryNameFuzzy() {
        RecordQuery q = new RecordQuery();
        RecordAskParser.bindCategoryName(q, "餐饮", cats());
        assertEquals(12L, q.getCategoryId());
    }
}
