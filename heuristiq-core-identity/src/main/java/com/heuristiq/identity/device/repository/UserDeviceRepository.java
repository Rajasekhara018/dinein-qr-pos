package com.heuristiq.uam.device.repository;

import com.heuristiq.uam.device.UserDevice;

import java.util.List;
import java.util.Optional;

public interface UserDeviceRepository {
    UserDevice save(UserDevice device);

    Optional<UserDevice> findById(String id);

    List<UserDevice> findByUserId(String userId);

    void delete(UserDevice device);
}
