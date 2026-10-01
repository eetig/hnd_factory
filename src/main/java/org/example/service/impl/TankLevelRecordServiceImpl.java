package org.example.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import org.example.dto.TankLevelImageVO;
import org.example.dto.TankLevelSaveDTO;
import org.example.dto.TankLevelVO;
import org.example.entity.TankLevelRecord;
import org.example.exception.BusinessException;
import org.example.mapper.TankLevelRecordMapper;
import org.example.service.TankLevelImageService;
import org.example.service.TankLevelRecordService;
import org.example.util.TankLevelValidator;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.text.Collator;
import java.time.LocalDate;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 月底储罐液位记录：查询 + 录入维护实现。
 *
 * <p>记录是「每月月底抄一次」的台账，数据量很小（一年 12 × 容器数），
 * 因此沿用本工程既有做法：库侧只做条件过滤，分页/排序展示交给前端。
 *
 * <p>图据（可多张）在子表里，由 {@link TankLevelImageService} 负责；
 * 本类只负责把图据装配进 VO。变更-011 之前图据是这张表上的 file_name 单列，
 * 当时的单张上传/删除方法已随之删除。
 */
@Service
public class TankLevelRecordServiceImpl extends ServiceImpl<TankLevelRecordMapper, TankLevelRecord>
        implements TankLevelRecordService {

    @Autowired
    private TankLevelImageService tankLevelImageService;

    @Override
    public List<TankLevelVO> listRecords(LocalDate startDate, LocalDate endDate,
                                         String location, String category, String keyword) {
        QueryWrapper<TankLevelRecord> wrapper = new QueryWrapper<>();
        if (startDate != null) {
            wrapper.ge("record_date", startDate);
        }
        if (endDate != null) {
            wrapper.le("record_date", endDate);
        }
        if (StringUtils.hasText(location)) {
            wrapper.eq("location", location.trim());
        }
        if (StringUtils.hasText(category)) {
            wrapper.eq("category", category.trim());
        }
        if (StringUtils.hasText(keyword)) {
            String kw = keyword.trim();
            // 括号必须显式包住 or 段：否则 or 会与上面的 eq 条件平级，
            // 变成「属地=某车间 或 物料含关键字」，把其它属地的记录一起带出来
            wrapper.and(w -> w.like("material_code", kw)
                    .or().like("material_name", kw)
                    .or().like("tank_name", kw)
                    .or().like("tank_code", kw));
        }
        // 同一天的记录按属地、容器编号排好，页面「序号」才是稳定可读的顺序
        wrapper.orderByDesc("record_date").orderByAsc("location").orderByAsc("tank_code").orderByAsc("id");

        List<TankLevelRecord> records = baseMapper.selectList(wrapper);
        if (records.isEmpty()) {
            return List.of();
        }

        // 图据一次 IN 查询取回整页再分组，不逐条查 —— 这是列表接口，N+1 会随页数放大
        Map<Long, List<TankLevelImageVO>> imagesByRecord = tankLevelImageService.mapByRecordIds(
                records.stream().map(TankLevelRecord::getId).collect(Collectors.toList()));

        return records.stream()
                .map(record -> toVO(record, imagesByRecord.get(record.getId())))
                .collect(Collectors.toList());
    }

    @Override
    public List<String> listLocations() {
        // 只取 location 一列再内存去重：表很小，换来的是不依赖 DISTINCT + selectObjs 的写法差异
        return baseMapper.selectList(new QueryWrapper<TankLevelRecord>().select("location")).stream()
                .map(TankLevelRecord::getLocation)
                .filter(StringUtils::hasText)
                .map(String::trim)
                .distinct()
                .sorted(Collator.getInstance(Locale.CHINA))
                .collect(Collectors.toList());
    }

    @Override
    public TankLevelVO saveRecord(TankLevelSaveDTO dto) {
        String error = TankLevelValidator.validate(dto);
        if (error != null) {
            throw new BusinessException(error);
        }

        TankLevelRecord record = new TankLevelRecord();
        // 字符串一律 trim 后落库：容器编号带前后空格时，唯一键 (record_date, tank_code)
        // 会把「 V150-A」和「V150-A」当成两条不同记录，页面看着却是同一个编号
        record.setRecordDate(dto.getRecordDate());
        record.setLocation(trim(dto.getLocation()));
        record.setCategory(trim(dto.getCategory()));
        record.setMaterialCode(trim(dto.getMaterialCode()));
        record.setMaterialName(trim(dto.getMaterialName()));
        record.setTankName(trim(dto.getTankName()));
        record.setTankCode(trim(dto.getTankCode()));
        record.setLevelValue(dto.getLevelValue());
        record.setTheoreticalWeight(dto.getTheoreticalWeight());

        try {
            if (dto.getId() == null) {
                baseMapper.insert(record);
            } else {
                requireRecord(dto.getId());
                record.setId(dto.getId());
                updateAllFields(record);
            }
        } catch (DuplicateKeyException e) {
            // 撞唯一键 uk_date_tank：这是正常的业务冲突（同一天同一容器只能一条），
            // 不能把 SQL 异常原样透给前端 —— 用户看不到「哪一行哪一列冲突了」
            throw new BusinessException("同一天已存在容器编号为「" + record.getTankCode() + "」的记录，"
                    + "请改记录日期或容器编号。");
        }

        // 新增的记录还没有图据；编辑的要把已有的带回去 ——
        // 不然前端保存完一刷新，弹窗里的图会「凭空消失」
        List<TankLevelImageVO> images = dto.getId() == null
                ? List.of()
                : tankLevelImageService.listImages(record.getId());
        return toVO(record, images);
    }

    @Override
    public void deleteRecord(Long id) {
        requireRecord(id);
        // 先删库再删文件：库是真相源，文件是派生物。反过来一旦删文件成功而删库失败，
        // 就留下一条指向空图的记录，用户看不出它已经坏了
        baseMapper.deleteById(id);
        // 子表没有外键，图据得显式清 —— 否则留下孤儿行 + MinIO 上的孤儿文件
        tankLevelImageService.deleteByRecordIds(List.of(id));
    }

    /**
     * 编辑时逐列显式赋值。
     *
     * <p>不能用 updateById：它跳过 null 字段，于是「把物料编码清空」这种操作存不进去，
     * 页面刷新后旧值又回来了 —— 用户会以为保存没生效。这里显式带上每一列，
     * 让 null 也如实写进库（= 该字段被清空）。
     */
    private void updateAllFields(TankLevelRecord record) {
        baseMapper.update(null, new UpdateWrapper<TankLevelRecord>()
                .eq("id", record.getId())
                .set("record_date", record.getRecordDate())
                .set("location", record.getLocation())
                .set("category", record.getCategory())
                .set("material_code", record.getMaterialCode())
                .set("material_name", record.getMaterialName())
                .set("tank_name", record.getTankName())
                .set("tank_code", record.getTankCode())
                .set("level_value", record.getLevelValue())
                .set("theoretical_weight", record.getTheoreticalWeight()));
    }

    private TankLevelRecord requireRecord(Long id) {
        if (id == null) {
            throw new BusinessException("缺少记录 id。");
        }
        TankLevelRecord record = baseMapper.selectById(id);
        if (record == null) {
            throw new BusinessException("该记录不存在或已被删除，请刷新后重试。");
        }
        return record;
    }

    private static String trim(String value) {
        return value == null ? null : value.trim();
    }

    private TankLevelVO toVO(TankLevelRecord record, List<TankLevelImageVO> images) {
        TankLevelVO vo = new TankLevelVO();
        vo.setId(record.getId());
        vo.setRecordDate(record.getRecordDate());
        vo.setLocation(record.getLocation());
        vo.setCategory(record.getCategory());
        vo.setMaterialCode(record.getMaterialCode());
        vo.setMaterialName(record.getMaterialName());
        vo.setTankName(record.getTankName());
        vo.setTankCode(record.getTankCode());
        vo.setLevelValue(record.getLevelValue());
        vo.setTheoreticalWeight(record.getTheoreticalWeight());
        // 空数组而不是 null：前端处处要判 length，多一个 null 分支就多一处漏判
        vo.setImages(images == null ? List.of() : images);
        return vo;
    }
}
