package org.example.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;
import org.example.entity.MaterialMaster;

import java.util.List;
import java.util.Map;

/**
 * 物料主数据 Mapper。
 *
 * <p>字典与计数这几条用 {@code @Select} 直接写：它们都是「4–10 行的字典表」或
 * {@code GROUP BY} 聚合，套 Wrapper 只会更绕；本工程其余单表查询仍走 Wrapper。
 *
 * <p>⚠️ 字典查询**不加缓存**：一共 4 条小查询（合计 &lt;0.5ms），换取的是"改了字典立刻生效"。
 * 真要缓存，得在写字典的地方一起失效，否则会出现"库里改了、页面上还是旧名"。
 */
@Mapper
public interface MaterialMasterMapper extends BaseMapper<MaterialMaster> {

    @Select("SELECT type_code AS code, type_name AS label, color FROM material_type_dict ORDER BY sort_no")
    List<Map<String, Object>> selectTypeDict();

    @Select("SELECT group_code AS code, group_name AS label FROM procurement_group_dict ORDER BY sort_no")
    List<Map<String, Object>> selectProcurementDict();

    @Select("SELECT mrp_code AS code, mrp_label AS label FROM mrp_type_dict ORDER BY sort_no")
    List<Map<String, Object>> selectMrpDict();

    @Select("SELECT valuation_type AS code, label, meaning, sort_no AS sortNo FROM material_valuation_type_dict ORDER BY sort_no")
    List<Map<String, Object>> selectValuationDict();

    /** 物料类型分布（一个物料一行：valuation_type='' 才是主档行，且默认不含停用） */
    @Select("SELECT material_type AS code, COUNT(*) AS cnt FROM material_master "
            + "WHERE valuation_type = '' AND status <> 0 GROUP BY material_type")
    List<Map<String, Object>> countMaterialByType();

    /** 状态分布（含停用：左栏要显示「停用 5390」） */
    @Select("SELECT status AS code, COUNT(*) AS cnt FROM material_master "
            + "WHERE valuation_type = '' GROUP BY status")
    List<Map<String, Object>> countMaterialByStatus();
}
