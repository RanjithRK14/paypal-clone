package com.paypal.wallet_service.dto;

import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class WalletResponse {

    private Long id;
    private Long userId;
    private String currency;
    private Long balance;
    private Long availableBalance;
}
