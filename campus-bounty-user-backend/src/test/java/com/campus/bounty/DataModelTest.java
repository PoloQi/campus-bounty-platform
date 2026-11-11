package com.campus.bounty;

import com.campus.bounty.trade.OrderStatus;
import com.campus.bounty.trade.PostType;
import com.campus.bounty.trade.TradeOrder;
import com.campus.bounty.trade.TradeOrderRepository;
import com.campus.bounty.trade.TradePost;
import com.campus.bounty.trade.TradePostRepository;
import com.campus.bounty.user.User;
import com.campus.bounty.user.UserRepository;
import com.campus.bounty.wallet.Wallet;
import com.campus.bounty.wallet.WalletLog;
import com.campus.bounty.wallet.WalletLogRepository;
import com.campus.bounty.wallet.WalletRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class DataModelTest {
    @Autowired UserRepository userRepository;
    @Autowired WalletRepository walletRepository;
    @Autowired WalletLogRepository walletLogRepository;
    @Autowired TradePostRepository postRepository;
    @Autowired TradeOrderRepository orderRepository;

    @Test
    void user_wallet_and_log_can_be_persisted_and_locked() {
        User user = new User();
        user.setUsername("model_user");
        user.setPasswordHash("bcrypt-hash");
        user.setNickname("模型用户");
        userRepository.saveAndFlush(user);

        Wallet wallet = new Wallet();
        wallet.setUserId(user.getId());
        walletRepository.saveAndFlush(wallet);
        walletLogRepository.saveAndFlush(WalletLog.of(user.getId(), "RECHARGE",
                new BigDecimal("10.00"), new BigDecimal("10.00"), null, "模型测试"));

        assertTrue(userRepository.findByUsername("model_user").isPresent());
        assertTrue(walletRepository.findByUserId(user.getId()).isPresent());
        assertTrue(walletRepository.lockByUserId(user.getId()).isPresent());
        assertEquals(1, walletLogRepository.findByUserIdOrderByIdDesc(user.getId()).size());
    }

    @Test
    void trade_post_and_order_can_be_persisted_and_queried() {
        TradePost post = new TradePost();
        post.setPublisherId(1001L);
        post.setType(PostType.BOUNTY);
        post.setTitle("帮忙取快递");
        post.setDescription("菜鸟驿站一件小快递");
        post.setPrice(new BigDecimal("5.00"));
        post.setCategory("跑腿");
        post.setDeadline(LocalDateTime.now().plusDays(1));
        postRepository.saveAndFlush(post);

        TradeOrder order = new TradeOrder();
        order.setPostId(post.getId());
        order.setBuyerId(1001L);
        order.setAmount(new BigDecimal("5.00"));
        orderRepository.saveAndFlush(order);

        assertEquals(1, postRepository.search(PostType.BOUNTY, "跑腿", "快递").size());
        assertTrue(orderRepository.findByIdForUpdate(order.getId()).isPresent());
        assertTrue(orderRepository.findByPostIdAndStatusNot(post.getId(), OrderStatus.CANCELLED).isPresent());
        assertNotNull(order.getCreatedAt());
    }

    @Test
    void username_is_unique() {
        User first = new User();
        first.setUsername("duplicate_model_user");
        first.setPasswordHash("hash-one");
        userRepository.saveAndFlush(first);

        User duplicate = new User();
        duplicate.setUsername("duplicate_model_user");
        duplicate.setPasswordHash("hash-two");
        assertThrows(DataIntegrityViolationException.class, () -> userRepository.saveAndFlush(duplicate));
    }

    @Test
    void one_user_can_only_have_one_wallet() {
        Wallet first = new Wallet();
        first.setUserId(2001L);
        walletRepository.saveAndFlush(first);

        Wallet duplicate = new Wallet();
        duplicate.setUserId(2001L);
        assertThrows(DataIntegrityViolationException.class, () -> walletRepository.saveAndFlush(duplicate));
    }
}
