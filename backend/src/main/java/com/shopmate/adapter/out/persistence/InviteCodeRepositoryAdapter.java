package com.shopmate.adapter.out.persistence;

import com.shopmate.adapter.out.persistence.entity.InviteCodeEntity;
import com.shopmate.adapter.out.persistence.repository.SpringDataInviteCodeRepository;
import com.shopmate.domain.model.InviteCode;
import com.shopmate.domain.model.InviteType;
import com.shopmate.domain.port.out.InviteCodeRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@Component
public class InviteCodeRepositoryAdapter implements InviteCodeRepository {

    private final SpringDataInviteCodeRepository jpaRepository;

    public InviteCodeRepositoryAdapter(SpringDataInviteCodeRepository jpaRepository) {
        this.jpaRepository = jpaRepository;
    }

    @Override
    public Optional<InviteCode> findByCode(String code) {
        return jpaRepository.findByCode(code).map(this::toDomain);
    }

    @Override
    public InviteCode save(InviteCode inviteCode) {
        InviteCodeEntity entity = jpaRepository.findById(inviteCode.id())
            .orElseGet(() -> new InviteCodeEntity(inviteCode.id(), inviteCode.code(), inviteCode.type().name(),
                inviteCode.groupId(), inviteCode.createdBy(), inviteCode.createdAt(), inviteCode.expiresAt(),
                inviteCode.usedBy(), inviteCode.usedAt()));
        return toDomain(jpaRepository.save(entity));
    }

    @Override
    @Transactional
    public boolean markUsed(UUID inviteId, UUID usedBy, Instant usedAt) {
        return jpaRepository.markUsed(inviteId, usedBy, usedAt) > 0;
    }

    private InviteCode toDomain(InviteCodeEntity e) {
        return new InviteCode(e.getId(), e.getCode(), InviteType.valueOf(e.getType()), e.getGroupId(),
            e.getCreatedBy(), e.getCreatedAt(), e.getExpiresAt(), e.getUsedBy(), e.getUsedAt());
    }
}
