package com.heuristq.dinein.menu;

import com.heuristq.dinein.menu.dto.KitchenStationDtos.KitchenStationRequest;
import com.heuristq.dinein.menu.dto.KitchenStationDtos.KitchenStationResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/admin/kitchen-stations")
public class AdminKitchenStationController {

    private final KitchenStationService service;

    public AdminKitchenStationController(KitchenStationService service) {
        this.service = service;
    }

    @GetMapping
    public List<KitchenStationResponse> list() {
        return service.list();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public KitchenStationResponse create(@Valid @RequestBody KitchenStationRequest request) {
        return service.create(request);
    }

    @PutMapping("/{id}")
    public KitchenStationResponse update(@PathVariable Long id, @Valid @RequestBody KitchenStationRequest request) {
        return service.update(id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        service.delete(id);
    }
}
