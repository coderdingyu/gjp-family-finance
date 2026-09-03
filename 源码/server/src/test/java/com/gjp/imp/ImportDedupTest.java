package com.gjp.imp;

import com.gjp.mapper.RecordMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ImportDedupTest {

    private RecordMapper mapper;
    private ImportDedup dedup;

    @BeforeEach
    void setUp() {
        mapper = mock(RecordMapper.class);
        when(mapper.countImportFingerprints(any(), any())).thenReturn(List.of());
        when(mapper.countImportOrderNos(any(), any())).thenReturn(List.of());
        dedup = new ImportDedup(mapper);
    }

    @Test
    void sameOrderNoDifferentAmountDateMerchantIsBatchDuplicate() {
        ImportItem excel = pending(1L, "2026-08-01", 2, "12.00", "星巴克", "ORD-X");
        ImportItem pdf = pending(2L, "2026-08-09", 2, "99.50", "全家", "ord-x");
        ImportJob job = job(excel, pdf);

        dedup.annotate(job);
        assertEquals(1, job.getDuplicateCount());
        assertNull(excel.getDuplicateKind());
        assertEquals("batch", pdf.getDuplicateKind());
        assertEquals("与本次其他文件重复", pdf.getDuplicateHint());

        List<ImportItem> keep = dedup.takeUniques(job, List.of(excel, pdf));
        assertEquals(1, keep.size());
        assertEquals(excel, keep.get(0));
        assertEquals("skipped", pdf.getStatus());
        assertEquals("合并：与本次其他文件重复（订单号相同）", pdf.getRejectReason());
    }

    @Test
    void sameOrderNoAlreadyInLedgerIsLedgerDuplicate() {
        when(mapper.countImportOrderNos(1L, 10L)).thenReturn(List.of(
                Map.of("orderNo", "ord-smoke-1", "cnt", 1)));
        ImportItem item = pending(1L, "2026-09-01", 2, "3.50", "便利店", "ORD-SMOKE-1");
        ImportJob job = job(item);

        dedup.annotate(job);
        assertEquals(1, job.getDuplicateCount());
        assertEquals("ledger", item.getDuplicateKind());
        assertEquals("与账本已有流水重复", item.getDuplicateHint());

        List<ImportItem> keep = dedup.takeUniques(job, List.of(item));
        assertEquals(0, keep.size());
        assertEquals("skipped", item.getStatus());
        assertEquals("合并：与账本已有流水重复（订单号相同）", item.getRejectReason());
    }

    @Test
    void oneSidedOrderNoStillUsesFingerprint() {
        ImportItem noOrder = pending(1L, "2026-08-01", 2, "28.50", "星巴克", null);
        ImportItem withOrder = pending(2L, "2026-08-01", 2, "28.50", "星巴克", "ONLY-ONE");
        ImportJob job = job(noOrder, withOrder);

        dedup.annotate(job);
        assertEquals(0, job.getDuplicateCount());
        assertNull(noOrder.getDuplicateKind());
        assertNull(withOrder.getDuplicateKind());

        List<ImportItem> keep = dedup.takeUniques(job, List.of(noOrder, withOrder));
        assertEquals(2, keep.size());
    }

    @Test
    void oneSidedImportWithoutOrderNoMatchesLedgerFingerprint() {
        when(mapper.countImportFingerprints(1L, 10L)).thenReturn(List.of(
                Map.of("recordDate", "2026-08-01", "type", 2, "amount", new BigDecimal("28.50"),
                        "merchant", "星巴克", "cnt", 1)));
        ImportItem noOrder = pending(1L, "2026-08-01", 2, "28.50", "星巴克", null);
        ImportJob job = job(noOrder);

        dedup.annotate(job);
        assertEquals(1, job.getDuplicateCount());
        assertEquals("ledger", noOrder.getDuplicateKind());
        assertEquals("与账本已有流水重复", noOrder.getDuplicateHint());
    }

    @Test
    void twoIdenticalFingerprintRowsWithoutOrderNoKeepCountBased() {
        ImportItem a = pending(1L, "2026-08-01", 2, "2.00", "农夫山泉", null);
        ImportItem b = pending(1L, "2026-08-01", 2, "2.00", "农夫山泉", "  ");
        ImportJob sameFile = job(a, b);
        dedup.annotate(sameFile);
        assertEquals(0, sameFile.getDuplicateCount());
        assertEquals(2, dedup.takeUniques(sameFile, List.of(a, b)).size());

        ImportItem excel = pending(1L, "2026-08-01", 2, "2.00", "农夫山泉", null);
        ImportItem pdf = pending(2L, "2026-08-01", 2, "2.00", "农夫山泉", null);
        ImportJob twoFiles = job(excel, pdf);
        dedup.annotate(twoFiles);
        assertEquals(1, twoFiles.getDuplicateCount());
        assertNull(excel.getDuplicateKind());
        assertEquals("batch", pdf.getDuplicateKind());
        assertEquals("与本次其他文件重复", pdf.getDuplicateHint());
        List<ImportItem> keep = dedup.takeUniques(twoFiles, List.of(excel, pdf));
        assertEquals(1, keep.size());
        assertEquals("skipped", pdf.getStatus());
        assertEquals("合并：与本次其他文件重复，只保留一份", pdf.getRejectReason());
    }

    @Test
    void excelAndPdfSameOrderNoKeepOneWhenLedgerEmpty() {
        ImportItem excel = pending(1L, "2026-08-01", 2, "10.00", "A", "X1");
        ImportItem pdf = pending(2L, "2026-08-02", 2, "20.00", "B", "X1");
        ImportJob job = job(excel, pdf);
        dedup.annotate(job);
        assertEquals(1, job.getDuplicateCount());
        assertEquals(1, dedup.takeUniques(job, List.of(excel, pdf)).size());
    }

    @Test
    void orderNoDuplicateSkippedEvenIfFingerprintWouldAllow() {
        ImportItem a = pending(1L, "2026-08-01", 2, "10.00", "商家甲", "SAME");
        ImportItem b = pending(1L, "2026-08-02", 2, "99.00", "商家乙", "SAME");
        ImportJob job = job(a, b);
        dedup.annotate(job);
        assertEquals(0, job.getDuplicateCount());
        assertEquals(2, dedup.takeUniques(job, List.of(a, b)).size());

        ImportItem excel = pending(1L, "2026-08-01", 2, "10.00", "商家甲", "SAME");
        ImportItem pdf = pending(2L, "2026-08-02", 2, "99.00", "商家乙", "SAME");
        ImportJob cross = job(excel, pdf);
        dedup.annotate(cross);
        assertEquals(1, cross.getDuplicateCount());
        assertEquals("batch", pdf.getDuplicateKind());
        assertEquals(1, dedup.takeUniques(cross, List.of(excel, pdf)).size());
    }

    private static ImportJob job(ImportItem... items) {
        ImportJob job = new ImportJob();
        job.setFamilyId(1L);
        job.setMemberId(10L);
        job.setItems(List.of(items));
        return job;
    }

    private static ImportItem pending(long fileId, String date, int type, String amount,
                                      String merchant, String orderNo) {
        ImportItem i = new ImportItem();
        i.setFileId(fileId);
        i.setStatus("pending");
        i.setRecordDate(LocalDate.parse(date));
        i.setType(type);
        i.setAmount(new BigDecimal(amount));
        i.setMerchant(merchant);
        i.setOrderNo(orderNo);
        return i;
    }
}
