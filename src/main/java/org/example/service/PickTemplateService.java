package org.example.service;

import org.example.dto.PickExportDTO;
import org.example.dto.PickTemplateVO;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;

/**
 * 领料解析：SAP 模板管理 + 按模板导出 Excel。
 *
 * <p>这是「图片解析」的下一步：图片解析只把单据读进我们自己的库；
 * 领料解析的终点是**一份能直接粘进 SAP 的 Excel**，所以模板必须来自 SAP 自己导出的文件，
 * 而不是我们凭空生成表头 —— 列名差一个字，粘贴就会错列。
 */
public interface PickTemplateService {

    /** 列表：不给 dept 就全给（前端按部门筛候选模板） */
    List<PickTemplateVO> list(String dept);

    /**
     * 上传一张 SAP 导出的模板：读表头自动认列，认不出来就抛 {@code BusinessException}。
     *
     * @param file    xlsx
     * @param name    模板名
     * @param docType 单据类型
     * @param dept    适用部门（可空）
     */
    PickTemplateVO upload(MultipartFile file, String name, String docType, String dept) throws IOException;

    void delete(Long id);

    /** 按模板生成可粘进 SAP 的 xlsx（一部门一 sheet，单与单之间空一行） */
    byte[] export(PickExportDTO dto);
}
