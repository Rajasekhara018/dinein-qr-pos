package com.heuristiq.payment.driver.service;

import com.heuristiq.payment.driver.model.DriverPayment;
import com.heuristiq.payment.driver.repository.DriverPaymentRepository;
import com.heuristiq.payment.security.TenantAccess;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Reads the driver payment records ({@code driver_payments}) Payment wrote before the accounting rebuild. Payment no longer writes
 * them: a ride's sale, with the driver's share, is booked once in Rider_WalletService (docs/ACCOUNTING_REBUILD_PLAN.md, section
 * 4), and the driver screens read Wallet. The old rows stay readable for history, each caller seeing only its organization's.
 */
@Service
@RequiredArgsConstructor
public class DriverEarningsService {

    private final DriverPaymentRepository driverPaymentRepository;

    /** The driver's records, newest first, of the caller's organization. */
    public List<DriverPayment> getEarnings(String driverId) {
        return driverPaymentRepository.findByDriverIdOrderByCreatedAtDesc(driverId).stream()
                .filter(row -> TenantAccess.canSee(row.getTenantId())).toList();
    }

    /** The driver's records for one ride, of the caller's organization. */
    public List<DriverPayment> getEarningsForRide(String driverId, String rideId) {
        return driverPaymentRepository.findByDriverIdAndRideId(driverId, rideId).stream()
                .filter(row -> TenantAccess.canSee(row.getTenantId())).toList();
    }
}
