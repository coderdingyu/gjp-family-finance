package com.gjp.asset;

import com.gjp.common.Result;
import com.gjp.entity.Asset;
import com.gjp.entity.Loan;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

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

    /** 资产负债总览 */
    @GetMapping("/summary")
    public Result<Map<String, Object>> summary() {
        return Result.ok(assetService.summary());
    }

    @GetMapping("/list")
    public Result<List<Asset>> listAsset() {
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
