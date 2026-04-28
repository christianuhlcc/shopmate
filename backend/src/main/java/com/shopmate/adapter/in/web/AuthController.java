package com.shopmate.adapter.in.web;

import com.shopmate.generated.api.AuthApi;
import com.shopmate.generated.model.AuthCodeRequest;
import com.shopmate.generated.model.TokenResponse;
import com.shopmate.infrastructure.security.AuthCodeService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
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
