package com.whatsuphouse.backend.domain.user.repository;

import com.whatsuphouse.backend.domain.user.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface UserRepository extends JpaRepository<User, UUID>, UserRepositoryCustom {

    Optional<User> findByEmail(String email);

    Optional<User> findByEmailAndDeleteYn(String email, String deleteYn);

    Optional<User> findFirstByNameAndPhoneAndDeleteYnOrderByCreatedAtDesc(String name, String phone, String deleteYn);

    boolean existsByEmail(String email);

    boolean existsByNickname(String nickname);

    Optional<User> findByIdAndDeletedAtIsNull(UUID id);

    Optional<User> findByIdAndDeletedAtIsNullAndDeleteYn(UUID id, String deleteYn);
}
