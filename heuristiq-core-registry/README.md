# Rider Service Registry

Copied into `bestride-rider-app` from `2026_RailCab_ServiceRegistry` (untouched, no README
existed there). A Eureka server (Spring Boot 4.0.7 — pinned like `Rider_ApiGateway`, since
Spring Cloud 2025.1.0's CompatibilityVerifier hard-fails on Boot 4.1.0). Port `${REGISTRY_PORT:8761}`,
Basic-Auth protected (`spring.security.user.name`/`.password`, sourced from
`EUREKA_USERNAME`/`EUREKA_PASSWORD`).

None of the 5 rider microservices (Pricing/Location/Dispatch/Ride/Payment) register with this
yet — they're routed statically from `Rider_ApiGateway` (see its
`railcab.gateway.rider-services.discovery-enabled` flag, default `false`). Adding
`spring-cloud-starter-netflix-eureka-client` to each and pointing them at this registry's
`eureka.client.service-url.defaultZone` is the natural next step toward real service discovery
instead of hardcoded static URLs. `Rider_UAM`/`Rider_DMS`/`Rider_NMS`/`Rider_SearchAPI` already
register here out of the box (same Eureka client config as their source repo).
