package com.heuristiq.uam.registration;

import com.heuristiq.uam.common.UamException;
import com.heuristiq.uam.dto.IdentityRegisterRequest;
import com.heuristiq.uam.dto.RegistrationResponse;
import com.heuristiq.uam.enums.UamEnums;
import com.heuristiq.uam.helper.RName;
import com.heuristiq.uam.helper.PhoneIdentity;
import com.heuristiq.uam.notification.NmsNotificationService;
import com.heuristiq.uam.users.NewUamUserBuilder;
import com.heuristiq.uam.users.UamUser;
import com.heuristiq.uam.users.repository.UamUserRepository;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Objects;

@Service
public class UserRegistrationService implements UserRegistrationServiceUseCase {
    @org.springframework.beans.factory.annotation.Autowired
    private com.heuristiq.uam.securitypolicy.SecurityPolicyRegistration policyRegistration;

    private final UamUserRepository userRepository;
    private final NmsNotificationService nmsNotificationService;

    public UserRegistrationService(UamUserRepository userRepository, NmsNotificationService nmsNotificationService) {
        this.userRepository = userRepository;
        this.nmsNotificationService = nmsNotificationService;
    }

    public RegisterOutput register(RegisterInput input) {
        IdentityRegisterRequest request = input.request();
        UamUser saved = createUser(request.mobile(), request.email(), request.name(), request.userType(), input.administrativeProvisioning());
        nmsNotificationService.sendAccountCreated(saved.getEmail(), displayName(saved),
                saved.getPhoneNumber() != null ? Objects.requireNonNullElse(PhoneIdentity.e164Of(saved.getPhoneNumber()), "") : "");
        return new RegisterOutput(new RegistrationResponse(
                RegistrationConstants.ACCOUNT_CREATED_MESSAGE,
                saved.getId(),
                saved.getId(),
                saved.getId(),
                RegistrationConstants.GENERIC_ONBOARDING_STATUS,
                "",
                false));
    }

    private UamUser createUser(String rawPhone, String rawEmail, String rawName, UamEnums.UserType userType, boolean approved) {
        var status = approved || policyRegistration == null ? UamEnums.UserStatus.ACTIVE : policyRegistration.initialStatus();
        String phone = normalize(rawPhone, "Phone is required");
        String email = normalize(rawEmail, "Email is required");
        String name = normalize(rawName, "Name is required");
        userRepository.findByPhone(phone).ifPresent(existing -> {
            throw UamException.conflict("An account with this phone number already exists.");
        });
        userRepository.findByEmailIgnoreCase(email).ifPresent(existing -> {
            throw UamException.conflict("An account with this email already exists.");
        });

        UamUser user = NewUamUserBuilder.newUser()
                .status(status)
                .userName(email)
                .email(email)
                .phoneNumber(PhoneIdentity.toStored(phone))
                .fullName(new RName(name))
                .roles(List.of(userType.name()))
                .build();
        return userRepository.save(user);
    }

    private String normalize(String value, String message) {
        if (!StringUtils.hasText(value)) {
            throw new UamException(message);
        }
        return value.trim();
    }

    private String displayName(UamUser user) {
        return user.getFullName() != null ? user.getFullName().getFullName() : "there";
    }
}
