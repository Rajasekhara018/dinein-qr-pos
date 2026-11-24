package com.heuristiq.paymentservice.payment.dto.admin;

import java.util.List;

/** Page envelope for GET /api/v1/internal/admin/payments — same shape as Rider_RideService's AdminRidePage. */
public record AdminPaymentPage(List<AdminPayment> content, long totalElements, int totalPages,
                               int currentPage, int pageSize) {
}
