package com.paypal.wallet_service.service;

import com.paypal.wallet_service.dto.*;
import com.paypal.wallet_service.exception.InsufficientFundsException;
import com.paypal.wallet_service.exception.NotFoundException;
import com.paypal.wallet_service.model.Transaction;
import com.paypal.wallet_service.model.Wallet;
import com.paypal.wallet_service.model.WalletHold;
import com.paypal.wallet_service.repository.TransactionRepository;
import com.paypal.wallet_service.repository.WalletHoldRepository;
import com.paypal.wallet_service.repository.WalletRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class WalletService {

    private final TransactionRepository transactionRepository;
    private final WalletRepository walletRepository;
    private final WalletHoldRepository walletHoldRepository;

    @Transactional
    public WalletResponse createWallet(CreateWalletRequest request) {
        return walletRepository.findByUserId(request.getUserId())
                .map(existing -> toResponse(existing))
                .orElseGet(() -> {
                    Wallet wallet = new Wallet(request.getUserId(),
                            request.getCurrency() != null ? request.getCurrency() : "INR");
                    return toResponse(walletRepository.save(wallet));
                });
    }

    @Transactional
    public WalletResponse credit(CreditRequest request) {
        System.out.println("💰 CREDIT userId=" + request.getUserId()
                + " amount=" + request.getAmount() + " currency=" + request.getCurrency());

        // Find by userId only — each user has exactly one wallet (userId is UNIQUE)
        Wallet wallet = walletRepository.findByUserId(request.getUserId())
                .orElseThrow(() ->
                        new NotFoundException("Wallet not found for user: " + request.getUserId()));

        wallet.setBalance(wallet.getBalance() + request.getAmount());
        wallet.setAvailableBalance(wallet.getAvailableBalance() + request.getAmount());
        Wallet saved = walletRepository.save(wallet);

        transactionRepository.save(Transaction.builder()
                .walletId(wallet.getId()).type("CREDIT")
                .amount(request.getAmount()).status("SUCCESS").build());

        System.out.println("✅ CREDIT done — new balance=" + saved.getBalance());
        return toResponse(saved);
    }

    @Transactional
    public WalletResponse debit(DebitRequest request) {
        System.out.println("💸 DEBIT userId=" + request.getUserId()
                + " amount=" + request.getAmount() + " currency=" + request.getCurrency());

        // Find by userId only — each user has exactly one wallet
        Wallet wallet = walletRepository.findByUserId(request.getUserId())
                .orElseThrow(() ->
                        new NotFoundException("Wallet not found for user: " + request.getUserId()));

        if (wallet.getAvailableBalance() < request.getAmount()) {
            transactionRepository.save(Transaction.builder()
                    .walletId(wallet.getId()).type("DEBIT")
                    .amount(request.getAmount()).status("FAILED").build());
            throw new InsufficientFundsException("Insufficient balance. " +
                    "Available: " + wallet.getAvailableBalance() +
                    ", Requested: " + request.getAmount());
        }

        wallet.setBalance(wallet.getBalance() - request.getAmount());
        wallet.setAvailableBalance(wallet.getAvailableBalance() - request.getAmount());
        Wallet saved = walletRepository.save(wallet);

        transactionRepository.save(Transaction.builder()
                .walletId(wallet.getId()).type("DEBIT")
                .amount(request.getAmount()).status("SUCCESS").build());

        System.out.println("✅ DEBIT done — new balance=" + saved.getBalance());
        return toResponse(saved);
    }

    public WalletResponse getWallet(Long userId) {
        Wallet wallet = walletRepository.findByUserId(userId)
                .orElseThrow(() -> new NotFoundException("Wallet not found for user: " + userId));
        return toResponse(wallet);
    }

    @Transactional
    public HoldResponse placeHold(HoldRequest request) {
        Wallet wallet = walletRepository.findByUserId(request.getUserId())
                .orElseThrow(() -> new NotFoundException("Wallet not found for user: " + request.getUserId()));

        if (wallet.getAvailableBalance() < request.getAmount()) {
            throw new InsufficientFundsException("Not enough balance to hold");
        }

        wallet.setAvailableBalance(wallet.getAvailableBalance() - request.getAmount());

        WalletHold hold = new WalletHold();
        hold.setWallet(wallet);
        hold.setAmount(request.getAmount());
        hold.setHoldReference("HOLD-" + System.currentTimeMillis());
        hold.setStatus("ACTIVE");

        walletRepository.save(wallet);
        walletHoldRepository.save(hold);

        return new HoldResponse(hold.getHoldReference(), hold.getAmount(), hold.getStatus());
    }

    @Transactional
    public WalletResponse captureHold(CaptureRequest request) {
        WalletHold hold = walletHoldRepository.findByHoldReference(request.getHoldReference())
                .orElseThrow(() -> new NotFoundException("Hold not found"));

        if (!"ACTIVE".equals(hold.getStatus())) {
            throw new IllegalStateException("Hold is not active");
        }

        Wallet wallet = hold.getWallet();
        wallet.setBalance(wallet.getBalance() - hold.getAmount());
        hold.setStatus("CAPTURED");
        walletRepository.save(wallet);
        walletHoldRepository.save(hold);

        return toResponse(wallet);
    }

    @Transactional
    public HoldResponse releaseHold(String holdReference) {
        WalletHold hold = walletHoldRepository.findByHoldReference(holdReference)
                .orElseThrow(() -> new NotFoundException("Hold not found"));

        if (!"ACTIVE".equals(hold.getStatus())) {
            throw new IllegalStateException("Hold is not active");
        }

        Wallet wallet = hold.getWallet();
        wallet.setAvailableBalance(wallet.getAvailableBalance() + hold.getAmount());
        hold.setStatus("RELEASED");
        walletRepository.save(wallet);
        walletHoldRepository.save(hold);

        return new HoldResponse(hold.getHoldReference(), hold.getAmount(), hold.getStatus());
    }

    // ── helper ──────────────────────────────────────────────────────────────

    private WalletResponse toResponse(Wallet w) {
        return new WalletResponse(w.getId(), w.getUserId(), w.getCurrency(),
                w.getBalance(), w.getAvailableBalance());
    }
}
