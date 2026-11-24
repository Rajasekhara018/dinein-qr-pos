package com.railcab.audit.domain;

/** Client surface that produced the event ({@code WALLET_WEB} is the standalone RealBoom Wallet web app). */
public enum AppChannel {
    WEB, ADMIN_WEB, WALLET_WEB, CUSTOMER_APP, DRIVER_APP, RESTAURANT_APP, SERVICE
}
