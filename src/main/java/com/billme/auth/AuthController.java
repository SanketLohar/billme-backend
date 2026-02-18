package com.billme.auth;

import com.billme.auth.token.RefreshToken;
import com.billme.auth.token.RefreshTokenService;
import com.billme.security.jwt.JwtService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;
    private final RefreshTokenService refreshTokenService;
    private final JwtService jwtService;

    // ================= CUSTOMER REGISTER =================
    @PostMapping("/register/customer")
    public ResponseEntity<AuthResponse> registerCustomer(
            @RequestBody CustomerRegisterRequest request) {

        return ResponseEntity.ok(authService.registerCustomer(request));
    }

    // ================= MERCHANT REGISTER =================
    @PostMapping("/register/merchant")
    public ResponseEntity<AuthResponse> registerMerchant(
            @RequestBody MerchantRegisterRequest request) {

        return ResponseEntity.ok(authService.registerMerchant(request));
    }

    // ================= LOGIN =================
    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(
            @RequestBody LoginRequest request) {

        return ResponseEntity.ok(authService.login(request));
    }

    // ================= REFRESH TOKEN =================
    @PostMapping("/refresh")
    public ResponseEntity<AuthResponse> refreshToken(
            @RequestBody RefreshRequest request) {

        RefreshToken refreshToken =
                refreshTokenService.validateRefreshToken(request.getRefreshToken());

        String newAccessToken =
                jwtService.generateAccessToken(
                        refreshToken.getUser().getEmail(),
                        refreshToken.getUser().getRole().name()
                );

        return ResponseEntity.ok(
                new AuthResponse(newAccessToken, request.getRefreshToken())
        );
    }


    // ================= LOGOUT =================
    @PostMapping("/logout")
    public ResponseEntity<String> logout(
            @RequestBody RefreshRequest request) {

        refreshTokenService.revokeToken(request.getRefreshToken());

        return ResponseEntity.ok("Logged out successfully");
    }

}
