package org.example.dto;

import lombok.Data;
import java.util.HashMap;
import java.util.Map;

/**
 * 镜像 excel-import-service 的 GoodsMoveExcelDTO，字段名必须与其保持一致，
 * 否则 Feign 反序列化时 dataList 无法正确映射。
 */
@Data
public class GoodsMoveExcelDTO {
    private String orderNo;          // 订单          -> order_no
    private String materialCode;     // 物料          -> material_code
    private String movementFlag;     // 移动标识
    private String item;             // 项目          -> material_doc_item
    private String materialDesc;     // 物料描述      -> material_desc
    private String entryQuantity;    // 以录入单位表示的数量
    private String batchNo;          // 批次          -> batch_no
    private String storageLocation;  // 存储地点      -> storage_location
    private String unit;             // 基本计量单位  -> unit
    private String movementType;     // 移动类型      -> movement_type
    private String materialDoc;      // 物料凭证      -> material_doc
    private String creditFlag;       // 借/贷标识     -> credit_flag
    private String quantity;         // 数量          -> quantity
    private String postingDate;      // 过账日期      -> posting_date

    // 用来存放 WPS 表格里额外新增的动态扩展列
    private Map<String, Object> extMap = new HashMap<>();
}
