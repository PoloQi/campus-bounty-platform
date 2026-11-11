package com.campus.bounty.user;

import com.campus.bounty.common.ApiResponse;
import com.campus.bounty.common.BizException;
import com.campus.bounty.user.dto.RegisterReq;
import com.campus.bounty.wallet.Wallet;
import com.campus.bounty.wallet.WalletRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class UserServiceTest {
    @Autowired UserService userService;
    @Autowired AuthController authController;
    @Autowired UserRepository userRepository;
    @Autowired WalletRepository walletRepository;
    @Autowired PasswordEncoder passwordEncoder;

    @Test
    void register_creates_student_with_bcrypt_password_and_empty_wallet() {
        User user = userService.register("stu_test_1", "123456", "123456", "小明");

        assertNotNull(user.getId());
        assertEquals("STUDENT", user.getRole());
        assertEquals("ACTIVE", user.getStatus());
        assertNotEquals("123456", user.getPasswordHash());
        assertTrue(passwordEncoder.matches("123456", user.getPasswordHash()));

        Wallet wallet = walletRepository.findByUserId(user.getId()).orElseThrow();
        assertEquals(0, wallet.getBalance().compareTo(BigDecimal.ZERO));
        assertEquals(0, wallet.getFrozen().compareTo(BigDecimal.ZERO));
    }

    @Test
    void register_rejects_duplicate_username() {
        userService.register("stu_duplicate", "123456", "123456", "第一个");

        assertThrows(BizException.class,
                () -> userService.register("stu_duplicate", "123456", "123456", "第二个"));
    }

    @Test
    void register_rejects_mismatched_confirmation_without_creating_user() {
        assertThrows(BizException.class,
                () -> userService.register("stu_mismatch", "123456", "654321", "小明"));

        assertFalse(userRepository.existsByUsername("stu_mismatch"));
    }

    @Test
    void registration_controller_returns_new_user_id() {
        RegisterReq request = new RegisterReq();
        request.setUsername("stu_controller");
        request.setPassword("123456");
        request.setConfirmPassword("123456");
        request.setNickname("控制器用户");

        ApiResponse<Long> response = authController.register(request);

        assertEquals(0, response.getCode());
        assertTrue(userRepository.findById(response.getData()).isPresent());
    }
}
