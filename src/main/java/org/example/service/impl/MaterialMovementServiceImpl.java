package org.example.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import org.example.constant.MovementTypeEnum;
import org.example.dto.DateRangeQueryDTO;
import org.example.dto.ExcelResult;
import org.example.dto.GoodsMoveExcelDTO;
import org.example.dto.GoodsMoveSummaryVO;
import org.example.dto.GoodsMoveVO;
import org.example.dto.PageQueryDTO;
import org.example.dto.WorkOrderImportSaveVO;
import org.example.entity.MaterialMovement;
import org.example.feign.ExcelParseFeign;
import org.example.mapper.MaterialMovementMapper;
import org.example.service.MaterialMovementService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.example.util.ExcelValueParser.parseDate;
import static org.example.util.ExcelValueParser.parseDecimal;
import static org.example.util.ExcelValueParser.parseInt;
import static org.example.util.ExcelValueParser.trim;

@Service
public class MaterialMovementServiceImpl extends ServiceImpl<MaterialMovementMapper, MaterialMovement> implements MaterialMovementService {

    @Autowired
    private ExcelParseFeign excelParseFeign;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ExcelResult<Void> importGoodsMove(MultipartFile file, String templateCode) {
        // 1. Feign 调用 Excel 解析服务
        ExcelResult<GoodsMoveExcelDTO> parsed = excelParseFeign.parseExcel(file, templateCode);

        ExcelResult<Void> result = new ExcelResult<>();
        result.setSuccess(Boolean.TRUE.equals(parsed.getSuccess()));
        result.setMsg(parsed.getMsg());

        if (!Boolean.TRUE.equals(parsed.getSuccess())
                || parsed.getDataList() == null
                || parsed.getDataList().isEmpty()) {
            result.setTotalRow(parsed.getTotalRow());
            result.setSuccessRow(0);
            result.setErrorMsgList(parsed.getErrorMsgList());
            return result;
        }

        // 2. 逐行映射 + 校验
        List<MaterialMovement> entities = new ArrayList<>();
        List<String> errors = new ArrayList<>(parsed.getErrorMsgList() == null ? List.of() : parsed.getErrorMsgList());
        int rowNum = 0;
        for (GoodsMoveExcelDTO dto : parsed.getDataList()) {
            rowNum++;
            MaterialMovement m = mapToEntity(dto);
            String err = validate(m, rowNum);
            if (err != null) {
                errors.add(err);
                continue;
            }
            entities.add(m);
        }

        // 3. 批量入库
        if (!entities.isEmpty()) {
            saveBatch(entities);
        }

        result.setTotalRow(parsed.getDataList().size());
        result.setSuccessRow(entities.size());
        result.setErrorMsgList(errors);
        return result;
    }

    // ================= 货物移动列表 =================
    @Override
    public IPage<GoodsMoveVO> listPage(PageQueryDTO query) {
        Page<MaterialMovement> page = new Page<>(query.resolvedPageNum(), query.resolvedPageSize());
        return baseMapper.selectPage(page, dateRangeOnly(query)
                        .orderByDesc("posting_date").orderByDesc("id"))
                .convert(this::toGoodsMoveVO);
    }

    @Override
    public List<GoodsMoveSummaryVO> summarizeByMaterial(DateRangeQueryDTO query) {
        // 按 (物料编码 + 来源库位) 分组：派生行要按来源库位单独取数，只按物料汇总就算不出来了
        QueryWrapper<MaterialMovement> wrapper = dateRangeOnly(query)
                .select("material_code", "storage_location", "SUM(quantity) AS move_qty")
                .groupBy("material_code", "storage_location")
                .orderByAsc("material_code").orderByAsc("storage_location");

        return baseMapper.selectMaps(wrapper).stream().map(row -> {
            GoodsMoveSummaryVO vo = new GoodsMoveSummaryVO();
            vo.setMaterialCode(trimToEmpty(row.get("material_code")));
            vo.setFromLocation(trimToEmpty(row.get("storage_location")));
            // SUM 在「该组所有 quantity 都是 NULL」时回 NULL，不是 0
            Object qty = row.get("move_qty");
            // 保留符号：SAP 原样，正数入库、负数出库/消耗。
            // 改造前前端是拿到明细后自己 Math.abs 的，取绝对值那一步留在前端（口径在那边）
            vo.setMoveQty(qty == null ? BigDecimal.ZERO : new BigDecimal(qty.toString()));
            return vo;
        }).collect(Collectors.toList());
    }

    /**
     * 只带日期区间（按**过账日期**）的 wrapper。
     *
     * <p>用 {@code ge}/{@code le} 而不是字符串比较：DATE 列走原生比较，且顺带把
     * {@code posting_date} 为 NULL 的行排除掉 —— 与改造前前端
     * {@code moveDate ? 区间判断 : true}（无日期字段的行放行）**不同**。
     * 那条「字段缺失就不过滤」的兜底是为了容忍后端没返回该字段，
     * 现在字段由本方法自己过滤、一定存在，兜底只会让没有过账日期的脏行混进结果。
     */
    private QueryWrapper<MaterialMovement> dateRangeOnly(DateRangeQueryDTO query) {
        QueryWrapper<MaterialMovement> wrapper = new QueryWrapper<>();
        LocalDate start = query.parsedStartDate();
        LocalDate end = query.parsedEndDate();
        if (start != null) {
            wrapper.ge("posting_date", start);
        }
        if (end != null) {
            wrapper.le("posting_date", end);
        }
        return wrapper;
    }

    private String trimToEmpty(Object value) {
        return value == null ? "" : value.toString().trim();
    }

    private GoodsMoveVO toGoodsMoveVO(MaterialMovement m) {
        GoodsMoveVO vo = new GoodsMoveVO();
        vo.setMoveNo(m.getId());                                      // 移动单号 -> 主键 id
        vo.setMaterialCode(m.getMaterialCode());                      // 货物编码
        vo.setMaterialDesc(m.getMaterialDesc());                      // 货物名称
        vo.setMoveType(MovementTypeEnum.nameOf(m.getMovementType())); // 移动类型 -> 中文名
        vo.setMoveQty(m.getQuantity());                               // 移动数量
        vo.setFromLocation(m.getStorageLocation());                   // 来源库位 -> 存储地点
        vo.setToLocation(null);                                       // 目标库位：表无此概念
        vo.setMoveDate(m.getPostingDate());                           // 移动日期
        return vo;
    }

    // ================= 批量保存导入的货物移动数据 =================
    /**
     * 按【订单号】整体替换：先删除本批订单下已有的全部货物移动记录，再写入新数据。
     * 同一订单在文件中出现多行时，这些行都会写入（它们属于同一订单的多个物料明细）。
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public WorkOrderImportSaveVO saveImported(List<MaterialMovement> list) {
        WorkOrderImportSaveVO vo = new WorkOrderImportSaveVO();
        if (list == null || list.isEmpty()) {
            return vo;
        }

        Set<String> orderNos = list.stream()
                .map(MaterialMovement::getOrderNo)
                .filter(StringUtils::hasText)
                .collect(Collectors.toSet());

        int deleted = 0;
        if (!orderNos.isEmpty()) {
            deleted = baseMapper.delete(new QueryWrapper<MaterialMovement>().in("order_no", orderNos));
        }
        saveBatch(list);

        vo.setInsertCount(list.size());
        vo.setUpdateCount(deleted);
        vo.setTotal(list.size());
        return vo;
    }

    private MaterialMovement mapToEntity(GoodsMoveExcelDTO dto) {
        MaterialMovement m = new MaterialMovement();
        m.setOrderNo(trim(dto.getOrderNo()));                 // 订单
        m.setMaterialCode(trim(dto.getMaterialCode()));       // 物料
        m.setMaterialDesc(trim(dto.getMaterialDesc()));       // 物料描述
        m.setMaterialDocItem(parseInt(dto.getItem()));        // 项目 -> 物料文档项目号
        m.setBatchNo(trim(dto.getBatchNo()));                 // 批次
        m.setStorageLocation(trim(dto.getStorageLocation())); // 存储地点
        m.setUnit(trim(dto.getUnit()));                       // 基本计量单位
        m.setMovementType(trim(dto.getMovementType()));       // 移动类型
        m.setMaterialDoc(trim(dto.getMaterialDoc()));         // 物料凭证
        m.setCreditFlag(trim(dto.getCreditFlag()));           // 借/贷标识
        m.setQuantity(parseDecimal(dto.getQuantity()));       // 数量
        m.setPostingDate(parseDate(dto.getPostingDate()));    // 过账日期
        // 注意：movement_item(货物移动项目行号) 无对应列，留空
        return m;
    }

    private String validate(MaterialMovement m, int row) {
        if (!StringUtils.hasText(m.getOrderNo())) {
            return "第" + row + "行：订单号(order_no)为空";
        }
        if (!StringUtils.hasText(m.getMaterialCode())) {
            return "第" + row + "行：物料编码(material_code)为空";
        }
        if (m.getQuantity() == null) {
            return "第" + row + "行：数量无法解析";
        }
        return null;
    }

}
