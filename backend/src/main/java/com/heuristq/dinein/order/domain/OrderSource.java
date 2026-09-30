package com.heuristq.dinein.order.domain;

/** Where an order was placed: a table QR, a staff member (waiter / counter), or a self-order kiosk. */
public enum OrderSource {
    GUEST_QR,
    STAFF,
    KIOSK
}
