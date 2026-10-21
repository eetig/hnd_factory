package org.example.controller;

import org.example.dto.Result;
import org.example.service.SliderCaptchaService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 滑动拼图验证码（2026-10-21）。两个端点都是**公开**的（游客注册前就要用）。
 *
 * <p>GET  /api/captcha/new      → 挑战 id + 目标位置百分比（前端画一条指示带）
 * <p>POST /api/captcha/verify   → 传回停下时的百分比，成功发一次性 ticket
 */
@RestController
@RequestMapping("/api/captcha")
public class CaptchaController {

    @Autowired
    private SliderCaptchaService captchaService;

    @GetMapping("/new")
    public Result<SliderCaptchaService.ChallengeView> create() {
        return Result.success(captchaService.create());
    }

    @PostMapping("/verify")
    public Result<Map<String, Object>> verify(@RequestBody Map<String, Object> body) {
        String id = body.get("id") == null ? null : String.valueOf(body.get("id"));
        int percent;
        try {
            percent = (int) Double.parseDouble(String.valueOf(body.getOrDefault("percent", "-99999")));
        } catch (Exception e) {
            percent = -99999;
        }

        SliderCaptchaService.VerifyResult r = captchaService.verify(id, percent);
        if (!r.ok()) {
            return Result.error(400, r.message());
        }
        return Result.success(Map.of("ticket", r.ticket()));
    }
}