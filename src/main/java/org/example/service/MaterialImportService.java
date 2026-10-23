package org.example.service;

import org.example.dto.MaterialImportPreviewVO;
import org.example.dto.MaterialImportResultVO;
import org.springframework.web.multipart.MultipartFile;

/**
 * 物料主数据导入（两阶段，2026-10-23，变更-159）。
 *
 * <p>{@link #preview} 只解析 + 比对，把结果放内存缓存并返回 token；{@link #commit} 凭 token
 * 落库。中间这一步是给人看的 —— 3 万行的导入不能"点一下就写"，得先看清
 * 「新增多少 / 变更多少 / 消失多少」再确认。
 */
public interface MaterialImportService {

    MaterialImportPreviewVO preview(MultipartFile file);

    MaterialImportResultVO commit(String token);
}
