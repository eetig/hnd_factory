package org.example.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.example.dto.ArchiveCategoryVO;
import org.example.dto.ArchiveDocumentQueryDTO;
import org.example.dto.ArchiveDocumentSaveDTO;
import org.example.dto.ArchiveDocumentVO;
import org.example.entity.ArchiveCategory;
import org.example.entity.ArchiveDocument;
import org.example.exception.BusinessException;
import org.example.feign.FileFeignClient;
import org.example.mapper.ArchiveCategoryMapper;
import org.example.mapper.ArchiveDocumentMapper;
import org.example.service.ArchiveService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 资料归档的实现（变更-126）。
 *
 * <p><b>两个必须记住的取舍</b>：
 * <ol>
 *   <li><b>软删不删文件</b>：删除只把 {@code deleted} 置 1，MinIO 上的文件留着 ——
 *       这才是"删错了还能找回来"的前提。只有<b>换文件/清空文件</b>才真的删存储侧的旧文件。</li>
 *   <li><b>删旧文件放在写库之后</b>（与 {@code PurchaseTrackingServiceImpl} 同一个理由）：
 *       写库失败要整体回滚，删文件先发生就删掉了一个仍被引用的对象；
 *       反过来"库成了、文件没删掉"只是留个孤儿，还能人工收 —— 两种错代价不同，选轻的。</li>
 * </ol>
 */
@Service
public class ArchiveServiceImpl implements ArchiveService {

    private static final Logger log = LoggerFactory.getLogger(ArchiveServiceImpl.class);

    /** 分页每页上限（PageQueryDTO 已有 200 的上限，这里再挡一次防御） */
    private static final int HARD_MAX_PAGE_SIZE = 200;

    @Autowired
    private ArchiveDocumentMapper documentMapper;

    @Autowired
    private ArchiveCategoryMapper categoryMapper;

    @Autowired
    private FileFeignClient fileFeignClient;

    // ================= 列表 =================

    @Override
    public IPage<ArchiveDocumentVO> listPage(ArchiveDocumentQueryDTO query) {
        // resolvedPageNum/Size 已做过归一化与 200 上限夹取（见 PageQueryDTO），这里不重复加戏
        int pageNum = query.resolvedPageNum();
        int pageSize = query.resolvedPageSize();

        LambdaQueryWrapper<ArchiveDocument> wrapper = new LambdaQueryWrapper<ArchiveDocument>()
                .eq(ArchiveDocument::getDeleted, 0);

        String keyword = trimToEmpty(query.getKeyword());
        if (!keyword.isEmpty()) {
            // 四列 OR 必须**整体括起来**，否则会和上面的 deleted=0 变成"deleted=0 OR (...)"，
            // 把所有已删的行漏出来（变更-093 在采购页踩过同一个坑）。
            wrapper.and(w -> w.like(ArchiveDocument::getName, keyword)
                    .or().like(ArchiveDocument::getDocNo, keyword)
                    .or().like(ArchiveDocument::getOwner, keyword)
                    .or().like(ArchiveDocument::getOriginPlace, keyword));
        }

        if (query.getCategoryId() != null) {
            Set<Long> ids = categoryAndChildrenIds(query.getCategoryId());
            wrapper.in(ArchiveDocument::getCategoryId, ids);
        }

        int withinDays = query.getExpireWithinDays() == null ? 0 : query.getExpireWithinDays();
        if (withinDays > 0) {
            LocalDate limit = LocalDate.now().plusDays(withinDays);
            // 已过期的也要能看到：它们比"还有几天到期"的更要紧
            wrapper.isNotNull(ArchiveDocument::getExpireDate)
                    .le(ArchiveDocument::getExpireDate, limit);
        }

        /*
         * 排序：
         *   · 只看"N 天内到期"时，按有效期升序 —— 最急的排最前（此时所有行都有有效期，
         *     不会出现 MySQL 把 NULL 排在升序最前的问题）；
         *   · 其余情况按 id 降序（最近录入在前）。
         *   · 末尾一律带唯一 id：分页时若排序键有重复行，翻页会出现重复/漏行（本仓库的既有约定）。
         */
        if (withinDays > 0) {
            wrapper.orderByAsc(ArchiveDocument::getExpireDate);
        }
        wrapper.orderByDesc(ArchiveDocument::getId);

        IPage<ArchiveDocument> page = documentMapper.selectPage(
                new Page<>(pageNum, pageSize), wrapper);

        Map<Long, String> categoryNames = categoryNameMap();
        List<ArchiveDocumentVO> rows = page.getRecords().stream()
                .map(row -> toVO(row, categoryNames.get(row.getCategoryId())))
                .toList();

        Page<ArchiveDocumentVO> result = new Page<>(page.getCurrent(), page.getSize(), page.getTotal());
        result.setRecords(rows);
        return result;
    }

    // ================= 分类 =================

    @Override
    public List<ArchiveCategoryVO> categoryTree() {
        List<ArchiveCategory> all = categoryMapper.selectList(
                new LambdaQueryWrapper<ArchiveCategory>()
                        .orderByAsc(ArchiveCategory::getSortNo)
                        .orderByAsc(ArchiveCategory::getId));

        // 每个分类下"直接挂着的"未删资料数，再往父级累加
        Map<Long, Long> directCount = new HashMap<>();
        for (ArchiveDocument row : documentMapper.selectList(
                new LambdaQueryWrapper<ArchiveDocument>()
                        .select(ArchiveDocument::getCategoryId)
                        .eq(ArchiveDocument::getDeleted, 0))) {
            if (row.getCategoryId() != null) {
                directCount.merge(row.getCategoryId(), 1L, Long::sum);
            }
        }

        Map<Long, ArchiveCategoryVO> nodes = new HashMap<>();
        List<ArchiveCategoryVO> roots = new ArrayList<>();
        for (ArchiveCategory c : all) {
            ArchiveCategoryVO vo = new ArchiveCategoryVO();
            vo.setId(c.getId());
            vo.setParentId(c.getParentId());
            vo.setName(c.getName());
            vo.setSortNo(c.getSortNo());
            vo.setDocumentCount(directCount.getOrDefault(c.getId(), 0L));
            nodes.put(c.getId(), vo);
            if (c.getParentId() == null) {
                roots.add(vo);
            }
        }
        // 挂到父级；父不存在（理论不会，因为删父级前会拒绝）时按大类处理，避免整条从树里消失
        for (ArchiveCategory c : all) {
            if (c.getParentId() == null) {
                continue;
            }
            ArchiveCategoryVO parent = nodes.get(c.getParentId());
            if (parent == null) {
                roots.add(nodes.get(c.getId()));
                continue;
            }
            parent.getChildren().add(nodes.get(c.getId()));
            parent.setDocumentCount(parent.getDocumentCount() + nodes.get(c.getId()).getDocumentCount());
        }
        return roots;
    }

    @Override
    public Long saveCategory(Long id, Long parentId, String name, Integer sortNo) {
        String trimmed = trimToEmpty(name);
        if (trimmed.isEmpty()) {
            throw new BusinessException("分类名称不能为空");
        }
        if (trimmed.length() > 64) {
            throw new BusinessException("分类名称最多 64 个字");
        }
        if (parentId != null) {
            ArchiveCategory parent = categoryMapper.selectById(parentId);
            if (parent == null) {
                throw new BusinessException("父分类不存在");
            }
            if (parent.getParentId() != null) {
                throw new BusinessException("只支持两级分类：不能挂在子分类下面");
            }
        }

        ArchiveCategory row = id == null ? new ArchiveCategory() : categoryMapper.selectById(id);
        if (row == null) {
            throw new BusinessException("分类不存在");
        }
        if (id != null && parentId != null && id.equals(parentId)) {
            throw new BusinessException("不能把分类挂到自己下面");
        }
        row.setParentId(parentId);
        row.setName(trimmed);
        row.setSortNo(sortNo == null ? 0 : sortNo);

        if (id == null) {
            categoryMapper.insert(row);
        } else if (categoryMapper.updateById(row) == 0) {
            throw new BusinessException("分类不存在");
        }
        return row.getId();
    }

    @Override
    public void deleteCategory(Long id) {
        if (id == null) {
            throw new BusinessException("分类不能为空");
        }
        Long children = categoryMapper.selectCount(
                new LambdaQueryWrapper<ArchiveCategory>().eq(ArchiveCategory::getParentId, id));
        if (children != null && children > 0) {
            throw new BusinessException("该分类下还有子分类，先把子分类删掉或移动");
        }
        Long docs = documentMapper.selectCount(
                new LambdaQueryWrapper<ArchiveDocument>()
                        .eq(ArchiveDocument::getCategoryId, id)
                        .eq(ArchiveDocument::getDeleted, 0));
        if (docs != null && docs > 0) {
            throw new BusinessException("该分类下还有 " + docs + " 条资料，先把它们移走再删分类");
        }
        if (categoryMapper.deleteById(id) == 0) {
            throw new BusinessException("分类不存在");
        }
    }

    // ================= 资料 =================

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long saveDocument(ArchiveDocumentSaveDTO dto, String operator) {
        String name = trimToEmpty(dto.getName());
        if (name.isEmpty()) {
            throw new BusinessException("资料名称不能为空");
        }
        if (dto.getCategoryId() == null) {
            throw new BusinessException("请选择分类");
        }
        if (categoryMapper.selectById(dto.getCategoryId()) == null) {
            throw new BusinessException("分类不存在");
        }
        String fileName = trimToEmpty(dto.getFileName());
        String originPlace = trimToEmpty(dto.getOriginPlace());
        if (fileName.isEmpty() && originPlace.isEmpty()) {
            // 两样都空 = 既没电子件也不知道纸质在哪 ⇒ 这条资料找不回来，等于没归档
            throw new BusinessException("请上传文件，或填写「原件在哪」");
        }

        ArchiveDocument row;
        String previousFile = "";
        if (dto.getId() == null) {
            row = new ArchiveDocument();
            row.setDeleted(0);
            row.setCreatedBy(trimToEmpty(operator));
        } else {
            row = documentMapper.selectById(dto.getId());
            if (row == null || Integer.valueOf(1).equals(row.getDeleted())) {
                throw new BusinessException("资料不存在或已删除");
            }
            previousFile = trimToEmpty(row.getFileName());
        }

        row.setName(name);
        row.setCategoryId(dto.getCategoryId());
        row.setFileName(fileName);
        row.setOrigName(trimToEmpty(dto.getOrigName()));
        row.setFileSize(dto.getFileSize() == null ? 0L : dto.getFileSize());
        row.setMimeType(trimToEmpty(dto.getMimeType()));
        row.setExpireDate(dto.getExpireDate());
        row.setOriginPlace(originPlace);
        row.setDocNo(trimToEmpty(dto.getDocNo()));
        row.setOwner(trimToEmpty(dto.getOwner()));
        row.setRemark(trimToEmpty(dto.getRemark()));

        if (dto.getId() == null) {
            documentMapper.insert(row);
        } else if (documentMapper.updateById(row) == 0) {
            throw new BusinessException("资料不存在");
        }

        // 删旧文件放在写库**之后**：写库失败要整体回滚，先删文件就删掉了仍被引用的对象。
        // 只删"被换掉/被清空"的那一个，且删除失败只记日志 —— 存储抖一下不该让保存整体失败。
        if (!previousFile.isEmpty() && !previousFile.equals(fileName)) {
            deleteStoredFile(previousFile);
        }
        return row.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteDocument(Long id) {
        ArchiveDocument row = documentMapper.selectById(id);
        if (row == null || Integer.valueOf(1).equals(row.getDeleted())) {
            throw new BusinessException("资料不存在或已删除");
        }
        row.setDeleted(1);
        if (documentMapper.updateById(row) == 0) {
            throw new BusinessException("资料不存在");
        }
        // ⚠️ **不删文件**：软删的目的就是"删错了还能找回来"（Q5）。要腾空间再说，不在这里做。
    }

    @Override
    public ArchiveDocument findDocument(Long id) {
        ArchiveDocument row = id == null ? null : documentMapper.selectById(id);
        if (row == null || Integer.valueOf(1).equals(row.getDeleted())) {
            throw new BusinessException("资料不存在或已删除");
        }
        return row;
    }

    // ================= 内部 =================

    /** 分类自身 + 其子类的 id（筛选大类时要把它名下所有资料的算进来） */
    private Set<Long> categoryAndChildrenIds(Long categoryId) {
        Set<Long> ids = new HashSet<>();
        ids.add(categoryId);
        for (ArchiveCategory child : categoryMapper.selectList(
                new LambdaQueryWrapper<ArchiveCategory>().eq(ArchiveCategory::getParentId, categoryId))) {
            ids.add(child.getId());
        }
        return ids;
    }

    private Map<Long, String> categoryNameMap() {
        Map<Long, String> map = new HashMap<>();
        for (ArchiveCategory c : categoryMapper.selectList(null)) {
            map.put(c.getId(), c.getName());
        }
        return map;
    }

    private ArchiveDocumentVO toVO(ArchiveDocument row, String categoryName) {
        ArchiveDocumentVO vo = new ArchiveDocumentVO();
        vo.setId(row.getId());
        vo.setName(row.getName());
        vo.setCategoryId(row.getCategoryId());
        vo.setCategoryName(categoryName == null ? "" : categoryName);
        vo.setFileName(trimToEmpty(row.getFileName()));
        vo.setOrigName(trimToEmpty(row.getOrigName()));
        vo.setFileSize(row.getFileSize() == null ? 0L : row.getFileSize());
        vo.setMimeType(trimToEmpty(row.getMimeType()));
        vo.setExpireDate(row.getExpireDate());
        // 到期天数由后端算，前端不自己算（口径只有一处）
        vo.setDaysToExpire(row.getExpireDate() == null
                ? null
                : ChronoUnit.DAYS.between(LocalDate.now(), row.getExpireDate()));
        vo.setOriginPlace(trimToEmpty(row.getOriginPlace()));
        vo.setDocNo(trimToEmpty(row.getDocNo()));
        vo.setOwner(trimToEmpty(row.getOwner()));
        vo.setRemark(trimToEmpty(row.getRemark()));
        vo.setHasFile(!trimToEmpty(row.getFileName()).isEmpty());
        vo.setCreatedBy(trimToEmpty(row.getCreatedBy()));
        vo.setCreateTime(row.getCreateTime());
        vo.setUpdateTime(row.getUpdateTime());
        return vo;
    }

    /**
     * 删存储侧的旧文件。
     *
     * <p>只记日志、不往上抛：换文件/清空文件时存储侧抖一下，不该让整次保存失败
     * （与 {@code PurchaseTrackingServiceImpl#deleteStoredDeliveryNote} 同一个分寸）。
     * 孤儿文件是可以人工收的，保存失败是使用者立刻能感知的坏事。
     */
    private void deleteStoredFile(String fileName) {
        try {
            fileFeignClient.delete(fileName);
        } catch (Exception e) {
            log.warn("删除资料旧文件失败，已忽略 fileName={}", fileName, e);
        }
    }

    private static String trimToEmpty(String value) {
        return value == null ? "" : value.trim();
    }
}
