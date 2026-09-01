package com.gjp.analysis;

/**
 * 一条分析结论。
 *
 * 与统计条目的区别在于：统计只给数字，这里必须给出"结论 + 数据依据 + 建议"三段，
 * 也就是课程要求里"通过客观数据找到存在的问题"。
 */
public class AnalysisItem {

    /** 规则编号，便于测试用例逐条对照，如 A1、A2 */
    private String code;
    /** 严重程度：danger=需要立刻关注 warning=需留意 info=中性提示 good=表现良好 */
    private String level;
    /** 结论标题，一句话说清问题 */
    private String title;
    /** 数据依据，把参与判断的数字写出来，让结论可追溯 */
    private String basis;
    /** 处理建议 */
    private String suggestion;

    public AnalysisItem() {
    }

    public AnalysisItem(String code, String level, String title, String basis, String suggestion) {
        this.code = code;
        this.level = level;
        this.title = title;
        this.basis = basis;
        this.suggestion = suggestion;
    }

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }

    public String getLevel() {
        return level;
    }

    public void setLevel(String level) {
        this.level = level;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getBasis() {
        return basis;
    }

    public void setBasis(String basis) {
        this.basis = basis;
    }

    public String getSuggestion() {
        return suggestion;
    }

    public void setSuggestion(String suggestion) {
        this.suggestion = suggestion;
    }
}
