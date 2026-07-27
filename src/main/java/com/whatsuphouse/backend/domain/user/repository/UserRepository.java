package com.whatsuphouse.backend.domain.user.repository;

import com.whatsuphouse.backend.domain.user.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface UserRepository extends JpaRepository<User, UUID>, UserRepositoryCustom {

    Optional<User> findByEmail(String email);

    Optional<User> findByEmailAndDeletedAtIsNull(String email);

    Optional<User> findFirstByNameAndPhoneAndDeletedAtIsNullOrderByCreatedAtDesc(String name, String phone);

    boolean existsByEmail(String email);

    boolean existsByNickname(String nickname);

    Optional<User> findByIdAndDeletedAtIsNull(UUID id);
}
