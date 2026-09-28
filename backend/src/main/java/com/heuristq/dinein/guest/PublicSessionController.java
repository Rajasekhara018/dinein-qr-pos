package com.heuristq.dinein.guest;

import com.heuristq.dinein.order.domain.OrderRepository;
import com.heuristq.dinein.order.domain.OrderStatus;
import com.heuristq.dinein.settings.SettingsService;
import com.heuristq.dinein.settings.dto.SettingsDtos.PublicRestaurantInfo;
import com.heuristq.dinein.shared.exception.ApiException;
import com.heuristq.dinein.shared.security.CookieFactory;
import com.heuristq.dinein.table.TableService;
import com.heuristq.dinein.table.domain.DiningTableEntity;
import com.heuristq.dinein.table.domain.DiningTableRepository;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.Optional;

@RestController
@RequestMapping("/api/v1/public/session")
public class PublicSessionController {

    public record TableInfo(Long id, String label) {
    }

    public record SessionResponse(TableInfo table, PublicRestaurantInfo restaurant, Instant expiresAt) {
    }

    private final GuestSessionService guestSessionService;
    private final TableService tableService;
    private final DiningTableRepository tableRepository;
    private final OrderRepository orderRepository;
    private final SettingsService settingsService;
    private final CookieFactory cookieFactory;

    public PublicSessionController(GuestSessionService guestSessionService, TableService tableService,
                                   DiningTableRepository tableRepository, OrderRepository orderRepository,
                                   SettingsService settingsService, CookieFactory cookieFactory) {
        this.guestSessionService = guestSessionService;
        this.tableService = tableService;
        this.tableRepository = tableRepository;
        this.orderRepository = orderRepository;
        this.settingsService = settingsService;
        this.cookieFactory = cookieFactory;
    }

    /**
     * With {@code t}: validates the table QR token and issues (or renews) the guest cookie.
     * Without {@code t}: resumes from an existing cookie (for reloads after the query string is gone).
     */
    @GetMapping
    public ResponseEntity<SessionResponse> session(@RequestParam(name = "t", required = false) String qrToken,
                                                   HttpServletRequest request) {
        Optional<GuestSession> existing = guestSessionService.fromRequest(request);
        DiningTableEntity table;
        GuestSession session;
        if (qrToken != null && !qrToken.isBlank()) {
            table = tableService.findActiveByToken(qrToken.trim()).orElseThrow(PublicSessionController::invalidTable);
            boolean alreadySeatedHere = existing.isPresent() && existing.get().tableId().equals(table.getId());
            // A device without a session for THIS table is a fresh arrival: if another guest session already has
            // an active order there, refuse rather than start a second, colliding self-service session for the same
            // physical table. Staff-placed orders don't count (see the repository method) -- a waiter is already
            // at the table, so a guest's own phone joining in isn't the "two different groups" case this guards
            // against. A device that already holds this table's session (adding a second round, or reloading) is
            // unaffected -- it's not a "fresh" scan.
            if (!alreadySeatedHere
                    && orderRepository.existsByTableIdAndStatusInAndGuestSessionIdIsNotNull(table.getId(), OrderStatus.KITCHEN_VISIBLE)) {
                throw new ApiException(HttpStatus.CONFLICT, "TABLE_OCCUPIED",
                        "This table is currently occupied. Please ask a staff member for help.");
            }
            session = existing.map(s -> guestSessionService.renew(s, table.getId()))
                    .orElseGet(() -> guestSessionService.newSession(table.getId()));
        } else {
            session = existing.orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "GUEST_SESSION_REQUIRED",
                    "Please scan the QR code on your table to continue"));
            table = tableRepository.findById(session.tableId()).filter(DiningTableEntity::isActive)
                    .orElseThrow(PublicSessionController::invalidTable);
            session = guestSessionService.renew(session, table.getId());
        }
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE,
                        cookieFactory.guestCookie(guestSessionService.encode(session), guestSessionService.ttl()).toString())
                .cacheControl(CacheControl.noStore())
                .body(new SessionResponse(new TableInfo(table.getId(), table.getLabel()),
                        settingsService.publicInfo(table.getRestaurantId()), session.expiresAt()));
    }

    private static ApiException invalidTable() {
        return new ApiException(HttpStatus.NOT_FOUND, "INVALID_TABLE", "Please scan the QR code on your table");
    }
}
