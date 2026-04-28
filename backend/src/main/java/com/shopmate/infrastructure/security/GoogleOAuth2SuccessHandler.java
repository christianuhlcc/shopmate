package com.shopmate.infrastructure.security;

import com.shopmate.domain.model.User;
import com.shopmate.domain.port.out.UserRepository;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.UUID;

@Component
public class GoogleOAuth2SuccessHandler implements AuthenticationSuccessHandler {

    private final UserRepository userRepository;
    private final AuthCodeService authCodeService;
    private final SecretKey secretKey;
    private final String frontendBaseUrl;

    public GoogleOAuth2SuccessHandler(
            UserRepository userRepository,
            AuthCodeService authCodeService,
            @Value("${shopmate.jwt.secret}") String jwtSecret,
            @Value("${shopmate.frontend.base-url}") String frontendBaseUrl) {
        this.userRepository = userRepository;
        this.authCodeService = authCodeService;
        this.secretKey = Keys.hmacShaKeyFor(jwtSecret.getBytes(StandardCharsets.UTF_8));
        this.frontendBaseUrl = frontendBaseUrl;
    }

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request,
                                        HttpServletResponse response,
                                        Authentication authentication) throws IOException, ServletException {
        OAuth2User oauth2User = (OAuth2User) authentication.getPrincipal();

        String email = oauth2User.getAttribute("email");
        String name = oauth2User.getAttribute("name");
        String picture = oauth2User.getAttribute("picture");

        User user = userRepository.findByEmail(email)
                .map(existing -> userRepository.save(new User(existing.id(), email, name, picture)))
                .orElseGet(() -> userRepository.save(new User(UUID.randomUUID(), email, name, picture)));

        long now = System.currentTimeMillis();
        long exp = now + 24L * 60 * 60 * 1000; // 24 hours
        String jwt = Jwts.builder()
                .subject(user.id().toString())
                .issuedAt(new Date(now))
                .expiration(new Date(exp))
                .signWith(secretKey)
                .compact();

        String code = authCodeService.issueCode(jwt);
        response.sendRedirect(frontendBaseUrl + "/auth/callback?code=" + code);
    }
}
