package org.example.service.impl;

import cn.dev33.satoken.secure.BCrypt;
import cn.dev33.satoken.stp.StpUtil;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import org.example.dto.LoginDTO;
import org.example.dto.LoginVO;
import org.example.dto.UserInfoVO;
import org.example.entity.SysRole;
import org.example.entity.SysRolePermission;
import org.example.entity.SysUser;
import org.example.exception.BusinessException;
import org.example.mapper.SysRoleMapper;
import org.example.mapper.SysRolePermissionMapper;
import org.example.mapper.SysUserMapper;
import org.example.service.AuthService;
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

    @Autowired
    private org.example.audit.AuditRecorder auditRecorder;

    /** 自助注册开关（application.yml 的 app.register.enabled，默认开） */
    @org.springframework.beans.factory.annotation.Value("${app.register.enabled:true}")
    private boolean registerEnabled;

    /**
     * 同一 IP 每小时最多 3 次注册。
     * 内存计数即可：内网工具、重启清零无所谓；真要抗刷应该上 Redis 或网关限流。
     */
    private final java.util.Map<String, java.util.Deque<Long>> registerAttempts =
            new java.util.concurrent.ConcurrentHashMap<>();

    private static final int REGISTER_LIMIT_PER_HOUR = 3;

    @Override
    public LoginVO login(LoginDTO dto) {
        // 1. 查用户
        SysUser user = sysUserMapper.selectOne(
                new QueryWrapper<SysUser>().eq("username", dto.getUsername()));
        // 口令或哈希为空一律视为不匹配（BCrypt.checkpw 遇 null 会抛异常），与原 BCryptUtil.matches 语义一致
        if (user == null || user.getPassword() == null || dto.getPassword() == null
                || !BCrypt.checkpw(dto.getPassword(), user.getPassword())) {
            throw new BusinessException("用户名或密码错误");
        }
        if (user.getStatus() != null && user.getStatus() != 1) {
            throw new BusinessException("账号已被禁用");
        }

        // 2. 登录
        StpUtil.login(user.getId());
        // 会话里记一份用户名：归档等地方要把"录入人"写成 username，
        // 而 getLoginIdAsString() 拿到的是**用户 id**（2026-10-20 修的，原先 created_by 存成 "1"）。
        StpUtil.getSession().set("username", user.getUsername());

        // 3. 组装返回
        SysRole role = loadRole(user);
        auditRecorder.recordAuthEvent(
                org.example.constant.AuditEventType.LOGIN, user.getUsername(),
                org.example.audit.RequestMetadata.currentIp(),
                org.example.audit.RequestMetadata.currentUserAgent());
        LoginVO vo = new LoginVO();
        vo.setToken(StpUtil.getTokenValue());
        vo.setUserId(user.getId());
        vo.setUsername(user.getUsername());
        vo.setRealName(user.getRealName());
        vo.setRoleKey(role == null ? null : role.getRoleKey());
        vo.setRoleName(role == null ? null : role.getRoleName());
        vo.setPermissions(loadPermissions(user));
        return vo;
    }

    @Override
    public LoginVO register(org.example.dto.RegisterDTO dto) {
        if (!registerEnabled) {
            throw new BusinessException("注册已关闭，请联系管理员开通账号");
        }
        String ip = org.example.audit.RequestMetadata.currentIp();
        String ua = org.example.audit.RequestMetadata.currentUserAgent();
        checkRegisterRate(ip);

        String username = dto.getUsername().trim();
        Long exists = sysUserMapper.selectCount(
                new QueryWrapper<SysUser>().eq("username", username));
        if (exists != null && exists > 0) {
            throw new BusinessException("账号已被占用，换一个");
        }

        // 角色**固定** member（只读）。绝不接受客户端传角色 —— 那是越权漏洞的经典来源。
        SysRole member = sysRoleMapper.selectOne(
                new QueryWrapper<SysRole>().eq("role_key", "member"));
        if (member == null) {
            throw new BusinessException("系统缺少 member 角色，请联系管理员（库未执行第 24 步）");
        }

        SysUser user = new SysUser();
        user.setUsername(username);
        user.setPassword(BCrypt.hashpw(dto.getPassword()));
        user.setRealName(dto.getRealName() == null || dto.getRealName().isBlank()
                ? username : dto.getRealName().trim());
        user.setRoleId(member.getId());
        user.setStatus(1);
        sysUserMapper.insert(user);

        auditRecorder.recordAuthEvent(
                org.example.constant.AuditEventType.REGISTER, username, ip, ua);

        LoginDTO loginDTO = new LoginDTO();
        loginDTO.setUsername(username);
        loginDTO.setPassword(dto.getPassword());
        return login(loginDTO);
    }

    /** 同一 IP 每小时最多 3 次；超了直接拒，不做"排队等待" */
    private void checkRegisterRate(String ip) {
        long now = System.currentTimeMillis();
        long oneHourAgo = now - 3_600_000L;
        java.util.Deque<Long> hits = registerAttempts.computeIfAbsent(
                ip == null ? "" : ip, k -> new java.util.concurrent.ConcurrentLinkedDeque<>());
        while (!hits.isEmpty() && hits.peekFirst() < oneHourAgo) {
            hits.pollFirst();
        }
        if (hits.size() >= REGISTER_LIMIT_PER_HOUR) {
            throw new BusinessException("注册太频繁，请一小时后再试");
        }
        hits.addLast(now);
    }

    @Override
    public void logout() {
        String username = org.example.audit.RequestMetadata.username();
        String ip = org.example.audit.RequestMetadata.currentIp();
        String ua = org.example.audit.RequestMetadata.currentUserAgent();
        auditRecorder.recordAuthEvent(
                org.example.constant.AuditEventType.LOGOUT, username, ip, ua);
        StpUtil.logout();
    }

    @Override
    public UserInfoVO getCurrentUserInfo() {
        // 未登录时 StpUtil 抛 NotLoginException，由 GlobalExceptionHandler 转成 401
        long userId = StpUtil.getLoginIdAsLong();

        SysUser user = sysUserMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException("用户不存在，请重新登录");
        }
        if (user.getStatus() != null && user.getStatus() != 1) {
            throw new BusinessException("账号已被禁用");
        }

        SysRole role = loadRole(user);
        UserInfoVO vo = new UserInfoVO();
        vo.setUserId(user.getId());
        vo.setUsername(user.getUsername());
        vo.setRealName(user.getRealName());
        vo.setRoleKey(role == null ? null : role.getRoleKey());
        vo.setRoleName(role == null ? null : role.getRoleName());
        vo.setPermissions(loadPermissions(user));
        return vo;
    }

    // ================= 私有工具 =================

    private SysRole loadRole(SysUser user) {
        return user.getRoleId() == null ? null : sysRoleMapper.selectById(user.getRoleId());
    }

    private List<String> loadPermissions(SysUser user) {
        if (user.getRoleId() == null) {
            return List.of();
        }
        return sysRolePermissionMapper.selectList(
                        new QueryWrapper<SysRolePermission>().eq("role_id", user.getRoleId()))
                .stream().map(SysRolePermission::getPermissionKey).collect(Collectors.toList());
    }
}
