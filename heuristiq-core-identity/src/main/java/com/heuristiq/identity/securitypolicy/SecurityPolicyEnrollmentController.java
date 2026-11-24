package com.heuristiq.uam.securitypolicy;

import com.heuristiq.uam.common.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/uam/identity/auth/mfa-enrollment")
public class SecurityPolicyEnrollmentController {
    public record Begin(@NotBlank String enrollmentToken) {}
    public record Complete(@NotBlank String enrollmentToken, @NotBlank String mfaId, @NotBlank String code) {}
    private final SecurityPolicyEnrollmentService enrollment;
    private final CommonRequestMapper mapper;
    public SecurityPolicyEnrollmentController(SecurityPolicyEnrollmentService enrollment, CommonRequestMapper mapper) {
        this.enrollment=enrollment; this.mapper=mapper;
    }
    @PostMapping("/begin") @RequestObjectType(Begin.class)
    public CommonResponseObject begin(@Valid @RequestBody CommonRequestObject body) {
        return CommonResponseObject.success(enrollment.begin(mapper.requireRequestObject(body, Begin.class).enrollmentToken()));
    }
    @PostMapping("/complete") @RequestObjectType(Complete.class)
    public CommonResponseObject complete(@Valid @RequestBody CommonRequestObject body) {
        var proof = mapper.requireRequestObject(body, Complete.class);
        return CommonResponseObject.success(enrollment.complete(proof.enrollmentToken(), proof.mfaId(), proof.code()));
    }
}
