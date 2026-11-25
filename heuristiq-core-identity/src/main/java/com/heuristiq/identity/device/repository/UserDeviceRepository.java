package com.heuristiq.identity.device.repository;

import com.heuristiq.identity.device.UserDevice;

import java.util.List;
import java.util.Optional;

public interface UserDeviceRepository {
    UserDevice save(UserDevice device);

    Optional<UserDevice> findById(String id);

    List<UserDevice> findByUserId(String userId);

    void delete(UserDevice device);
}
