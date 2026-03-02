package com.paypal.transaction_service.dto;

import lombok.Data;

@Data
public class CreditRequest {

    private Long userId;
    private String currency;
    private Long amount;

}
