package com.paypal.wallet_service.controller;

import com.paypal.wallet_service.dto.*;
import com.paypal.wallet_service.service.WalletService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/wallets")
@RequiredArgsConstructor
public class WalletController {

    private final WalletService walletService;

    // Create wallet (called from user-service)
    @PostMapping
    public ResponseEntity<WalletResponse> createWallet(@RequestBody CreateWalletRequest request) {
        return ResponseEntity.ok(walletService.createWallet(request));
    }

    // Credit wallet
    @PostMapping("/credit")
    public ResponseEntity<WalletResponse> credit( @RequestHeader("X-User-Id") Long userId,
                                                  @RequestBody CreditRequest request) {
        return ResponseEntity.ok(walletService.credit(request));
    }

    // Debit wallet
    @PostMapping("/debit")
    public ResponseEntity<WalletResponse> debit(@RequestBody DebitRequest request) {
        return ResponseEntity.ok(walletService.debit(request));
    }

    // Place hold
    @PostMapping("/hold")
    public ResponseEntity<HoldResponse> hold(@RequestBody HoldRequest request) {
        return ResponseEntity.ok(walletService.placeHold(request));
    }

    // Capture hold
    @PostMapping("/capture")
    public ResponseEntity<WalletResponse> capture(@RequestBody CaptureRequest request) {
        return ResponseEntity.ok(walletService.captureHold(request));
    }

    // Release hold
    @PostMapping("/release/{holdReference}")
    public ResponseEntity<HoldResponse> release(@PathVariable String holdReference) {
        return ResponseEntity.ok(walletService.releaseHold(holdReference));
    }

    // Get wallet
    @GetMapping("/{userId}")
    public ResponseEntity<WalletResponse> getWallet(@PathVariable Long userId) {
        return ResponseEntity.ok(walletService.getWallet(userId));
    }
}