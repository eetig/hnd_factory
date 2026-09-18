package org.example.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.example.constant.MovementTypeEnum;
import org.example.dto.ExcelResult;
import org.example.dto.GoodsMoveExcelDTO;
import org.example.dto.GoodsMoveVO;
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
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class MaterialMovementServiceImpl extends ServiceImpl<MaterialMovementMapper, MaterialMovement> implements MaterialMovementService {

    @Autowired
    private ExcelParseFeign excelParseFeign;

    private static final List<DateTimeFormatter> DATE_FORMATTERS = List.of(
            DateTimeFormatter.ofPattern("yyyy-MM-dd"),
            DateTimeFormatter.ofPattern("yyyy/M/d"),
            DateTimeFormatter.ofPattern("yyyy.M.d"),
            DateTimeFormatter.ofPattern("yyyyMMdd"),
            DateTimeFormatter.ofPattern("M/d/yyyy")
    );

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
    public ExcelResult<GoodsMoveVO> listGoodsMoves() {
        List<MaterialMovement> list = baseMapper.selectList(
                new QueryWrapper<MaterialMovement>().orderByDesc("posting_date").orderByDesc("id"));

        List<GoodsMoveVO> vos = list.stream().map(this::toGoodsMoveVO).collect(Collectors.toList());

        ExcelResult<GoodsMoveVO> result = new ExcelResult<>();
        result.setSuccess(true);
        result.setMsg("操作成功");
        result.setDataList(vos);
        result.setTotalRow(vos.size());
        result.setSuccessRow(vos.size());
        return result;
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

    private String trim(String s) {
        return s == null ? null : s.trim();
    }

    private Integer parseInt(String s) {
        if (!StringUtils.hasText(s)) {
            return null;
        }
        try {
            return Integer.parseInt(s.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private BigDecimal parseDecimal(String s) {
        if (!StringUtils.hasText(s)) {
            return null;
        }
        try {
            return new BigDecimal(s.trim().replace(",", "").replace(" ", ""));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private LocalDate parseDate(String s) {
        if (!StringUtils.hasText(s)) {
            return null;
        }
        String v = s.trim();
        for (DateTimeFormatter f : DATE_FORMATTERS) {
            try {
                return LocalDate.parse(v, f);
            } catch (DateTimeParseException ignored) {
                // try next format
            }
        }
        // 兜底：Excel 日期序列号（如 46202）
        try {
            long serial = (long) Double.parseDouble(v);
            return LocalDate.of(1899, 12, 30).plusDays(serial);
        } catch (NumberFormatException ignored) {
            return null;
        }
    }
}
