package com.billme.auth;

import com.billme.auth.token.RefreshToken;
import com.billme.auth.token.RefreshTokenService;
import com.billme.customer.CustomerProfile;
import com.billme.repository.CustomerProfileRepository;
import com.billme.merchant.MerchantProfile;
import com.billme.repository.MerchantProfileRepository;
import com.billme.repository.UserRepository;
import com.billme.security.jwt.JwtService;
import com.billme.user.Role;
import com.billme.user.User;
import com.billme.wallet.Wallet;
import com.billme.repository.WalletRepository;

import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

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
            throw new RuntimeException("Email already registered");
        }

        User user = new User();
        user.setEmail(request.getEmail());
        user.setPassword(passwordEncoder.encode(request.getPassword()));
        user.setRole(Role.CUSTOMER);
        user.setActive(true);

        userRepository.save(user);

        CustomerProfile profile = new CustomerProfile();
        profile.setUser(user);
        profile.setName(request.getName());
        profile.setFaceEmbeddings(request.getFaceEmbeddings());

        customerProfileRepository.save(profile);

        Wallet wallet = new Wallet();
        wallet.setUser(user);
        wallet.setBalance(BigDecimal.ZERO);

        walletRepository.save(wallet);

        return new AuthResponse("CUSTOMER_REGISTERED", null);
    }

    // ================= MERCHANT REGISTER =================
    @Transactional
    public AuthResponse registerMerchant(MerchantRegisterRequest request) {

        if (userRepository.findByEmail(request.getEmail()).isPresent()) {
            throw new RuntimeException("Email already registered");
        }

        User user = new User();
        user.setEmail(request.getEmail());
        user.setPassword(passwordEncoder.encode(request.getPassword()));
        user.setRole(Role.MERCHANT);
        user.setActive(true);

        userRepository.save(user);

        MerchantProfile profile = new MerchantProfile();
        profile.setUser(user);
        profile.setBusinessName(request.getBusinessName());
        profile.setOwnerName(request.getOwnerName());
        profile.setPhone(request.getPhone());
        profile.setAddress(request.getAddress());
        profile.setUpiId(request.getUpiId());
        profile.setProfileCompleted(false);

        merchantProfileRepository.save(profile);

        Wallet wallet = new Wallet();
        wallet.setUser(user);
        wallet.setBalance(BigDecimal.ZERO);

        walletRepository.save(wallet);

        return new AuthResponse("MERCHANT_REGISTERED", null);
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
                .orElseThrow(() -> new RuntimeException("User not found"));

        // ✅ Correct method usage
        String accessToken = jwtService.generateAccessToken(
                user.getEmail(),
                user.getRole().name()
        );

        RefreshToken refreshToken =
                refreshTokenService.createRefreshToken(user);

        return new AuthResponse(accessToken, refreshToken.getToken());
    }
}
