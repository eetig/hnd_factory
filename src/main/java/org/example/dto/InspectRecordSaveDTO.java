package org.example.dto;

import java.util.List;

/**
 * 提交一次巡检（2026-11-21，变更-255）。
 *
 * <p>templateName / kind / operatorName 都由服务端抄模板或查用户，
 * 不接受客户端传 —— 手机端的职责只是「我照着哪篇看完了、留了什么」。
 */
public class InspectRecordSaveDTO {

    /** 手机端生成的唯一号；重传时带同一个值，服务端据此幂等 */
    private String clientUuid;

    private Long templateId;

    /** yyyy-MM-dd HH:mm，可以为空（默认取服务端当前时间） */
    private String startedAt;

    /** yyyy-MM-dd HH:mm，可以为空（默认取服务端当前时间） */
    private String finishedAt;

    /** 现场备注，可选 */
    private String note;

    /** 现场照片：手机先传到 /api/inspect/photo 换成 /files/xxx，再整条提交；最多 9 张 */
    private List<String> images;

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
}
