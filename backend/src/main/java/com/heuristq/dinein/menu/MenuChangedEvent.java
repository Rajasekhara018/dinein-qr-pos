package com.heuristq.dinein.menu;

/** Published whenever anything guests see on the menu changes; invalidates the menu cache after commit. */
public record MenuChangedEvent(String reason) {
}
