package com.paypal.transaction_service.dto;

import lombok.Data;

@Data
public class HoldRequest {

    private Long userId;
    private String currency;
    private Long amount;
}
