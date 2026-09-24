package org.example.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import org.example.dto.Result;
import org.example.dto.WorkOrderImageVO;
import org.example.service.WorkOrderImageService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.Arrays;
import java.util.List;

@RestController
@RequestMapping("/api/work-order/image")
public class WorkOrderImageController {

    @Autowired
    private WorkOrderImageService workOrderImageService;

    // 上传多张工单图片，返回新上传的图片列表
    @SaCheckPermission("work_order:image:upload")
    @PostMapping("/upload")
    public Result<List<WorkOrderImageVO>> upload(
            @RequestPart("orderNo") String orderNo,
            @RequestPart("files") MultipartFile[] files) {
        List<WorkOrderImageVO> list = workOrderImageService.uploadImages(orderNo, Arrays.asList(files));
        return Result.success(list);
    }

    // 删除指定图片记录
    @SaCheckPermission("work_order:image:delete")
    @DeleteMapping("/delete")
    public Result<Void> delete(@RequestParam("imageId") Long imageId) {
        workOrderImageService.deleteImage(imageId);
        return Result.success(null);
    }

    // 查询工单下所有图片（弹窗展示用）—— 查询类接口，免登录
    @GetMapping("/list")
    public Result<List<WorkOrderImageVO>> list(@RequestParam("orderNo") String orderNo) {
        return Result.success(workOrderImageService.listImages(orderNo));
    }
}
