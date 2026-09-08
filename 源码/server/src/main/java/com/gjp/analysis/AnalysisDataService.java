package com.gjp.analysis;

import com.gjp.common.BizException;
import com.gjp.common.AppTime;
import com.gjp.common.UserContext;
import com.gjp.mapper.AnalysisMapper;
import com.gjp.mapper.MemberMapper;
import com.gjp.mapper.StatMapper;
import com.gjp.stat.DateRange;
import com.gjp.stat.StatService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Objects;

@Service
public class AnalysisDataService {
    private final StatService stats;
    private final StatMapper statMapper;
    private final AnalysisMapper mapper;
    private final MemberMapper members;

    public AnalysisDataService(StatService stats, StatMapper statMapper, AnalysisMapper mapper, MemberMapper members) {
        this.stats = stats; this.statMapper = statMapper; this.mapper = mapper; this.members = members;
    }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public AnalysisData load(DateRange requested, Long requestedMemberId) {
        Long memberId = UserContext.resolveMemberId(requestedMemberId);
        if (requested.getStart().isAfter(AppTime.today())) throw new BizException("请选择已发生的日期区间");
        LocalDate end = requested.effectiveEnd();
        if (ChronoUnit.DAYS.between(requested.getStart(), end) > 366 * 5) {
            throw new BizException("单次分析最多支持五年，请缩小日期区间");
        }
        AnalysisData d = new AnalysisData();
        d.range = DateRange.of(requested.getStart(), end, null, null);
        d.memberId = memberId;
        Long family = UserContext.getFamilyId();
        d.members = members.selectByFamily(family).stream()
                .filter(m -> memberId == null || Objects.equals(memberId, m.getId())).toList();
        if (memberId != null && d.members.isEmpty()) throw new BizException(403, "成员不在当前家庭范围内");
        d.trend = stats.trend(d.range, memberId);
        d.categories = stats.categoryStat(2, d.range, memberId);
        d.merchants = stats.merchantRank(d.range, 8, memberId);
        d.areas = stats.areaStat(d.range, memberId);
        d.gift = statMapper.sumGiftExpense(family, d.range.getStart(), end, memberId);
        d.categoryMonths = mapper.categoryMonths(family, memberId, d.range.getStart(), end);
        d.memberMonths = mapper.memberMonths(family, memberId, d.range.getStart(), end);
        d.quality = mapper.quality(family, memberId, d.range.getStart(), end);
        return d;
    }
}
