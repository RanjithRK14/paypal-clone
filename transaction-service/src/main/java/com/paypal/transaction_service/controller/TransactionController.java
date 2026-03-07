package com.paypal.transaction_service.controller;

import com.paypal.transaction_service.model.Transaction;
import com.paypal.transaction_service.service.TransactionService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/transactions")
@RequiredArgsConstructor
public class TransactionController {

    private final TransactionService service;

    @PostMapping("/create")
    public ResponseEntity<?> create(@Valid @RequestBody Transaction transaction,
                                    HttpServletRequest request) {

        // Extract userId from gateway
        String userIdHeader = request.getHeader("X-User-Id");

        if (userIdHeader == null) {
            return ResponseEntity.status(403)
                    .body("Missing X-User-Id header from gateway");
        }

        Long userId = Long.parseLong(userIdHeader);

        // Automatically assign senderId from JWT
        transaction.setSenderId(userId);

        System.out.println("Sender extracted from JWT: " + userId);

        Transaction created = service.createTransaction(transaction);

        return ResponseEntity.ok(created);
    }

    @GetMapping("/{id}")
    public ResponseEntity<?> getById(@PathVariable Long id) {

        Transaction transaction = service.getTransactionById(id);

        if (transaction == null) {
            return ResponseEntity.status(404)
                    .body("Transaction not found");
        }

        return ResponseEntity.ok(transaction);
    }

    @GetMapping("/my")
    public ResponseEntity<?> getMyTransactions(HttpServletRequest request) {

        String userIdHeader = request.getHeader("X-User-Id");

        if (userIdHeader == null) {
            return ResponseEntity.status(403)
                    .body("Missing X-User-Id header from gateway");
        }

        Long userId = Long.parseLong(userIdHeader);

        List<Transaction> transactions = service.getTransactionsByUser(userId);

        return ResponseEntity.ok(transactions);
    }
}