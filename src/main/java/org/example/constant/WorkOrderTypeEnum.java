package org.example.constant;

import lombok.Getter;

/**
 * 工单类型：根据工单号前缀自动识别。
 */
@Getter
public enum WorkOrderTypeEnum {
    OPERATE("1000", "operate", "操作工单"),
    PACKAGE("2000", "package", "包装工单"),
    TRANSFER("3000", "transfer", "转桶工单"),
    REWORK("4000", "rework", "返工工单");

    private final String prefix;    // 工单号前缀
    private final String typeKey;   // 前端类型标识
    private final String typeName;  // 类型中文名

    WorkOrderTypeEnum(String prefix, String typeKey, String typeName) {
        this.prefix = prefix;
        this.typeKey = typeKey;
        this.typeName = typeName;
    }

    // 根据工单号前缀识别类型，识别不出返回 null
    public static WorkOrderTypeEnum detect(String orderNo) {
        if (orderNo == null) {
            return null;
        }
        String no = orderNo.trim();
        for (WorkOrderTypeEnum t : values()) {
            if (no.startsWith(t.prefix)) {
                return t;
            }
        }
        return null;
    }

    // 前端类型标识 -> 工单号前缀（operate/package/transfer/rework）
    public static String prefixOf(String typeKey) {
        if (typeKey == null) {
            return null;
        }
        String key = typeKey.trim();
        for (WorkOrderTypeEnum t : values()) {
            if (t.typeKey.equals(key)) {
                return t.prefix;
            }
        }
        return null;
    }
}
