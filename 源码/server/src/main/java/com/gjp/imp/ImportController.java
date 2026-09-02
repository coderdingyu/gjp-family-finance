package com.gjp.imp;

import com.gjp.common.Result;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;

/**
 * 文件导入。智能体只负责抽出候选流水，入库仍走本系统校验。
 */
@RestController
@RequestMapping("/api/import")
public class ImportController {

    private final ImportService importService;

    public ImportController(ImportService importService) {
        this.importService = importService;
    }

    @GetMapping("/config")
    public Result<Map<String, Object>> config() {
        return Result.ok(importService.config());
    }

    @PostMapping("/jobs")
    public Result<ImportJob> create(@RequestParam("files") MultipartFile[] files,
                                    @RequestParam(value = "memberId", required = false) Long memberId) {
        return Result.ok(importService.create(files, memberId));
    }

    @GetMapping("/jobs")
    public Result<java.util.List<ImportJob>> list() {
        return Result.ok(importService.list());
    }

    @GetMapping("/jobs/{id}")
    public Result<ImportJob> detail(@PathVariable Long id) {
        return Result.ok(importService.detail(id));
    }

    @PostMapping("/jobs/{id}/cancel")
    public Result<ImportJob> cancel(@PathVariable Long id) {
        return Result.ok(importService.cancel(id));
    }

    @PostMapping("/jobs/{id}/files/{fileId}/cancel")
    public Result<ImportJob> cancelFile(@PathVariable Long id, @PathVariable Long fileId) {
        return Result.ok(importService.cancelFile(id, fileId));
    }

    @PostMapping("/jobs/{id}/confirm")
    public Result<ImportJob> confirm(@PathVariable Long id, @RequestBody(required = false) ConfirmRequest body) {
        boolean merge = body != null && Boolean.TRUE.equals(body.getMerge());
        return Result.ok(importService.confirm(id, body == null ? null : body.getItemIds(), merge));
    }
}
