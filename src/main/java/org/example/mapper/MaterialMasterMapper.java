package org.example.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;
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
    /**
     * 批量插入/更新（2026-10-23，变更-159 导入用）。
     *
     * <p>用 {@code INSERT ... ON DUPLICATE KEY UPDATE} 一条语句同时覆盖「新增」和「变更」：
     * 唯一键是 {@code (material_code, valuation_type)}，行在不在由键决定，不需要先查 id。
     * 30,434 行的全量导入按 1000 行一批，实测 3 秒左右（见统筹里记录的数字）。
     *
     * <p>为什么不用 MyBatis-Plus 的 saveOrUpdateBatch：它按主键判断，而导入的行没有 id
     * （新行是自增主键），会退化成「全是 insert」，变更行反而插不进去。
     */
    @Insert("<script>"
            + "INSERT INTO material_master (material_code, valuation_type, material_name, name_norm, unit,"
            + " material_type, material_group, procurement_group, mrp_type, valuation_class, price_control,"
            + " price, price_unit, currency, plant, creator, changed_at, status, enabled,"
            + " source_sheet, source_row, source_file, imported_at) VALUES "
            + "<foreach collection='list' item='m' separator=','>"
            + "(#{m.materialCode}, #{m.valuationType}, #{m.materialName}, #{m.nameNorm}, #{m.unit},"
            + " #{m.materialType}, #{m.materialGroup}, #{m.procurementGroup}, #{m.mrpType}, #{m.valuationClass},"
            + " #{m.priceControl}, #{m.price}, #{m.priceUnit}, #{m.currency}, #{m.plant}, #{m.creator},"
            + " #{m.changedAt}, #{m.status}, #{m.enabled}, #{m.sourceSheet}, #{m.sourceRow}, #{m.sourceFile},"
            + " #{m.importedAt})"
            + "</foreach>"
            + " ON DUPLICATE KEY UPDATE"
            + " material_name = VALUES(material_name), name_norm = VALUES(name_norm), unit = VALUES(unit),"
            + " material_type = VALUES(material_type), material_group = VALUES(material_group),"
            + " procurement_group = VALUES(procurement_group), mrp_type = VALUES(mrp_type),"
            + " valuation_class = VALUES(valuation_class), price_control = VALUES(price_control),"
            + " price = VALUES(price), price_unit = VALUES(price_unit), currency = VALUES(currency),"
            + " plant = VALUES(plant), creator = VALUES(creator), changed_at = VALUES(changed_at),"
            + " status = VALUES(status), enabled = VALUES(enabled),"
            + " source_sheet = VALUES(source_sheet), source_row = VALUES(source_row),"
            + " source_file = VALUES(source_file), imported_at = VALUES(imported_at)"
            + "</script>")
    int upsertBatch(@Param("list") List<MaterialMaster> rows);

    /** 文件里消失的键：软停用（不删除，否则历史单据里的编码会变成孤儿） */
    @Update("<script>"
            + "UPDATE material_master SET status = 0, enabled = 0 WHERE id IN "
            + "<foreach collection='ids' item='id' open='(' separator=',' close=')'>#{id}</foreach>"
            + "</script>")
    int disableBatch(@Param("ids") List<Long> ids);
}