package com.paypal.user_service.dto;

import lombok.Data;

@Data
public class SignUpRequest {

    private String name;
    private String email;
    private String password;
    private String adminKey;
}
