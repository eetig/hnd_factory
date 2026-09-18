package org.example.util;

import cn.dev33.satoken.secure.BCrypt;

/**
 * BCrypt 密码加密/校验工具（复用 Sa-Token 内置的 BCrypt 实现）。
 */
public class BCryptUtil {

    public static String encode(String rawPassword) {
        return BCrypt.hashpw(rawPassword, BCrypt.gensalt());
    }

    public static boolean matches(String rawPassword, String hashed) {
        if (rawPassword == null || hashed == null) {
            return false;
        }
        return BCrypt.checkpw(rawPassword, hashed);
    }
}
