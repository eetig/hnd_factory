package org.example.controller;

import jakarta.validation.Valid;
import org.example.dto.PickOrderSubmitDTO;
import org.example.dto.PickOrderVO;
import org.example.dto.Result;
import org.example.service.PickOrderService;
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
 * 手机拍领料单 → 暂存队列 → 电脑取用导出。
 *
 * <p>提交与取用都要登录（{@code /api/pick-order/**} 在 {@code LOGIN_READ_PATTERNS} 里）：
 * 队列里是部门与物料清单，不该匿名可读。
 */
@RestController
@RequestMapping("/api/pick-order")
public class PickOrderController {

    @Autowired
    private PickOrderService pickOrderService;

    /** 手机端调用：把核对好的单发到电脑 */
    @PostMapping("/submit")
    public Result<Integer> submit(@Valid @RequestBody PickOrderSubmitDTO dto) {
        return Result.success(pickOrderService.submit(dto));
    }

    /** 电脑端调用：还有几张没取用（页面上的「手机传来 N 单」） */
    @GetMapping("/pending")
    public Result<List<PickOrderVO>> pending() {
        return Result.success(pickOrderService.pending());
    }

    /** 电脑端点「取用」时拉明细 */
    @GetMapping("/detail")
    public Result<PickOrderVO> detail(@RequestParam Long id) {
        return Result.success(pickOrderService.detail(id));
    }

    /** 取用成功（已填进核对界面）后才标记，避免手滑把没导出的单弄丢 */
    @PostMapping("/used")
    public Result<Void> used(@RequestParam Long id) {
        pickOrderService.markUsed(id);
        return Result.success(null);
    }

    @DeleteMapping("/discard")
    public Result<Void> discard(@RequestParam Long id) {
        pickOrderService.discard(id);
        return Result.success(null);
    }
}
