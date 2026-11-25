package com.heuristiq.identity.pos.adapter.out;

import static com.heuristiq.identity.pos.adapter.out.PosAdapterConstants.*;

import com.heuristiq.identity.organization.legalprofile.domain.TaxId;
import com.heuristiq.identity.organization.legalprofile.domain.TaxIdType;
import com.heuristiq.identity.pos.application.port.LegalEntityStore;
import com.heuristiq.identity.pos.domain.LegalEntity;
import com.heuristiq.identity.pos.domain.PosAddress;
import com.heuristiq.identity.pos.domain.PosException;
import com.heuristiq.identity.pos.domain.PosStatus;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;

/** PostgreSQL store of legal entities and their tax ids ({@code pos_legal_entity}, {@code pos_legal_entity_tax_id}; V44). */
public class JdbcLegalEntityStore implements LegalEntityStore {

    private final JdbcTemplate jdbc;
    private final TransactionTemplate tx;

    public JdbcLegalEntityStore(JdbcTemplate jdbc, TransactionTemplate tx) {
        this.jdbc = jdbc;
        this.tx = tx;
    }

    @Override
    public Optional<LegalEntity> find(String tenantId, String id) {
        return jdbc.query(SELECT_ENTITY_SQL, (rs, row) -> read(rs), tenantId, id).stream().findFirst().map(this::withTaxIds);
    }

    @Override
    public List<LegalEntity> list(String tenantId) {
        return jdbc.query(LIST_ENTITIES_SQL, (rs, row) -> read(rs), tenantId).stream().map(this::withTaxIds).toList();
    }

    @Override
    public LegalEntity insert(LegalEntity e) {
        tx.executeWithoutResult(status -> {
            PosAddress a = e.address();
            jdbc.update(INSERT_ENTITY_SQL, e.id(), e.tenantId(), e.country(), e.legalName(), e.currency(), line(a, 1), line(a, 2),
                    line(a, 3), line(a, 4), line(a, 5), line(a, 6), e.invoicePrefix(), e.financialYearStartMonth(), e.status().name(),
                    e.version(), Timestamp.from(e.updatedAt()), e.updatedBy());
            insertTaxIds(e);
        });
        return e;
    }

    @Override
    public LegalEntity update(LegalEntity e, long expectedVersion) {
        tx.executeWithoutResult(status -> {
            PosAddress a = e.address();
            int rows = jdbc.update(UPDATE_ENTITY_SQL, e.country(), e.legalName(), e.currency(), line(a, 1), line(a, 2), line(a, 3),
                    line(a, 4), line(a, 5), line(a, 6), e.invoicePrefix(), e.financialYearStartMonth(), e.status().name(), e.version(),
                    Timestamp.from(e.updatedAt()), e.updatedBy(), e.id(), e.tenantId(), expectedVersion);
            if (rows == 0) {
                throw PosException.conflict(ERROR_STALE_VERSION, FIELD_VERSION);
            }
            jdbc.update(DELETE_TAX_IDS_SQL, e.tenantId(), e.id());
            insertTaxIds(e);
        });
        return e;
    }

    private void insertTaxIds(LegalEntity e) {
        for (TaxId taxId : e.taxIds()) {
            jdbc.update(INSERT_TAX_ID_SQL, e.id(), e.tenantId(), taxId.type().name(), taxId.value());
        }
    }

    private LegalEntity withTaxIds(LegalEntity e) {
        List<TaxId> taxIds = jdbc.query(SELECT_TAX_IDS_SQL, (rs, row) -> new TaxId(TaxIdType.valueOf(rs.getString(1)), rs.getString(2)),
                e.tenantId(), e.id());
        return new LegalEntity(e.id(), e.tenantId(), e.country(), e.legalName(), e.currency(), taxIds, e.address(), e.invoicePrefix(),
                e.financialYearStartMonth(), e.status(), e.version(), e.updatedAt(), e.updatedBy());
    }

    private static LegalEntity read(ResultSet rs) throws SQLException {
        return new LegalEntity(rs.getString(1), rs.getString(2), rs.getString(3), rs.getString(4), rs.getString(5), List.of(),
                address(rs, 6), rs.getString(12), rs.getInt(13), PosStatus.valueOf(rs.getString(14)), rs.getLong(15),
                rs.getTimestamp(16).toInstant(), rs.getString(17));
    }

    /** The six address columns starting at {@code from}; null when the row has no address. */
    static PosAddress address(ResultSet rs, int from) throws SQLException {
        String country = rs.getString(from + 5);
        if (country == null && rs.getString(from) == null) {
            return null;
        }
        return new PosAddress(rs.getString(from), rs.getString(from + 1), rs.getString(from + 2), rs.getString(from + 3),
                rs.getString(from + 4), country);
    }

    /** The n-th address field (1 line1 ... 6 country), null for a missing address. */
    static String line(PosAddress a, int n) {
        if (a == null) {
            return null;
        }
        return switch (n) {
            case 1 -> a.line1();
            case 2 -> a.line2();
            case 3 -> a.city();
            case 4 -> a.region();
            case 5 -> a.postalCode();
            default -> a.country();
        };
    }
}
