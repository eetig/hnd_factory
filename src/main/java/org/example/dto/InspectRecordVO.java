package org.example.dto;

import java.util.List;

/**
 * 一条巡检记录（2026-11-21，变更-255）。
 *
 * <p>只回答四件事：谁、什么时候、照着哪篇、留了什么。
 * 没有「异常数」——整篇是照着看过的，异常写在备注里由人判断。
 */
public class InspectRecordVO {

    private Long id;

    private String clientUuid;

    private Long templateId;

    /** 提交那一刻的清单名；模板后来改名/删除，历史仍是当时那个名字 */
    private String templateName;

    private String kind;

    private String operatorName;

    /** yyyy-MM-dd HH:mm */
    private String startedAt;

    /** yyyy-MM-dd HH:mm */
    private String finishedAt;

    private String note;

    private List<String> images;

    private int photoCount;

    /** yyyy-MM-dd HH:mm */
    private String createdAt;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getClientUuid() {
        return clientUuid;
    }

    public void setClientUuid(String clientUuid) {
        this.clientUuid = clientUuid;
    }

    public Long getTemplateId() {
        return templateId;
    }

    public void setTemplateId(Long templateId) {
        this.templateId = templateId;
    }

    public String getTemplateName() {
        return templateName;
    }

    public void setTemplateName(String templateName) {
        this.templateName = templateName;
    }

    public String getKind() {
        return kind;
    }

    public void setKind(String kind) {
        this.kind = kind;
    }

    public String getOperatorName() {
        return operatorName;
    }

    public void setOperatorName(String operatorName) {
        this.operatorName = operatorName;
    }

    public String getStartedAt() {
        return startedAt;
    }

    public void setStartedAt(String startedAt) {
        this.startedAt = startedAt;
    }

    public String getFinishedAt() {
        return finishedAt;
    }

    public void setFinishedAt(String finishedAt) {
        this.finishedAt = finishedAt;
    }

    public String getNote() {
        return note;
    }

    public void setNote(String note) {
        this.note = note;
    }

    public List<String> getImages() {
        return images;
    }

    public void setImages(List<String> images) {
        this.images = images;
    }

    public int getPhotoCount() {
        return photoCount;
    }

    public void setPhotoCount(int photoCount) {
        this.photoCount = photoCount;
    }

    public String getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(String createdAt) {
        this.createdAt = createdAt;
    }
}
