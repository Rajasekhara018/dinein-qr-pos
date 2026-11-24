package com.heuristiq.uam.users.repository.mongo;

import com.heuristiq.uam.helper.PhoneIdentity;
import com.heuristiq.uam.users.UamUser;
import com.heuristiq.uam.users.repository.PhoneDuplicates;
import com.heuristiq.uam.users.UserPhoneConstants;
import com.heuristiq.uam.users.persistence.mongo.UamUserDocument;
import com.heuristiq.uam.users.repository.UamUserRepository;
import com.heuristiq.uam.users.repository.UamUserSearchCriteria;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Repository;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;

@Repository
@ConditionalOnProperty(prefix = "railcab.uam.persistence", name = "type", havingValue = "mongo", matchIfMissing = true)
class UamUserMongoRepositoryAdapter implements UamUserRepository {
    private final UamUserMongoSpringRepository springRepository;
    private final MongoTemplate mongoTemplate;

    UamUserMongoRepositoryAdapter(UamUserMongoSpringRepository springRepository, MongoTemplate mongoTemplate) {
        this.springRepository = springRepository;
        this.mongoTemplate = mongoTemplate;
    }

    @Override
    public Page<UamUser> search(UamUserSearchCriteria criteria, Pageable pageable) {
        Query query = query(criteria);
        long total = mongoTemplate.count(query, UamUserDocument.class);
        List<UamUser> users = mongoTemplate.find(Query.of(query).with(pageable), UamUserDocument.class)
                .stream().map(this::toDomain).toList();
        return new PageImpl<>(users, pageable, total);
    }

    @Override
    public long count(UamUserSearchCriteria criteria) {
        return mongoTemplate.count(query(criteria), UamUserDocument.class);
    }

    /** UamUserSearchCriteria -> Mongo query; same rules as the JPA adapter's Specification. */
    private static Query query(UamUserSearchCriteria c) {
        List<Criteria> and = new ArrayList<>();
        if (StringUtils.hasText(c.roleCode())) {
            and.add(Criteria.where("roles").is(c.roleCode()));
        }
        if (c.status() != null) {
            and.add(Criteria.where("status").is(c.status()));
        }
        for (String term : c.searchTerms()) {
            String regex = Pattern.quote(term);
            List<Criteria> any = new ArrayList<>(List.of(
                    Criteria.where("userName").regex(regex, "i"),
                    Criteria.where("email").regex(regex, "i"),
                    Criteria.where("displayName").regex(regex, "i"),
                    Criteria.where("phoneNumber.number").regex(regex, "i"),
                    Criteria.where("fullName.firstName").regex(regex, "i"),
                    Criteria.where("fullName.middleName").regex(regex, "i"),
                    Criteria.where("fullName.lastName").regex(regex, "i")));
            PhoneIdentity.searchFragment(term).ifPresent(fragment -> any.add(
                    Criteria.where(UserPhoneConstants.FIELD_PHONE_E164).regex(Pattern.quote(fragment))));
            and.add(new Criteria().orOperator(any));
        }
        if (c.tenantScope() != null) {
            and.add(new Criteria().orOperator(
                    Criteria.where("tenantId").is(c.tenantScope()),
                    Criteria.where("tenantId").is(null)));
        }
        if (c.createdFrom() != null || c.createdBefore() != null) {
            Criteria created = Criteria.where("audit.createdAt");
            if (c.createdFrom() != null) created.gte(c.createdFrom());
            if (c.createdBefore() != null) created.lt(c.createdBefore());
            and.add(created);
        }
        return and.isEmpty() ? new Query() : new Query(new Criteria().andOperator(and));
    }

    @Override
    public UamUser save(UamUser user) {
        try {
            return toDomain(springRepository.save(toDocument(user)));
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

    private UamUser toDomain(UamUserDocument doc) {
        return UamUser.builder()
                .id(doc.getId())
                .tenantId(doc.getTenantId())
                .userName(doc.getUserName())
                .email(doc.getEmail())
                .phoneNumber(doc.getPhoneNumber())
                .employeeCode(doc.getEmployeeCode())
                .displayName(doc.getDisplayName())
                .fullName(doc.getFullName())
                .status(doc.getStatus())
                .departmentId(doc.getDepartmentId())
                .reportingManagerId(doc.getReportingManagerId())
                .designationId(doc.getDesignationId())
                .locationId(doc.getLocationId())
                .timezone(doc.getTimezone())
                .preferredLanguage(doc.getPreferredLanguage())
                .profileImage(doc.getProfileImage())
                .gender(doc.getGender())
                .joiningDate(doc.getJoiningDate())
                .relievingDate(doc.getRelievingDate())
                .active(doc.getActive())
                .apiUser(doc.getApiUser())
                .roles(doc.getRoles())
                .attributes(doc.getAttributes())
                .audit(doc.getAudit())
                .build();
    }

    private UamUserDocument toDocument(UamUser user) {
        return UamUserDocument.builder()
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
