package com.billme.auth;

import lombok.Data;

@Data
public class MerchantRegisterRequest {

    private String email;
    private String password;

    private String businessName;
    private String ownerName;
    private String phone;
    private String address;
    private String upiId;

}
