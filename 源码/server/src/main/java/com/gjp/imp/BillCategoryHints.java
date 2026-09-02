package com.gjp.imp;

import java.util.Locale;

/**
 * 微信/支付宝导出没有分类列时，用商家和商品名猜一个末级分类，再交给 CategoryBinder 对齐。
 */
public final class BillCategoryHints {

    private BillCategoryHints() {
    }

    public static String guess(Integer type, String merchant, String remark) {
        String blob = compact(merchant) + compact(remark);
        if (blob.isEmpty()) {
            return "";
        }
        if (type != null && type == 1) {
            if (contains(blob, "工资", "薪资")) {
                return "基本工资";
            }
            if (contains(blob, "红包", "礼金")) {
                return "红包礼金";
            }
            if (contains(blob, "转账")) {
                return "红包礼金";
            }
            return "";
        }
        if (contains(blob, "肯德基", "麦当劳", "汉堡", "快餐", "KFC")) {
            return "快餐";
        }
        if (contains(blob, "火锅", "烧烤")) {
            return "火锅烧烤";
        }
        if (contains(blob, "咖啡", "奶茶", "喜茶", "瑞幸", "茶百道")) {
            return "咖啡奶茶";
        }
        if (contains(blob, "外卖", "美团", "饿了么")) {
            return "外卖";
        }
        if (contains(blob, "食堂", "餐厅", "餐饮", "小吃", "就餐", "堂食", "套餐", "校联")) {
            return "正餐";
        }
        if (contains(blob, "京东", "拼多多", "淘宝", "天猫")) {
            return "日用品";
        }
        if (contains(blob, "蜜雪")) {
            return "咖啡奶茶";
        }
        if (contains(blob, "超市", "盒马", "永辉", "买菜", "生鲜")) {
            return "家庭买菜";
        }
        if (contains(blob, "水果", "零食")) {
            return "零食水果";
        }
        if (contains(blob, "滴滴", "打车", "出行")) {
            return "打车";
        }
        if (contains(blob, "12306", "中铁", "铁路", "地铁", "公交", "一卡通")) {
            return "公共交通";
        }
        if (contains(blob, "加油")) {
            return "加油";
        }
        if (contains(blob, "电费", "充电") && !contains(blob, "宝")) {
            return "电费";
        }
        if (contains(blob, "水费", "订水")) {
            return "水费";
        }
        if (contains(blob, "电影", "影城")) {
            return "电影";
        }
        if (contains(blob, "游戏", "充值")) {
            return "游戏充值";
        }
        if (contains(blob, "会员", "云空间", "流媒体")) {
            return "流媒体会员";
        }
        if (contains(blob, "洗衣", "日用", "便利")) {
            return "日用品";
        }
        if (contains(blob, "红包", "随礼", "份子")) {
            return "礼金红包";
        }
        if (contains(blob, "转账") && contains(blob, "保洁", "送礼")) {
            return "送礼";
        }
        return "";
    }

    private static boolean contains(String blob, String... words) {
        String lower = blob.toLowerCase(Locale.ROOT);
        for (String w : words) {
            if (lower.contains(w.toLowerCase(Locale.ROOT))) {
                return true;
            }
        }
        return false;
    }

    private static String compact(String s) {
        return s == null ? "" : s.replaceAll("\\s+", "");
    }
}
