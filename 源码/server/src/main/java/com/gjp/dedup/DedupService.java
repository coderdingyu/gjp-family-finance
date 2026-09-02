package com.gjp.dedup;

import com.gjp.common.BizException;
import com.gjp.common.UserContext;
import com.gjp.entity.Record;
import com.gjp.mapper.DedupMapper;
import com.gjp.mapper.RecordMapper;
import com.gjp.record.RecordService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 账单查重业务（需求第 6 条）。
 *
 * 流程：SQL 找出两两可疑配对 → 并查集合并成组 → 补齐流水明细 → 给出理由和保留建议。
 *
 * 并查集这一步是必要的：如果 A 和 B 重复、B 和 C 重复，
 * 配对表里是 (A,B) 和 (B,C) 两条，直接按配对展示会让用户看到两组、
 * 且同一条流水出现在多组里，勾选删除时很容易删错。合并成 {A,B,C} 一组才符合直觉。
 */
@Service
public class DedupService {

    /** 一次最多返回多少组，避免历史数据很脏时前端渲染卡死 */
    private static final int MAX_GROUPS = 200;

    @Autowired
    private DedupMapper dedupMapper;
    @Autowired
    private RecordMapper recordMapper;
    @Autowired
    private RecordService recordService;

    /**
     * 扫描重复流水。
     *
     * @param dayTolerance 日期容差（天），0=只找同一天，默认 3
     * @param sameMember   是否要求同一成员（默认 true：不同成员各自记同样金额通常是两笔真实消费）
     * @param sameCategory 是否要求同一分类（默认 false：分类可能被填得不一致）
     */
    public Map<String, Object> scan(Integer dayTolerance, Boolean sameMember, Boolean sameCategory,
                                    String startDate, String endDate, Long requestedMemberId) {
        int tol = dayTolerance == null ? 3 : dayTolerance;
        if (tol < 0 || tol > 30) {
            throw new BizException("日期容差只能在 0-30 天之间");
        }
        boolean byMember = sameMember == null || sameMember;
        boolean byCategory = sameCategory != null && sameCategory;

        Long familyId = UserContext.getFamilyId();
        Long memberId = UserContext.resolveMemberId(requestedMemberId);

        List<Map<String, Object>> pairs = dedupMapper.findPairs(
                familyId, memberId, tol, byMember, byCategory, startDate, endDate);

        // ---- 并查集：把配对合并成组 ----
        Map<Long, Long> parent = new HashMap<>();
        Map<String, Integer> pairDayDiff = new HashMap<>();
        for (Map<String, Object> pair : pairs) {
            Long a = ((Number) pair.get("idA")).longValue();
            Long b = ((Number) pair.get("idB")).longValue();
            int diff = ((Number) pair.get("dayDiff")).intValue();
            pairDayDiff.put(a + "-" + b, diff);
            union(parent, a, b);
        }

        // ---- 按根节点分组 ----
        Map<Long, List<Long>> clusters = new LinkedHashMap<>();
        for (Long id : parent.keySet()) {
            clusters.computeIfAbsent(find(parent, id), k -> new ArrayList<>()).add(id);
        }

        List<DuplicateGroup> groups = new ArrayList<>();
        for (List<Long> ids : clusters.values()) {
            if (ids.size() < 2) {
                continue;
            }
            ids.sort(Comparator.naturalOrder());

            List<Record> records = new ArrayList<>();
            for (Long id : ids) {
                Record r = recordMapper.selectById(id, familyId);
                if (r != null) {
                    records.add(r);
                }
            }
            if (records.size() < 2) {
                continue;
            }
            // 按日期升序，用户看起来更自然
            records.sort(Comparator.comparing(Record::getRecordDate).thenComparing(Record::getId));

            int maxDiff = 0;
            for (int i = 0; i < ids.size(); i++) {
                for (int j = i + 1; j < ids.size(); j++) {
                    Integer d = pairDayDiff.get(ids.get(i) + "-" + ids.get(j));
                    if (d != null) {
                        maxDiff = Math.max(maxDiff, d);
                    }
                }
            }

            DuplicateGroup g = new DuplicateGroup();
            g.setAmount(records.get(0).getAmount());
            g.setCount(records.size());
            g.setMaxDayDiff(maxDiff);
            g.setMatchType(maxDiff == 0 ? "完全一致" : "高度相似");
            g.setReason(buildReason(records, maxDiff));
            // 建议保留最早录入的那条（ID 最小），其余交给用户判断
            g.setSuggestKeepId(records.stream().min(Comparator.comparing(Record::getId))
                    .map(Record::getId).orElse(null));
            g.setRecords(records);
            groups.add(g);
        }

        // 金额大的排前面：金额越大，重复录入造成的统计偏差越严重，优先让用户处理
        groups.sort(Comparator.comparing(DuplicateGroup::getAmount).reversed());
        boolean truncated = groups.size() > MAX_GROUPS;
        if (truncated) {
            groups = groups.subList(0, MAX_GROUPS);
        }

        BigDecimal wasted = BigDecimal.ZERO;
        int extraCount = 0;
        for (DuplicateGroup g : groups) {
            // 假设每组只保留一条，多出来的金额就是被重复计入统计的部分
            extraCount += g.getCount() - 1;
            wasted = wasted.add(g.getAmount().multiply(BigDecimal.valueOf(g.getCount() - 1L)));
        }

        Map<String, Object> result = new HashMap<>();
        result.put("groups", groups);
        result.put("groupCount", groups.size());
        result.put("extraCount", extraCount);
        result.put("extraAmount", wasted);
        result.put("truncated", truncated);
        result.put("params", Map.of("dayTolerance", tol, "sameMember", byMember, "sameCategory", byCategory));
        return result;
    }

    private String buildReason(List<Record> records, int maxDiff) {
        Record first = records.get(0);
        StringBuilder sb = new StringBuilder();
        sb.append(records.size()).append(" 笔金额均为 ").append(first.getAmount()).append(" 元的")
                .append(first.getType() != null && first.getType() == 1 ? "收入" : "支出");
        if (maxDiff == 0) {
            sb.append("，发生日期完全相同（").append(first.getRecordDate()).append("）");
        } else {
            sb.append("，发生日期相差 ").append(maxDiff).append(" 天内（")
                    .append(first.getRecordDate()).append(" ~ ")
                    .append(records.get(records.size() - 1).getRecordDate()).append("）");
        }
        boolean sameMerchant = records.stream()
                .map(r -> r.getMerchant() == null ? "" : r.getMerchant())
                .distinct().count() == 1;
        if (sameMerchant && first.getMerchant() != null && !first.getMerchant().isEmpty()) {
            sb.append("，商家均为「").append(first.getMerchant()).append("」");
        }
        sb.append("。请确认是否为重复录入。");
        return sb.toString();
    }

    /** 用户确认后删除选中的流水；复用流水模块的批量删除以保证权限校验与日志一致 */
    public int deleteSelected(List<Long> ids) {
        return recordService.deleteBatch(ids);
    }

    // ---------------- 并查集 ----------------

    private Long find(Map<Long, Long> parent, Long x) {
        Long p = parent.get(x);
        if (p == null) {
            parent.put(x, x);
            return x;
        }
        if (!p.equals(x)) {
            Long root = find(parent, p);
            parent.put(x, root);
            return root;
        }
        return x;
    }

    private void union(Map<Long, Long> parent, Long a, Long b) {
        Long ra = find(parent, a);
        Long rb = find(parent, b);
        if (!ra.equals(rb)) {
            parent.put(ra, rb);
        }
    }
}
