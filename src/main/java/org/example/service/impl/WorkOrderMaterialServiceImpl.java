package org.example.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.example.dto.WorkOrderMaterialVO;
import org.example.entity.MaterialMovement;
import org.example.mapper.MaterialMovementMapper;
import org.example.service.WorkOrderMaterialService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 按工单查投料实现（2026-10-31，变更-172）。
 *
 * <h2>为什么用货物移动凭证，而不是领料单</h2>
 * `material_pick` / `material_pick_summary` **没有工单号**（只有单据号/领料日期），
 * 关联不到工单；`material_movement` 3851 行**全部带 order_no**，且有 movement_type，
 * 所以投料口径只能落在这里。
 *
 * <h2>口径</h2>
 * 261 = 投料（库里是**负数**，如 -1835）；262 = 冲销（正数）。
 * 净投料 = −(Σ261 + Σ262)，同物料多笔合并，按物料给明细。
 * 实测样本：工单 1000232951 有 8 笔 261、合计 -9554.8 ⇒ 净投料 9554.8。
 */
@Service
public class WorkOrderMaterialServiceImpl implements WorkOrderMaterialService {

    /** 投料 */
    private static final String TYPE_ISSUE = "261";
    /** 投料冲销 */
    private static final String TYPE_REVERSE = "262";

    @Autowired
    private MaterialMovementMapper materialMovementMapper;

    @Override
    public Map<String, WorkOrderMaterialVO> byOrderNos(Collection<String> orderNos) {
        Map<String, WorkOrderMaterialVO> out = new LinkedHashMap<>();
        Set<String> wanted = normalize(orderNos);
        if (wanted.isEmpty()) {
            return out;
        }

        List<MaterialMovement> rows = materialMovementMapper.selectList(
                new LambdaQueryWrapper<MaterialMovement>()
                        .in(MaterialMovement::getOrderNo, wanted)
                        .in(MaterialMovement::getMovementType, List.of(TYPE_ISSUE, TYPE_REVERSE)));

        // 工单 → 物料编码 → 累加器
        Map<String, Map<String, Acc>> buckets = new LinkedHashMap<>();
        for (MaterialMovement row : rows) {
            String orderNo = trim(row.getOrderNo());
            String code = trim(row.getMaterialCode());
            if (orderNo.isEmpty()) {
                continue;
            }
            Map<String, Acc> byCode = buckets.computeIfAbsent(orderNo, k -> new LinkedHashMap<>());
            Acc acc = byCode.computeIfAbsent(code, k -> new Acc(row.getMaterialDesc(), row.getUnit()));
            BigDecimal qty = row.getQuantity() == null ? BigDecimal.ZERO : row.getQuantity();
            // 261 是负数、262 是正数 ⇒ 直接相加，最后取反得到"净投料"
            acc.raw = acc.raw.add(qty);
            if (TYPE_ISSUE.equals(trim(row.getMovementType()))) {
                acc.issueRows++;
            } else {
                acc.reversed = true;
            }
        }

        for (Map.Entry<String, Map<String, Acc>> e : buckets.entrySet()) {
            WorkOrderMaterialVO vo = new WorkOrderMaterialVO();
            vo.setOrderNo(e.getKey());
            List<WorkOrderMaterialVO.Item> items = new ArrayList<>();
            BigDecimal net = BigDecimal.ZERO;
            String unit = null;
            boolean mixed = false;
            for (Map.Entry<String, Acc> ie : e.getValue().entrySet()) {
                Acc acc = ie.getValue();
                BigDecimal qty = acc.raw.negate(); // -(-1835) = 1835
                WorkOrderMaterialVO.Item item = new WorkOrderMaterialVO.Item();
                item.setMaterialCode(ie.getKey());
                item.setMaterialDesc(acc.desc);
                item.setQty(qty);
                item.setUnit(acc.unit);
                item.setIssueRows(acc.issueRows);
                item.setReversed(acc.reversed);
                items.add(item);

                if (unit == null) {
                    unit = acc.unit;
                } else if (!unit.equals(acc.unit)) {
                    mixed = true;
                }
                if (!mixed) {
                    net = net.add(qty);
                }
            }
            vo.setItems(items);
            vo.setMaterialCount(items.size());
            vo.setMixedUnit(mixed);
            vo.setUnit(mixed ? null : unit);
            vo.setNet(mixed ? null : net);
            out.put(e.getKey(), vo);
        }
        return out;
    }

    @Override
    public List<WorkOrderMaterialVO> list(Collection<String> orderNos) {
        return new ArrayList<>(byOrderNos(orderNos).values());
    }

    private static Set<String> normalize(Collection<String> in) {
        Set<String> out = new LinkedHashSet<>();
        if (in == null) {
            return out;
        }
        for (String s : in) {
            String v = trim(s);
            if (!v.isEmpty()) {
                out.add(v);
            }
        }
        return out;
    }

    private static String trim(String s) {
        return s == null ? "" : s.trim();
    }

    /** 单物料累加器 */
    private static final class Acc {
        private final String desc;
        private final String unit;
        private BigDecimal raw = BigDecimal.ZERO;
        private int issueRows;
        private boolean reversed;

        private Acc(String desc, String unit) {
            this.desc = desc;
            this.unit = unit;
        }
    }
}
