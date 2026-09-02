package com.gjp.imp;

import com.gjp.entity.Category;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 把智能体吐出的分类名绑到本家庭的末级分类。
 * 只绑末级：流水校验不允许选还有下级的节点。
 */
public final class CategoryBinder {

    private final List<Category> leaves;

    private CategoryBinder(List<Category> leaves) {
        this.leaves = leaves;
    }

    public static CategoryBinder from(List<Category> all) {
        Set<Long> parentIds = new HashSet<>();
        for (Category c : all) {
            if (c.getParentId() != null && c.getParentId() != 0L) {
                parentIds.add(c.getParentId());
            }
        }
        List<Category> leaves = new ArrayList<>();
        for (Category c : all) {
            if (!parentIds.contains(c.getId())) {
                leaves.add(c);
            }
        }
        return new CategoryBinder(leaves);
    }

    /** 给智能体看的末级名单，按收入/支出分开 */
    public String listText() {
        StringBuilder income = new StringBuilder();
        StringBuilder expense = new StringBuilder();
        for (Category c : leaves) {
            String path = path(c);
            if (c.getType() != null && c.getType() == 1) {
                if (income.length() > 0) {
                    income.append('、');
                }
                income.append(path);
            } else {
                if (expense.length() > 0) {
                    expense.append('、');
                }
                expense.append(path);
            }
        }
        return "收入：" + income + "\n支出：" + expense;
    }

    /** 只给末级名，少占模型上下文。 */
    public String listCompact() {
        StringBuilder income = new StringBuilder();
        StringBuilder expense = new StringBuilder();
        for (Category c : leaves) {
            String name = c.getCategoryName();
            if (name == null || name.isBlank()) {
                continue;
            }
            if (c.getType() != null && c.getType() == 1) {
                if (income.length() > 0) {
                    income.append('、');
                }
                income.append(name);
            } else {
                if (expense.length() > 0) {
                    expense.append('、');
                }
                expense.append(name);
            }
        }
        return "收入：" + income + "\n支出：" + expense;
    }

    public Category match(Integer type, String name) {
        int t = type == null || type != 1 ? 2 : 1;
        if (name != null && !name.isBlank()) {
            String raw = name.trim();
            String compact = compact(raw);
            Category exact = findExact(t, raw, compact);
            if (exact != null) {
                return exact;
            }
            Category fuzzy = findContains(t, raw, compact);
            if (fuzzy != null) {
                return fuzzy;
            }
        }
        return fallback(t);
    }

    private Category findExact(int type, String raw, String compact) {
        for (Category c : leaves) {
            if (!typeEquals(c, type)) {
                continue;
            }
            if (raw.equals(c.getCategoryName()) || compact.equals(compact(c.getCategoryName()))) {
                return c;
            }
            String p = path(c);
            if (raw.equals(p) || compact.equals(compact(p))) {
                return c;
            }
        }
        return null;
    }

    private Category findContains(int type, String raw, String compact) {
        Category best = null;
        int bestLen = -1;
        for (Category c : leaves) {
            if (!typeEquals(c, type)) {
                continue;
            }
            String n = compact(c.getCategoryName());
            if (n.isEmpty()) {
                continue;
            }
            if (n.contains(compact) || compact.contains(n)) {
                if (n.length() > bestLen) {
                    best = c;
                    bestLen = n.length();
                }
            }
        }
        return best;
    }

    private Category fallback(int type) {
        for (Category c : leaves) {
            if (typeEquals(c, type) && "杂项".equals(c.getCategoryName())) {
                return c;
            }
        }
        for (Category c : leaves) {
            if (typeEquals(c, type) && ("其他支出".equals(c.getParentName()) || "其他收入".equals(c.getParentName()))) {
                return c;
            }
        }
        for (Category c : leaves) {
            if (typeEquals(c, type)) {
                return c;
            }
        }
        return leaves.isEmpty() ? null : leaves.get(0);
    }

    private static boolean typeEquals(Category c, int type) {
        return c.getType() != null && c.getType() == type;
    }

    private static String path(Category c) {
        if (c.getParentName() != null && !c.getParentName().isBlank()) {
            return c.getParentName() + "/" + c.getCategoryName();
        }
        return c.getCategoryName();
    }

    private static String compact(String s) {
        return s == null ? "" : s.replaceAll("\\s+", "");
    }
}
