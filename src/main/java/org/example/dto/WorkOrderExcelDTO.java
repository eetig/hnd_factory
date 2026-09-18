package org.example.dto;

import lombok.Data;
import java.util.HashMap;
import java.util.Map;

/**
 * 镜像 excel-import-service 的 WorkOrderExcelDTO，字段名必须与其保持一致，
 * 否则 Feign 反序列化时 dataList 无法正确映射。
 */
@Data
public class WorkOrderExcelDTO {
    private String docType;          // 单据类型（不落库）
    private String storageTank;      // 储罐号
    private String plantCode;        // 工厂
    private String orderNo;          // 订单
    private String materialCode;     // 物料
    private String teamName;         // 班组名称
    private String customerBrand;    // 客户牌号
    private String materialDesc;     // 物料描述
    private String orderType;        // 订单类型
    private String mrpController;    // MRP控制员
    private String producerCount;    // 生产主管
    private String batchNo;          // 批次
    private String orderQty;         // 订单数量
    private String unit;             // 计量单位
    private String prodVersion;      // 生产版本
    private String planStartDate;    // 基本开始日期
    private String planFinishDate;   // 基本完成日期
    private String confirmedQty;     // 确认的产量
    private String lastChangedBy;    // 最后更改人
    private String sysStatus;        // 系统状态
    private String confQty;          // 确认产量(CONF_UNIT)
    private String deliveredQty;     // 已交货数量
    private String actualFinishDate; // 实际完成日期
    private String actualFinishTime; // 实际完成时间（不落库）
    private String storageTankName;  // 储罐名称
    private String changeDate;       // 更改日期
    private String changeTime;       // 更改时间
    private String perBarrelWeight;  // 每桶重量
    private String moveType;         // 移动类型（货物移动文件才有，用于识别单据大类）

    private Map<String, Object> extMap = new HashMap<>();
}
