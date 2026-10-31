package com.nintails.ecommerce.repository;

import com.nintails.ecommerce.entity.User;
import com.nintails.ecommerce.entity.User.Role;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.List;

@Repository
public interface UserRepository extends JpaRepository<User, Long> {
    Optional<User> findByEmail(String email);
    boolean existsByEmail(String email);

    long countByRole(Role role);

    Page<User> findByRole(Role role, Pageable pageable);

    List<User> findByRoleOrderByFullNameAsc(Role role);

    Optional<User> findByIdAndRole(Long id, Role role);

    List<User> findByDeliveryStore_Owner_IdOrderByFullNameAsc(Long ownerId);

    List<User> findByDeliveryWarehouse_IdOrderByFullNameAsc(Long warehouseId);

    List<User> findByDeliveryWarehouse_IdAndRoleAndDeliveryAgentActiveTrueOrderByCreatedAtAsc(
            Long warehouseId, Role role);

    boolean existsByPhoneNumberAndPhoneVerifiedTrue(String phoneNumber);
}
