package com.heuristq.dinein.order;

import com.heuristq.dinein.order.domain.OrderType;
import com.heuristq.dinein.shared.exception.ApiException;

/** Validation of the requested order type, shared by guest and staff-assisted placement. */
public final class OrderTypeRules {

    private OrderTypeRules() {
    }

    /**
     * @return the requested type, DINE_IN when none was sent
     * @throws ApiException 400 TAKEAWAY_DISABLED when takeaway is requested but switched off in the settings
     */
    public static OrderType resolve(OrderType requested, boolean takeawayEnabled) {
        OrderType type = requested == null ? OrderType.DINE_IN : requested;
        if (type == OrderType.TAKEAWAY && !takeawayEnabled) {
            throw ApiException.badRequest("TAKEAWAY_DISABLED", "Takeaway is not available right now");
        }
        return type;
    }

    /**
     * Staff-assisted orders: dine-in needs a table, takeaway may have one (e.g. the counter) or none.
     *
     * @throws ApiException 400 TABLE_REQUIRED
     */
    public static void requireTableForDineIn(OrderType type, Long tableId) {
        if (type == OrderType.DINE_IN && tableId == null) {
            throw ApiException.badRequest("TABLE_REQUIRED", "Choose a table for a dine-in order");
        }
    }
}
