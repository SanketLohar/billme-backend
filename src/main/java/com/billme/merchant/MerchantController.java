package com.billme.merchant;

import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/merchant")
@RequiredArgsConstructor
public class MerchantController {

    @GetMapping("/profile")
    public String getMerchantProfile(Authentication authentication) {
        return "Merchant profile accessed by: " + authentication.getName();
    }
}
