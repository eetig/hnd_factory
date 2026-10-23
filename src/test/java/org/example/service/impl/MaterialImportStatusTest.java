package org.example.service.impl;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * 物料导入里的「状态推导」（2026-10-23，变更-159）。
 *
 * <p>SAP 导出没有状态列，停用只能从名称里读。这条规则错一次，全量导入就会把
 * 停用的物料重新"复活"（或者把正常物料停掉），所以单列出来钉住：
 * <ul>
 *   <li>「待禁用」优先于「禁用」判断 —— 它含「禁用」两个字，顺序反了会被判成停用；</li>
 *   <li>括号形（「（禁用）」「（已禁用，请用…）」）与「作废」「勿用」都算停用；</li>
 *   <li>只是名字里恰好含这些字样的（如「停用申请单」）按停用处理是<b>有意</b>的：
 *       实测 211 行括号形停用名都是这么标出来的，宁可多停不可漏停。</li>
 * </ul>
 */
class MaterialImportStatusTest {

    @Test
    @DisplayName("待禁用 → 2；精确禁用/作废、含禁用/作废/勿用/停用 → 0")
    void derivesStatusFromName() {
        assertEquals(2, MaterialImportServiceImpl.statusOf("配电柜_待禁用"));
        assertEquals(0, MaterialImportServiceImpl.statusOf("禁用"));
        assertEquals(0, MaterialImportServiceImpl.statusOf("作废"));
        assertEquals(0, MaterialImportServiceImpl.statusOf("ZS-8738_50KG蓝方桶（无logo）（禁用）"));
        assertEquals(0, MaterialImportServiceImpl.statusOf("SY175T（已禁用，请用111000049）"));
        assertEquals(0, MaterialImportServiceImpl.statusOf("1000KG_白色_方型_胶罐_新罐（勿用）"));
        assertEquals(1, MaterialImportServiceImpl.statusOf("1T罐"));
        assertEquals(1, MaterialImportServiceImpl.statusOf(null));
    }
}
