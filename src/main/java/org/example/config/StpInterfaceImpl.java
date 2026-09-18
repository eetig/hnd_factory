package org.example.config;

import cn.dev33.satoken.stp.StpInterface;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import org.example.entity.SysRole;
import org.example.entity.SysRolePermission;
import org.example.entity.SysUser;
import org.example.mapper.SysRoleMapper;
import org.example.mapper.SysRolePermissionMapper;
import org.example.mapper.SysUserMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Sa-Token 权限数据源：根据登录用户加载其角色标识与权限标识集合。
 * 供 @SaCheckPermission / @SaCheckRole 注解鉴权时调用。
 */
@Component
public class StpInterfaceImpl implements StpInterface {

    @Autowired
    private SysUserMapper sysUserMapper;

    @Autowired
    private SysRoleMapper sysRoleMapper;

    @Autowired
    private SysRolePermissionMapper sysRolePermissionMapper;

    @Override
    public List<String> getPermissionList(Object loginId, String loginType) {
        SysUser user = sysUserMapper.selectById(Long.valueOf(String.valueOf(loginId)));
        if (user == null || user.getRoleId() == null) {
            return List.of();
        }
        return sysRolePermissionMapper.selectList(
                        new QueryWrapper<SysRolePermission>().eq("role_id", user.getRoleId()))
                .stream().map(SysRolePermission::getPermissionKey).collect(Collectors.toList());
    }

    @Override
    public List<String> getRoleList(Object loginId, String loginType) {
        SysUser user = sysUserMapper.selectById(Long.valueOf(String.valueOf(loginId)));
        if (user == null || user.getRoleId() == null) {
            return List.of();
        }
        SysRole role = sysRoleMapper.selectById(user.getRoleId());
        return role == null ? List.of() : List.of(role.getRoleKey());
    }
}
