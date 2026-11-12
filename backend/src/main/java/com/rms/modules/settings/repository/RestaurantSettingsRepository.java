package com.rms.modules.settings.repository;

import com.rms.modules.settings.entity.RestaurantSettings;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RestaurantSettingsRepository extends JpaRepository<RestaurantSettings, Byte> {
}
