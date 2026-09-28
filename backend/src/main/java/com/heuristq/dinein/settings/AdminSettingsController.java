package com.heuristq.dinein.settings;

import com.heuristq.dinein.settings.dto.SettingsDtos.SettingsResponse;
import com.heuristq.dinein.settings.dto.SettingsDtos.UpdateSettingsRequest;
import com.heuristq.dinein.shared.security.CurrentStaff;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/settings")
public class AdminSettingsController {

    private final SettingsService settingsService;

    public AdminSettingsController(SettingsService settingsService) {
        this.settingsService = settingsService;
    }

    /** Readable by managers too (the admin UI needs name/thresholds); only owners may change it. */
    @GetMapping
    public SettingsResponse get() {
        return settingsService.get(CurrentStaff.require().restaurantId());
    }

    @PutMapping
    @PreAuthorize("hasRole('OWNER')")
    public SettingsResponse update(@Valid @RequestBody UpdateSettingsRequest request) {
        return settingsService.update(CurrentStaff.require().restaurantId(), request);
    }
}
