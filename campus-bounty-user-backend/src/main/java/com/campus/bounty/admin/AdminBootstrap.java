package com.campus.bounty.admin;

import com.campus.bounty.user.User;
import com.campus.bounty.user.UserRepository;
import com.campus.bounty.wallet.Wallet;
import com.campus.bounty.wallet.WalletRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.env.Environment;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;

@Component
@RequiredArgsConstructor
public class AdminBootstrap implements CommandLineRunner {
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final WalletRepository walletRepository;
    private final Environment environment;

    @Override
    @Transactional
    public void run(String... args) {
        String username = environment.getProperty("bounty.admin.username", "").strip();
        String password = environment.getProperty("bounty.admin.password", "");
        if (username.isEmpty() && password.isEmpty()) {
            if (environment.matchesProfiles("local", "test")) {
                provision("admin", "admin123");
            }
            return;
        }
        if (username.isEmpty() || username.length() > 20) {
            throw new IllegalStateException("BOUNTY_ADMIN_USERNAME 必须为 1 至 20 个字符");
        }
        if (password.isBlank() || password.length() < 12 || password.length() > 32
                || password.getBytes(StandardCharsets.UTF_8).length > 72) {
            throw new IllegalStateException("BOUNTY_ADMIN_PASSWORD 必须为 12 至 32 个字符且不超过 72 个 UTF-8 字节");
        }
        provision(username, password);
    }

    private void provision(String username, String password) {
        User existing = userRepository.findByUsername(username).orElse(null);
        if (existing != null) {
            if (!"ADMIN".equals(existing.getRole())) {
                throw new IllegalStateException("预置管理员用户名已被非管理员账号占用");
            }
            return;
        }
        User admin = new User();
        admin.setUsername(username);
        admin.setNickname("平台管理员");
        admin.setPasswordHash(passwordEncoder.encode(password));
        admin.setRole("ADMIN");
        userRepository.save(admin);

        Wallet wallet = new Wallet();
        wallet.setUserId(admin.getId());
        walletRepository.save(wallet);
    }
}
