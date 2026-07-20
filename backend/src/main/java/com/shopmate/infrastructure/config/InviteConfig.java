package com.shopmate.infrastructure.config;

import com.shopmate.application.service.InviteService;
import com.shopmate.domain.port.out.GroupRepository;
import com.shopmate.domain.port.out.InviteCodeRepository;
import com.shopmate.domain.port.out.UserRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Wires {@link InviteService} as a Spring bean. It isn't a {@code @Service}
 * itself because it needs the bootstrap-code property as a plain constructor
 * argument, which type-based autowiring can't resolve; the {@code @Value}
 * lookup happens here in infrastructure, keeping the service class itself
 * framework-free beyond what the rest of {@code application/service} does.
 */
@Configuration
public class InviteConfig {

    @Bean
    public InviteService inviteService(InviteCodeRepository inviteCodeRepository,
                                        UserRepository userRepository,
                                        GroupRepository groupRepository,
                                        @Value("${shopmate.invites.bootstrap-code:}") String bootstrapCode) {
        return new InviteService(inviteCodeRepository, userRepository, groupRepository, bootstrapCode);
    }
}
