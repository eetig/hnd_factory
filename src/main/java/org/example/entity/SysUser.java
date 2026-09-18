package org.example.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("sys_user")
public class SysUser {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String username;      // 账号
    private String password;      // 密码（BCrypt 密文）
    private String realName;      // 真实姓名
    private Long roleId;          // 角色ID，关联 sys_role.id
    private Integer status;       // 状态 1启用 0禁用
    private LocalDateTime createTime;
}
