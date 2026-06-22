package com.priestess.identity.repository;

import com.priestess.identity.entity.MenuEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface MenuRepository extends JpaRepository<MenuEntity, UUID> {

    Optional<MenuEntity> findByMenuName(String menuName);
}
