package com.heuristiq.uam.users.repository.jpa;

import com.heuristiq.uam.helper.PhoneIdentity;
import com.heuristiq.uam.users.UamUser;
import com.heuristiq.uam.users.repository.PhoneDuplicates;
import com.heuristiq.uam.users.persistence.jpa.UamUserEntity;
import com.heuristiq.uam.users.repository.UamUserRepository;
import com.heuristiq.uam.users.repository.UamUserSearchCriteria;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Predicate;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.util.StringUtils;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

@Repository
@ConditionalOnProperty(prefix = "railcab.uam.persistence", name = "type", havingValue = "postgres")
class UamUserJpaRepositoryAdapter implements UamUserRepository {
    private final UamUserJpaSpringRepository springRepository;

    UamUserJpaRepositoryAdapter(UamUserJpaSpringRepository springRepository) {
        this.springRepository = springRepository;
    }

    @Override
    public UamUser save(UamUser user) {
        try {
            return toDomain(springRepository.save(toEntity(user)));
        } catch (DataIntegrityViolationException e) {
            throw PhoneDuplicates.translate(e);
        }
    }

    @Override
    public Optional<UamUser> findById(String id) {
        return springRepository.findById(id).map(this::toDomain);
    }

    @Override
    public List<UamUser> findAllByIds(Collection<String> ids) {
        return springRepository.findAllById(ids).stream().map(this::toDomain).toList();
    }

    @Override
    public List<UamUser> findAll(Sort sort) {
        return springRepository.findAll(sort).stream().map(this::toDomain).toList();
    }

    @Override
    public Page<UamUser> findAll(Pageable pageable) {
        return springRepository.findAll(pageable).map(this::toDomain);
    }

    @Override
    public void delete(UamUser user) {
        springRepository.deleteById(user.getId());
    }

    @Override
    public Optional<UamUser> findByUserNameIgnoreCase(String userName) {
        return springRepository.findByUserNameIgnoreCase(userName).map(this::toDomain);
    }

    @Override
    public Optional<UamUser> findByEmailIgnoreCase(String email) {
        return springRepository.findByEmailIgnoreCase(email).map(this::toDomain);
    }

    @Override
    public Optional<UamUser> findByPhone(String rawPhone) {
        return PhoneIdentity.lookup(rawPhone).flatMap(lookup -> canonicalMatch(lookup)
                .or(() -> legacyMatch(lookup)));
    }

    /** The row carrying the canonical E.164 value (every row written or backfilled since the column was added). */
    private Optional<UamUser> canonicalMatch(PhoneIdentity.Lookup lookup) {
        return lookup.e164() == null ? Optional.empty()
                : springRepository.findByPhoneE164(lookup.e164()).map(this::toDomain);
    }

    /** Rows without a canonical value (unparsable legacy numbers): national number plus compatible country. */
    private Optional<UamUser> legacyMatch(PhoneIdentity.Lookup lookup) {
        return springRepository.findAllByPhoneNumber_Number(lookup.nationalNumber()).stream()
                .map(this::toDomain)
                .filter(user -> PhoneIdentity.matches(user.getPhoneNumber(), lookup))
                .findFirst();
    }

    @Override
    public boolean existsByUserNameIgnoreCase(String userName) {
        return springRepository.existsByUserNameIgnoreCase(userName);
    }

    @Override
    public Page<UamUser> search(UamUserSearchCriteria criteria, Pageable pageable) {
        return springRepository.findAll(specification(criteria), pageable).map(this::toDomain);
    }

    @Override
    public long count(UamUserSearchCriteria criteria) {
        return springRepository.count(specification(criteria));
    }

    /** UamUserSearchCriteria -> SQL predicate; same rules as the Mongo adapter's query. */
    private static Specification<UamUserEntity> specification(UamUserSearchCriteria c) {
        return (root, query, cb) -> {
            List<Predicate> and = new ArrayList<>();
            if (StringUtils.hasText(c.roleCode())) {
                and.add(cb.isMember(c.roleCode(), root.<List<String>>get("roles")));
            }
            if (c.status() != null) {
                and.add(cb.equal(root.get("status"), c.status()));
            }
            for (String term : c.searchTerms()) {
                String pattern = "%" + escapeLike(term.toLowerCase(Locale.ROOT)) + "%";
                List<Predicate> any = new ArrayList<>(List.of(
                        like(cb, root.get("userName"), pattern),
                        like(cb, root.get("email"), pattern),
                        like(cb, root.get("displayName"), pattern),
                        like(cb, root.get("phoneNumber").get("number"), pattern),
                        like(cb, root.get("fullName").get("firstName"), pattern),
                        like(cb, root.get("fullName").get("middleName"), pattern),
                        like(cb, root.get("fullName").get("lastName"), pattern)));
                PhoneIdentity.searchFragment(term).ifPresent(fragment -> any.add(
                        like(cb, root.get("phoneE164"), "%" + escapeLike(fragment) + "%")));
                and.add(cb.or(any.toArray(Predicate[]::new)));
            }
            if (c.tenantScope() != null) {
                and.add(cb.or(cb.equal(root.get("tenantId"), c.tenantScope()), cb.isNull(root.get("tenantId"))));
            }
            if (c.createdFrom() != null) {
                and.add(cb.greaterThanOrEqualTo(root.get("audit").get("createdAt"), c.createdFrom()));
            }
            if (c.createdBefore() != null) {
                and.add(cb.lessThan(root.get("audit").get("createdAt"), c.createdBefore()));
            }
            return cb.and(and.toArray(Predicate[]::new));
        };
    }

    private static final char LIKE_ESCAPE = '!';

    private static Predicate like(CriteriaBuilder cb, Expression<String> field, String pattern) {
        return cb.like(cb.lower(field), pattern, LIKE_ESCAPE);
    }

    /** Search terms are literal text: escape LIKE wildcards so "50%" or "a_b" match themselves. */
    private static String escapeLike(String term) {
        return term.replace("!", "!!").replace("%", "!%").replace("_", "!_");
    }

    private UamUser toDomain(UamUserEntity entity) {
        return UamUser.builder()
                .id(entity.getId())
                .tenantId(entity.getTenantId())
                .userName(entity.getUserName())
                .email(entity.getEmail())
                .phoneNumber(entity.getPhoneNumber())
                .employeeCode(entity.getEmployeeCode())
                .displayName(entity.getDisplayName())
                .fullName(entity.getFullName())
                .status(entity.getStatus())
                .departmentId(entity.getDepartmentId())
                .reportingManagerId(entity.getReportingManagerId())
                .designationId(entity.getDesignationId())
                .locationId(entity.getLocationId())
                .timezone(entity.getTimezone())
                .preferredLanguage(entity.getPreferredLanguage())
                .profileImage(entity.getProfileImage())
                .gender(entity.getGender())
                .joiningDate(entity.getJoiningDate())
                .relievingDate(entity.getRelievingDate())
                .active(entity.getActive())
                .apiUser(entity.getApiUser())
                .roles(entity.getRoles())
                .attributes(entity.getAttributes())
                .audit(entity.getAudit())
                .build();
    }

    private UamUserEntity toEntity(UamUser user) {
        return UamUserEntity.builder()
                .id(user.getId())
                .tenantId(user.getTenantId())
                .userName(user.getUserName())
                .email(user.getEmail())
                .phoneNumber(user.getPhoneNumber())
                .phoneE164(PhoneIdentity.e164Of(user.getPhoneNumber()))
                .employeeCode(user.getEmployeeCode())
                .displayName(user.getDisplayName())
                .fullName(user.getFullName())
                .status(user.getStatus())
                .departmentId(user.getDepartmentId())
                .reportingManagerId(user.getReportingManagerId())
                .designationId(user.getDesignationId())
                .locationId(user.getLocationId())
                .timezone(user.getTimezone())
                .preferredLanguage(user.getPreferredLanguage())
                .profileImage(user.getProfileImage())
                .gender(user.getGender())
                .joiningDate(user.getJoiningDate())
                .relievingDate(user.getRelievingDate())
                .active(user.getActive())
                .apiUser(user.getApiUser())
                .roles(user.getRoles())
                .attributes(user.getAttributes())
                .audit(user.getAudit())
                .build();
    }
}
