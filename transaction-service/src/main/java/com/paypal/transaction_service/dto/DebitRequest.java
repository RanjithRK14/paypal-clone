package com.paypal.transaction_service.dto;

import lombok.Data;

@Data
public class DebitRequest {

    private Long userId;
    private String currency;
    private Long amount;

}
