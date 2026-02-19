package com.billme.auth;

import lombok.Data;

@Data
public class CustomerRegisterRequest {

    private String email;
    private String password;
    private String name;

    // JSON string of averaged embedding from frontend
    private String faceEmbeddings;
}
