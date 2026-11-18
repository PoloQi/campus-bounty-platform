package com.campus.bounty.wallet;

import com.campus.bounty.common.BizException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

@Service
@RequiredArgsConstructor
public class WalletService {
    private static final BigDecimal MAX_SINGLE_AMOUNT = new BigDecimal("99999999.99");

    private final WalletRepository walletRepository;
    private final WalletLogRepository walletLogRepository;

    @Transactional
    public Wallet getOrCreate(Long userId) {
        return walletRepository.findByUserId(userId).orElseGet(() -> {
            Wallet wallet = new Wallet();
            wallet.setUserId(userId);
            return walletRepository.save(wallet);
        });
    }

    private Wallet lock(Long userId) {
        return walletRepository.lockByUserId(userId).orElseGet(() -> {
            Wallet wallet = new Wallet();
            wallet.setUserId(userId);
            return walletRepository.save(wallet);
        });
    }

    @Transactional
    public Wallet recharge(Long userId, BigDecimal amount) {
        validateAmount(amount);
        Wallet wallet = lock(userId);
        wallet.setBalance(wallet.getBalance().add(amount));
        Wallet saved = walletRepository.save(wallet);
        walletLogRepository.save(WalletLog.of(userId, "RECHARGE", amount,
                saved.getBalance(), null, "模拟充值"));
        return saved;
    }

    @Transactional
    public void freeze(Long userId, BigDecimal amount, Long orderId) {
        validateAmount(amount);
        Wallet wallet = lock(userId);
        if (wallet.getBalance().compareTo(amount) < 0) {
            throw new BizException("余额不足，无法冻结");
        }
        wallet.setBalance(wallet.getBalance().subtract(amount));
        wallet.setFrozen(wallet.getFrozen().add(amount));
        Wallet saved = walletRepository.save(wallet);
        walletLogRepository.save(WalletLog.of(userId, "FREEZE", amount.negate(),
                saved.getBalance(), orderId, "冻结至订单"));
    }

    @Transactional
    public void unfreeze(Long userId, BigDecimal amount, Long orderId) {
        validateAmount(amount);
        Wallet wallet = lock(userId);
        if (wallet.getFrozen().compareTo(amount) < 0) {
            throw new BizException("冻结金额异常，无法解冻");
        }
        wallet.setFrozen(wallet.getFrozen().subtract(amount));
        wallet.setBalance(wallet.getBalance().add(amount));
        Wallet saved = walletRepository.save(wallet);
        walletLogRepository.save(WalletLog.of(userId, "UNFREEZE", amount,
                saved.getBalance(), orderId, "取消解冻退回"));
    }

    @Transactional
    public void settle(Long buyerId, Long sellerId, BigDecimal amount, Long orderId) {
        validateAmount(amount);
        // Two orders between the same users may settle in opposite directions.
        boolean buyerFirst = buyerId.compareTo(sellerId) < 0;
        Wallet first = lock(buyerFirst ? buyerId : sellerId);
        Wallet second = lock(buyerFirst ? sellerId : buyerId);
        Wallet buyer = buyerFirst ? first : second;
        Wallet seller = buyerFirst ? second : first;
        if (buyer.getFrozen().compareTo(amount) < 0) {
            throw new BizException("冻结金额异常，无法结算");
        }
        buyer.setFrozen(buyer.getFrozen().subtract(amount));
        Wallet savedBuyer = walletRepository.save(buyer);
        // The buyer's available balance changed when this order was funded.
        walletLogRepository.save(WalletLog.of(buyerId, "PAY", BigDecimal.ZERO,
                savedBuyer.getBalance(), orderId, "订单托管款已结算 ¥" + amount.setScale(2).toPlainString()));

        seller.setBalance(seller.getBalance().add(amount));
        Wallet savedSeller = walletRepository.save(seller);
        walletLogRepository.save(WalletLog.of(sellerId, "INCOME", amount,
                savedSeller.getBalance(), orderId, "接单收入"));
    }

    public List<WalletLog> logs(Long userId) {
        return walletLogRepository.findByUserIdOrderByIdDesc(userId);
    }

    private void validateAmount(BigDecimal amount) {
        if (amount == null || amount.signum() <= 0 || amount.stripTrailingZeros().scale() > 2) {
            throw new BizException("金额必须大于 0 且最多两位小数");
        }
        if (amount.compareTo(MAX_SINGLE_AMOUNT) > 0) {
            throw new BizException("单笔金额不能超过 " + MAX_SINGLE_AMOUNT.toPlainString());
        }
    }
}
