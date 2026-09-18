package org.example.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import org.example.dto.ImgResult;
import org.example.dto.UploadResult;
import org.example.dto.WorkOrderImageVO;
import org.example.entity.WorkOrderImage;
import org.example.exception.BusinessException;
import org.example.feign.ImgFeignClient;
import org.example.mapper.WorkOrderImageMapper;
import org.example.service.WorkOrderImageService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class WorkOrderImageServiceImpl implements WorkOrderImageService {

    @Autowired
    private WorkOrderImageMapper workOrderImageMapper;

    @Autowired
    private ImgFeignClient imgFeignClient;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public List<WorkOrderImageVO> uploadImages(String orderNo, List<MultipartFile> files) {
        if (!StringUtils.hasText(orderNo)) {
            throw new BusinessException("工单号不能为空");
        }
        if (files == null || files.isEmpty()) {
            throw new BusinessException("请选择要上传的图片");
        }

        List<WorkOrderImageVO> result = new ArrayList<>();
        for (MultipartFile file : files) {
            // 1. 上传到 MinIO（img-service）
            ImgResult<UploadResult> up = imgFeignClient.upload(file);
            if (up == null || up.getCode() != 200 || up.getData() == null) {
                throw new BusinessException("图片上传失败");
            }
            // 2. 新增一条记录，保留原有图片
            WorkOrderImage img = new WorkOrderImage();
            img.setOrderNo(orderNo);
            img.setFileName(up.getData().getFileName());
            workOrderImageMapper.insert(img);
            result.add(toVO(img));
        }
        return result;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteImage(Long imageId) {
        WorkOrderImage img = workOrderImageMapper.selectById(imageId);
        if (img == null) {
            return;
        }
        // 删 MinIO 文件 + 删这一条数据库记录
        imgFeignClient.delete(img.getFileName());
        workOrderImageMapper.deleteById(imageId);
    }

    @Override
    public List<WorkOrderImageVO> listImages(String orderNo) {
        return workOrderImageMapper.selectList(
                        new QueryWrapper<WorkOrderImage>().eq("order_no", orderNo).orderByAsc("id"))
                .stream().map(this::toVO).collect(Collectors.toList());
    }

    private WorkOrderImageVO toVO(WorkOrderImage img) {
        WorkOrderImageVO vo = new WorkOrderImageVO();
        vo.setImageId(img.getId());
        vo.setUrl(toUrl(img.getFileName()));
        return vo;
    }

    private String toUrl(String fileName) {
        ImgResult<String> r = imgFeignClient.getPresignedUrl(fileName);
        return r != null && r.getCode() == 200 ? r.getData() : null;
    }
}
