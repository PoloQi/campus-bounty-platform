package com.campus.bounty.wallet;

import com.campus.bounty.common.BizException;
import com.campus.bounty.user.User;
import com.campus.bounty.user.UserService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class WalletDomainTest {
    private static final BigDecimal AMOUNT = new BigDecimal("50.00");

    @Autowired WalletService walletService;
    @Autowired UserService userService;

    @Test
    void freeze_moves_balance_to_frozen_and_writes_log() {
        User user = register("wallet_domain_freeze");
        walletService.recharge(user.getId(), new BigDecimal("100.00"));

        walletService.freeze(user.getId(), AMOUNT, 101L);

        Wallet wallet = walletService.getOrCreate(user.getId());
        assertMoney("50.00", wallet.getBalance());
        assertMoney("50.00", wallet.getFrozen());
        WalletLog log = latestLog(user.getId());
        assertEquals("FREEZE", log.getType());
        assertMoney("-50.00", log.getAmount());
        assertMoney("50.00", log.getBalanceAfter());
        assertEquals(101L, log.getOrderId());
    }

    @Test
    void freeze_rejects_insufficient_balance_without_partial_changes() {
        User user = register("wallet_domain_insufficient");
        walletService.recharge(user.getId(), new BigDecimal("10.00"));

        assertThrows(BizException.class, () -> walletService.freeze(user.getId(), AMOUNT, 102L));

        Wallet wallet = walletService.getOrCreate(user.getId());
        assertMoney("10.00", wallet.getBalance());
        assertMoney("0", wallet.getFrozen());
        assertEquals(1, walletService.logs(user.getId()).size());
    }

    @Test
    void unfreeze_returns_money_and_writes_log() {
        User user = register("wallet_domain_unfreeze");
        walletService.recharge(user.getId(), new BigDecimal("100.00"));
        walletService.freeze(user.getId(), AMOUNT, 103L);

        walletService.unfreeze(user.getId(), AMOUNT, 103L);

        Wallet wallet = walletService.getOrCreate(user.getId());
        assertMoney("100.00", wallet.getBalance());
        assertMoney("0", wallet.getFrozen());
        WalletLog log = latestLog(user.getId());
        assertEquals("UNFREEZE", log.getType());
        assertMoney("50.00", log.getAmount());
        assertMoney("100.00", log.getBalanceAfter());
        assertEquals(103L, log.getOrderId());
    }

    @Test
    void unfreeze_rejects_more_than_frozen_amount() {
        User user = register("wallet_domain_unfreeze_invalid");
        walletService.recharge(user.getId(), new BigDecimal("100.00"));
        walletService.freeze(user.getId(), new BigDecimal("10.00"), 104L);

        assertThrows(BizException.class, () -> walletService.unfreeze(user.getId(), AMOUNT, 104L));

        Wallet wallet = walletService.getOrCreate(user.getId());
        assertMoney("90.00", wallet.getBalance());
        assertMoney("10.00", wallet.getFrozen());
        assertEquals(2, walletService.logs(user.getId()).size());
    }

    @Test
    void settle_pays_seller_from_buyer_frozen_and_conserves_money() {
        User buyer = register("wallet_domain_buyer");
        User seller = register("wallet_domain_seller");
        walletService.recharge(buyer.getId(), new BigDecimal("100.00"));
        walletService.freeze(buyer.getId(), AMOUNT, 105L);

        walletService.settle(buyer.getId(), seller.getId(), AMOUNT, 105L);

        Wallet buyerWallet = walletService.getOrCreate(buyer.getId());
        Wallet sellerWallet = walletService.getOrCreate(seller.getId());
        assertMoney("50.00", buyerWallet.getBalance());
        assertMoney("0", buyerWallet.getFrozen());
        assertMoney("50.00", sellerWallet.getBalance());
        assertMoney("0", sellerWallet.getFrozen());
        assertMoney("100.00", buyerWallet.getBalance().add(buyerWallet.getFrozen())
                .add(sellerWallet.getBalance()).add(sellerWallet.getFrozen()));

        WalletLog pay = latestLog(buyer.getId());
        assertEquals("PAY", pay.getType());
        assertMoney("-50.00", pay.getAmount());
        assertMoney("50.00", pay.getBalanceAfter());
        assertEquals(105L, pay.getOrderId());

        WalletLog income = latestLog(seller.getId());
        assertEquals("INCOME", income.getType());
        assertMoney("50.00", income.getAmount());
        assertMoney("50.00", income.getBalanceAfter());
        assertEquals(105L, income.getOrderId());
    }

    @Test
    void settle_rejects_invalid_frozen_amount_without_crediting_seller() {
        User buyer = register("wallet_domain_settle_buyer");
        User seller = register("wallet_domain_settle_seller");
        walletService.recharge(buyer.getId(), new BigDecimal("100.00"));
        walletService.freeze(buyer.getId(), new BigDecimal("10.00"), 106L);

        assertThrows(BizException.class,
                () -> walletService.settle(buyer.getId(), seller.getId(), AMOUNT, 106L));

        Wallet buyerWallet = walletService.getOrCreate(buyer.getId());
        assertMoney("90.00", buyerWallet.getBalance());
        assertMoney("10.00", buyerWallet.getFrozen());
        assertMoney("0", walletService.getOrCreate(seller.getId()).getBalance());
        assertEquals(2, walletService.logs(buyer.getId()).size());
        assertEquals(0, walletService.logs(seller.getId()).size());
    }

    @Test
    void domain_methods_reject_non_positive_and_excess_precision_amounts() {
        User buyer = register("wallet_domain_amount_buyer");
        User seller = register("wallet_domain_amount_seller");
        walletService.recharge(buyer.getId(), new BigDecimal("100.00"));
        BigDecimal invalid = new BigDecimal("0.001");

        assertThrows(BizException.class, () -> walletService.freeze(buyer.getId(), invalid, 107L));
        assertThrows(BizException.class, () -> walletService.unfreeze(buyer.getId(), invalid, 107L));
        assertThrows(BizException.class,
                () -> walletService.settle(buyer.getId(), seller.getId(), invalid, 107L));
        assertThrows(BizException.class, () -> walletService.freeze(buyer.getId(), BigDecimal.ZERO, 107L));

        assertMoney("100.00", walletService.getOrCreate(buyer.getId()).getBalance());
        assertMoney("0", walletService.getOrCreate(buyer.getId()).getFrozen());
        assertEquals(1, walletService.logs(buyer.getId()).size());
        assertEquals(0, walletService.logs(seller.getId()).size());
    }

    private User register(String username) {
        return userService.register(username, "123456", "123456", "资金测试");
    }

    private WalletLog latestLog(Long userId) {
        List<WalletLog> logs = walletService.logs(userId);
        return logs.get(0);
    }

    private void assertMoney(String expected, BigDecimal actual) {
        assertEquals(0, new BigDecimal(expected).compareTo(actual));
    }
}
