package org.example.controller;

import cn.dev33.satoken.stp.StpUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.example.dto.Result;
import org.example.entity.SysRole;
import org.example.entity.SysUser;
import org.example.exception.BusinessException;
import org.example.mapper.SysRoleMapper;
import org.example.mapper.SysUserMapper;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 注册用户名单（只读）。
 *
 * <p>使用方要求（2026-11-13）：PC、App、微信端都没有地方看「有哪些注册用户、什么时候注册的」，
 * 需要一个查看入口，但**只有手机 App 端提供、只有管理员能用**。所以这里只做两件事：
 * 只允许 admin 调；只回名单需要的最小字段（绝不回 password）。
 */
@RestController
@RequestMapping("/api/user-admin")
public class UserAdminController {

    private final SysUserMapper userMapper;
    private final SysRoleMapper roleMapper;

    public UserAdminController(SysUserMapper userMapper, SysRoleMapper roleMapper) {
        this.userMapper = userMapper;
        this.roleMapper = roleMapper;
    }

    /** 全部注册用户，按注册时间倒序（最近注册的在最上面） */
    @GetMapping("/list")
    public Result<Map<String, Object>> list() {
        if (!StpUtil.hasRole("admin")) {
            throw new BusinessException("只有管理员能看用户名单");
        }
        Map<Long, SysRole> roleById = new HashMap<>();
        for (SysRole role : roleMapper.selectList(null)) {
            roleById.put(role.getId(), role);
        }

        List<SysUser> users = userMapper.selectList(
                new LambdaQueryWrapper<SysUser>().orderByDesc(SysUser::getCreateTime).orderByDesc(SysUser::getId));

        List<Map<String, Object>> rows = new ArrayList<>();
        int enabled = 0;
        for (SysUser user : users) {
            SysRole role = user.getRoleId() == null ? null : roleById.get(user.getRoleId());
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("id", user.getId());
            row.put("username", user.getUsername());
            row.put("realName", user.getRealName());
            row.put("roleKey", role == null ? "" : role.getRoleKey());
            row.put("roleName", role == null ? "未分配角色" : role.getRoleName());
            row.put("status", user.getStatus());
            row.put("createTime", user.getCreateTime());
            rows.add(row);
            if (user.getStatus() != null && user.getStatus() == 1) {
                enabled++;
            }
        }

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("total", rows.size());
        data.put("enabled", enabled);
        data.put("users", rows);
        return Result.success(data);
    }
}
