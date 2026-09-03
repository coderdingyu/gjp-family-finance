package com.gjp.imp;

import com.gjp.dify.DifyClient;
import com.gjp.dify.DifyParseResult;
import com.gjp.dify.ImagePrep;
import com.gjp.entity.Category;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BillTextParserTest {

    @Test
    void extractBillRows() {
        String tsv = "日期\t类型\t分类\t金额\t商家\n"
                + "2026-08-01\t支出\t正餐\t128.5\t海底捞\n"
                + "2026-08-03\t收入\t基本工资\t12000\t\n";
        DifyParseResult r = BillTextParser.parse(tsv);
        assertTrue(r.isRelevant());
        assertEquals(2, r.getRecords().size());
        Map<String, Object> first = r.getRecords().get(0);
        assertEquals(2, first.get("type"));
        assertEquals(new BigDecimal("128.5"), first.get("amount"));
        assertEquals("海底捞", first.get("merchant"));
    }

    @Test
    void rejectUnrelatedTable() {
        DifyParseResult r = BillTextParser.parse("姓名,学号,成绩\n张三,1,88\n");
        assertFalse(r.isRelevant());
    }

    @Test
    void parseGenericAliasesWithoutDate() {
        String csv = "项目名称,对手,进出方向,人民币\n"
                + "星巴克门店消费,星巴克,支出,28.50\n"
                + "八月工资到账,公司,收入,8000.00\n";
        DifyParseResult r = BillTextParser.parse(csv);
        assertTrue(r.isRelevant());
        assertEquals(2, r.getRecords().size());
        assertEquals(2, r.getRecords().get(0).get("type"));
        assertEquals(new BigDecimal("28.50"), r.getRecords().get(0).get("amount"));
        assertEquals("星巴克", r.getRecords().get(0).get("merchant"));
        assertEquals(1, r.getRecords().get(1).get("type"));
        assertEquals(LocalDate.now(), LocalDate.parse((String) r.getRecords().get(0).get("recordDate")));
    }

    @Test
    void parseBankDebitCredit() {
        String tsv = "交易日期\t摘要\t借方\t贷方\n"
                + "2026-08-01\t星巴克\t28.50\t\n"
                + "2026-08-03\t工资\t\t8000\n";
        DifyParseResult r = BillTextParser.parse(tsv);
        assertTrue(r.isRelevant());
        assertEquals(2, r.getRecords().size());
        assertEquals(2, r.getRecords().get(0).get("type"));
        assertEquals(new BigDecimal("28.50"), r.getRecords().get(0).get("amount"));
        assertEquals("星巴克", r.getRecords().get(0).get("merchant"));
        assertEquals(1, r.getRecords().get(1).get("type"));
        assertEquals(new BigDecimal("8000"), r.getRecords().get(1).get("amount"));
    }

    @Test
    void parseEnglishSheet() {
        String csv = "Date,Description,Amount,Type\n2026-08-01,Starbucks,28.50,Expense\n";
        DifyParseResult r = BillTextParser.parse(csv);
        assertTrue(r.isRelevant());
        assertEquals(1, r.getRecords().size());
        assertEquals(2, r.getRecords().get(0).get("type"));
        assertEquals("Starbucks", r.getRecords().get(0).get("merchant"));
        assertEquals(new BigDecimal("28.50"), r.getRecords().get(0).get("amount"));
    }

    @Test
    void parseSpaceSeparatedTable() {
        String text = "日期    对方    收/支    金额\n2026-08-01    盒马    支出    47.80\n";
        DifyParseResult r = BillTextParser.parse(text);
        assertTrue(r.isRelevant());
        assertEquals(1, r.getRecords().size());
        assertEquals("盒马", r.getRecords().get(0).get("merchant"));
        assertEquals(new BigDecimal("47.80"), r.getRecords().get(0).get("amount"));
    }

    @Test
    void parseLooseLineWithDateAndAmount() {
        DifyParseResult r = BillTextParser.parse("周末消费 2026-08-01 星巴克 支出 28.50 元\n");
        assertTrue(r.isRelevant());
        assertEquals(1, r.getRecords().size());
        assertEquals(2, r.getRecords().get(0).get("type"));
        assertEquals(new BigDecimal("28.50"), r.getRecords().get(0).get("amount"));
        assertEquals(LocalDate.of(2026, 8, 1), LocalDate.parse((String) r.getRecords().get(0).get("recordDate")));
        assertTrue(String.valueOf(r.getRecords().get(0).get("merchant")).contains("星巴克"));
    }

    @Test
    void parseYuanAndSignedAmount() {
        assertEquals(new BigDecimal("18.00"), BillTextParser.parseAmount("18.00元"));
        assertEquals(new BigDecimal("28.5"), BillTextParser.parseAmount("人民币28.5"));
        assertEquals(new BigDecimal("10.00"), BillTextParser.parseAmount("(10.00)"));
        assertEquals(2, BillTextParser.parseType("借方"));
        assertEquals(1, BillTextParser.parseType("贷方"));
    }

    @Test
    void parseDateAndPay() {
        assertEquals(LocalDate.of(2026, 8, 1), BillTextParser.parseDate("2026/08/01 12:00:00"));
        assertEquals("微信", BillTextParser.normalizePay("微信支付"));
        assertEquals(new BigDecimal("18.00"), BillTextParser.parseAmount("¥18.00"));
    }

    @Test
    void parseWeChatExport() {
        String tsv = "交易时间\t交易类型\t交易对方\t商品\t收/支\t金额(元)\t支付方式\t当前状态\t备注\n"
                + "2026-09-02 12:01:22\t商户消费\t聚惠时光超市福雷德店\t总部\t支出\t¥10.00\t零钱通\t支付成功\t/\n"
                + "2026-09-01 20:42:27\t转账\t姚天凯\t转账备注:微信转账\t收入\t¥35.00\t/\t已转入零钱通\t/\n"
                + "2026-08-20 10:00:00\t零钱通转出\t零钱通\t/\t/\t¥200.00\t零钱通\t提现已到账\t/\n";
        DifyParseResult r = BillTextParser.parse(tsv);
        assertTrue(r.isRelevant());
        assertEquals(2, r.getRecords().size());
        assertEquals(2, r.getRecords().get(0).get("type"));
        assertEquals(new BigDecimal("10.00"), r.getRecords().get(0).get("amount"));
        assertEquals("聚惠时光超市福雷德店", r.getRecords().get(0).get("merchant"));
        assertEquals("微信", r.getRecords().get(0).get("payMethod"));
        assertEquals(1, r.getRecords().get(1).get("type"));
        assertEquals("家庭买菜", BillCategoryHints.guess(2, "聚惠时光超市福雷德店", "总部"));
    }

    @Test
    void wechatPreambleIsNotHeader() {
        List<String> lines = List.of(
                "微信支付账单明细",
                "共187笔记录",
                "交易时间\t交易类型\t交易对方\t商品\t收/支\t金额(元)\t支付方式\t当前状态\t交易单号\t商户单号\t备注",
                "2026-09-02 12:01:22\t商户消费\t超市\t买菜\t支出\t10\t零钱通\t支付成功\t4200\t8362\t/"
        );
        List<String> slim = ExcelTextExtractor.slim(ExcelTextExtractor.dropPreamble(lines));
        assertTrue(slim.get(0).startsWith("交易时间"));
        assertTrue(slim.get(0).contains("交易单号"));
        assertEquals(2, slim.size());
        List<String> chunks = ExcelTextExtractor.chunkLines(slim);
        assertEquals(1, chunks.size());
        assertTrue(BillTextParser.parse(chunks.get(0)).isRelevant());
    }

    @Test
    void parseWeChatProofPdfText() {
        String text = """
                微信支付交易明细证明
                交易时间
                2026-09-02
                12:01:22
                商户消费
                支出
                零钱通
                10.00
                测试超市
                福雷德店
                83620260902930534
                2026-09-01
                20:42:27
                转账
                收入
                /
                35.00
                同学甲
                /
                2026-08-27
                16:32:49
                零钱通转出
                其他
                零钱通
                588.35
                /
                /
                """;
        assertTrue(WeChatPdfParser.looksLike(text));
        DifyParseResult r = WeChatPdfParser.parse(text);
        assertTrue(r.isRelevant());
        assertEquals(2, r.getRecords().size());
        assertEquals("测试超市福雷德店", r.getRecords().get(0).get("merchant"));
        assertEquals("微信", r.getRecords().get(0).get("payMethod"));
        assertEquals(1, r.getRecords().get(1).get("type"));
    }

    @Test
    void parseWeChatProofCombinedLine() {
        String text = """
                微信支付交易明细证明
                2026-09-02
                12:01:22
                商户消费 支出 零钱通 10.00 测试超市
                福雷德店
                83620260902930534
                2026-09-01
                20:42:27
                转账 收入 / 35.00 同学甲
                /
                """;
        DifyParseResult r = WeChatPdfParser.parse(text);
        assertEquals(2, r.getRecords().size());
        assertEquals("测试超市福雷德店", r.getRecords().get(0).get("merchant"));
        assertEquals("微信", r.getRecords().get(0).get("payMethod"));
        assertEquals(1, r.getRecords().get(1).get("type"));
    }

    @Test
    void parseWeChatProofWrappedType() {
        String text = """
                微信支付交易明细证明
                2026-08-31
                09:14:42
                测试商户-退
                款
                收入 零钱通 83.00 测试商户
                83620260831930534
                9306
                2026-08-29
                15:30:47
                扫二维码付
                款
                支出 零钱 10.00 校园食堂
                10001073012026082
                2026-08-27
                16:32:49
                零钱通转出-
                到零钱
                其他 零钱通 588.35 / /
                4200000000000000
                """;
        DifyParseResult r = WeChatPdfParser.parse(text);
        assertEquals(2, r.getRecords().size());
        assertEquals(1, r.getRecords().get(0).get("type"));
        assertEquals(new BigDecimal("83.00"), r.getRecords().get(0).get("amount"));
        assertEquals("测试商户", r.getRecords().get(0).get("merchant"));
        assertEquals(2, r.getRecords().get(1).get("type"));
        assertEquals("校园食堂", r.getRecords().get(1).get("merchant"));
    }

    @Test
    void parseAlipayCsvWithTabsInOrderId() {
        String header = "交易时间,交易分类,交易对方,对方账号,商品说明,收/支,金额,收/付款方式,交易状态,交易订单号,商家订单号,备注,";
        String row1 = "2026-08-31 18:40:02,餐饮美食,盒马,a***@example.com,盒马烘焙,支出,47.80,花呗,交易成功,202608310001\t,P18M001\t,,";
        String row2 = "2026-08-31 15:07:24,交通出行,哈啰出行,b***@example.com,哈啰单车卡抵扣骑行费用,支出,0.00,哈啰骑行卡,交易成功,202608310002\t,CPR001\t,,";
        String row3 = "2026-08-27 15:56:56,退款,商户甲,/,退款-会员,不计收支,0.02,建设银行储蓄卡(4918),退款成功,202608270003\t,T200\t,,";
        String row4 = "2026-08-27 13:46:17,日用百货,商户乙,/,加速器月卡,支出,0.02,建设银行储蓄卡(4918),交易关闭,202608270004\t,T201\t,,";
        String row5 = "2026-08-18 15:35:51,日用百货,测试超市,/,总部,支出,7.40,花呗,交易成功,202608180005\t,0912\t,,";
        String csv = "导出信息：\n共5笔记录\n" + header + "\n" + row1 + "\n" + row2 + "\n" + row3 + "\n" + row4 + "\n" + row5 + "\n";
        String table = ExcelTextExtractor.tableText(csv.getBytes(java.nio.charset.Charset.forName("GBK")), "支付宝交易明细.csv");
        assertTrue(table.startsWith("交易时间"));
        assertTrue(table.contains("交易订单号"));
        DifyParseResult r = BillTextParser.parse(table);
        assertTrue(r.isRelevant());
        assertEquals(2, r.getRecords().size());
        assertEquals(new BigDecimal("47.80"), r.getRecords().get(0).get("amount"));
        assertEquals("盒马", r.getRecords().get(0).get("merchant"));
        assertEquals("支付宝", r.getRecords().get(0).get("payMethod"));
        assertEquals("202608310001", r.getRecords().get(0).get("orderNo"));
        assertEquals("测试超市", r.getRecords().get(1).get("merchant"));
        assertEquals("家庭买菜", BillCategoryHints.guess(2, "测试超市", "总部"));
        assertEquals("零食水果", BillCategoryHints.guess(2, "张朋飞", "售货柜_可口可乐"));
    }

    @Test
    void parseAlipayProofPdfText() {
        String text = """
                支付宝支付科技有限公司   交易流水证明
                收/支 交易对方 商品说明 收/付款方式 金额 交易订单号 商家订单号 交易时间
                支出 盒马 盒马烘焙 花呗 47.80 20260831230014435014445 P18M001 2026-08-31
                黑松露蛋糕 09559 18:40:02
                支出 哈啰出行 哈啰单车卡抵扣骑 哈啰骑行卡 0.00 20260831230014435014345 CPR001 2026-08-31
                行费用 03889 15:07:24
                不计 测试商户 自动解冻 0.00 20260901114728905005 6000001 2026-09-01
                收支 09:01:46
                支出 杭州杭港地铁 地铁-文泽路- 花呗 5.00 20260831230014435014395 012501 2026-08-31
                有限公司 12:24:29 90995 12:24:27
                支出 测试店铺 会员续费 招商银行储蓄 16.96 20260802230014435014574 6000001 2026-08-02
                卡(7867) 63875 09:00:24
                5. 部分账单记为不计收支类。
                支付宝支付科技有限公司
                """;
        assertTrue(AlipayPdfParser.looksLike(text));
        DifyParseResult r = AlipayPdfParser.parse(text);
        assertTrue(r.isRelevant());
        assertEquals(3, r.getRecords().size());
        assertEquals("盒马", r.getRecords().get(0).get("merchant"));
        assertEquals(new BigDecimal("47.80"), r.getRecords().get(0).get("amount"));
        assertEquals("支付宝", r.getRecords().get(0).get("payMethod"));
        assertEquals("杭州杭港地铁", r.getRecords().get(1).get("merchant"));
        assertEquals(new BigDecimal("5.00"), r.getRecords().get(1).get("amount"));
        assertEquals(new BigDecimal("16.96"), r.getRecords().get(2).get("amount"));
        assertEquals("测试店铺", r.getRecords().get(2).get("merchant"));
    }

    @Test
    void listCompactOnlyLeafNames() {
        Category parent = new Category();
        parent.setId(1L);
        parent.setParentId(0L);
        parent.setCategoryName("餐饮");
        parent.setType(2);
        parent.setParentName(null);
        Category leaf = new Category();
        leaf.setId(2L);
        leaf.setParentId(1L);
        leaf.setCategoryName("正餐");
        leaf.setType(2);
        leaf.setParentName("餐饮");
        Category income = new Category();
        income.setId(3L);
        income.setParentId(0L);
        income.setCategoryName("基本工资");
        income.setType(1);
        CategoryBinder binder = CategoryBinder.from(List.of(parent, leaf, income));
        String compact = binder.listCompact();
        assertTrue(compact.contains("正餐"));
        assertTrue(compact.contains("基本工资"));
        assertFalse(compact.contains("餐饮/正餐"));
        assertTrue(binder.listText().contains("餐饮/正餐"));
    }

    @Test
    void textChunksSplitByFiftyRows() {
        StringBuilder sb = new StringBuilder("日期\t金额\n");
        for (int i = 0; i < 51; i++) {
            sb.append("2026-08-01\t1.00\n");
        }
        List<String> chunks = ExcelTextExtractor.textChunks(sb.toString());
        assertEquals(2, chunks.size());
        assertTrue(chunks.get(0).startsWith("日期"));
        assertTrue(chunks.get(1).startsWith("日期"));
    }

    @Test
    void shrinkLargeImageForVision() throws Exception {
        BufferedImage img = new BufferedImage(1600, 1600, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        Random r = new Random(1);
        for (int y = 0; y < 1600; y += 8) {
            for (int x = 0; x < 1600; x += 8) {
                g.setColor(new Color(r.nextInt(256), r.nextInt(256), r.nextInt(256)));
                g.fillRect(x, y, 8, 8);
            }
        }
        g.dispose();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(img, "bmp", out);
        byte[] raw = out.toByteArray();
        assertTrue(raw.length > 280_000);
        byte[] slim = ImagePrep.forVision(raw);
        assertTrue(slim.length < raw.length);
        BufferedImage after = ImageIO.read(new ByteArrayInputStream(slim));
        assertTrue(Math.max(after.getWidth(), after.getHeight()) <= 1280);
    }

    @Test
    void extractJsonFromFence() {
        String raw = "好的，结果如下：\n```json\n{\"relevant\":true,\"records\":[]}\n```\n";
        assertEquals("{\"relevant\":true,\"records\":[]}", DifyClient.extractJson(raw));
    }

    @Test
    void extractJsonStripsThinkAndTakesFirstObject() {
        String raw = """
                {
                  "relevant": true,
                  "reason": "",
                  "records": [{"type": 2, "amount": 28.5, "recordDate": "2026-08-03", "merchant": "海底捞"}]
                }

                recordDate: "2026-08-03" etc.

                是否可能盒马鲜生分类“家庭买菜”还是“日用品”？盒马鲜生通常生鲜超市，家庭买菜更合适。

                确保 amount number 28.5, 102, 4, 35.

                最终只输出 JSON。不要代码块。
                </think>{
                  "relevant": true,
                  "reason": "",
                  "records": [
                    {"type": 2, "amount": 28.5, "recordDate": "2026-08-03", "merchant": "海底捞"},
                    {"type": 2, "amount": 102, "recordDate": "2026-08-05", "merchant": "盒马鲜生"}
                  ]
                }
                """;
        String json = DifyClient.extractJson(raw);
        assertTrue(json.startsWith("{"));
        assertFalse(json.contains("</think>"));
        assertFalse(json.contains("家庭买菜还是"));
        assertTrue(json.contains("\"relevant\""));
        assertTrue(json.contains("盒马鲜生"));
        assertTrue(json.contains("102"));
    }

    @Test
    void extractJsonStripsWrappedThinkBlock() {
        String raw = "<think>先分析金额贰拾捌元伍角=28.5</think>{\"relevant\":true,\"reason\":\"\",\"records\":[{\"amount\":28.5}]}";
        assertEquals("{\"relevant\":true,\"reason\":\"\",\"records\":[{\"amount\":28.5}]}", DifyClient.extractJson(raw));
    }

    @Test
    void parseOrderNoColumn() {
        String tsv = "日期\t类型\t金额\t商家\t订单号\n"
                + "2026-08-01\t支出\t10\t超市\tORD-88\n"
                + "2026-08-01\t支出\t99\t便利店\t\n";
        DifyParseResult r = BillTextParser.parse(tsv);
        assertTrue(r.isRelevant());
        assertEquals(2, r.getRecords().size());
        assertEquals("ORD-88", r.getRecords().get(0).get("orderNo"));
        assertEquals("", r.getRecords().get(1).get("orderNo"));
    }

}
