package org.example.constant;

import lombok.Getter;

/**
 * SAP 移动类型 -> 中文名。
 */
@Getter
public enum MovementTypeEnum {
    M261("261", "领料消耗"),
    M262("262", "退料"),
    M101("101", "采购订单收货"),
    M102("102", "采购订单收货冲销"),
    M531("531", "副产品收货"),
    M532("532", "RE副产品");

    private final String code;
    private final String typeName;

    MovementTypeEnum(String code, String typeName) {
        this.code = code;
        this.typeName = typeName;
    }

    // 代码 -> 中文名；未知代码原样返回，null 返回 null
    public static String nameOf(String code) {
        if (code == null) {
            return null;
        }
        String c = code.trim();
        for (MovementTypeEnum e : values()) {
            if (e.code.equals(c)) {
                return e.typeName;
            }
        }
        return c;
    }
}
