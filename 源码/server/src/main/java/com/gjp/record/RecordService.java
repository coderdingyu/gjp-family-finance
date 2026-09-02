package com.gjp.record;

import com.gjp.common.BizException;
import com.gjp.common.PageResult;
import com.gjp.common.UserContext;
import com.gjp.entity.Category;
import com.gjp.entity.Record;
import com.gjp.mapper.CategoryMapper;
import com.gjp.mapper.MemberMapper;
import com.gjp.log.OperationLogService;
import com.gjp.mapper.RecordMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 收支流水业务（核心模块）。
 *
 * 这里承担了整个系统的数据质量把关：录入时校验成员、分类、金额、日期，
 * 并保证分类的收入/支出类型与流水类型一致。统计模块的准确性完全依赖这一层的校验。
 *
 * 数据范围（需求第 9 条）：普通成员只能看、只能改自己名下的流水；
 * 户主可以看全家、也可以替任意成员记账。收敛点有三处，缺一处就会越权：
 *   1. 查询   page() 里强制覆盖 q.memberId
 *   2. 单条读 detail() 里校验这条流水的归属
 *   3. 写入   validate() 里校验 memberId 是否在允许范围内
 */
@Service
public class RecordService {

    /** 允许的支付方式，与前端下拉框保持一致 */
    private static final List<String> PAY_METHODS = List.of("现金", "微信", "支付宝", "银行卡", "其他");

    /** 单笔金额上限，防止手滑多输几个 0 把统计图拉爆 */
    private static final BigDecimal MAX_AMOUNT = new BigDecimal("99999999.99");

    @Autowired
    private RecordMapper recordMapper;
    @Autowired
    private MemberMapper memberMapper;
    @Autowired
    private CategoryMapper categoryMapper;
    @Autowired
    private OperationLogService logService;

    /** 分页查询 + 当前条件下的收支合计（合计是按全部匹配记录算的，不是只算当前页） */
    public Map<String, Object> page(RecordQuery q) {
        UserContext.requireFamilyMember();
        Long familyId = UserContext.getFamilyId();
        normalize(q);
        // 权限收敛：普通成员无论前端传什么 memberId，都会被改写成自己
        q.setMemberId(UserContext.resolveMemberId(q.getMemberId()));

        long total = recordMapper.countByQuery(familyId, q);
        List<Record> list = total == 0 ? List.of() : recordMapper.selectByQuery(familyId, q);

        Map<String, Object> result = new HashMap<>();
        result.put("page", new PageResult<>(total, list));
        result.put("sumIncome", recordMapper.sumAmountByQuery(familyId, q, 1));
        result.put("sumExpense", recordMapper.sumAmountByQuery(familyId, q, 2));
        result.put("scopeLocked", UserContext.scopeMemberId() != null);
        return result;
    }

    /** 兜底分页参数，避免前端传 0 或负数导致 SQL 报错，也避免一次拉几万条 */
    private void normalize(RecordQuery q) {
        if (q.getPageNum() == null || q.getPageNum() < 1) {
            q.setPageNum(1);
        }
        if (q.getPageSize() == null || q.getPageSize() < 1) {
            q.setPageSize(10);
        }
        if (q.getPageSize() > 200) {
            q.setPageSize(200);
        }
        if (q.getStartDate() != null && q.getEndDate() != null
                && q.getStartDate().isAfter(q.getEndDate())) {
            throw new BizException("起始日期不能晚于结束日期");
        }
        if (q.getMinAmount() != null && q.getMaxAmount() != null
                && q.getMinAmount().compareTo(q.getMaxAmount()) > 0) {
            throw new BizException("金额下限不能大于上限");
        }
        if (q.getKeyword() != null && !q.getKeyword().isBlank()) {
            q.setKeyword(escapeLike(q.getKeyword().trim()));
        }
        q.setOffset((q.getPageNum() - 1) * q.getPageSize());
    }

    /** 把用户输入的 % _ \ 当成字面量，避免 keyword='%' 变成查全部 */
    private String escapeLike(String keyword) {
        return keyword.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }

    public Record detail(Long id) {
        Record record = recordMapper.selectById(id, UserContext.getFamilyId());
        if (record == null) {
            throw new BizException("流水不存在");
        }
        Long scope = UserContext.scopeMemberId();
        if (scope != null && !scope.equals(record.getMemberId())) {
            // 提示语和"不存在"保持一致，避免被用来探测别人有哪些流水ID
            throw new BizException("流水不存在");
        }
        return record;
    }

    public Record add(Record record) {
        Long familyId = UserContext.getFamilyId();
        validate(record, familyId);
        record.setFamilyId(familyId);
        recordMapper.insert(record);

        Record saved = detail(record.getId());
        logService.record(OperationLogService.M_RECORD, OperationLogService.A_ADD, saved.getId(),
                summary("新增", saved));
        return saved;
    }

    public Record update(Long id, Record record) {
        Long familyId = UserContext.getFamilyId();
        Record before = detail(id);
        validate(record, familyId);
        record.setId(id);
        record.setFamilyId(familyId);
        recordMapper.update(record);

        Record after = detail(id);
        logService.record(OperationLogService.M_RECORD, OperationLogService.A_UPDATE, id,
                summary("修改", after),
                "{\"before\":{\"amount\":" + before.getAmount() + ",\"date\":\"" + before.getRecordDate()
                        + "\"},\"after\":{\"amount\":" + after.getAmount() + ",\"date\":\"" + after.getRecordDate() + "\"}}");
        return after;
    }

    public void delete(Long id) {
        Record before = detail(id);
        recordMapper.deleteById(id, UserContext.getFamilyId());
        logService.record(OperationLogService.M_RECORD, OperationLogService.A_DELETE, id,
                summary("删除", before));
    }

    /** 批量删除，查重功能里用户勾选多条后一次删掉 */
    public int deleteBatch(List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            throw new BizException("请选择要删除的流水");
        }
        if (ids.size() > 200) {
            throw new BizException("一次最多删除 200 条");
        }
        Long familyId = UserContext.getFamilyId();
        int deleted = 0;
        BigDecimal total = BigDecimal.ZERO;
        for (Long id : ids) {
            // 逐条走 detail() 是为了复用它的归属校验，避免普通成员批量删掉别人的流水
            Record before = detail(id);
            recordMapper.deleteById(id, familyId);
            total = total.add(before.getAmount());
            deleted++;
        }
        logService.record(OperationLogService.M_RECORD, OperationLogService.A_BATCH_DELETE, null,
                "批量删除 " + deleted + " 笔流水，合计 " + total + " 元");
        return deleted;
    }

    private String summary(String action, Record r) {
        return action + (r.getType() != null && r.getType() == 1 ? "收入" : "支出")
                + " " + r.getAmount() + " 元｜" + r.getRecordDate()
                + "｜" + (r.getMemberName() == null ? "" : r.getMemberName())
                + "｜" + categoryPath(r)
                + (r.getMerchant() == null || r.getMerchant().isEmpty() ? "" : "｜" + r.getMerchant());
    }

    /**
     * 拼出分类的完整路径，如"餐饮支出/外出就餐/快餐"。
     * 三级分类下只写父级会得到"外出就餐/快餐"，看不出属于哪个大类，日志里不好排查。
     */
    private String categoryPath(Record r) {
        StringBuilder sb = new StringBuilder();
        Integer level = r.getCategoryLevel();
        if (level != null && level >= 3 && r.getRootCategoryName() != null) {
            sb.append(r.getRootCategoryName()).append('/');
        }
        if (r.getParentCategoryName() != null) {
            sb.append(r.getParentCategoryName()).append('/');
        }
        if (r.getCategoryName() != null) {
            sb.append(r.getCategoryName());
        }
        return sb.toString();
    }

    /** 录入过的商家和片区，前端用来做输入联想，减少"海底捞/海底捞火锅"这类同店不同名 */
    public Map<String, Object> options() {
        Long familyId = UserContext.getFamilyId();
        Map<String, Object> map = new HashMap<>();
        map.put("merchants", recordMapper.selectMerchants(familyId));
        map.put("areas", recordMapper.selectAreas(familyId));
        map.put("payMethods", PAY_METHODS);
        return map;
    }

    /**
     * 流水校验。顺序上先查引用是否存在，再校验数值，最后规范化可选字段，
     * 报错信息都写成用户能直接看懂的话。
     */
    private void validate(Record record, Long familyId) {
        if (record.getType() == null || (record.getType() != 1 && record.getType() != 2)) {
            throw new BizException("请选择收入或支出");
        }
        if (record.getMemberId() == null || memberMapper.selectById(record.getMemberId(), familyId) == null) {
            throw new BizException("请选择有效的家庭成员");
        }
        Long scope = UserContext.scopeMemberId();
        if (scope != null && !scope.equals(record.getMemberId())) {
            throw new BizException("普通成员只能记录自己名下的收支，如需代记请联系户主");
        }
        if (record.getCategoryId() == null) {
            throw new BizException("请选择收支分类");
        }
        Category category = categoryMapper.selectById(record.getCategoryId(), familyId);
        if (category == null) {
            throw new BizException("请选择有效的收支分类");
        }
        if (!category.getType().equals(record.getType())) {
            // 例如把"工资收入"选到一笔支出上，这类数据会让统计结果完全失真
            throw new BizException("分类【" + category.getCategoryName() + "】属于"
                    + (category.getType() == 1 ? "收入" : "支出") + "类，与当前流水类型不一致");
        }
        if (categoryMapper.countChildren(category.getId()) > 0) {
            throw new BizException("请选择到末级分类，不要只选还有下级的分类");
        }

        if (record.getAmount() == null) {
            throw new BizException("请输入金额");
        }
        if (record.getAmount().compareTo(BigDecimal.ZERO) <= 0) {
            throw new BizException("金额必须大于 0");
        }
        if (record.getAmount().compareTo(MAX_AMOUNT) > 0) {
            throw new BizException("单笔金额不能超过 " + MAX_AMOUNT + " 元，请确认是否多输了 0");
        }
        // 统一保留两位小数，防止 0.005 这类值在汇总时产生分币误差
        record.setAmount(record.getAmount().setScale(2, RoundingMode.HALF_UP));

        if (record.getRecordDate() == null) {
            throw new BizException("请选择发生日期");
        }
        if (record.getRecordDate().isAfter(LocalDate.now())) {
            throw new BizException("发生日期不能晚于今天");
        }
        if (record.getRecordDate().isBefore(LocalDate.of(2000, 1, 1))) {
            throw new BizException("发生日期不能早于 2000 年");
        }

        if (record.getPayMethod() != null && !record.getPayMethod().isEmpty()
                && !PAY_METHODS.contains(record.getPayMethod())) {
            throw new BizException("支付方式只能是：" + String.join("/", PAY_METHODS));
        }
        if (record.getIsGift() == null || (record.getIsGift() != 0 && record.getIsGift() != 1)) {
            record.setIsGift(0);
        }
        if (record.getRemark() != null && record.getRemark().length() > 255) {
            throw new BizException("备注不能超过 255 个字");
        }
        record.setMerchant(trim(record.getMerchant()));
        record.setArea(trim(record.getArea()));
    }

    private String trim(String s) {
        return s == null ? null : s.trim();
    }
}
