package org.example.dto;

/**
 * 容器底图上传结果（img-service 返回的镜像）：**一张进、两张出**。
 *
 * <p>与 img-service 的 {@code org.example.dto.VesselDrawingResult} 字段一一对应 ——
 * 两仓各一份，改一处要改两处。
 *
 * <p>前端拿到 {@code fileName} 后拼成 {@code /files/{fileName}} 存进
 * {@code equipment_ledger.image_file}；亮线版按 {@code -dark} 后缀约定自动拼出来
 * （见前端 `useVesselList.js` 的 darkVariantOf）。
 */
public class VesselDrawingResult {

    private String fileName;      // 白纸版（浅色主题用）
    private String darkFileName;  // 亮线版（深色主题用）

    public String getFileName() {
        return fileName;
    }

    public void setFileName(String fileName) {
        this.fileName = fileName;
    }

    public String getDarkFileName() {
        return darkFileName;
    }

    public void setDarkFileName(String darkFileName) {
        this.darkFileName = darkFileName;
    }
}
