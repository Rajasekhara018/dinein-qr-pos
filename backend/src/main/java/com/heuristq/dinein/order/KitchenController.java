package com.heuristq.dinein.order;

import com.heuristq.dinein.order.domain.OrderStatus;
import com.heuristq.dinein.order.dto.OrderDtos.KitchenOrderView;
import com.heuristq.dinein.order.dto.OrderDtos.PriorityRequest;
import com.heuristq.dinein.order.dto.OrderDtos.StatusChangeRequest;
import com.heuristq.dinein.settings.SettingsService;
import com.heuristq.dinein.settings.domain.RestaurantSettingsEntity;
import com.heuristq.dinein.shared.security.CurrentStaff;
import com.heuristq.dinein.shared.security.StaffPrincipal;
import com.heuristq.dinein.staff.DeviceTokenService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.EnumSet;
import java.util.List;
import java.util.Set;

@RestController
@RequestMapping("/api/v1/kitchen")
public class KitchenController {

    /** Kitchen actions: Start, Ready, Served. */
    private static final Set<OrderStatus> KITCHEN_TARGETS =
            EnumSet.of(OrderStatus.PREPARING, OrderStatus.READY, OrderStatus.COMPLETED);

    public record KitchenConfig(String restaurantName, int warnMinutes, int alertMinutes, int readyAutoHideMinutes) {
    }

    public record HeartbeatRequest(String applicationVersion) {
    }

    private final OrderQueryService queryService;
    private final OrderLifecycleService lifecycle;
    private final SettingsService settingsService;
    private final DeviceTokenService deviceTokenService;

    public KitchenController(OrderQueryService queryService, OrderLifecycleService lifecycle,
                             SettingsService settingsService, DeviceTokenService deviceTokenService) {
        this.queryService = queryService;
        this.lifecycle = lifecycle;
        this.settingsService = settingsService;
        this.deviceTokenService = deviceTokenService;
    }

    @GetMapping("/config")
    public KitchenConfig config() {
        RestaurantSettingsEntity s = settingsService.forRestaurant(CurrentStaff.require().restaurantId());
        return new KitchenConfig(s.getName(), s.getKitchenWarnMinutes(), s.getKitchenAlertMinutes(),
                s.getReadyAutoHideMinutes());
    }

    @GetMapping("/orders")
    public List<KitchenOrderView> orders(
            @RequestParam(name = "status", defaultValue = "CONFIRMED,PREPARING,READY") List<OrderStatus> statuses) {
        return queryService.kitchenOrders(statuses);
    }

    @PatchMapping("/orders/{id}/status")
    public KitchenOrderView changeStatus(@PathVariable Long id, @Valid @RequestBody StatusChangeRequest request) {
        StaffPrincipal staff = CurrentStaff.require();
        return lifecycle.changeByStaff(id, request.status(), KITCHEN_TARGETS,
                (staff.isDevice() ? "device:" + staff.deviceId() : "user:" + staff.userId()));
    }

    @PatchMapping("/orders/{id}/priority")
    public KitchenOrderView setPriority(@PathVariable Long id, @Valid @RequestBody PriorityRequest request) {
        StaffPrincipal staff = CurrentStaff.require();
        return lifecycle.setPriority(id, request.priority(),
                (staff.isDevice() ? "device:" + staff.deviceId() : "user:" + staff.userId()));
    }

    /** Called periodically by a kitchen device so liveness tracking doesn't depend on how chatty its other
     *  traffic happens to be. A no-op for a staff JWT session (no device to track). */
    @PostMapping("/heartbeat")
    public void heartbeat(@RequestBody(required = false) HeartbeatRequest request) {
        StaffPrincipal staff = CurrentStaff.require();
        if (staff.isDevice()) {
            deviceTokenService.heartbeat(staff.deviceId(), request == null ? null : request.applicationVersion());
        }
    }
}
