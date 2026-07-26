package com.hotsearch.service;

import com.hotsearch.dto.ChangePasswordRequest;
import com.hotsearch.dto.LoginRequest;
import com.hotsearch.dto.TokenResponse;
import com.hotsearch.entity.User;
import com.hotsearch.exception.BusinessException;
import com.hotsearch.exception.NotFoundException;
import com.hotsearch.repository.UserRepository;
import com.hotsearch.util.JwtUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthService.class);
    private static final String DEFAULT_ADMIN_PASSWORD = "admin123";

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;
    private final String adminInitialPassword;

    public AuthService(UserRepository userRepository, PasswordEncoder passwordEncoder, JwtUtil jwtUtil,
                       @Value("${admin.initial-password:}") String adminInitialPassword) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtUtil = jwtUtil;
        this.adminInitialPassword = adminInitialPassword;
    }

    public TokenResponse login(LoginRequest req) {
        User user = userRepository.findByUsername(req.username())
                .orElseThrow(() -> new BusinessException("用户名或密码错误"));
        if (!passwordEncoder.matches(req.password(), user.getPasswordHash())) {
            throw new BusinessException("用户名或密码错误");
        }
        String token = jwtUtil.generateToken(user.getId(), user.getUsername());
        return new TokenResponse(token, user.getUsername(),
                Boolean.TRUE.equals(user.getMustChangePassword()));
    }

    @Transactional
    public void changePassword(Long userId, ChangePasswordRequest req) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new NotFoundException("用户不存在"));
        if (!passwordEncoder.matches(req.oldPassword(), user.getPasswordHash())) {
            throw new BusinessException("原密码错误");
        }
        user.setPasswordHash(passwordEncoder.encode(req.newPassword()));
        user.setMustChangePassword(false);
        userRepository.save(user);
    }

    /**
     * 首次启动时创建默认管理员。密码优先取环境变量 ADMIN_INITIAL_PASSWORD；
     * 未设置时使用内置默认值，并强制首次登录修改密码。
     */
    public void initDefaultAdmin() {
        if (userRepository.existsByUsername("admin")) return;

        boolean usingBuiltinPassword = adminInitialPassword == null || adminInitialPassword.isBlank();
        String password = usingBuiltinPassword ? DEFAULT_ADMIN_PASSWORD : adminInitialPassword;
        User admin = new User("admin", passwordEncoder.encode(password), "ADMIN");
        admin.setMustChangePassword(true);
        userRepository.save(admin);
        if (usingBuiltinPassword) {
            log.warn("已创建默认管理员 admin（内置初始密码），请立即登录并修改密码；"
                    + "生产环境建议通过 ADMIN_INITIAL_PASSWORD 指定初始密码");
        } else {
            log.info("已根据 ADMIN_INITIAL_PASSWORD 创建管理员 admin");
        }
    }
}
