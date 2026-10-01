package org.example.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import org.example.component.ImgUploader;
import org.example.dto.TankLevelImageVO;
import org.example.entity.TankLevelImage;
import org.example.exception.BusinessException;
import org.example.feign.ImgFeignClient;
import org.example.mapper.TankLevelImageMapper;
import org.example.service.TankLevelImageService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class TankLevelImageServiceImpl extends ServiceImpl<TankLevelImageMapper, TankLevelImage>
        implements TankLevelImageService {

    private static final Logger log = LoggerFactory.getLogger(TankLevelImageServiceImpl.class);

    /**
     * 图据只收这两种格式。
     *
     * <p>ImgUploader 会把「非 png」一律按 jpg 提交，所以放 .gif / .bmp 进来不会被挡，
     * 而是以 jpg 的名义存进 MinIO —— 落库文件名看着正常、打开却不是那张图。
     * 与前端 file input 的 accept 收窄理由相同（img-service 只认 jpg/png）。
     */
    private static final Set<String> ALLOWED_IMAGE_EXT = Set.of("png", "jpg", "jpeg");

    /** 图片对外基础路径：同源部署留空 → 前端拿到 /files/xxx 相对路径（契约 2.3） */
    @Value("${image.base-url:}")
    private String imageBaseUrl;

    @Autowired
    private ImgUploader imgUploader;

    @Autowired
    private ImgFeignClient imgFeignClient;

    @Override
    public List<TankLevelImageVO> listImages(Long recordId) {
        if (recordId == null) {
            return List.of();
        }
        return baseMapper.selectList(new QueryWrapper<TankLevelImage>()
                        .eq("record_id", recordId)
                        .orderByAsc("id"))
                .stream().map(this::toVO).collect(Collectors.toList());
    }

    @Override
    public Map<Long, List<TankLevelImageVO>> mapByRecordIds(Collection<Long> recordIds) {
        if (recordIds == null || recordIds.isEmpty()) {
            return Map.of();
        }
        // 一次 IN 查询搞定整页：列表接口逐条查图据是 N+1，页数一多就线性放大
        return baseMapper.selectList(new QueryWrapper<TankLevelImage>()
                        .in("record_id", recordIds)
                        .orderByAsc("id"))
                .stream()
                .collect(Collectors.groupingBy(TankLevelImage::getRecordId,
                        Collectors.mapping(this::toVO, Collectors.toList())));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public List<TankLevelImageVO> uploadImages(Long recordId, List<MultipartFile> files) {
        if (recordId == null) {
            throw new BusinessException("缺少记录 id：请先保存这条记录，再上传图据。");
        }
        if (files == null || files.isEmpty()) {
            throw new BusinessException("请选择要上传的图片。");
        }

        List<TankLevelImageVO> result = new ArrayList<>();
        for (MultipartFile file : files) {
            if (file == null || file.isEmpty()) {
                continue;
            }

            String originalName = file.getOriginalFilename();
            String ext = extensionOf(originalName);
            if (!ALLOWED_IMAGE_EXT.contains(ext)) {
                throw new BusinessException("图据只支持 PNG / JPG 格式：" + originalName);
            }

            String fileName;
            try {
                fileName = imgUploader.upload(file.getBytes(), originalName, ext);
            } catch (IOException e) {
                log.warn("读取上传图据失败 recordId={}", recordId, e);
                throw new BusinessException("图片读取失败，请重试。");
            } catch (RuntimeException e) {
                log.warn("图据上传到 img-service 失败 recordId={}", recordId, e);
                throw new BusinessException("图据上传失败，请稍后重试。");
            }

            TankLevelImage image = new TankLevelImage();
            image.setRecordId(recordId);
            image.setFileName(fileName);
            baseMapper.insert(image);
            result.add(toVO(image));
        }

        if (result.isEmpty()) {
            throw new BusinessException("请选择要上传的图片。");
        }
        return result;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteImage(Long imageId) {
        TankLevelImage image = imageId == null ? null : baseMapper.selectById(imageId);
        if (image == null) {
            // 已经不存在：直接返回，不报错 —— 删除请求重发一次不该变成失败
            return;
        }
        // 先删库再删文件：库是真相源。反过来一旦删文件成功而删库失败，
        // 就留下一条指向空图的记录，用户看不出它已经坏了
        baseMapper.deleteById(imageId);
        deleteStoredFile(image.getFileName());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteByRecordIds(Collection<Long> recordIds) {
        if (recordIds == null || recordIds.isEmpty()) {
            return;
        }

        List<TankLevelImage> images = baseMapper.selectList(
                new QueryWrapper<TankLevelImage>().in("record_id", recordIds));
        if (images.isEmpty()) {
            return;
        }

        baseMapper.delete(new QueryWrapper<TankLevelImage>().in("record_id", recordIds));
        images.forEach(image -> deleteStoredFile(image.getFileName()));
    }

    /**
     * 删 img-service 上的文件。
     *
     * <p>失败只记日志、不往上抛：数据库那边已经清干净了，残留一个 MinIO 文件是次要问题，
     * 不该让「删图」这件事因为存储侧抖动而整个失败。
     */
    private void deleteStoredFile(String fileName) {
        if (!StringUtils.hasText(fileName)) {
            return;
        }
        try {
            imgFeignClient.delete(fileName.trim());
        } catch (Exception e) {
            log.warn("删除 img-service 图据失败，已忽略 fileName={}", fileName, e);
        }
    }

    /** 取扩展名（小写）；没有扩展名时返回空串，交由白名单拒掉 */
    private static String extensionOf(String fileName) {
        if (fileName == null) {
            return "";
        }
        int dot = fileName.lastIndexOf('.');
        if (dot < 0 || dot == fileName.length() - 1) {
            return "";
        }
        return fileName.substring(dot + 1).toLowerCase(Locale.ROOT);
    }

    private TankLevelImageVO toVO(TankLevelImage image) {
        TankLevelImageVO vo = new TankLevelImageVO();
        vo.setImageId(image.getId());
        vo.setUrl(toUrl(image.getFileName()));
        vo.setThumbnailUrl(toThumbUrl(image.getFileName()));
        return vo;
    }

    /**
     * 原图：固定 URL，纯字符串拼接。
     *
     * <p>不调 Feign 取预签名 URL：预签名每次都带新的 X-Amz-Date，浏览器按 URL 缓存，
     * URL 一变就全部重下（变更-001 的根因）。
     */
    private String toUrl(String fileName) {
        return StringUtils.hasText(fileName) ? imageBaseUrl + "/files/" + fileName : null;
    }

    /** 缩略图：固定 URL */
    private String toThumbUrl(String fileName) {
        return StringUtils.hasText(fileName) ? imageBaseUrl + "/thumbs/" + fileName : null;
    }
}
