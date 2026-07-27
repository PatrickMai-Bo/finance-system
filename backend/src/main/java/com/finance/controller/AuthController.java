package com.finance.controller;

import com.finance.common.R;
import com.finance.config.JwtUtil;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

/**
 * 登录认证(单用户自用)
 * 默认账号 admin / admin123,后续接 MySQL 后从库读取。
 */
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final JwtUtil jwtUtil;

    public AuthController(JwtUtil jwtUtil) {
        this.jwtUtil = jwtUtil;
    }

    @PostMapping("/login")
    public R<Map<String, Object>> login(@RequestBody Map<String, String> body) {
        String username = body.getOrDefault("username", "");
        String password = body.getOrDefault("password", "");
        // MVP 阶段固定校验,步骤④接库后改为数据库校验
        if ("admin".equals(username) && "admin123".equals(password)) {
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
        data.put("username", "admin");
        data.put("nickname", "投资者");
        return R.ok(data);
    }
}
