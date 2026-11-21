package org.example.dto;

/**
 * 巡检清单（2026-11-21，变更-255：清单＝一整篇正文）。
 *
 * <p>不再有「检查项」数组：正文是一段可以直接读的小作文，换行就是分段。
 * charCount 给手机端列表做「共 486 字」这类安静元信息用。
 */
public class InspectTemplateVO {

    private Long id;

    private String name;

    /** shop 车间 / pump 泵房 */
    private String kind;

    /** 正文原文，含换行 */
    private String body;

    /** 正文字数（不含换行与首尾空白），列表里显示「共 N 字」 */
    private int charCount;

    /** yyyy-MM-dd HH:mm */
    private String updatedAt;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

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

    public int getCharCount() {
        return charCount;
    }

    public void setCharCount(int charCount) {
        this.charCount = charCount;
    }

    public String getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(String updatedAt) {
        this.updatedAt = updatedAt;
    }
}
