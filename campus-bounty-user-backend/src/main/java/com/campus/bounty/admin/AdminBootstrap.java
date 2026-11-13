package com.campus.bounty.admin;

import com.campus.bounty.user.User;
import com.campus.bounty.user.UserRepository;
import com.campus.bounty.wallet.Wallet;
import com.campus.bounty.wallet.WalletRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class AdminBootstrap implements CommandLineRunner {
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final WalletRepository walletRepository;

    @Override
    @Transactional
    public void run(String... args) {
        if (userRepository.existsByUsername("admin")) {
            return;
        }
        User admin = new User();
        admin.setUsername("admin");
        admin.setNickname("平台管理员");
        admin.setPasswordHash(passwordEncoder.encode("admin123"));
        admin.setRole("ADMIN");
        userRepository.save(admin);

        Wallet wallet = new Wallet();
        wallet.setUserId(admin.getId());
        walletRepository.save(wallet);
    }
}
