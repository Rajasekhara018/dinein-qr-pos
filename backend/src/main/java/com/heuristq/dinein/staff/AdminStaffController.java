package com.heuristq.dinein.staff;

import com.heuristq.dinein.shared.security.CurrentStaff;
import com.heuristq.dinein.staff.dto.DeviceResponse;
import com.heuristq.dinein.staff.dto.StaffRequests.CreateStaff;
import com.heuristq.dinein.staff.dto.StaffRequests.UpdateStaff;
import com.heuristq.dinein.staff.dto.StaffResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
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
@RequestMapping("/api/v1/admin")
@PreAuthorize("@perm.has('MANAGE_STAFF')")
public class AdminStaffController {

    private final StaffService staffService;
    private final DeviceTokenService deviceTokenService;

    public AdminStaffController(StaffService staffService, DeviceTokenService deviceTokenService) {
        this.staffService = staffService;
        this.deviceTokenService = deviceTokenService;
    }

    @GetMapping("/staff")
    public List<StaffResponse> list() {
        return staffService.list();
    }

    @PostMapping("/staff")
    @ResponseStatus(HttpStatus.CREATED)
    public StaffResponse create(@Valid @RequestBody CreateStaff request) {
        return staffService.create(request);
    }

    @PutMapping("/staff/{id}")
    public StaffResponse update(@PathVariable Long id, @Valid @RequestBody UpdateStaff request) {
        return staffService.update(id, request, CurrentStaff.require());
    }

    @GetMapping("/devices")
    public List<DeviceResponse> devices() {
        return deviceTokenService.list();
    }

    @DeleteMapping("/devices/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void revokeDevice(@PathVariable Long id) {
        deviceTokenService.revoke(id);
    }
}
