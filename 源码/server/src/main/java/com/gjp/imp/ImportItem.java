package com.gjp.imp;

import java.math.BigDecimal;
import java.time.LocalDate;

public class ImportItem {

    private Long id;
    private Long jobId;
    private Long fileId;
    private Long familyId;
    private String status;
    private String rejectReason;
    private Integer type;
    private String categoryName;
    private Long categoryId;
    private BigDecimal amount;
    private LocalDate recordDate;
    private String merchant;
    private String area;
    private String payMethod;
    private Integer isGift;
    private String remark;
    private String sourceName;
    /** 仅详情接口填充：ledger=与账本重复，batch=与本次其他文件重复 */
    private String duplicateKind;
    private String duplicateHint;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getJobId() {
        return jobId;
    }

    public void setJobId(Long jobId) {
        this.jobId = jobId;
    }

    public Long getFileId() {
        return fileId;
    }

    public void setFileId(Long fileId) {
        this.fileId = fileId;
    }

    public Long getFamilyId() {
        return familyId;
    }

    public void setFamilyId(Long familyId) {
        this.familyId = familyId;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getRejectReason() {
        return rejectReason;
    }

    public void setRejectReason(String rejectReason) {
        this.rejectReason = rejectReason;
    }

    public Integer getType() {
        return type;
    }

    public void setType(Integer type) {
        this.type = type;
    }

    public String getCategoryName() {
        return categoryName;
    }

    public void setCategoryName(String categoryName) {
        this.categoryName = categoryName;
    }

    public Long getCategoryId() {
        return categoryId;
    }

    public void setCategoryId(Long categoryId) {
        this.categoryId = categoryId;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public void setAmount(BigDecimal amount) {
        this.amount = amount;
    }

    public LocalDate getRecordDate() {
        return recordDate;
    }

    public void setRecordDate(LocalDate recordDate) {
        this.recordDate = recordDate;
    }

    public String getMerchant() {
        return merchant;
    }

    public void setMerchant(String merchant) {
        this.merchant = merchant;
    }

    public String getArea() {
        return area;
    }

    public void setArea(String area) {
        this.area = area;
    }

    public String getPayMethod() {
        return payMethod;
    }

    public void setPayMethod(String payMethod) {
        this.payMethod = payMethod;
    }

    public Integer getIsGift() {
        return isGift;
    }

    public void setIsGift(Integer isGift) {
        this.isGift = isGift;
    }

    public String getRemark() {
        return remark;
    }

    public void setRemark(String remark) {
        this.remark = remark;
    }

    public String getSourceName() {
        return sourceName;
    }

    public void setSourceName(String sourceName) {
        this.sourceName = sourceName;
    }

    public String getDuplicateKind() {
        return duplicateKind;
    }

    public void setDuplicateKind(String duplicateKind) {
        this.duplicateKind = duplicateKind;
    }

    public String getDuplicateHint() {
        return duplicateHint;
    }

    public void setDuplicateHint(String duplicateHint) {
        this.duplicateHint = duplicateHint;
    }
}
