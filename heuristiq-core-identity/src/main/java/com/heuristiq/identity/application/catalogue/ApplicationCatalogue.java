package com.heuristiq.identity.application.catalogue;

import java.util.List;
import java.util.Locale;
import java.util.Optional;

import com.heuristiq.identity.enums.UamEnums.ApplicationType;

/**
 * The closed list of applications an organization can create. Adding an application to the platform is a code change
 * here; there are no custom codes. Web apps read this list from {@code GET /api/v1/uam/applications/catalogue} and never
 * hard-code it.
 */
public final class ApplicationCatalogue {

    /** The Pasupu Kumkuma matrimony product (customer app, agent area and operations portal). */
    public static final String PASUPU_KUMKUMA_MATRIMONY_APP = "PASUPU_KUMKUMA_MATRIMONY_APP";

    /** Display name of the matrimony brand; one place to change its spelling. */
    public static final String PASUPU_KUMKUMA_MATRIMONY_NAME = "Pasupu Kumkuma Matrimony";

    /** The BestRide ride-hailing product (customer and driver apps). */
    public static final String BESTRIDE_RIDER_APP = "BESTRIDE_RIDER_APP";

    /** The food delivery product. */
    public static final String FOOD_DELIVER_APP = "FOOD_DELIVER_APP";

    /** The Best Purchase product: online shopping and orders, with its inventory (items, sites, locations, legal entities). */
    public static final String BEST_PURCHASE_APP = "BEST_PURCHASE_APP";

    /** The centralised wallet shared by the other products. */
    public static final String CENTRALISED_WALLET_APP = "CENTRALISED_WALLET_APP";
    public static final String TRAIN_BOOKING_APP = "TRAIN_BOOKING_APP";
    public static final String FRANCHISE_MANAGEMENT_APP = "FRANCHISE_MANAGEMENT_APP";

    /** The DineIn restaurant product: POS, kiosk, QR ordering and kitchen display. */
    public static final String DINEIN_POS_APP = "DINEIN_POS_APP";

    /** Every catalogue entry, in display order. */
    public static final List<ApplicationCatalogueEntry> ENTRIES = List.of(
            new ApplicationCatalogueEntry(PASUPU_KUMKUMA_MATRIMONY_APP, PASUPU_KUMKUMA_MATRIMONY_NAME,
                    "Matrimony customer app, agent area and operations portal.", ApplicationType.WEB, 10, "favorite", "MATRIMONY"),
            new ApplicationCatalogueEntry(BESTRIDE_RIDER_APP, "BestRide Rider",
                    "Ride booking for customers and drivers.", ApplicationType.MOBILE, 20, "local_taxi", "RIDE"),
            new ApplicationCatalogueEntry(FOOD_DELIVER_APP, "Food Delivery",
                    "Food ordering and delivery.", ApplicationType.MOBILE, 30, "restaurant", "FOOD"),
            new ApplicationCatalogueEntry(BEST_PURCHASE_APP, "Best Purchase",
                    "Online shopping, orders and inventory: items, sites, locations and legal entities.", ApplicationType.WEB, 40,
                    "shopping_cart", "IMS"),
            new ApplicationCatalogueEntry(CENTRALISED_WALLET_APP, "Centralised Wallet",
                    "Wallet balances, top-ups and payments shared by the other applications.", ApplicationType.WEB, 50,
                    "account_balance_wallet", "WALLET"),
            new ApplicationCatalogueEntry(TRAIN_BOOKING_APP, "Train Booking",
                    "Train search, booking, tickets and refunds.", ApplicationType.MOBILE, 60, "train", "TRAIN"),
            new ApplicationCatalogueEntry(FRANCHISE_MANAGEMENT_APP, "Franchise Management",
                    "Franchises, agents, plans, commissions and onboarding.", ApplicationType.WEB, 70, "storefront", "FRANCHISE"),
            new ApplicationCatalogueEntry(DINEIN_POS_APP, "DineIn POS & Kiosk",
                    "Restaurant POS, kiosk, QR ordering and kitchen display.", ApplicationType.WEB, 80, "point_of_sale", "DINEIN"));

    private ApplicationCatalogue() {
    }

    /** The entry for a code (trimmed, any case), or empty when the code is not in the catalogue. */
    public static Optional<ApplicationCatalogueEntry> find(String code) {
        if (code == null) {
            return Optional.empty();
        }
        String normalized = code.trim().toUpperCase(Locale.ROOT);
        return ENTRIES.stream().filter(entry -> entry.code().equals(normalized)).findFirst();
    }

    /** True when the code (trimmed, any case) is in the catalogue. */
    public static boolean contains(String code) {
        return find(code).isPresent();
    }

    /** The catalogue codes in display order. */
    public static List<String> codes() {
        return ENTRIES.stream().map(ApplicationCatalogueEntry::code).toList();
    }
}
