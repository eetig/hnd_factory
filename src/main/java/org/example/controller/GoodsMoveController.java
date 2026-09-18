package org.example.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import org.example.dto.ExcelResult;
import org.example.dto.GoodsMoveVO;
import org.example.service.MaterialMovementService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/goods-move")
public class GoodsMoveController {

    @Autowired
    private MaterialMovementService materialMovementService;

    // 货物移动列表（全量返回，前端本地做日期筛选与分页）
    @SaCheckPermission("goods_move:view")
    @GetMapping("/list")
    public ExcelResult<GoodsMoveVO> list() {
        return materialMovementService.listGoodsMoves();
    }
}
