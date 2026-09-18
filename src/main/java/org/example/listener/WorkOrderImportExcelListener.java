package org.example.listener;

import com.alibaba.excel.context.AnalysisContext;
import com.alibaba.excel.event.AnalysisEventListener;
import lombok.Getter;
import org.example.dto.WorkOrderExcelDTO;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 工单 Excel 解析监听器：动态表头，按表头名映射到 WorkOrderExcelDTO。
 */
@Getter
public class WorkOrderImportExcelListener extends AnalysisEventListener<Map<Integer, String>> {

    private List<String> headerNameList = new ArrayList<>();
    private final List<WorkOrderExcelDTO> dataList = new ArrayList<>();
    private final List<String> errorMsgList = new ArrayList<>();

    @Override
    public void invokeHeadMap(Map<Integer, String> headMap, AnalysisContext context) {
        headerNameList = new ArrayList<>(headMap.values());
    }

    @Override
    public void invoke(Map<Integer, String> rowMap, AnalysisContext context) {
        WorkOrderExcelDTO dto = new WorkOrderExcelDTO();
        Map<String, Object> extMap = dto.getExtMap();

        for (int i = 0; i < headerNameList.size(); i++) {
            String header = normalize(headerNameList.get(i));
            String cellValue = rowMap.getOrDefault(i, "");
            switch (header) {
                case "单据类型": dto.setDocType(cellValue); break;
                case "储罐号": dto.setStorageTank(cellValue); break;
                case "工厂": dto.setPlantCode(cellValue); break;
                case "订单": dto.setOrderNo(cellValue); break;
                case "物料": dto.setMaterialCode(cellValue); break;
                case "班组名称": dto.setTeamName(cellValue); break;
                case "客户牌号": dto.setCustomerBrand(cellValue); break;
                case "物料描述": dto.setMaterialDesc(cellValue); break;
                case "订单类型": dto.setOrderType(cellValue); break;
                case "MRP控制员": dto.setMrpController(cellValue); break;
                case "生产主管": dto.setProducerCount(cellValue); break;
                case "批次": dto.setBatchNo(cellValue); break;
                case "订单数量(GMEIN)": dto.setOrderQty(cellValue); break;
                case "计量单位(=GMEIN)": dto.setUnit(cellValue); break;
                case "生产版本": dto.setProdVersion(cellValue); break;
                case "基本开始日期": dto.setPlanStartDate(cellValue); break;
                case "基本完成日期": dto.setPlanFinishDate(cellValue); break;
                case "确认的产量(GMEIN)": dto.setConfirmedQty(cellValue); break;
                case "最后更改人": dto.setLastChangedBy(cellValue); break;
                case "系统状态": dto.setSysStatus(cellValue); break;
                case "确认产量(CONF_UNIT)": dto.setConfQty(cellValue); break;
                case "已交货数量(GMEIN)": dto.setDeliveredQty(cellValue); break;
                case "实际完成日期": dto.setActualFinishDate(cellValue); break;
                case "实际完成时间": dto.setActualFinishTime(cellValue); break;
                case "储罐名称": dto.setStorageTankName(cellValue); break;
                case "更改日期": dto.setChangeDate(cellValue); break;
                case "更改时间": dto.setChangeTime(cellValue); break;
                case "每桶重量(AMEIN)": dto.setPerBarrelWeight(cellValue); break;
                case "移动类型": dto.setMoveType(cellValue); break;
                default:
                    // 不在固定字段里的，全部放进扩展Map
                    extMap.put(header, cellValue);
                    break;
            }
        }
        dataList.add(dto);
    }

    @Override
    public void doAfterAllAnalysed(AnalysisContext context) {
        // 解析结束回调
    }

    // 去空白 + 全角括号转半角，避免表头多空格/全角括号导致匹配失败
    private String normalize(String s) {
        if (s == null) return "";
        StringBuilder sb = new StringBuilder();
        for (char c : s.toCharArray()) {
            if (c == '　' || Character.isWhitespace(c)) continue;
            if (c == '（') { sb.append('('); continue; }
            if (c == '）') { sb.append(')'); continue; }
            sb.append(c);
        }
        return sb.toString();
    }
}
