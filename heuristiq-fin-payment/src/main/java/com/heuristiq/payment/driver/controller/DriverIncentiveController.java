package com.heuristiq.paymentservice.driver.controller;

import com.heuristiq.paymentservice.driver.dto.DriverIncentiveResponse;
import com.heuristiq.paymentservice.driver.service.DriverIncentiveService;
import com.heuristiq.paymentservice.security.OwnershipGuard;
import com.heuristiq.paymentservice.shared.dto.ResponseObject;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Ported from Rider_BookingsAPI's {@code DriverIncentiveController} (there mounted at
 * {@code /driver/incentives}), adapted to this service's own {@code /drivers/{driverId}/...}
 * convention (matching DriverPaymentController's payouts/earnings endpoints).
 */
@RestController
@RequestMapping("/api/v1/payment/drivers")
@RequiredArgsConstructor
public class DriverIncentiveController {

    private final DriverIncentiveService driverIncentiveService;

    /** Award endpoint — mirrors the monolith's POST /driver/incentives/calculate/{driverId}. */
    @PostMapping("/{driverId}/incentives/calculate")
    public ResponseEntity<ResponseObject> calculateAndAwardIncentives(@PathVariable String driverId) {
        OwnershipGuard.requireSelfOrPrivileged(driverId);
        var awarded = driverIncentiveService.calculateAndAwardIncentives(driverId).stream()
                .map(DriverIncentiveResponse::from)
                .toList();
        return ResponseEntity.ok(ResponseObject.builder().status(true).object(awarded).build());
    }

    @GetMapping("/{driverId}/incentives")
    public ResponseEntity<ResponseObject> getIncentives(@PathVariable String driverId) {
        OwnershipGuard.requireSelfOrPrivileged(driverId);
        var incentives = driverIncentiveService.getIncentives(driverId).stream()
                .map(DriverIncentiveResponse::from)
                .toList();
        return ResponseEntity.ok(ResponseObject.builder().status(true).object(incentives).build());
    }
}
