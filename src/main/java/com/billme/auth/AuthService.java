package com.billme.auth;

import com.billme.auth.token.RefreshToken;
import com.billme.auth.token.RefreshTokenService;
import com.billme.customer.CustomerProfile;
import com.billme.repository.CustomerProfileRepository;
import com.billme.repository.MerchantProfileRepository;
import com.billme.repository.UserRepository;
import com.billme.repository.WalletRepository;
import com.billme.security.jwt.JwtService;
import com.billme.user.Role;
import com.billme.user.User;
import com.billme.wallet.Wallet;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final MerchantProfileRepository merchantProfileRepository;
    private final CustomerProfileRepository customerProfileRepository;
    private final WalletRepository walletRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuthenticationManager authenticationManager;
    private final JwtService jwtService;
    private final RefreshTokenService refreshTokenService;

    // ================= CUSTOMER REGISTER =================
    @Transactional
    public AuthResponse registerCustomer(CustomerRegisterRequest request) {

        if (userRepository.findByEmail(request.getEmail()).isPresent()) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Email already registered"
            );
        }


        if (request.getFaceEmbeddings() == null || request.getFaceEmbeddings().isBlank()) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Face embedding is required"
            );
        }

        User user = User.builder()
                .email(request.getEmail())
                .password(passwordEncoder.encode(request.getPassword()))
                .role(Role.CUSTOMER)
                .active(true)
                .build();

        userRepository.save(user);

        CustomerProfile profile = CustomerProfile.builder()
                .user(user)
                .name(request.getName())
                .faceEmbeddings(request.getFaceEmbeddings())
                .build();

        customerProfileRepository.save(profile);

        Wallet wallet = Wallet.builder()
                .user(user)
                .balance(BigDecimal.ZERO)
                .build();

        walletRepository.save(wallet);

        String accessToken = jwtService.generateAccessToken(
                user.getEmail(),
                user.getRole().name()
        );

        RefreshToken refreshToken = refreshTokenService.createRefreshToken(user);

        return new AuthResponse(accessToken, refreshToken.getToken());
    }

    // ================= MERCHANT REGISTER =================
    @Transactional
    public AuthResponse registerMerchant(MerchantRegisterRequest request) {

        if (userRepository.findByEmail(request.getEmail()).isPresent()) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Merchant already registered"
            );
        }

        User user = User.builder()
                .email(request.getEmail())
                .password(passwordEncoder.encode(request.getPassword()))
                .role(Role.MERCHANT)
                .active(true)
                .build();

        userRepository.save(user);

        merchantProfileRepository.save(
                com.billme.merchant.MerchantProfile.builder()
                        .user(user)
                        .businessName(request.getBusinessName())
                        .ownerName(request.getOwnerName())
                        .phone(request.getPhone())
                        .address(request.getAddress())
                        .upiId(request.getUpiId())
                        .profileCompleted(false)
                        .build()
        );

        walletRepository.save(
                Wallet.builder()
                        .user(user)
                        .balance(BigDecimal.ZERO)
                        .build()
        );

        String accessToken = jwtService.generateAccessToken(
                user.getEmail(),
                user.getRole().name()
        );

        RefreshToken refreshToken = refreshTokenService.createRefreshToken(user);

        return new AuthResponse(accessToken, refreshToken.getToken());
    }

    // ================= LOGIN =================
    @Transactional
    public AuthResponse login(LoginRequest request) {

        authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(
                        request.getEmail(),
                        request.getPassword()
                )
        );

        User user = userRepository.findByEmail(request.getEmail())
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "User not found"
                ));

        String accessToken = jwtService.generateAccessToken(
                user.getEmail(),
                user.getRole().name()
        );

        RefreshToken refreshToken =
                refreshTokenService.createRefreshToken(user);

        return new AuthResponse(accessToken, refreshToken.getToken());
    }
}
