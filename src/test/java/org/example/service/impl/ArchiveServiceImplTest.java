package org.example.service.impl;

import org.example.dto.ArchiveDocumentQueryDTO;
import org.example.dto.ArchiveDocumentSaveDTO;
import org.example.entity.ArchiveCategory;
import org.example.entity.ArchiveDocument;
import org.example.exception.BusinessException;
import org.example.feign.FileFeignClient;
import org.example.mapper.ArchiveCategoryMapper;
import org.example.mapper.ArchiveDocumentMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.util.ReflectionTestUtils.setField;

/**
 * {@link ArchiveServiceImpl} 的行为测试（变更-126）。
 *
 * <p>钉的是四条容易"能编译、能过眼、运行时才错"的不变式：
 * <ol>
 *   <li><b>成对删旧文件，且在写库之后</b> —— 用 {@link InOrder} 断言
 *       {@code updateById} 先于 {@code fileFeignClient.delete}；顺序反了，写库失败时
 *       就删掉了一个仍被引用的对象。</li>
 *   <li><b>软删不删文件</b> —— 删记录只置 {@code deleted=1}，文件必须留着（Q5 的"删错了还能找回来"）。</li>
 *   <li><b>"文件或原件位置"至少填一个</b> —— 两个都空的记录等于没归档。</li>
 *   <li><b>分类删前校验</b> —— 有子类或有资料时拒绝，避免出现孤儿资料。</li>
 * </ol>
 */
class ArchiveServiceImplTest {

    private ArchiveDocumentMapper documentMapper;
    private ArchiveCategoryMapper categoryMapper;
    private FileFeignClient fileFeignClient;
    private ArchiveServiceImpl service;

    @BeforeEach
    void setUp() {
        documentMapper = mock(ArchiveDocumentMapper.class);
        categoryMapper = mock(ArchiveCategoryMapper.class);
        fileFeignClient = mock(FileFeignClient.class);
        service = new ArchiveServiceImpl();
        setField(service, "documentMapper", documentMapper);
        setField(service, "categoryMapper", categoryMapper);
        setField(service, "fileFeignClient", fileFeignClient);
    }

    // ================= 保存：校验 =================

    @Test
    @DisplayName("名称为空 → 拒绝，且不碰任何 Mapper")
    void rejectsBlankName() {
        ArchiveDocumentSaveDTO dto = dto("   ", 1L, "a.pdf", "");
        assertThrows(BusinessException.class, () -> service.saveDocument(dto, "eetig"));
        verifyNoInteractions(documentMapper, categoryMapper, fileFeignClient);
    }

    @Test
    @DisplayName("没选分类 / 分类不存在 → 拒绝")
    void rejectsMissingCategory() {
        ArchiveDocumentSaveDTO noCategory = dto("安全许可证", null, "a.pdf", "");
        assertThrows(BusinessException.class, () -> service.saveDocument(noCategory, "eetig"));

        ArchiveDocumentSaveDTO ghost = dto("安全许可证", 99L, "a.pdf", "");
        when(categoryMapper.selectById(99L)).thenReturn(null);
        assertThrows(BusinessException.class, () -> service.saveDocument(ghost, "eetig"));
        verifyNoInteractions(documentMapper);
    }

    @Test
    @DisplayName("文件与原件位置都空 → 拒绝（跨字段规则，注解表达不了）")
    void rejectsNeitherFileNorPaper() {
        when(categoryMapper.selectById(1L)).thenReturn(category(1L, null));
        ArchiveDocumentSaveDTO dto = dto("安全许可证", 1L, "  ", "  ");
        BusinessException e = assertThrows(BusinessException.class, () -> service.saveDocument(dto, "eetig"));
        assertTrue(e.getMessage().contains("原件"));
        verifyNoInteractions(documentMapper);
    }

    // ================= 保存：成对操作 =================

    @Test
    @DisplayName("新增：落库 + 记录入人；不该去删任何文件")
    void createsDocument() {
        when(categoryMapper.selectById(2L)).thenReturn(category(2L, 1L));
        when(documentMapper.insert(any(ArchiveDocument.class))).thenAnswer(inv -> {
            ArchiveDocument row = inv.getArgument(0);
            assertNull(row.getId());
            assertEquals("安全许可证.pdf", row.getOrigName());
            assertEquals("eetig", row.getCreatedBy());
            assertEquals(0, row.getDeleted());
            row.setId(7L);
            return 1;
        });

        Long id = service.saveDocument(dto("安全许可证", 2L, "abc.pdf", ""), "eetig");

        assertEquals(7L, id);
        verify(fileFeignClient, never()).delete(any());
    }

    @Test
    @DisplayName("修改但文件没变：不删文件")
    void keepsFileWhenUnchanged() {
        when(categoryMapper.selectById(2L)).thenReturn(category(2L, 1L));
        when(documentMapper.selectById(9L)).thenReturn(existing(9L, "abc.pdf"));
        when(documentMapper.updateById(any(ArchiveDocument.class))).thenReturn(1);

        ArchiveDocumentSaveDTO dto = dto("安全许可证", 2L, "abc.pdf", "");
        dto.setId(9L);
        service.saveDocument(dto, "eetig");

        verify(fileFeignClient, never()).delete(any());
    }

    @Test
    @DisplayName("换文件：旧文件要删，且**必须排在写库之后**（InOrder 断言）")
    void deletesReplacedFileAfterWrite() {
        when(categoryMapper.selectById(2L)).thenReturn(category(2L, 1L));
        when(documentMapper.selectById(9L)).thenReturn(existing(9L, "old.pdf"));
        when(documentMapper.updateById(any(ArchiveDocument.class))).thenReturn(1);

        ArchiveDocumentSaveDTO dto = dto("安全许可证", 2L, "new.pdf", "");
        dto.setId(9L);
        service.saveDocument(dto, "eetig");

        InOrder order = inOrder(documentMapper, fileFeignClient);
        order.verify(documentMapper).updateById(any(ArchiveDocument.class));
        order.verify(fileFeignClient).delete("old.pdf");
    }

    @Test
    @DisplayName("清空文件（改成只剩纸质）：旧文件要删")
    void deletesFileWhenCleared() {
        when(categoryMapper.selectById(2L)).thenReturn(category(2L, 1L));
        when(documentMapper.selectById(9L)).thenReturn(existing(9L, "old.pdf"));
        when(documentMapper.updateById(any(ArchiveDocument.class))).thenReturn(1);

        ArchiveDocumentSaveDTO dto = dto("安全许可证", 2L, "", "办公楼 3 层档案柜");
        dto.setId(9L);
        service.saveDocument(dto, "eetig");

        verify(fileFeignClient).delete("old.pdf");
    }

    @Test
    @DisplayName("删存储侧失败不许连累保存（只记日志）")
    void toleratesStorageFailureOnReplace() {
        when(categoryMapper.selectById(2L)).thenReturn(category(2L, 1L));
        when(documentMapper.selectById(9L)).thenReturn(existing(9L, "old.pdf"));
        when(documentMapper.updateById(any(ArchiveDocument.class))).thenReturn(1);
        when(fileFeignClient.delete("old.pdf")).thenThrow(new RuntimeException("storage down"));

        ArchiveDocumentSaveDTO dto = dto("安全许可证", 2L, "new.pdf", "");
        dto.setId(9L);
        assertEquals(9L, service.saveDocument(dto, "eetig"));
    }

    // ================= 删除 / 分类 =================

    @Test
    @DisplayName("删资料：软删，且**绝不删文件**（Q5：删错了还能找回来）")
    void deleteSoftDeletesAndKeepsFile() {
        when(documentMapper.selectById(9L)).thenReturn(existing(9L, "abc.pdf"));
        when(documentMapper.updateById(any(ArchiveDocument.class))).thenReturn(1);

        service.deleteDocument(9L);

        verify(documentMapper).updateById(any(ArchiveDocument.class));
        verify(fileFeignClient, never()).delete(any());
    }

    @Test
    @DisplayName("删分类：有子类 → 拒绝")
    void refusesCategoryWithChildren() {
        when(categoryMapper.selectCount(any())).thenReturn(2L);
        assertThrows(BusinessException.class, () -> service.deleteCategory(5L));
        verify(categoryMapper, never()).deleteById(any(Long.class));
    }

    @Test
    @DisplayName("删分类：还有资料 → 拒绝")
    void refusesCategoryWithDocuments() {
        when(categoryMapper.selectCount(any())).thenReturn(0L);
        when(documentMapper.selectCount(any())).thenReturn(3L);
        BusinessException e = assertThrows(BusinessException.class, () -> service.deleteCategory(5L));
        assertTrue(e.getMessage().contains("3"));
        verify(categoryMapper, never()).deleteById(any(Long.class));
    }

    @Test
    @DisplayName("分类只支持两级：不能挂到子分类下面")
    void refusesThirdLevel() {
        when(categoryMapper.selectById(8L)).thenReturn(category(8L, 1L)); // 8 自己已经是子类
        assertThrows(BusinessException.class, () -> service.saveCategory(null, 8L, "三级", 0));
    }

    // ================= 列表 =================

    @Test
    @DisplayName("列表：VO 的派生列算对（categoryName / hasFile / daysToExpire）")
    void listMapsToVoWithDerivedFields() {
        ArchiveDocument withFile = existing(9L, "abc.pdf");
        withFile.setExpireDate(LocalDate.now().plusDays(10));
        ArchiveDocument paperOnly = existing(10L, "");
        paperOnly.setExpireDate(null);

        var page = new com.baomidou.mybatisplus.extension.plugins.pagination.Page<ArchiveDocument>(1, 10, 2);
        page.setRecords(List.of(withFile, paperOnly));
        when(documentMapper.selectPage(any(), any())).thenReturn(page);
        when(categoryMapper.selectList(any())).thenReturn(List.of(category(2L, 1L)));

        ArchiveDocumentQueryDTO query = new ArchiveDocumentQueryDTO();
        query.setPageNum(1);
        query.setPageSize(10);
        var result = service.listPage(query);

        assertEquals(2, result.getRecords().size());
        var first = result.getRecords().get(0);
        assertEquals("分类2", first.getCategoryName(), "分类名要补上（不做 JOIN，用全量分类表补）");
        assertTrue(first.getHasFile(), "有电子件");
        assertEquals(10L, first.getDaysToExpire(), "距有效期天数由后端算");

        var second = result.getRecords().get(1);
        assertFalse(second.getHasFile(), "没有电子件");
        assertNull(second.getDaysToExpire(), "无有效期 → null（不是 0，也不是很大的负数）");

        // ⚠️ 未验：SQL 里 OR 四列**是否被整体括起来**（变更-093 那个 "deleted=0 OR (...)" 漏软删的坑）。
        //    窥探 LambdaQueryWrapper 的内部拿不到稳定结果（第一版试过，参数类型与预期不符），
        //    这件事只能靠真实 SQL，留给部署后用一条带关键字的查询核对（开工方案第 5 步）。
    }

    // ================= 夹具 =================

    private static ArchiveDocumentSaveDTO dto(String name, Long categoryId, String fileName, String originPlace) {
        ArchiveDocumentSaveDTO dto = new ArchiveDocumentSaveDTO();
        dto.setName(name);
        dto.setCategoryId(categoryId);
        dto.setFileName(fileName);
        dto.setOriginPlace(originPlace);
        dto.setOrigName("安全许可证.pdf");
        dto.setFileSize(1024L);
        dto.setMimeType("application/pdf");
        dto.setExpireDate(LocalDate.now().plusDays(30));
        return dto;
    }

    private static ArchiveCategory category(Long id, Long parentId) {
        ArchiveCategory c = new ArchiveCategory();
        c.setId(id);
        c.setParentId(parentId);
        c.setName("分类" + id);
        c.setSortNo(0);
        return c;
    }

    private static ArchiveDocument existing(Long id, String fileName) {
        ArchiveDocument row = new ArchiveDocument();
        row.setId(id);
        row.setName("旧名字");
        row.setCategoryId(2L);
        row.setFileName(fileName);
        row.setDeleted(0);
        row.setCreatedBy("someone");
        return row;
    }
}
