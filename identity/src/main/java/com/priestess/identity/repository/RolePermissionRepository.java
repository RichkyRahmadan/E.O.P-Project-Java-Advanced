package com.priestess.identity.repository;

import com.priestess.identity.entity.RoleEntity;
import com.priestess.identity.entity.RolePermissionEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface RolePermissionRepository extends JpaRepository<RolePermissionEntity, UUID> {

    List<RolePermissionEntity> findAllByRole(RoleEntity role);
}
