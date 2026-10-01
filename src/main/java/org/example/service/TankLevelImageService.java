package org.example.service;

import org.example.dto.TankLevelImageVO;
import org.springframework.web.multipart.MultipartFile;

import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * 月底储罐液位记录的图据：查看 / 上传 / 删除。
 *
 * <p>与 {@code WorkOrderImageService} 同一套模式，差别在归属键是记录的自增 id 而不是业务键
 * （液位记录的业务键「日期 + 容器编号」正是用户会编辑的字段，挂它就要级联更新图片表）。
 *
 * <p>写操作要求 {@code tank_level:edit} 权限（在 Controller 上声明）；查询免登录。
 */
public interface TankLevelImageService {

    /** 某条记录的全部图据，按上传顺序（id 升序）*/
    List<TankLevelImageVO> listImages(Long recordId);

    /**
     * 给某条记录追加若干张图（不覆盖已有图）。
     *
     * @return 本次上传成功的图片，顺序与入参 files 一致
     */
    List<TankLevelImageVO> uploadImages(Long recordId, List<MultipartFile> files);

    /** 删除一张图：连同它在 img-service 上的文件 */
    void deleteImage(Long imageId);

    /**
     * 删除若干条记录名下的全部图据（删记录时连带调用）。
     *
     * <p>子表没有外键，不主动清就会留下孤儿行 + MinIO 上的孤儿文件。
     */
    void deleteByRecordIds(Collection<Long> recordIds);

    /**
     * 批量取多张记录的图据，按 recordId 分组。
     *
     * <p>供列表接口一次装配好整页的图据 —— 逐条查是 N+1，
     * 记录数一多（一年 12 × 容器数）就会随页数线性放大。
     */
    Map<Long, List<TankLevelImageVO>> mapByRecordIds(Collection<Long> recordIds);
}
