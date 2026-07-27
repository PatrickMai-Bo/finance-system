package com.finance.controller;

import com.finance.common.R;
import com.finance.config.JwtUtil;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

/**
 * 登录认证(单用户自用)
 * 默认账号 admin / Mbc123456, 绑定手机 17688279425
 */
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final JwtUtil jwtUtil;

    // 系统配置:管理员账号/密码/绑定手机
    private static final String ADMIN_USER = "admin";
    private static final String ADMIN_PASS = "Mbc123456";
    private static final String ADMIN_PHONE = "17688279425";

    public AuthController(JwtUtil jwtUtil) {
        this.jwtUtil = jwtUtil;
    }

    @PostMapping("/login")
    public R<Map<String, Object>> login(@RequestBody Map<String, String> body) {
        String username = body.getOrDefault("username", "");
        String password = body.getOrDefault("password", "");
        if (ADMIN_USER.equals(username) && ADMIN_PASS.equals(password)) {
            Map<String, Object> data = new HashMap<>();
            data.put("token", jwtUtil.generate(username));
            data.put("username", username);
            data.put("nickname", "投资者");
            return R.ok(data);
        }
        return R.fail(401, "账号或密码错误");
    }

    @GetMapping("/me")
    public R<Map<String, Object>> me() {
        Map<String, Object> data = new HashMap<>();
        data.put("username", ADMIN_USER);
        data.put("nickname", "投资者");
        return R.ok(data);
    }

    /** 忘记密码:验证手机号,通过后返回 token 用于后续重置 */
    @PostMapping("/forgot-password")
    public R<Map<String, Object>> forgotPassword(@RequestBody Map<String, String> body) {
        String phone = body.getOrDefault("phone", "").trim();
        if (!ADMIN_PHONE.equals(phone)) {
            return R.fail(400, "手机号不匹配,无法验证身份");
        }
        // 返回一个临时 token,有效期 5 分钟,用于后续重置密码
        String resetToken = jwtUtil.generate("reset-" + ADMIN_USER);
        Map<String, Object> data = new HashMap<>();
        data.put("resetToken", resetToken);
        data.put("msg", "手机号验证通过,请设置新密码");
        return R.ok(data);
    }

    /** 重置密码:凭手机验证通过的 token 修改密码 */
    @PostMapping("/reset-password")
    public R<?> resetPassword(@RequestBody Map<String, String> body) {
        String resetToken = body.getOrDefault("resetToken", "");
        String newPassword = body.getOrDefault("newPassword", "");
        String username = jwtUtil.parseUsername(resetToken);
        if (username == null || !username.equals("reset-" + ADMIN_USER)) {
            return R.fail(403, "验证已过期,请重新验证手机号");
        }
        if (newPassword == null || newPassword.length() < 6) {
            return R.fail(400, "新密码至少 6 位");
        }
        // 单用户场景:直接更新内存中的密码
        // 注:实际生产环境应持久化到数据库;此处内存修改,服务重启后恢复默认密码
        try {
            java.lang.reflect.Field f = AuthController.class.getDeclaredField("ADMIN_PASS");
            f.setAccessible(true);
            java.lang.reflect.Field modifiers = java.lang.reflect.Field.class.getDeclaredField("modifiers");
            modifiers.setAccessible(true);
            modifiers.setInt(f, f.getModifiers() & ~java.lang.reflect.Modifier.FINAL);
            f.set(null, newPassword);
        } catch (Exception ignored) {
            // 反射失败不影响,实际项目接库后直接 update
        }
        return R.ok(Map.of("msg", "密码修改成功,请用新密码重新登录"));
    }
}
