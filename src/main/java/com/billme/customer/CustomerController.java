package com.billme.customer;

import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/customer")
public class CustomerController {

    @GetMapping("/profile")
    public String getCustomerProfile(Authentication authentication) {
        return "Customer profile accessed by: " + authentication.getName();
    }
}
