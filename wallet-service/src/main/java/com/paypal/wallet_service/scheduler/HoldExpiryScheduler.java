package com.paypal.wallet_service.scheduler;

import com.paypal.wallet_service.service.WalletService;
import com.paypal.wallet_service.model.WalletHold;
import com.paypal.wallet_service.repository.WalletHoldRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

@Component
@RequiredArgsConstructor
public class HoldExpiryScheduler {

    private final WalletHoldRepository walletHoldRepository;

    private final WalletService walletService;

    @Scheduled(fixedRateString = "${wallet.hold.expiry.scan-rate-ms:60000}")
    public void expireOldHolds() {
        LocalDateTime now = LocalDateTime.now();

        // simple: fetch expired active holds (OK for small data sets)
        List<WalletHold> expired = walletHoldRepository.findByStatusAndExpiresAtBefore("ACTIVE", now);

        for (WalletHold hold : expired) {
            String ref = hold.getHoldReference();
            try {
                // reuse existing release logic (locks, audit, idempotency)
                walletService.releaseHold(ref);
                System.out.println("🔄 Expired hold released: " + ref);
            } catch (Exception e) {
                // log and continue - don't block the sweep
                System.err.println("❌ Failed to release expired hold " + ref + ": " + e.getMessage());
            }
        }
    }
}
