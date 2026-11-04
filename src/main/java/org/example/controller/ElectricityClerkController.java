package org.example.controller;

import cn.dev33.satoken.stp.StpUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.example.dto.Result;
import org.example.entity.SysRole;
import org.example.entity.SysUser;
import org.example.exception.BusinessException;
import org.example.mapper.SysRoleMapper;
import org.example.mapper.SysUserMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 抄表员授权（2026-11-04）。
 *
 * <h2>使用方的要求</h2>
 * <blockquote>
 * 只有我或者我同意的人才能录入电量
 * </blockquote>
 *
 * <p>所以「读」和「写」分开：
 * <ul>
 *   <li><b>看</b>：任何登录用户都能看汇总、分类汇总、预提单的用电量部分
 *       （电费金额只有 admin 能看到，见前端 gate）。</li>
 *   <li><b>录</b>：只有 admin，以及被 admin 授过「抄表员（meter_clerk）」角色的人。</li>
 * </ul>
 *
 * <p>授权就是改 {@code sys_user.role_id}：授 = 改成 meter_clerk，撤销 = 改回 member。
 * 一个用户只能有一个角色，所以"撤销"是明确回到普通用户，而不是"少了一个权限"。
 */
@RestController
@RequestMapping("/api/electricity/clerk")
public class ElectricityClerkController {

    /** 抄表员角色的标识。与前端 hasPerm / 后端判定共用同一个字符串 */
    public static final String CLERK_ROLE_KEY = "meter_clerk";

    @Autowired
    private SysUserMapper userMapper;

    @Autowired
    private SysRoleMapper roleMapper;

    private SysRole roleOf(String roleKey) {
        return roleMapper.selectOne(new LambdaQueryWrapper<SysRole>().eq(SysRole::getRoleKey, roleKey));
    }

    /** 是否允许录入电量：admin 或抄表员 */
    public static boolean canWriteReadings() {
        return StpUtil.hasRole("admin") || StpUtil.hasRole(CLERK_ROLE_KEY);
    }

    /** 抄表员名单 + 可授予的候选人（admin 与抄表员本身都排除） */
    @GetMapping
    public Result<Map<String, Object>> list() {
        if (!StpUtil.hasRole("admin")) {
            throw new BusinessException("只有管理员能管理抄表员");
        }
        SysRole clerk = roleOf(CLERK_ROLE_KEY);
        if (clerk == null) {
            throw new BusinessException("缺少「抄表员」角色，请先执行角色初始化 SQL");
        }
        SysRole admin = roleOf("admin");

        List<SysUser> users = userMapper.selectList(new LambdaQueryWrapper<SysUser>().orderByAsc(SysUser::getId));
        List<Map<String, Object>> clerks = new ArrayList<>();
        List<Map<String, Object>> candidates = new ArrayList<>();
        for (SysUser user : users) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("id", user.getId());
            row.put("username", user.getUsername());
            row.put("realName", user.getRealName());
            if (clerk.getId().equals(user.getRoleId())) {
                clerks.add(row);
            } else if (admin == null || !admin.getId().equals(user.getRoleId())) {
                candidates.add(row);
            }
        }
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("clerks", clerks);
        data.put("candidates", candidates);
        data.put("roleKey", CLERK_ROLE_KEY);
        return Result.success(data);
    }

    /** 授予/撤销抄表员 */
    @PutMapping("/{userId}")
    public Result<Map<String, Object>> set(@PathVariable("userId") Long userId,
                                          @RequestParam("grant") boolean grant) {
        if (!StpUtil.hasRole("admin")) {
            throw new BusinessException("只有管理员能管理抄表员");
        }
        SysUser user = userMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException("用户不存在");
        }
        SysRole admin = roleOf("admin");
        if (admin != null && admin.getId().equals(user.getRoleId())) {
            throw new BusinessException("管理员本身就是抄表权限，不用另外授予");
        }
        SysRole target = roleOf(grant ? CLERK_ROLE_KEY : "member");
        if (target == null) {
            throw new BusinessException(grant ? "缺少「抄表员」角色" : "缺少「普通用户」角色");
        }
        user.setRoleId(target.getId());
        userMapper.updateById(user);

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("id", user.getId());
        data.put("username", user.getUsername());
        data.put("granted", grant);
        return Result.success(data);
    }
}
