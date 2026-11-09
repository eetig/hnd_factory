package org.example.controller;

import cn.dev33.satoken.stp.StpUtil;
import jakarta.validation.Valid;
import org.example.dto.EquipmentTankSaveDTO;
import org.example.dto.Result;
import org.example.exception.BusinessException;
import org.example.service.EquipmentTankService;
import org.example.vo.EquipmentTankVO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 储罐（容器实例）接口（2026-11-09）。
 *
 * <p>使用方的模型：设备台账是**图纸**，储罐是车间按图纸买回来的**容器**，
 * 「容器我来指定哪张图纸」。所以这里的写操作只有一件事值得强调：
 * {@code blueprintId} 由人手工选择，接口不做任何自动匹配。
 *
 * <p>权限：读全员、写要管理员（与设备台账同一档），因为它们共同支撑"液位→质量"的计算链，
 * 写错会让报表数错。
 */
@RestController
@RequestMapping("/api/equipment/tank")
public class EquipmentTankController {

    @Autowired
    private EquipmentTankService tankService;

    @GetMapping("/list")
    public Result<List<EquipmentTankVO>> list(
            @RequestParam(value = "keyword", required = false) String keyword,
            @RequestParam(value = "enabled", required = false) Integer enabled,
            @RequestParam(value = "workshop", required = false) String workshop) {
        return Result.success(tankService.list(keyword, enabled, workshop));
    }

    @PostMapping("/save")
    public Result<Long> save(@Valid @RequestBody EquipmentTankSaveDTO dto) {
        requireAdmin();
        return Result.success(tankService.save(dto));
    }

    @PostMapping("/enable")
    public Result<Void> enable(@RequestParam("id") Long id, @RequestParam("enabled") boolean enabled) {
        requireAdmin();
        tankService.setEnabled(id, enabled);
        return Result.success(null);
    }

    @DeleteMapping("/delete")
    public Result<Void> delete(@RequestParam("id") Long id) {
        requireAdmin();
        tankService.delete(id);
        return Result.success(null);
    }

    private void requireAdmin() {
        if (!StpUtil.hasRole("admin")) {
            throw new BusinessException("只有管理员能维护储罐");
        }
    }
}
