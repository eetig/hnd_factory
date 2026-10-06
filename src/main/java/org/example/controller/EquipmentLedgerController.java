package org.example.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import org.example.component.ImgUploader;
import org.example.dto.EquipmentLedgerVO;
import org.example.dto.EquipmentSaveDTO;
import org.example.dto.Result;
import org.example.dto.VesselDrawingResult;
import org.example.dto.VesselVO;
import org.example.service.EquipmentLedgerService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/**
 * 设备台账查询（只读）。
 *
 * <p>典型用法：输入「三甲」→ 返回设备名称/位号/规格/车间里含「三甲」的设备及其参数
 * （位号、规格、容积或换热面积、封头容积、每 mm 液位对应体积与质量）。
 *
 * <p>现已有前端入口：月底储罐液位记录录入时的「容器名称」候选 —— 那边点开下拉就调本接口
 * （空关键字取「全部候选」，见下方 {@code keyword} 说明），所以**不能**再把空关键字当无意义输入拒掉。
 *
 * <p>查询类接口，免登录 —— 与 /api/pick/list、/api/inbound/list 的既有约定一致
 * （本工程用注解式鉴权，未加 @SaCheck* 即为放行）。本次没有任何写接口，
 * 将来若加录入/维护接口，必须补 @SaCheckPermission（见《前后端改动统筹》不变式 1）。
 */
@RestController
@RequestMapping("/api/equipment")
public class EquipmentLedgerController {

    @Autowired
    private EquipmentLedgerService equipmentLedgerService;

    /** 底图上传要转发给 img-service（前端不直连那个服务），复用既有的上传组件 */
    @Autowired
    private ImgUploader imgUploader;

    /**
     * 按关键字模糊检索设备台账；结果按设备名称**拼音顺序**（首字母）返回。
     *
     * @param keyword  关键字，匹配位号/名称/规格/车间；**留空返回前 limit 条**
     *                 （即「全部候选」，供下拉框点开即列出），变更-012 起如此
     * @param workshop 车间，可选，精确匹配
     * @param limit    条数上限，可选，默认 20、最多 100
     */
    @GetMapping("/search")
    public Result<List<EquipmentLedgerVO>> search(
            @RequestParam(value = "keyword", required = false) String keyword,
            @RequestParam(value = "workshop", required = false) String workshop,
            @RequestParam(value = "limit", required = false) Integer limit) {
        return Result.success(equipmentLedgerService.search(keyword, workshop, limit));
    }

    /**
     * 「压力容器体积计算」页的容器列表（变更-025）—— 该页下拉的**唯一数据源**。
     *
     * <p>为什么不复用上面的 {@code /search}：那个接口的 VO 与库表一一对应、不做裁剪，
     * 同时服务「容器名称」输入框；把内径 / 筒体长 / 底图坐标塞进去，会让那边背上一堆
     * 它用不着的字段。这里只回该页画图与算体积要用的，形状是精简的。
     *
     * <p>只返回**几何与底图填齐**且 {@code enabled=1} 的行 —— 台账里绝大多数行没有这些参数，
     * 不出现在列表里是预期。容器几何与底图坐标的填法见 docs/schema.sql 的列注释。
     *
     * <p>同样是查询接口、免登录（见类注释）。无参数：候选就那么多，不需要分页或筛选。
     */
    @GetMapping("/vessels")
    public Result<List<VesselVO>> vessels() {
        return Result.success(equipmentLedgerService.listVessels());
    }

    // ===== 以下三个服务「设备数据维护」页（2026-10-06 起本表不再只读）=====

    /**
     * 台账全表，供维护页列表用。**免登录只读**（同上面的查询接口），但**停用的行也会返回** ——
     * 维护页要能重新启用它们。
     */
    @GetMapping("/ledger/list")
    public Result<List<EquipmentLedgerVO>> ledgerList() {
        return Result.success(equipmentLedgerService.listAll());
    }

    /**
     * 保存一行（{@code id} 为空则新增）。
     *
     * <p>⚠️ **写接口必须带 @SaCheckPermission** —— 类注释里那条约定就是这么写的。
     * 它之外还有 {@code SaTokenConfigure} 那道「写操作一律要求 admin」的全局闸门（决策-003），
     * 两道并存：闸门管「必须是 admin」，本注解管「得有这个权限位」，
     * 将来要放开给非 admin 的维护员时只需给角色加权限位、不必动结构。
     *
     * <p>可写字段由 {@link EquipmentSaveDTO} 的字段集合界定（白名单即类型本身）。
     */
    @PostMapping("/ledger/save")
    @SaCheckPermission("equipment:edit")
    public Result<Long> ledgerSave(@RequestBody EquipmentSaveDTO payload) {
        return Result.success(equipmentLedgerService.save(payload));
    }

    /**
     * 停用 / 启用一行（不提供删除 —— 台账是历史依据，删了就查不到「某一行是怎么来的」）。
     *
     * <p>单独一个接口、而不是并进 save：免得「保存表单」顺手把停用状态改了。
     */
    @PostMapping("/ledger/enable")
    @SaCheckPermission("equipment:edit")
    public Result<Void> ledgerEnable(@RequestParam("id") Long id, @RequestParam("enabled") boolean enabled) {
        equipmentLedgerService.setEnabled(id, enabled);
        return Result.success(null);
    }

    /**
     * 上传容器底图：**一张进、两张出**（白纸版 + 亮线版）。
     *
     * <p>前端不直连 img-service（那个服务不对外暴露，上传一直是打到本服务再转发的），
     * 所以这里做一层转发 —— 顺便把权限也关在这一层：img-service 那边没有鉴权注解，
     * 光靠它自己是拦不住的。
     *
     * <p>返回的 {@code fileName} 由前端拼成 {@code /files/xxx} 存进 {@code image_file}；
     * 亮线版按 {@code -dark} 后缀自动拼出来，前端不用记第二个名字。
     */
    @PostMapping(value = "/ledger/upload-image", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @SaCheckPermission("equipment:edit")
    public Result<VesselDrawingResult> ledgerUploadImage(@RequestPart("file") MultipartFile file) {
        return Result.success(imgUploader.uploadVesselDrawing(file));
    }
}
