package org.example.service;

import org.example.dto.WorkOrderImageVO;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

public interface WorkOrderImageService {
    // 上传多张图片，返回新上传的图片列表
    List<WorkOrderImageVO> uploadImages(String orderNo, List<MultipartFile> files);

    // 删除指定图片记录
    void deleteImage(Long imageId);

    // 查询工单下所有图片
    List<WorkOrderImageVO> listImages(String orderNo);
}
