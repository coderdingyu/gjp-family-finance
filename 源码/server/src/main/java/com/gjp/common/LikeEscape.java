package com.gjp.common;

/**
 * 把用户输入的 %、_、\ 当成字面量，避免 LIKE 通配符把筛选变成“查全部”。
 */
public final class LikeEscape {

    private LikeEscape() {
    }

    public static String of(String keyword) {
        if (keyword == null) {
            return null;
        }
        String text = keyword.trim();
        if (text.isEmpty()) {
            return null;
        }
        return text.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }
}
