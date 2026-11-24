package com.heuristq.dinein.kiosk;

import com.heuristq.dinein.kiosk.dto.KioskDtos.BrandingResponse;
import com.heuristq.dinein.kiosk.dto.KioskDtos.CreateDeviceRequest;
import com.heuristq.dinein.kiosk.dto.KioskDtos.KioskDeviceView;
import com.heuristq.dinein.kiosk.dto.KioskDtos.PairingCodeResponse;
import com.heuristq.dinein.kiosk.dto.KioskDtos.UpdateBrandingRequest;
import com.heuristq.dinein.kiosk.dto.KioskDtos.UpsellRuleRequest;
import com.heuristq.dinein.kiosk.dto.KioskDtos.UpsellRuleView;
import com.heuristq.dinein.shared.security.CurrentStaff;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** Owner / manager screens for kiosks: register and pair devices, and set how the kiosk looks. */
@RestController
@RequestMapping("/api/v1/admin")
public class AdminKioskController {

    private final KioskDeviceService deviceService;
    private final KioskBrandingService brandingService;
    private final KioskUpsellService upsellService;

    public AdminKioskController(KioskDeviceService deviceService, KioskBrandingService brandingService,
                                KioskUpsellService upsellService) {
        this.deviceService = deviceService;
        this.brandingService = brandingService;
        this.upsellService = upsellService;
    }

    @GetMapping("/kiosk-devices")
    public List<KioskDeviceView> list() {
        return deviceService.list();
    }

    /** Returns the one-time pairing code. It is not retrievable afterwards; issue a new one if it is lost. */
    @PostMapping("/kiosk-devices")
    public PairingCodeResponse create(@Valid @RequestBody CreateDeviceRequest request) {
        return deviceService.create(request.name());
    }

    @PostMapping("/kiosk-devices/{id}/pairing-code")
    public PairingCodeResponse reissueCode(@PathVariable Long id) {
        return deviceService.reissueCode(id);
    }

    @PostMapping("/kiosk-devices/{id}/revoke")
    public ResponseEntity<Void> revoke(@PathVariable Long id) {
        deviceService.revoke(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/kiosk-upsells")
    public List<UpsellRuleView> upsells() {
        return upsellService.list();
    }

    @PostMapping("/kiosk-upsells")
    public UpsellRuleView createUpsell(@Valid @RequestBody UpsellRuleRequest request) {
        return upsellService.create(request);
    }

    @PutMapping("/kiosk-upsells/{id}")
    public UpsellRuleView updateUpsell(@PathVariable Long id, @Valid @RequestBody UpsellRuleRequest request) {
        return upsellService.update(id, request);
    }

    @DeleteMapping("/kiosk-upsells/{id}")
    public ResponseEntity<Void> deleteUpsell(@PathVariable Long id) {
        upsellService.delete(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/kiosk-branding")
    public BrandingResponse branding() {
        return brandingService.get(CurrentStaff.require().restaurantId());
    }

    @PutMapping("/kiosk-branding")
    public BrandingResponse updateBranding(@Valid @RequestBody UpdateBrandingRequest request) {
        return brandingService.update(request);
    }
}
