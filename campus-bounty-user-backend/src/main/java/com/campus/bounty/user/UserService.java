package com.campus.bounty.user;

import com.campus.bounty.common.BizException;
import com.campus.bounty.wallet.Wallet;
import com.campus.bounty.wallet.WalletRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class UserService {
    private final UserRepository userRepository;
    private final WalletRepository walletRepository;
    private final PasswordEncoder passwordEncoder;

    @Transactional
    public User register(String username, String password, String confirmPassword, String nickname) {
        if (!password.equals(confirmPassword)) {
            throw new BizException("两次输入的密码不一致");
        }
        if (userRepository.existsByUsername(username)) {
            throw new BizException("用户名已存在");
        }

        User user = new User();
        user.setUsername(username);
        user.setPasswordHash(passwordEncoder.encode(password));
        user.setNickname(nickname == null || nickname.isBlank() ? username : nickname);
        userRepository.save(user);

        Wallet wallet = new Wallet();
        wallet.setUserId(user.getId());
        walletRepository.save(wallet);
        return user;
    }

    public User login(String username, String password) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new BizException("用户名或密码错误"));
        if (!passwordEncoder.matches(password, user.getPasswordHash())) {
            throw new BizException("用户名或密码错误");
        }
        if ("BANNED".equals(user.getStatus())) {
            throw new BizException("账号已被封禁");
        }
        return user;
    }
}
