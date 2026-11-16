package org.example.dto;

import java.util.List;

/**
 * 待办条目（2026-11-16）。
 *
 * <p>返回给手机端的形状就是界面上要显示的东西：一句话、可选的补充、截止日、
 * 是否完成、附图、谁加的、什么时候加的。字段刻意扁平 —— 手机端分组
 * （今天 / 本周 / 以后 / 没定）只按 {@code dueDate} 自己算，不由后端替它定口径。
 */
public class TodoVO {

    private Long id;
    private String title;
    private String note;
    private String dueDate;
    private boolean done;
    private String doneAt;
    private List<String> images;
    private String createdBy;
    private String createdAt;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

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

    public boolean isDone() {
        return done;
    }

    public void setDone(boolean done) {
        this.done = done;
    }

    public String getDoneAt() {
        return doneAt;
    }

    public void setDoneAt(String doneAt) {
        this.doneAt = doneAt;
    }

    public List<String> getImages() {
        return images;
    }

    public void setImages(List<String> images) {
        this.images = images;
    }

    public String getCreatedBy() {
        return createdBy;
    }

    public void setCreatedBy(String createdBy) {
        this.createdBy = createdBy;
    }

    public String getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(String createdAt) {
        this.createdAt = createdAt;
    }
}
