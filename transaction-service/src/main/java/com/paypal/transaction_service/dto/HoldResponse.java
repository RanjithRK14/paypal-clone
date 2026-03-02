package com.paypal.transaction_service.dto;

import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class HoldResponse {

    private String holdReference;
    private Long amount;
    private String status;
}
