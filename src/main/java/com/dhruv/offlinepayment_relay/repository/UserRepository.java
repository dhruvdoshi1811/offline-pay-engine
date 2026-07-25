package com.dhruv.offlinepayment_relay.repository;

import com.dhruv.offlinepayment_relay.entity.Role;
import com.dhruv.offlinepayment_relay.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface UserRepository extends JpaRepository<User, UUID> {

    Optional<User> findByEmail(String email);

    boolean existsByEmail(String email);

    boolean existsByRole(Role role);
}
