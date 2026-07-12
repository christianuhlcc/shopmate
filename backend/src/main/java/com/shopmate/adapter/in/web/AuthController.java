package com.shopmate.adapter.in.web;

import com.shopmate.generated.api.AuthApi;
import com.shopmate.generated.model.AuthCodeRequest;
import com.shopmate.generated.model.TokenResponse;
import com.shopmate.infrastructure.security.AuthCodeService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

// The OpenAPI spec declares `servers: /api`, but the generator does not include
// that base path in the interface mappings — it must be added at class level.
@RestController
@RequestMapping("/api")
public class AuthController implements AuthApi {

    private final AuthCodeService authCodeService;

    public AuthController(AuthCodeService authCodeService) {
        this.authCodeService = authCodeService;
    }

    @Override
    public ResponseEntity<TokenResponse> exchangeAuthCode(@Valid @RequestBody AuthCodeRequest authCodeRequest) {
        String jwt = authCodeService.exchange(authCodeRequest.getCode());
        return ResponseEntity.ok(new TokenResponse(jwt));
    }
}
