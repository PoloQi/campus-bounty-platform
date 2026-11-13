package com.campus.bounty.user;

import com.campus.bounty.common.BizException;
import com.campus.bounty.user.dto.UpdateProfileReq;
import com.campus.bounty.user.dto.UserProfileResp;
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

    public UserProfileResp profile(Long userId) {
        return UserProfileResp.from(userRepository.findById(userId)
                .orElseThrow(() -> new BizException("用户不存在")));
    }

    @Transactional
    public User updateProfile(Long userId, UpdateProfileReq request) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BizException("用户不存在"));
        if (request.getNickname() != null) {
            user.setNickname(request.getNickname().isBlank() ? null : request.getNickname());
        }
        if (request.getAvatar() != null) {
            user.setAvatar(request.getAvatar().isBlank() ? null : request.getAvatar());
        }
        if (request.getBio() != null) {
            user.setBio(request.getBio().isBlank() ? null : request.getBio());
        }
        if (request.getContact() != null) {
            user.setContact(request.getContact().isBlank() ? null : request.getContact());
        }
        return userRepository.save(user);
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
