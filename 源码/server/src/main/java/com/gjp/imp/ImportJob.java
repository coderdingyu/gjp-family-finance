package com.gjp.imp;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

public class ImportJob {

    private Long id;
    private Long familyId;
    private Long userId;
    private Long memberId;
    private String status;
    private Integer totalFiles;
    private Integer doneFiles;
    private Integer extracted;
    private Integer imported;
    private Integer rejected;
    private String message;
    private LocalDateTime createTime;
    private LocalDateTime finishTime;

    private String memberName;
    /** 当前上传人从第一次导入起的序号，不是数据库主键 */
    private Integer seqNo;
    private Integer duplicateCount;
    private List<ImportFileRow> files = new ArrayList<>();
    private List<ImportItem> items = new ArrayList<>();

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getFamilyId() {
        return familyId;
    }

    public void setFamilyId(Long familyId) {
        this.familyId = familyId;
    }

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public Long getMemberId() {
        return memberId;
    }

    public void setMemberId(Long memberId) {
        this.memberId = memberId;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Integer getTotalFiles() {
        return totalFiles;
    }

    public void setTotalFiles(Integer totalFiles) {
        this.totalFiles = totalFiles;
    }

    public Integer getDoneFiles() {
        return doneFiles;
    }

    public void setDoneFiles(Integer doneFiles) {
        this.doneFiles = doneFiles;
    }

    public Integer getExtracted() {
        return extracted;
    }

    public void setExtracted(Integer extracted) {
        this.extracted = extracted;
    }

    public Integer getImported() {
        return imported;
    }

    public void setImported(Integer imported) {
        this.imported = imported;
    }

    public Integer getRejected() {
        return rejected;
    }

    public void setRejected(Integer rejected) {
        this.rejected = rejected;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public LocalDateTime getCreateTime() {
        return createTime;
    }

    public void setCreateTime(LocalDateTime createTime) {
        this.createTime = createTime;
    }

    public LocalDateTime getFinishTime() {
        return finishTime;
    }

    public void setFinishTime(LocalDateTime finishTime) {
        this.finishTime = finishTime;
    }

    public String getMemberName() {
        return memberName;
    }

    public void setMemberName(String memberName) {
        this.memberName = memberName;
    }

    public Integer getSeqNo() {
        return seqNo;
    }

    public void setSeqNo(Integer seqNo) {
        this.seqNo = seqNo;
    }

    public List<ImportFileRow> getFiles() {
        return files;
    }

    public void setFiles(List<ImportFileRow> files) {
        this.files = files;
    }

    public List<ImportItem> getItems() {
        return items;
    }

    public void setItems(List<ImportItem> items) {
        this.items = items;
    }

    public Integer getDuplicateCount() {
        return duplicateCount;
    }

    public void setDuplicateCount(Integer duplicateCount) {
        this.duplicateCount = duplicateCount;
    }
}
