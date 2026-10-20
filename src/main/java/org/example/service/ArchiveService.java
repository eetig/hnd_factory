package org.example.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import org.example.dto.ArchiveCategoryVO;
import org.example.dto.ArchiveDocumentQueryDTO;
import org.example.dto.ArchiveDocumentSaveDTO;
import org.example.dto.ArchiveDocumentVO;
import org.example.entity.ArchiveDocument;

import java.util.List;

/**
 * 资料归档（变更-126）。
 *
 * <p>一句话：把化工厂散落的资料收进一张台账，做到「检查时找得到、到期能提醒」。
 * 设计决策见 {@code docs/资料归档-开工方案.md}（已定 6 条），这里只实现，不再讨论。
 *
 * <p>权限口径（Q5）：**全员可看可下载**，增/改/删要 {@code archive:edit}（只授 admin），
 * 删除是软删、文件不物理删。所以本服务里没有"只给某人看"的过滤 —— 那是刻意的。
 */
public interface ArchiveService {

    /** 分页列表：关键字（名称/编号/责任人/原件位置）+ 分类（含子类）+ N 天内到期 */
    IPage<ArchiveDocumentVO> listPage(ArchiveDocumentQueryDTO query);

    /** 分类树（两级），每个节点带"该分类下未删除资料数" */
    List<ArchiveCategoryVO> categoryTree();

    /**
     * 保存（新增或修改）。
     *
     * <p>必填：名称、分类；且「电子件」与「原件位置」至少填一个 —— 两个都空等于这条资料
     * 既没有文件也不知道纸质在哪，等于没归档。
     *
     * @return 落库后的主键
     */
    Long saveDocument(ArchiveDocumentSaveDTO dto, String operator);

    /** 删除一条（软删；文件保留 —— 软删就是为了"删错了还能找回来"） */
    void deleteDocument(Long id);

    /** 新增或改名分类（只支持两级：给 parentId 时，父必须是一个大类） */
    Long saveCategory(Long id, Long parentId, String name, Integer sortNo);

    /** 删分类：有子类或有资料时拒绝（否则会出现"孤儿资料"） */
    void deleteCategory(Long id);

    /** 取一条实体（下载时控制器要用 fileName/origName/mimeType） */
    ArchiveDocument findDocument(Long id);
}
