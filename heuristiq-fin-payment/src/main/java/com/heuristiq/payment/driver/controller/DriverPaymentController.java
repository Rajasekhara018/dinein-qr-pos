package com.heuristiq.payment.driver.controller;

import com.heuristiq.payment.driver.dto.DriverEarningsResponse;
import com.heuristiq.payment.driver.dto.DriverPayoutRequestDto;
import com.heuristiq.payment.driver.dto.DriverPayoutResponse;
import com.heuristiq.payment.driver.service.DriverEarningsService;
import com.heuristiq.payment.driver.service.DriverPayoutService;
import com.heuristiq.payment.security.OwnershipGuard;
import com.heuristiq.payment.shared.dto.ResponseObject;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/payment/drivers")
@RequiredArgsConstructor
public class DriverPaymentController {

    private final DriverPayoutService driverPayoutService;
    private final DriverEarningsService driverEarningsService;

    @PostMapping("/{driverId}/payouts")
    public ResponseEntity<ResponseObject> requestPayout(@PathVariable String driverId,
                                                          @Valid @RequestBody DriverPayoutRequestDto req) {
        OwnershipGuard.requireSelfOrPrivileged(driverId);
        var payout = driverPayoutService.requestPayout(driverId, req);
        return ResponseEntity.ok(ResponseObject.builder().status(true).object(DriverPayoutResponse.from(payout)).build());
    }

    @GetMapping("/{driverId}/payouts")
    public ResponseEntity<ResponseObject> getPayouts(@PathVariable String driverId) {
        OwnershipGuard.requireSelfOrPrivileged(driverId);
        var payouts = driverPayoutService.getPayouts(driverId).stream().map(DriverPayoutResponse::from).toList();
        return ResponseEntity.ok(ResponseObject.builder().status(true).object(payouts).build());
    }

    @GetMapping("/{driverId}/earnings")
    public ResponseEntity<ResponseObject> getEarnings(@PathVariable String driverId,
                                                        @RequestParam(required = false) String rideId) {
        OwnershipGuard.requireSelfOrPrivileged(driverId);
        var earnings = (rideId == null || rideId.isBlank())
                ? driverEarningsService.getEarnings(driverId)
                : driverEarningsService.getEarningsForRide(driverId, rideId);
        var response = earnings.stream().map(DriverEarningsResponse::from).toList();
        return ResponseEntity.ok(ResponseObject.builder().status(true).object(response).build());
    }
}
