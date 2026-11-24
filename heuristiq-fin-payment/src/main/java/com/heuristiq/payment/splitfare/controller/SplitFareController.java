package com.heuristiq.payment.splitfare.controller;

import com.heuristiq.payment.splitfare.dto.SplitFareResponse;
import com.heuristiq.payment.security.OwnershipGuard;
import com.heuristiq.payment.shared.dto.ResponseObject;
import com.heuristiq.payment.shared.exception.NotFoundException;
import com.heuristiq.payment.splitfare.model.SplitFareRequest;
import com.heuristiq.payment.splitfare.dto.CreateSplitFareRequest;
import com.heuristiq.payment.splitfare.service.SplitFareService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/payment/split-fare")
@RequiredArgsConstructor
public class SplitFareController {

    private final SplitFareService splitFareService;

    @PostMapping
    public ResponseEntity<ResponseObject> createSplitRequest(@Valid @RequestBody CreateSplitFareRequest req) {
        OwnershipGuard.requireSelfOrPrivileged(req.initiatorUserId());
        var saved = splitFareService.createSplitRequest(req);
        return ResponseEntity.ok(ResponseObject.builder().status(true).object(SplitFareResponse.from(saved)).build());
    }

    @PostMapping("/{id}/participants/{participantOrder}/pay")
    public ResponseEntity<ResponseObject> payParticipant(@PathVariable String id, @PathVariable int participantOrder) {
        OwnershipGuard.requireSelfOrPrivileged(splitFareService.getSplit(id).getInitiatorUserId());
        var saved = splitFareService.payParticipant(id, participantOrder);
        return ResponseEntity.ok(ResponseObject.builder().status(true).object(SplitFareResponse.from(saved)).build());
    }

    @GetMapping("/{id}")
    public ResponseEntity<ResponseObject> getSplit(@PathVariable String id) {
        SplitFareRequest split = splitFareService.getSplit(id);
        if (!OwnershipGuard.isPartyOrPrivileged(split.getInitiatorUserId())) {
            throw new NotFoundException("Split fare request not found: " + id);
        }
        return ResponseEntity.ok(ResponseObject.builder().status(true).object(SplitFareResponse.from(split)).build());
    }
}
