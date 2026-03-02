package com.paypal.transaction_service.service;

import com.paypal.transaction_service.model.Transaction;

import java.util.List;

public interface TransactionService {

    Transaction createTransaction(Transaction transaction);

    public Transaction getTransactionById(Long id);

    public List<Transaction> getTransactionsByUser(Long userId);
}
