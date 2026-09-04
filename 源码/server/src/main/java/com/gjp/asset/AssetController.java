package com.gjp.asset;

import com.gjp.asset.quote.AssetValuationService;
import com.gjp.asset.quote.CarTree;
import com.gjp.common.Result;
import com.gjp.common.UserContext;
import com.gjp.entity.Asset;
import com.gjp.entity.Loan;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * 资产与贷款管理接口（拓展功能）。
 */
@RestController
@RequestMapping("/api/asset")
public class AssetController {

    @Autowired
    private AssetService assetService;
    @Autowired
    private AssetValuationService valuationService;
    @Autowired
    private CarTree carTree;

    /** 资产负债总览（金额为实时估值，失败则回落登记值） */
    @GetMapping("/summary")
    public Result<Map<String, Object>> summary() {
        return Result.ok(assetService.summary());
    }

    @GetMapping("/list")
    public Result<List<AssetVO>> listAsset() {
        return Result.ok(assetService.listAsset());
    }

    @PostMapping
    public Result<Asset> addAsset(@RequestBody Asset asset) {
        return Result.ok(assetService.addAsset(asset));
    }

    @PutMapping("/{id}")
    public Result<Asset> updateAsset(@PathVariable Long id, @RequestBody Asset asset) {
        return Result.ok(assetService.updateAsset(id, asset));
    }

    @DeleteMapping("/{id}")
    public Result<Void> deleteAsset(@PathVariable Long id) {
        assetService.deleteAsset(id);
        return Result.ok();
    }

    /**
     * 表单预览：查行情。失败也 200，带 reason，不阻断保存。
     */
    @GetMapping("/quote")
    public Result<Map<String, Object>> quote(@RequestParam String type,
                                             @RequestParam(required = false) String symbol,
                                             @RequestParam(required = false) BigDecimal shares) {
        UserContext.requireOwner();
        return Result.ok(valuationService.quotePreview(type, symbol, shares));
    }

    /**
     * 表单预览：估残值 / 估市值。
     */
    @GetMapping("/estimate")
    public Result<Map<String, Object>> estimate(@RequestParam String type,
                                                @RequestParam(required = false) String carModel,
                                                @RequestParam(required = false) String city,
                                                @RequestParam(required = false) String community,
                                                @RequestParam(required = false) BigDecimal areaSqm,
                                                @RequestParam(required = false) Integer modelYear,
                                                @RequestParam(required = false) Integer mileageKm,
                                                @RequestParam(required = false) BigDecimal cost,
                                                @RequestParam(required = false)
                                                @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate buyDate) {
        UserContext.requireOwner();
        return Result.ok(valuationService.estimatePreview(type, carModel, city, community,
                areaSqm, modelYear, mileageKm, cost, buyDate));
    }


    /**
     * 管家婆自研二手车残值：指导价 + 车龄/里程折价；挂牌可达时用挂牌中位。
     */
    @GetMapping("/used-car-price")
    public Result<Map<String, Object>> usedCarPrice(@RequestParam String carModel,
                                                    @RequestParam(required = false) String city,
                                                    @RequestParam(required = false) Integer modelYear,
                                                    @RequestParam(required = false) Integer mileageKm,
                                                    @RequestParam(required = false) BigDecimal cost,
                                                    @RequestParam(required = false)
                                                    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate buyDate) {
        UserContext.requireOwner();
        return Result.ok(valuationService.usedCarPrice(carModel, city, modelYear, mileageKm, cost, buyDate));
    }

    /** 车辆三级目录：品牌 / 车系 / 年款 */
    @GetMapping("/car-tree")
    public Result<List<Map<String, Object>>> carTree() {
        UserContext.requireOwner();
        return Result.ok(this.carTree.tree());
    }

    /** 存款利息预览（与列表同一公式） */
    @GetMapping("/interest")
    public Result<Map<String, Object>> interest(@RequestParam BigDecimal principal,
                                                @RequestParam BigDecimal annualRate,
                                                @RequestParam(required = false) Integer termMonths,
                                                @RequestParam String interestMethod,
                                                @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate buyDate) {
        UserContext.requireOwner();
        return Result.ok(valuationService.interestPreview(principal, annualRate, termMonths, interestMethod, buyDate));
    }

    @GetMapping("/loan/list")
    public Result<List<Map<String, Object>>> listLoan() {
        return Result.ok(assetService.listLoan());
    }

    @PostMapping("/loan")
    public Result<Loan> addLoan(@RequestBody Loan loan) {
        return Result.ok(assetService.addLoan(loan));
    }

    @PutMapping("/loan/{id}")
    public Result<Loan> updateLoan(@PathVariable Long id, @RequestBody Loan loan) {
        return Result.ok(assetService.updateLoan(id, loan));
    }

    @DeleteMapping("/loan/{id}")
    public Result<Void> deleteLoan(@PathVariable Long id) {
        assetService.deleteLoan(id);
        return Result.ok();
    }
}
