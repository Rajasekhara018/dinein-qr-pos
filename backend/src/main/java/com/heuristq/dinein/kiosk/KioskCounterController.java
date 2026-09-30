package com.heuristq.dinein.kiosk;

import com.heuristq.dinein.kiosk.dto.KioskDtos.CounterOrderView;
import com.heuristq.dinein.kiosk.dto.KioskDtos.CounterPayRequest;
import com.heuristq.dinein.payment.domain.OfflinePaymentMethod;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** Counter screen for kiosk orders: cashiers and waiters (and managers / owners) see unpaid orders and take payment. */
@RestController
@RequestMapping("/api/v1/waiter/kiosk-orders")
public class KioskCounterController {

    private final KioskCounterService service;

    public KioskCounterController(KioskCounterService service) {
        this.service = service;
    }

    @GetMapping
    public List<CounterOrderView> pending() {
        return service.pending();
    }

    @PostMapping("/{id}/pay")
    public ResponseEntity<Void> pay(@PathVariable Long id, @Valid @RequestBody CounterPayRequest request) {
        service.pay(id, OfflinePaymentMethod.valueOf(request.method()));
        return ResponseEntity.noContent().build();
    }
}
