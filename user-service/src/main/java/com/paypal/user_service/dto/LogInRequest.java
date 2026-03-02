package com.paypal.user_service.dto;

import lombok.Data;

@Data
public class LogInRequest {

    private String email;
    private String password;
}
