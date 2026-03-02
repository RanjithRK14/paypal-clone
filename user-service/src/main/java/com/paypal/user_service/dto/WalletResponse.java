package com.paypal.user_service.dto;

import lombok.Data;

@Data
public class WalletResponse {

    private Long id;
    private Long userId;
    private String currency;
    private Long balance;
    private Long availableBalance;

}
