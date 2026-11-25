package com.heuristiq.identity.organization.legalprofile.adapter.out;

import static com.heuristiq.identity.organization.legalprofile.adapter.out.LegalProfileAdapterConstants.*;

import com.heuristiq.identity.organization.legalprofile.application.port.LegalProfileStore;
import com.heuristiq.identity.organization.legalprofile.domain.AddressKind;
import com.heuristiq.identity.organization.legalprofile.domain.LegalAddress;
import com.heuristiq.identity.organization.legalprofile.domain.LegalEntityType;
import com.heuristiq.identity.organization.legalprofile.domain.LegalProfile;
import com.heuristiq.identity.organization.legalprofile.domain.LegalProfileException;
import com.heuristiq.identity.organization.legalprofile.domain.StoredLegalProfile;
import com.heuristiq.identity.organization.legalprofile.domain.TaxId;
import com.heuristiq.identity.organization.legalprofile.domain.TaxIdType;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * PostgreSQL store of legal profiles ({@code organization_legal_profile}, {@code organization_tax_id},
 * {@code organization_address}; migration V37). Every statement filters on {@code tenant_id}. {@link #save} runs in one
 * transaction: the profile row is locked (or, for a first save, the organization is serialized by an advisory lock), the
 * version is checked, and the tax ids and addresses are replaced as whole sets.
 */
public class JdbcLegalProfileStore implements LegalProfileStore {

    private final JdbcTemplate jdbc;
    private final TransactionTemplate tx;

    public JdbcLegalProfileStore(JdbcTemplate jdbc, TransactionTemplate tx) {
        this.jdbc = jdbc;
        this.tx = tx;
    }

    @Override
    public Optional<StoredLegalProfile> find(String organizationId) {
        // The profile row first, then its sets: no second query runs while the first result set is still open.
        List<StoredLegalProfile> rows = jdbc.query(SELECT_PROFILE_SQL, (rs, row) -> {
            String entityType = rs.getString(1);
            Timestamp updatedAt = rs.getTimestamp(8);
            LegalProfile profile = new LegalProfile(entityType == null ? null : LegalEntityType.valueOf(entityType), null,
                    rs.getString(2), rs.getString(3), rs.getString(4), rs.getBoolean(5), List.of(), List.of());
            return new StoredLegalProfile(profile, rs.getLong(6), updatedAt == null ? null : updatedAt.toInstant(), rs.getString(7));
        }, organizationId);
        return rows.stream().findFirst().map(s -> {
            LegalProfile p = s.profile();
            return new StoredLegalProfile(new LegalProfile(p.entityType(), null, p.tradingName(), p.registrationNumber(),
                    p.registrationCountry(), p.taxRegistered(), taxIds(organizationId), addresses(organizationId)),
                    s.version(), s.updatedAt(), s.updatedBy());
        });
    }

    @Override
    public StoredLegalProfile save(String organizationId, LegalProfile profile, Long expectedVersion, String userId, Instant at) {
        return tx.execute(status -> {
            long version;
            if (expectedVersion == null) {
                jdbc.query(LOCK_FIRST_SAVE_SQL, rs -> { }, organizationId);
                if (!jdbc.queryForList(LOCK_PROFILE_SQL, Long.class, organizationId).isEmpty()) {
                    throw LegalProfileException.staleVersion();
                }
                version = FIRST_VERSION;
                jdbc.update(INSERT_PROFILE_SQL, organizationId, name(profile.entityType()), profile.tradingName(),
                        profile.registrationNumber(), profile.registrationCountry(), profile.taxRegistered(), version, userId,
                        Timestamp.from(at));
            } else {
                List<Long> locked = jdbc.queryForList(LOCK_PROFILE_SQL, Long.class, organizationId);
                if (locked.isEmpty() || !expectedVersion.equals(locked.get(0))) {
                    throw LegalProfileException.staleVersion();
                }
                version = expectedVersion + 1;
                jdbc.update(UPDATE_PROFILE_SQL, name(profile.entityType()), profile.tradingName(), profile.registrationNumber(),
                        profile.registrationCountry(), profile.taxRegistered(), version, userId, Timestamp.from(at), organizationId,
                        expectedVersion);
                jdbc.update(DELETE_TAX_IDS_SQL, organizationId);
                jdbc.update(DELETE_ADDRESSES_SQL, organizationId);
            }
            for (TaxId taxId : profile.taxIds()) {
                jdbc.update(INSERT_TAX_ID_SQL, organizationId, taxId.type().name(), taxId.value());
            }
            for (LegalAddress address : profile.addresses()) {
                jdbc.update(INSERT_ADDRESS_SQL, organizationId, address.kind().name(), address.line1(), address.line2(), address.city(),
                        address.region(), address.postalCode(), address.country());
            }
            return new StoredLegalProfile(profile.withLegalName(null), version, at, userId);
        });
    }

    private List<TaxId> taxIds(String organizationId) {
        return jdbc.query(SELECT_TAX_IDS_SQL, (rs, row) -> new TaxId(TaxIdType.valueOf(rs.getString(1)), rs.getString(2)), organizationId);
    }

    private List<LegalAddress> addresses(String organizationId) {
        return jdbc.query(SELECT_ADDRESSES_SQL, (rs, row) -> new LegalAddress(AddressKind.valueOf(rs.getString(1)), rs.getString(2),
                rs.getString(3), rs.getString(4), rs.getString(5), rs.getString(6), rs.getString(7)), organizationId);
    }

    private static String name(Enum<?> value) {
        return value == null ? null : value.name();
    }
}
