package org.example.controller;

import cn.dev33.satoken.stp.StpUtil;
import org.example.component.ImgUploader;
import org.example.dto.Result;
import org.example.dto.TodoSaveDTO;
import org.example.dto.TodoVO;
import org.example.exception.BusinessException;
import org.example.service.TodoService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;

/**
 * 待办接口（2026-11-16）。
 *
 * <p>产品口径：只有手机用，但数据在服务器上 —— 换手机不丢事，以后要在电脑端看也不用改接口。
 *
 * <p>权限只要登录：待办是「自己的事」，不涉及金额与台账，没有必要卡管理员；
 * 也不按人隔离 —— 这张单子上的事谁看见谁去办，共享反而比隔离有用。
 */
@RestController
@RequestMapping("/api/todo")
public class TodoController {

    private static final long MAX_BYTES = 8L * 1024 * 1024;

    @Autowired
    private TodoService todoService;

    @Autowired
    private ImgUploader imgUploader;

    /** scope=open 看还没办的（默认），scope=done 看已完成的 */
    @GetMapping("/list")
    public Result<List<TodoVO>> list(@RequestParam(required = false, defaultValue = "open") String scope) {
        requireLogin();
        return Result.success(todoService.list(scope));
    }

    @PostMapping("/create")
    public Result<TodoVO> create(@RequestBody TodoSaveDTO dto) {
        requireLogin();
        return Result.success(todoService.create(dto, StpUtil.getLoginIdAsLong()));
    }

    @PutMapping("/{id}")
    public Result<TodoVO> update(@PathVariable Long id, @RequestBody TodoSaveDTO dto) {
        requireLogin();
        return Result.success(todoService.update(id, dto));
    }

    /** 勾上 / 取消勾选：done=true 进「已完成」，done=false 回到清单 */
    @PostMapping("/{id}/done")
    public Result<TodoVO> toggleDone(@PathVariable Long id,
                                     @RequestParam(required = false, defaultValue = "true") boolean done) {
        requireLogin();
        return Result.success(todoService.toggleDone(id, done));
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        requireLogin();
        todoService.delete(id);
        return Result.success(null);
    }

    /**
     * 待办附图。
     *
     * <p>与电费拍照同一个上传器（file-service），返回 {@code /files/xxx.jpg}，
     * 手机端直接把这个路径存进待办即可。单独给一个端点而不是复用别处，
     * 是因为待办可能独立于任何业务单据存在。
     */
    @PostMapping("/photo")
    public Result<Map<String, String>> uploadPhoto(@RequestParam("file") MultipartFile file) {
        requireLogin();
        if (file == null || file.isEmpty()) {
            throw new BusinessException("没有收到图片");
        }
        if (file.getSize() > MAX_BYTES) {
            throw new BusinessException("图片超过 8 MB，请重拍或压缩");
        }
        try {
            // ImgUploader 只认 png/jpg 两种后缀，其余一律按 jpg 提交；
            // 这里必须把真实后缀传下去，否则一张 png 会被标成 image/jpeg 被 file-service 拒收。
            String name = file.getOriginalFilename();
            String ext = (name != null && name.toLowerCase().endsWith(".png")) ? "png" : "jpg";
            String fileName = imgUploader.upload(file.getBytes(), name, ext);
            return Result.success(Map.of("path", "/files/" + fileName, "fileName", fileName));
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            throw new BusinessException("图片上传失败：" + e.getMessage());
        }
    }

    private static void requireLogin() {
        if (!StpUtil.isLogin()) {
            throw new BusinessException("请先登录");
        }
    }
}
