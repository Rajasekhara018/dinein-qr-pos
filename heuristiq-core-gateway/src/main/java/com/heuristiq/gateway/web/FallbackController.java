package com.heuristiq.gateway.web;

import com.heuristiq.gateway.common.constants.MessageConstants;
import com.heuristiq.gateway.common.dto.GatewayErrorResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * Circuit-breaker fallback endpoints — returns a 503 JSON body instead of propagating
 * a connection-refused or timeout error to the client.
 */
@RestController
@Tag(name = "Circuit Breaker Fallbacks", description = "Invoked internally when a downstream service's circuit breaker is open; not meant to be called directly by API consumers.")
public class FallbackController {

    @Operation(summary = "Admin service fallback",
            description = "Circuit-breaker forward target when the admin service route trips.")
    @ApiResponse(responseCode = "503", description = "Admin service is temporarily unavailable.")
    @RequestMapping("/fallback/admin")
    public Mono<GatewayErrorResponse> adminFallback(ServerWebExchange exchange) {
        exchange.getResponse().setStatusCode(HttpStatus.SERVICE_UNAVAILABLE);
        return Mono.just(GatewayErrorResponse.of(
                MessageConstants.SERVICE_UNAVAILABLE_TITLE,
                "Admin service is temporarily unavailable. Please try again shortly."));
    }

    @Operation(summary = "Bookings service fallback",
            description = "Circuit-breaker forward target when the bookings service route trips.")
    @ApiResponse(responseCode = "503", description = "Bookings service is temporarily unavailable.")
    @RequestMapping("/fallback/bookings")
    public Mono<GatewayErrorResponse> bookingsFallback(ServerWebExchange exchange) {
        exchange.getResponse().setStatusCode(HttpStatus.SERVICE_UNAVAILABLE);
        return Mono.just(GatewayErrorResponse.of(
                MessageConstants.SERVICE_UNAVAILABLE_TITLE,
                "Bookings service is temporarily unavailable. Please try again shortly."));
    }

    @Operation(summary = "IRCTC integration fallback",
            description = "Circuit-breaker forward target when the IRCTC integration route trips.")
    @ApiResponse(responseCode = "503", description = "IRCTC integration is temporarily unavailable.")
    @RequestMapping("/fallback/irctc")
    public Mono<GatewayErrorResponse> irctcFallback(ServerWebExchange exchange) {
        exchange.getResponse().setStatusCode(HttpStatus.SERVICE_UNAVAILABLE);
        return Mono.just(GatewayErrorResponse.of(
                MessageConstants.SERVICE_UNAVAILABLE_TITLE,
                "IRCTC integration is temporarily unavailable. Please try again shortly."));
    }

    @Operation(summary = "UAM service fallback",
            description = "Circuit-breaker forward target when the UAM (User Access Management) service route trips.")
    @ApiResponse(responseCode = "503", description = "UAM service is temporarily unavailable.")
    @RequestMapping("/fallback/uam")
    public Mono<GatewayErrorResponse> uamFallback(ServerWebExchange exchange) {
        exchange.getResponse().setStatusCode(HttpStatus.SERVICE_UNAVAILABLE);
        return Mono.just(GatewayErrorResponse.of(
                MessageConstants.SERVICE_UNAVAILABLE_TITLE,
                "UAM service is temporarily unavailable. Please try again shortly."));
    }

    @Operation(summary = "DMS service fallback",
            description = "Circuit-breaker forward target when the DMS (Document Management System) service route trips.")
    @ApiResponse(responseCode = "503", description = "DMS service is temporarily unavailable.")
    @RequestMapping("/fallback/dms")
    public Mono<GatewayErrorResponse> dmsFallback(ServerWebExchange exchange) {
        exchange.getResponse().setStatusCode(HttpStatus.SERVICE_UNAVAILABLE);
        return Mono.just(GatewayErrorResponse.of(
                MessageConstants.SERVICE_UNAVAILABLE_TITLE,
                "DMS service is temporarily unavailable. Please try again shortly."));
    }

    @Operation(summary = "Notification service fallback",
            description = "Circuit-breaker forward target when the notification (NMS) service route trips.")
    @ApiResponse(responseCode = "503", description = "Notification service is temporarily unavailable.")
    @RequestMapping("/fallback/nms")
    public Mono<GatewayErrorResponse> nmsFallback(ServerWebExchange exchange) {
        exchange.getResponse().setStatusCode(HttpStatus.SERVICE_UNAVAILABLE);
        return Mono.just(GatewayErrorResponse.of(
                MessageConstants.SERVICE_UNAVAILABLE_TITLE,
                "Notification service is temporarily unavailable. Please try again shortly."));
    }

    @Operation(summary = "Payment service fallback",
            description = "Circuit-breaker forward target when the payment service route trips.")
    @ApiResponse(responseCode = "503", description = "Payment service is temporarily unavailable.")
    @RequestMapping("/fallback/payment")
    public Mono<GatewayErrorResponse> paymentFallback(ServerWebExchange exchange) {
        exchange.getResponse().setStatusCode(HttpStatus.SERVICE_UNAVAILABLE);
        return Mono.just(GatewayErrorResponse.of(
                MessageConstants.SERVICE_UNAVAILABLE_TITLE,
                "Payment service is temporarily unavailable. Please try again shortly."));
    }

    @Operation(summary = "Search service fallback",
            description = "Circuit-breaker forward target when the search service route trips.")
    @ApiResponse(responseCode = "503", description = "Search service is temporarily unavailable.")
    @RequestMapping("/fallback/search")
    public Mono<GatewayErrorResponse> searchFallback(ServerWebExchange exchange) {
        exchange.getResponse().setStatusCode(HttpStatus.SERVICE_UNAVAILABLE);
        return Mono.just(GatewayErrorResponse.of(
                MessageConstants.SERVICE_UNAVAILABLE_TITLE,
                "Search service is temporarily unavailable. Please try again shortly."));
    }

    @Operation(summary = "Rider Pricing service fallback",
            description = "Circuit-breaker forward target when the rider-pricing service route trips.")
    @ApiResponse(responseCode = "503", description = "Rider Pricing service is temporarily unavailable.")
    @RequestMapping("/fallback/rider-pricing")
    public Mono<GatewayErrorResponse> riderPricingFallback(ServerWebExchange exchange) {
        exchange.getResponse().setStatusCode(HttpStatus.SERVICE_UNAVAILABLE);
        return Mono.just(GatewayErrorResponse.of(
                MessageConstants.SERVICE_UNAVAILABLE_TITLE,
                "Rider Pricing service is temporarily unavailable. Please try again shortly."));
    }

    @Operation(summary = "Rider Location service fallback",
            description = "Circuit-breaker forward target when the rider-location service route trips.")
    @ApiResponse(responseCode = "503", description = "Rider Location service is temporarily unavailable.")
    @RequestMapping("/fallback/rider-location")
    public Mono<GatewayErrorResponse> riderLocationFallback(ServerWebExchange exchange) {
        exchange.getResponse().setStatusCode(HttpStatus.SERVICE_UNAVAILABLE);
        return Mono.just(GatewayErrorResponse.of(
                MessageConstants.SERVICE_UNAVAILABLE_TITLE,
                "Rider Location service is temporarily unavailable. Please try again shortly."));
    }

    @Operation(summary = "Rider Dispatch service fallback",
            description = "Circuit-breaker forward target when the rider-dispatch service route trips.")
    @ApiResponse(responseCode = "503", description = "Rider Dispatch service is temporarily unavailable.")
    @RequestMapping("/fallback/rider-dispatch")
    public Mono<GatewayErrorResponse> riderDispatchFallback(ServerWebExchange exchange) {
        exchange.getResponse().setStatusCode(HttpStatus.SERVICE_UNAVAILABLE);
        return Mono.just(GatewayErrorResponse.of(
                MessageConstants.SERVICE_UNAVAILABLE_TITLE,
                "Rider Dispatch service is temporarily unavailable. Please try again shortly."));
    }

    @Operation(summary = "Rider Ride service fallback",
            description = "Circuit-breaker forward target when the rider-ride service route trips.")
    @ApiResponse(responseCode = "503", description = "Rider Ride service is temporarily unavailable.")
    @RequestMapping("/fallback/rider-ride")
    public Mono<GatewayErrorResponse> riderRideFallback(ServerWebExchange exchange) {
        exchange.getResponse().setStatusCode(HttpStatus.SERVICE_UNAVAILABLE);
        return Mono.just(GatewayErrorResponse.of(
                MessageConstants.SERVICE_UNAVAILABLE_TITLE,
                "Rider Ride service is temporarily unavailable. Please try again shortly."));
    }

    @Operation(summary = "Public trip tracking fallback",
            description = "Circuit-breaker forward target when the public live-trip tracking route (its own breaker) trips.")
    @ApiResponse(responseCode = "503", description = "Trip tracking is temporarily unavailable.")
    @RequestMapping("/fallback/rider-ride-public-track")
    public Mono<GatewayErrorResponse> riderRidePublicTrackFallback(ServerWebExchange exchange) {
        exchange.getResponse().setStatusCode(HttpStatus.SERVICE_UNAVAILABLE);
        return Mono.just(GatewayErrorResponse.of(
                MessageConstants.SERVICE_UNAVAILABLE_TITLE,
                "Trip tracking is temporarily unavailable. Please try again shortly."));
    }

    @Operation(summary = "Rider Payment service fallback",
            description = "Circuit-breaker forward target when the rider-payment service route trips.")
    @ApiResponse(responseCode = "503", description = "Rider Payment service is temporarily unavailable.")
    @RequestMapping("/fallback/rider-payment")
    public Mono<GatewayErrorResponse> riderPaymentFallback(ServerWebExchange exchange) {
        exchange.getResponse().setStatusCode(HttpStatus.SERVICE_UNAVAILABLE);
        return Mono.just(GatewayErrorResponse.of(
                MessageConstants.SERVICE_UNAVAILABLE_TITLE,
                "Rider Payment service is temporarily unavailable. Please try again shortly."));
    }

    @Operation(summary = "Rider Customer service fallback",
            description = "Circuit-breaker forward target when the rider-customer service route trips.")
    @ApiResponse(responseCode = "503", description = "Rider Customer service is temporarily unavailable.")
    @RequestMapping("/fallback/rider-customer")
    public Mono<GatewayErrorResponse> riderCustomerFallback(ServerWebExchange exchange) {
        exchange.getResponse().setStatusCode(HttpStatus.SERVICE_UNAVAILABLE);
        return Mono.just(GatewayErrorResponse.of(
                MessageConstants.SERVICE_UNAVAILABLE_TITLE,
                "Rider Customer service is temporarily unavailable. Please try again shortly."));
    }

    @Operation(summary = "Rider Onboarding service fallback",
            description = "Circuit-breaker forward target when the rider-onboarding service route trips.")
    @ApiResponse(responseCode = "503", description = "Rider Onboarding service is temporarily unavailable.")
    @RequestMapping("/fallback/rider-onboarding")
    public Mono<GatewayErrorResponse> riderOnboardingFallback(ServerWebExchange exchange) {
        exchange.getResponse().setStatusCode(HttpStatus.SERVICE_UNAVAILABLE);
        return Mono.just(GatewayErrorResponse.of(
                MessageConstants.SERVICE_UNAVAILABLE_TITLE,
                "Rider Onboarding service is temporarily unavailable. Please try again shortly."));
    }

    @Operation(summary = "Rider Driver Earnings service fallback",
            description = "Circuit-breaker forward target when the rider-driver-earnings service route trips.")
    @ApiResponse(responseCode = "503", description = "Rider Driver Earnings service is temporarily unavailable.")
    @RequestMapping("/fallback/rider-driver-earnings")
    public Mono<GatewayErrorResponse> riderDriverEarningsFallback(ServerWebExchange exchange) {
        exchange.getResponse().setStatusCode(HttpStatus.SERVICE_UNAVAILABLE);
        return Mono.just(GatewayErrorResponse.of(
                MessageConstants.SERVICE_UNAVAILABLE_TITLE,
                "Rider Driver Earnings service is temporarily unavailable. Please try again shortly."));
    }

    @Operation(summary = "Rider Audit service fallback",
            description = "Circuit-breaker forward target when the rider-audit service route trips.")
    @ApiResponse(responseCode = "503", description = "Rider Audit service is temporarily unavailable.")
    @RequestMapping("/fallback/rider-audit")
    public Mono<GatewayErrorResponse> riderAuditFallback(ServerWebExchange exchange) {
        exchange.getResponse().setStatusCode(HttpStatus.SERVICE_UNAVAILABLE);
        return Mono.just(GatewayErrorResponse.of(
                MessageConstants.SERVICE_UNAVAILABLE_TITLE,
                "Rider Audit service is temporarily unavailable. Please try again shortly."));
    }

    @Operation(summary = "Rider Reports service fallback",
            description = "Circuit-breaker forward target when the rider-reports service route trips.")
    @ApiResponse(responseCode = "503", description = "Rider Reports service is temporarily unavailable.")
    @RequestMapping("/fallback/rider-reports")
    public Mono<GatewayErrorResponse> riderReportsFallback(ServerWebExchange exchange) {
        exchange.getResponse().setStatusCode(HttpStatus.SERVICE_UNAVAILABLE);
        return Mono.just(GatewayErrorResponse.of(
                MessageConstants.SERVICE_UNAVAILABLE_TITLE,
                "Rider Reports service is temporarily unavailable. Please try again shortly."));
    }

    @Operation(summary = "Rider Wallet service fallback",
            description = "Circuit-breaker forward target when the rider-wallet service route trips.")
    @ApiResponse(responseCode = "503", description = "Rider Wallet service is temporarily unavailable.")
    @RequestMapping("/fallback/rider-wallet")
    public Mono<GatewayErrorResponse> riderWalletFallback(ServerWebExchange exchange) {
        exchange.getResponse().setStatusCode(HttpStatus.SERVICE_UNAVAILABLE);
        return Mono.just(GatewayErrorResponse.of(
                MessageConstants.SERVICE_UNAVAILABLE_TITLE,
                "Rider Wallet service is temporarily unavailable. Please try again shortly."));
    }

    @Operation(summary = "Rider Accounting service fallback",
            description = "Circuit-breaker forward target when the rider-accounting service route trips.")
    @ApiResponse(responseCode = "503", description = "Rider Accounting service is temporarily unavailable.")
    @RequestMapping("/fallback/rider-accounting")
    public Mono<GatewayErrorResponse> riderAccountingFallback(ServerWebExchange exchange) {
        exchange.getResponse().setStatusCode(HttpStatus.SERVICE_UNAVAILABLE);
        return Mono.just(GatewayErrorResponse.of(
                MessageConstants.SERVICE_UNAVAILABLE_TITLE,
                "Rider Accounting service is temporarily unavailable. Please try again shortly."));
    }

    @Operation(summary = "Franchise service fallback",
            description = "Circuit-breaker forward target when the franchise service route trips.")
    @ApiResponse(responseCode = "503", description = "Franchise service is temporarily unavailable.")
    @RequestMapping("/fallback/franchise")
    public Mono<GatewayErrorResponse> franchiseFallback(ServerWebExchange exchange) {
        exchange.getResponse().setStatusCode(HttpStatus.SERVICE_UNAVAILABLE);
        return Mono.just(GatewayErrorResponse.of(
                MessageConstants.SERVICE_UNAVAILABLE_TITLE,
                "Franchise service is temporarily unavailable. Please try again shortly."));
    }

    @Operation(summary = "Food delivery admin API fallback",
            description = "Circuit-breaker forward target when the food API route trips.")
    @ApiResponse(responseCode = "503", description = "Food delivery service is temporarily unavailable.")
    @RequestMapping("/fallback/food")
    public Mono<GatewayErrorResponse> foodFallback(ServerWebExchange exchange) {
        exchange.getResponse().setStatusCode(HttpStatus.SERVICE_UNAVAILABLE);
        return Mono.just(GatewayErrorResponse.of(
                MessageConstants.SERVICE_UNAVAILABLE_TITLE,
                "Food delivery service is temporarily unavailable. Please try again shortly."));
    }

    @Operation(summary = "Rider Config service fallback",
            description = "Circuit-breaker forward target when the rider-config service route trips.")
    @ApiResponse(responseCode = "503", description = "Rider Config service is temporarily unavailable.")
    @RequestMapping("/fallback/rider-config")
    public Mono<GatewayErrorResponse> riderConfigFallback(ServerWebExchange exchange) {
        exchange.getResponse().setStatusCode(HttpStatus.SERVICE_UNAVAILABLE);
        return Mono.just(GatewayErrorResponse.of(
                MessageConstants.SERVICE_UNAVAILABLE_TITLE,
                "Rider Config service is temporarily unavailable. Please try again shortly."));
    }

    @Operation(summary = "Rider Train service fallback",
            description = "Circuit-breaker forward target when the rider-train service route trips.")
    @ApiResponse(responseCode = "503", description = "Rider Train service is temporarily unavailable.")
    @RequestMapping("/fallback/rider-train")
    public Mono<GatewayErrorResponse> riderTrainFallback(ServerWebExchange exchange) {
        exchange.getResponse().setStatusCode(HttpStatus.SERVICE_UNAVAILABLE);
        return Mono.just(GatewayErrorResponse.of(
                MessageConstants.SERVICE_UNAVAILABLE_TITLE,
                "Rider Train service is temporarily unavailable. Please try again shortly."));
    }

    @Operation(summary = "Rider Commerce service fallback",
            description = "Circuit-breaker forward target when the rider-commerce service route trips.")
    @ApiResponse(responseCode = "503", description = "Rider Commerce service is temporarily unavailable.")
    @RequestMapping("/fallback/rider-commerce")
    public Mono<GatewayErrorResponse> riderCommerceFallback(ServerWebExchange exchange) {
        exchange.getResponse().setStatusCode(HttpStatus.SERVICE_UNAVAILABLE);
        return Mono.just(GatewayErrorResponse.of(
                MessageConstants.SERVICE_UNAVAILABLE_TITLE,
                "The shop is temporarily unavailable. Please try again shortly."));
    }

    @Operation(summary = "Matrimony service fallback",
            description = "Circuit-breaker forward target when the matrimony service route trips.")
    @ApiResponse(responseCode = "503", description = "Matrimony service is temporarily unavailable.")
    @RequestMapping("/fallback/matrimony")
    public Mono<GatewayErrorResponse> matrimonyFallback(ServerWebExchange exchange) {
        exchange.getResponse().setStatusCode(HttpStatus.SERVICE_UNAVAILABLE);
        return Mono.just(GatewayErrorResponse.of(
                MessageConstants.SERVICE_UNAVAILABLE_TITLE,
                "Matrimony service is temporarily unavailable. Please try again shortly."));
    }

    @Operation(summary = "Rider Workflow service fallback",
            description = "Circuit-breaker forward target when the rider-workflow service route trips.")
    @ApiResponse(responseCode = "503", description = "Rider Workflow service is temporarily unavailable.")
    @RequestMapping("/fallback/rider-workflow")
    public Mono<GatewayErrorResponse> riderWorkflowFallback(ServerWebExchange exchange) {
        exchange.getResponse().setStatusCode(HttpStatus.SERVICE_UNAVAILABLE);
        return Mono.just(GatewayErrorResponse.of(
                MessageConstants.SERVICE_UNAVAILABLE_TITLE,
                "Rider Workflow service is temporarily unavailable. Please try again shortly."));
    }
}
