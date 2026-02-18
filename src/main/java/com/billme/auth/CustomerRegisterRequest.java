package com.billme.auth;

import lombok.Data;

@Data
public class CustomerRegisterRequest {

    private String email;
    private String password;
    private String name;

    private String faceEmbeddings;  // Base64 string
}
