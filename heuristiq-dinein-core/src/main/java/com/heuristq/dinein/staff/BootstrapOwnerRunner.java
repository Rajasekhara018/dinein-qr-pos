package com.heuristq.dinein.staff;

import com.heuristq.dinein.shared.config.AppProperties;
import com.heuristq.dinein.staff.domain.StaffRole;
import com.heuristq.dinein.staff.domain.StaffUserEntity;
import com.heuristq.dinein.staff.domain.StaffUserRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Creates the first OWNER account from {@code BOOTSTRAP_OWNER_PASSWORD} when no owner exists yet.
 * The password is marked temporary, so the owner is forced to change it on first login. This account is also
 * the platform operator's own login (there's no separate platform-admin login system), so it's the one account
 * seeded with {@code platformAdmin = true} -- every restaurant onboarded afterwards via {@code /platform} gets
 * an ordinary (non-platform-admin) OWNER, see {@code RestaurantOnboardingService}.
 */
@Slf4j
@Component
@Order(1)
public class BootstrapOwnerRunner implements ApplicationRunner {

    private final StaffUserRepository staffUserRepository;
    private final PasswordEncoder passwordEncoder;
    private final AppProperties.Security securityProps;

    public BootstrapOwnerRunner(StaffUserRepository staffUserRepository, PasswordEncoder passwordEncoder,
                                AppProperties properties) {
        this.staffUserRepository = staffUserRepository;
        this.passwordEncoder = passwordEncoder;
        this.securityProps = properties.security();
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (staffUserRepository.existsByRole(StaffRole.OWNER)) {
            return;
        }
        String password = securityProps.bootstrapOwnerPassword();
        if (password == null || password.isBlank()) {
            throw new IllegalStateException(
                    "No OWNER account exists. Set BOOTSTRAP_OWNER_PASSWORD to create the first owner.");
        }
        String username = securityProps.bootstrapOwnerUsername() == null || securityProps.bootstrapOwnerUsername().isBlank()
                ? "owner" : securityProps.bootstrapOwnerUsername().trim();
        StaffUserEntity owner = new StaffUserEntity();
        owner.setUsername(username);
        owner.setDisplayName("Owner");
        owner.setRole(StaffRole.OWNER);
        owner.setPasswordHash(passwordEncoder.encode(password));
        owner.setMustChangePassword(true);
        owner.setPlatformAdmin(true);
        staffUserRepository.save(owner);
        log.info("bootstrap.owner_created username={} (password change required on first login)", username);
    }
}
