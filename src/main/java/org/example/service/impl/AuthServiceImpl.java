package org.example.service.impl;

import cn.dev33.satoken.stp.StpUtil;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import org.example.dto.LoginDTO;
import org.example.dto.LoginVO;
import org.example.entity.SysRole;
import org.example.entity.SysRolePermission;
import org.example.entity.SysUser;
import org.example.exception.BusinessException;
import org.example.mapper.SysRoleMapper;
import org.example.mapper.SysRolePermissionMapper;
import org.example.mapper.SysUserMapper;
import org.example.service.AuthService;
import org.example.util.BCryptUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Service
public class AuthServiceImpl implements AuthService {

    @Autowired
    private SysUserMapper sysUserMapper;

    @Autowired
    private SysRoleMapper sysRoleMapper;

    @Autowired
    private SysRolePermissionMapper sysRolePermissionMapper;

    @Override
    public LoginVO login(LoginDTO dto) {
        // 1. 查用户
        SysUser user = sysUserMapper.selectOne(
                new QueryWrapper<SysUser>().eq("username", dto.getUsername()));
        if (user == null || !BCryptUtil.matches(dto.getPassword(), user.getPassword())) {
            throw new BusinessException("用户名或密码错误");
        }
        if (user.getStatus() != null && user.getStatus() != 1) {
            throw new BusinessException("账号已被禁用");
        }

        // 2. 登录
        StpUtil.login(user.getId());

        // 3. 组装返回
        LoginVO vo = new LoginVO();
        vo.setToken(StpUtil.getTokenValue());
        vo.setUserId(user.getId());
        vo.setUsername(user.getUsername());
        vo.setRealName(user.getRealName());

        SysRole role = user.getRoleId() == null ? null : sysRoleMapper.selectById(user.getRoleId());
        vo.setRoleKey(role == null ? null : role.getRoleKey());
        vo.setRoleName(role == null ? null : role.getRoleName());

        List<String> perms = user.getRoleId() == null ? List.of()
                : sysRolePermissionMapper.selectList(
                                new QueryWrapper<SysRolePermission>().eq("role_id", user.getRoleId()))
                        .stream().map(SysRolePermission::getPermissionKey).collect(Collectors.toList());
        vo.setPermissions(perms);
        return vo;
    }

    @Override
    public void logout() {
        StpUtil.logout();
    }
}
