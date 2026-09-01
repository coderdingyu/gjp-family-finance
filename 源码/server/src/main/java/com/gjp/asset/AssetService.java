package com.gjp.asset;

import com.gjp.common.BizException;
import com.gjp.common.UserContext;
import com.gjp.entity.Asset;
import com.gjp.entity.Loan;
import com.gjp.mapper.AssetMapper;
import com.gjp.mapper.LoanMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 资产与贷款业务（拓展功能）。
 *
 * 家庭财务的完整图景 = 收支流水（现金流）+ 资产负债（存量）。
 * 这里给出净资产 = 资产合计 − 贷款剩余本金，让家庭知道"攒下的钱到底剩多少"。
 */
@Service
public class AssetService {

    private static final List<String> ASSET_TYPES = List.of("房产", "车辆", "存款", "股票", "基金", "其他");
    private static final List<String> LOAN_TYPES = List.of("房贷", "车贷", "消费贷");

    @Autowired
    private AssetMapper assetMapper;
    @Autowired
    private LoanMapper loanMapper;

    // ---------------- 资产 ----------------

    public List<Asset> listAsset() {
        return assetMapper.selectByFamily(UserContext.getFamilyId());
    }

    public Asset addAsset(Asset asset) {
        validateAsset(asset);
        asset.setFamilyId(UserContext.getFamilyId());
        assetMapper.insert(asset);
        return asset;
    }

    public Asset updateAsset(Long id, Asset asset) {
        Long familyId = UserContext.getFamilyId();
        if (assetMapper.selectById(id, familyId) == null) {
            throw new BizException("资产不存在");
        }
        validateAsset(asset);
        asset.setId(id);
        asset.setFamilyId(familyId);
        assetMapper.update(asset);
        return asset;
    }

    public void deleteAsset(Long id) {
        Long familyId = UserContext.getFamilyId();
        if (assetMapper.selectById(id, familyId) == null) {
            throw new BizException("资产不存在");
        }
        assetMapper.deleteById(id, familyId);
    }

    private void validateAsset(Asset asset) {
        if (!StringUtils.hasText(asset.getAssetName())) {
            throw new BizException("请输入资产名称");
        }
        if (!ASSET_TYPES.contains(asset.getAssetType())) {
            throw new BizException("资产类型只能是：" + String.join("/", ASSET_TYPES));
        }
        if (asset.getAmount() == null || asset.getAmount().compareTo(BigDecimal.ZERO) < 0) {
            throw new BizException("当前价值不能为空或负数");
        }
        if (asset.getCost() != null && asset.getCost().compareTo(BigDecimal.ZERO) < 0) {
            throw new BizException("取得成本不能为负数");
        }
        if (asset.getBuyDate() != null && asset.getBuyDate().isAfter(LocalDate.now())) {
            throw new BizException("取得日期不能晚于今天");
        }
    }

    // ---------------- 贷款 ----------------

    /** 贷款列表，同时算出每笔的剩余期数与剩余本金 */
    public List<Map<String, Object>> listLoan() {
        List<Map<String, Object>> result = new ArrayList<>();
        for (Loan loan : loanMapper.selectByFamily(UserContext.getFamilyId())) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("id", loan.getId());
            row.put("loanName", loan.getLoanName());
            row.put("loanType", loan.getLoanType());
            row.put("totalAmount", loan.getTotalAmount());
            row.put("monthlyPayment", loan.getMonthlyPayment());
            row.put("totalMonths", loan.getTotalMonths());
            row.put("paidMonths", loan.getPaidMonths());
            row.put("startDate", loan.getStartDate());
            row.put("remainMonths", remainMonths(loan));
            row.put("remainAmount", remainAmount(loan));
            row.put("paidAmount", loan.getMonthlyPayment()
                    .multiply(BigDecimal.valueOf(loan.getPaidMonths() == null ? 0 : loan.getPaidMonths())));
            row.put("progress", loan.getTotalMonths() == null || loan.getTotalMonths() == 0
                    ? BigDecimal.ZERO
                    : BigDecimal.valueOf(loan.getPaidMonths() == null ? 0 : loan.getPaidMonths())
                    .multiply(BigDecimal.valueOf(100))
                    .divide(BigDecimal.valueOf(loan.getTotalMonths()), 2, RoundingMode.HALF_UP));
            result.add(row);
        }
        return result;
    }

    private int remainMonths(Loan loan) {
        int total = loan.getTotalMonths() == null ? 0 : loan.getTotalMonths();
        int paid = loan.getPaidMonths() == null ? 0 : loan.getPaidMonths();
        return Math.max(total - paid, 0);
    }

    /** 剩余本金按"剩余期数 × 月供"估算，等额本息的利息部分不单独拆分，够家庭记账使用 */
    private BigDecimal remainAmount(Loan loan) {
        return loan.getMonthlyPayment().multiply(BigDecimal.valueOf(remainMonths(loan)))
                .setScale(2, RoundingMode.HALF_UP);
    }

    public Loan addLoan(Loan loan) {
        validateLoan(loan);
        loan.setFamilyId(UserContext.getFamilyId());
        loanMapper.insert(loan);
        return loan;
    }

    public Loan updateLoan(Long id, Loan loan) {
        Long familyId = UserContext.getFamilyId();
        if (loanMapper.selectById(id, familyId) == null) {
            throw new BizException("贷款记录不存在");
        }
        validateLoan(loan);
        loan.setId(id);
        loan.setFamilyId(familyId);
        loanMapper.update(loan);
        return loan;
    }

    public void deleteLoan(Long id) {
        Long familyId = UserContext.getFamilyId();
        if (loanMapper.selectById(id, familyId) == null) {
            throw new BizException("贷款记录不存在");
        }
        loanMapper.deleteById(id, familyId);
    }

    private void validateLoan(Loan loan) {
        if (!StringUtils.hasText(loan.getLoanName())) {
            throw new BizException("请输入贷款名称");
        }
        if (!LOAN_TYPES.contains(loan.getLoanType())) {
            throw new BizException("贷款类型只能是：" + String.join("/", LOAN_TYPES));
        }
        if (loan.getTotalAmount() == null || loan.getTotalAmount().compareTo(BigDecimal.ZERO) <= 0) {
            throw new BizException("贷款总额必须大于 0");
        }
        if (loan.getMonthlyPayment() == null || loan.getMonthlyPayment().compareTo(BigDecimal.ZERO) <= 0) {
            throw new BizException("每月还款额必须大于 0");
        }
        if (loan.getTotalMonths() == null || loan.getTotalMonths() <= 0) {
            throw new BizException("总期数必须大于 0");
        }
        if (loan.getPaidMonths() == null || loan.getPaidMonths() < 0) {
            loan.setPaidMonths(0);
        }
        if (loan.getPaidMonths() > loan.getTotalMonths()) {
            throw new BizException("已还期数不能超过总期数");
        }
    }

    // ---------------- 汇总 ----------------

    /** 资产负债总览：资产合计、贷款剩余合计、净资产、按类型的资产构成 */
    public Map<String, Object> summary() {
        Long familyId = UserContext.getFamilyId();
        List<Asset> assets = assetMapper.selectByFamily(familyId);
        List<Loan> loans = loanMapper.selectByFamily(familyId);

        BigDecimal totalAsset = BigDecimal.ZERO;
        Map<String, BigDecimal> byType = new LinkedHashMap<>();
        for (Asset a : assets) {
            totalAsset = totalAsset.add(a.getAmount());
            byType.merge(a.getAssetType(), a.getAmount(), BigDecimal::add);
        }

        BigDecimal totalLoanRemain = BigDecimal.ZERO;
        BigDecimal monthlyPayTotal = BigDecimal.ZERO;
        for (Loan l : loans) {
            totalLoanRemain = totalLoanRemain.add(remainAmount(l));
            if (remainMonths(l) > 0) {
                monthlyPayTotal = monthlyPayTotal.add(l.getMonthlyPayment());
            }
        }

        List<Map<String, Object>> composition = new ArrayList<>();
        for (Map.Entry<String, BigDecimal> e : byType.entrySet()) {
            Map<String, Object> item = new HashMap<>();
            item.put("name", e.getKey());
            item.put("amount", e.getValue());
            item.put("ratio", totalAsset.compareTo(BigDecimal.ZERO) == 0
                    ? BigDecimal.ZERO
                    : e.getValue().multiply(BigDecimal.valueOf(100))
                    .divide(totalAsset, 2, RoundingMode.HALF_UP));
            composition.add(item);
        }

        Map<String, Object> map = new LinkedHashMap<>();
        map.put("totalAsset", totalAsset);
        map.put("totalLoanRemain", totalLoanRemain);
        map.put("netAsset", totalAsset.subtract(totalLoanRemain));
        map.put("monthlyPayTotal", monthlyPayTotal);
        map.put("assetCount", assets.size());
        map.put("loanCount", loans.size());
        map.put("composition", composition);
        return map;
    }
}
