package com.gjp.analysis;

import com.gjp.entity.Member;
import com.gjp.stat.DateRange;
import com.gjp.stat.vo.AmountItem;
import com.gjp.stat.vo.MonthAmount;
import java.math.BigDecimal;
import java.util.List;

/** 一次只读事务内取得的聚合快照；规则和 AI 共用，不发送流水原件。 */
public class AnalysisData {
    public DateRange range;
    public Long memberId;
    public List<MonthAmount> trend;
    public List<AmountItem> categories;
    public List<AmountItem> merchants;
    public List<AmountItem> areas;
    public List<Member> members;
    public List<CategoryMonth> categoryMonths;
    public List<MemberMonth> memberMonths;
    public Quality quality;
    public BigDecimal gift;

    public static class CategoryMonth {
        public String ym;
        public Long categoryId;
        public String name;
        public BigDecimal amount;
        public int count;
    }
    public static class MemberMonth {
        public String ym;
        public Long memberId;
        public BigDecimal amount;
    }
    public static class Quality {
        public int expenseCount;
        public int merchantFilledCount;
        public int incomeCount;
    }
}
