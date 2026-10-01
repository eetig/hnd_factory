package org.example.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import org.example.dto.ExcelResult;
import org.example.dto.Result;
import org.example.dto.TankLevelImageVO;
import org.example.dto.TankLevelSaveDTO;
import org.example.dto.TankLevelVO;
import org.example.service.TankLevelImageService;
import org.example.service.TankLevelRecordService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.Arrays;
import java.util.List;

/**
 * 月底储罐液位记录：查询（免登录）+ 录入维护（需权限）。
 *
 * <p>查询类接口免登录 —— 与 /api/pick/list、/api/inbound/list 的既有约定一致
 * （本工程用注解式鉴权，未加 @SaCheck* 即为放行）。
 *
 * <p>写入类接口一律带 {@code @SaCheckPermission}（不变式 1）。权限标识没有走
 * {@code constant/PermConstants} —— 那个类目前没有任何引用，既有 Controller 全部用字面量，
 * 这里保持同样的写法，免得同一个标识出现「有的地方读常量、有的地方读字面量」两种来源。
 */
@RestController
@RequestMapping("/api/tank-level")
public class TankLevelController {

    @Autowired
    private TankLevelRecordService tankLevelRecordService;

    @Autowired
    private TankLevelImageService tankLevelImageService;

    /**
     * 液位记录列表。
     *
     * <p>返回体沿用既有列表接口的 ExcelResult（前端统一读 dataList），
     * 参数全部可选：不传即不加该条件。
     */
    @GetMapping("/list")
    public ExcelResult<TankLevelVO> list(
            @RequestParam(value = "startDate", required = false) String startDate,
            @RequestParam(value = "endDate", required = false) String endDate,
            @RequestParam(value = "location", required = false) String location,
            @RequestParam(value = "category", required = false) String category,
            @RequestParam(value = "keyword", required = false) String keyword) {

        List<TankLevelVO> list = tankLevelRecordService.listRecords(
                parseDate(startDate), parseDate(endDate), location, category, keyword);

        ExcelResult<TankLevelVO> result = new ExcelResult<>();
        result.setSuccess(true);
        result.setMsg("查询成功");
        result.setDataList(list);
        result.setTotalRow(list.size());
        result.setSuccessRow(list.size());
        return result;
    }

    /** 属地下拉选项（来自库中数据，避免前端写死车间名） */
    @GetMapping("/locations")
    public Result<List<String>> locations() {
        return Result.success(tankLevelRecordService.listLocations());
    }

    /** 新增（id 为空）或编辑（id 有值）一条记录 —— 行内编辑的「保存」 */
    @SaCheckPermission("tank_level:edit")
    @PostMapping("/save")
    public Result<TankLevelVO> save(@RequestBody TankLevelSaveDTO dto) {
        return Result.success("保存成功", tankLevelRecordService.saveRecord(dto));
    }

    /** 删除一条记录（连同它在 img-service 上的图据） */
    @SaCheckPermission("tank_level:delete")
    @DeleteMapping("/delete")
    public Result<Void> delete(@RequestParam("id") Long id) {
        tankLevelRecordService.deleteRecord(id);
        return Result.success("删除成功", null);
    }

    /**
     * 上传一批图据（可多张，变更-011）。
     *
     * <p>与 {@code /api/work-order/image/upload} 同一套签名（multipart 的 {@code recordId}
     * + 多个 {@code files}），前端两处共用同一个弹窗组件。
     *
     * <p>要求该记录已保存 —— 图据挂在记录 id 上，新增行须先保存拿到 id 再传图。
     */
    @SaCheckPermission("tank_level:edit")
    @PostMapping("/image/upload")
    public Result<List<TankLevelImageVO>> uploadImages(@RequestParam("recordId") Long recordId,
                                                       @RequestPart("files") MultipartFile[] files) {
        List<MultipartFile> list = files == null ? List.of() : Arrays.asList(files);
        return Result.success("上传成功", tankLevelImageService.uploadImages(recordId, list));
    }

    /** 删除一张图据（连同 img-service 上的文件） */
    @SaCheckPermission("tank_level:edit")
    @DeleteMapping("/image/delete")
    public Result<Void> deleteImage(@RequestParam("imageId") Long imageId) {
        tankLevelImageService.deleteImage(imageId);
        return Result.success("图据已删除", null);
    }

    /**
     * 某条记录的全部图据 —— 弹窗每次打开时刷新用。
     *
     * <p>查询类接口，免登录（与 /api/work-order/image/list 一致）。
     */
    @GetMapping("/image/list")
    public Result<List<TankLevelImageVO>> listImages(@RequestParam("recordId") Long recordId) {
        return Result.success(tankLevelImageService.listImages(recordId));
    }

    /**
     * 日期参数容错：缺失或格式不对一律当作「不限制」，而不是抛异常。
     * 前端已按 yyyy-MM-dd 传值，这里兜的是手工调接口与历史脏值。
     */
    private LocalDate parseDate(String value) {
        if (value == null || value.trim().isEmpty()) {
            return null;
        }
        try {
            return LocalDate.parse(value.trim());
        } catch (DateTimeParseException e) {
            return null;
        }
    }
}
