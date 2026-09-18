package org.example.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import org.example.dto.ExcelResult;
import org.example.dto.V150ProductionVO;
import org.example.entity.MaterialMovement;
import org.example.entity.WorkOrder;
import org.example.mapper.MaterialMovementMapper;
import org.example.mapper.WorkOrderMapper;
import org.example.service.V150Service;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class V150ServiceImpl implements V150Service {

    @Autowired
    private WorkOrderMapper workOrderMapper;

    @Autowired
    private MaterialMovementMapper materialMovementMapper;

    // 报工相关移动类型：261/262 投料、101/102/531/532 收货
    private static final List<String> MOVE_TYPES = List.of("261", "262", "101", "102", "531", "532");

    @Override
    public ExcelResult<V150ProductionVO> getV150Production(LocalDate startDate, LocalDate endDate) {
        // 默认本月1号 ~ 今天
        LocalDate start = startDate != null ? startDate : LocalDate.now().withDayOfMonth(1);
        LocalDate end = endDate != null ? endDate : LocalDate.now();

        // 1. 查 V150 工单（产品=V150 + 开始日期在范围内），按工单开始日期倒序
        QueryWrapper<WorkOrder> woWrapper = new QueryWrapper<>();
        woWrapper.like("material_desc", "V150");
        woWrapper.ge("plan_start_date", start);
        woWrapper.le("plan_start_date", end);
        woWrapper.orderByDesc("plan_start_date");
        List<WorkOrder> workOrders = workOrderMapper.selectList(woWrapper);

        if (workOrders.isEmpty()) {
            return buildResult(List.of());
        }

        // 2. 查这些工单的物料流水
        List<String> orderNos = workOrders.stream().map(WorkOrder::getOrderNo).collect(Collectors.toList());
        QueryWrapper<MaterialMovement> mvWrapper = new QueryWrapper<>();
        mvWrapper.in("order_no", orderNos);
        mvWrapper.in("movement_type", MOVE_TYPES);
        List<MaterialMovement> movements = materialMovementMapper.selectList(mvWrapper);

        // 3. 先按【工单号、物料名称】分组求和
        Map<String, Map<String, BigDecimal>> qtyByOrderAndMaterial = movements.stream()
                .collect(Collectors.groupingBy(
                        MaterialMovement::getOrderNo,
                        Collectors.groupingBy(
                                MaterialMovement::getMaterialDesc,
                                Collectors.reducing(BigDecimal.ZERO, MaterialMovement::getQuantity, BigDecimal::add)
                        )
                ));

        // 4. 再按工单号分组，组装成 VO 里的 materialMap
        List<V150ProductionVO> vos = workOrders.stream().map(wo -> {
            V150ProductionVO vo = new V150ProductionVO();
            vo.setWorkStartDate(wo.getPlanStartDate());
            vo.setWorkOrderNo(wo.getOrderNo());
            vo.setMaterialMap(qtyByOrderAndMaterial.getOrDefault(wo.getOrderNo(), Map.of()));
            return vo;
        }).collect(Collectors.toList());

        return buildResult(vos);
    }

    private ExcelResult<V150ProductionVO> buildResult(List<V150ProductionVO> list) {
        ExcelResult<V150ProductionVO> result = new ExcelResult<>();
        result.setSuccess(true);
        result.setMsg("查询成功");
        result.setDataList(list);
        result.setTotalRow(list.size());
        return result;
    }
}
