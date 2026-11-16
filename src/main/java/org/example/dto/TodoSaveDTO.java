package org.example.dto;

import java.util.List;

/**
 * 待办的新增 / 修改入参（2026-11-16）。
 *
 * <p>手机端整条提交：标题、补充、截止日、附图数组。空字段一律按「清空」处理，
 * 与手机端改完看到的界面一致（不做「不传就保留」那种隐式规则，改一次就是整条）。
 */
public class TodoSaveDTO {

    /** 一句话说明要办什么，必填 */
    private String title;

    /** 补充说明，可空 */
    private String note;

    /** yyyy-MM-dd；空字符串 = 没定日期 */
    private String dueDate;

    /** 附图路径（/files/xxx.jpg），可空 */
    private List<String> images;

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getNote() {
        return note;
    }

    public void setNote(String note) {
        this.note = note;
    }

    public String getDueDate() {
        return dueDate;
    }

    public void setDueDate(String dueDate) {
        this.dueDate = dueDate;
    }

    public List<String> getImages() {
        return images;
    }

    public void setImages(List<String> images) {
        this.images = images;
    }
}
