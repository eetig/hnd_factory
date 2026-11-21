package org.example.dto;

/**
 * 巡检清单的新增 / 修改入参（2026-11-21，变更-255）。
 *
 * <p>整条提交：名称、类别、正文。空字段一律按「清空」处理，
 * 不做「不传就保留」那种隐式规则 —— 改一次就是整条。
 */
public class InspectTemplateSaveDTO {

    private String name;

    private String kind;

    /** 正文：一整篇检查内容，换行保留（手机端按段落渲染） */
    private String body;

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getKind() {
        return kind;
    }

    public void setKind(String kind) {
        this.kind = kind;
    }

    public String getBody() {
        return body;
    }

    public void setBody(String body) {
        this.body = body;
    }
}
