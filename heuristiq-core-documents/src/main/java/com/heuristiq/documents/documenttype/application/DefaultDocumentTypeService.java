package com.railcab.dms.documenttype.application;

import com.railcab.dms.documenttype.domain.DefaultDocumentTypes;
import com.railcab.dms.documenttype.domain.DocumentType;
import com.railcab.dms.documenttype.domain.DocumentTypeConstants;
import com.railcab.dms.documenttype.domain.DocumentTypeException;
import com.railcab.dms.documenttype.domain.DocumentTypeRepository;
import com.railcab.dms.documenttype.domain.PrebuiltDocumentTypes;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.StringUtils;

import java.time.Clock;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Makes sure an organization has its {@code DEFAULT} document type, and right after it the prebuilt catalog
 * ({@link PrebuiltDocumentTypes}: customer, driver and franchise onboarding documents), creating them on first need.
 *
 * <p>The type is written straight through the repository as the system (no change request, so no maker-checker): it is a
 * built-in, not a business decision. Afterwards it is an ordinary record: Ops edit or deactivate it through the normal
 * change-request flow, and this service never touches an existing one (so an edit or a deactivation is never undone).
 *
 * <p>Idempotent and safe on several instances: a record already there is left alone, and when two instances insert at
 * once the loser's CONFLICT (unique organization + code) is read as "someone else did it".
 *
 * <p>Why on first use and not at startup: DMS keeps no list of organizations (the organization arrives with each call
 * from the token), and the table of types cannot name an organization that has none yet. Every path that needs types (list,
 * the readiness check, resolving a type for an upload) calls this first, so each organization, old or new, gets its default
 * the first time DMS is used for it.
 */
public class DefaultDocumentTypeService {
    private static final Logger log = LoggerFactory.getLogger(DefaultDocumentTypeService.class);

    /**
     * Settings of the default type.
     *
     * @param enabled       false switches the whole feature off (nothing is created, no fallback)
     * @param maxFileSizeMb largest accepted file
     * @param retentionDays retention of uploads of the type, or null for the service default
     * @param prebuiltCatalog true to also create the prebuilt catalog (only when {@code enabled})
     */
    public record Settings(boolean enabled, int maxFileSizeMb, Integer retentionDays, boolean prebuiltCatalog) {

        /** Settings without the prebuilt catalog (only the DEFAULT type). */
        public Settings(boolean enabled, int maxFileSizeMb, Integer retentionDays) {
            this(enabled, maxFileSizeMb, retentionDays, false);
        }
    }

    private final DocumentTypeRepository repository;
    private final Clock clock;
    private final Settings settings;
    /** Organizations this instance already confirmed; bounded, so a long-running instance cannot grow without limit. */
    private final Set<String> confirmed = Collections.newSetFromMap(Collections.synchronizedMap(
            new LinkedHashMap<String, Boolean>(16, 0.75f, true) {
                @Override
                protected boolean removeEldestEntry(Map.Entry<String, Boolean> eldest) {
                    return size() > DocumentTypeConstants.SEEDED_TENANTS_REMEMBERED;
                }
            }));

    public DefaultDocumentTypeService(DocumentTypeRepository repository, Clock clock, Settings settings) {
        this.repository = repository;
        this.clock = clock;
        this.settings = settings;
    }

    /** A service that does nothing, for callers built without the feature. */
    public static DefaultDocumentTypeService disabled() {
        return new DefaultDocumentTypeService(null, Clock.systemUTC(),
                new Settings(false, DocumentTypeConstants.MIN_FILE_SIZE_MB, null));
    }

    /** True when the feature is on. */
    public boolean enabled() {
        return settings.enabled();
    }

    /**
     * Creates the organization's default type when it has none, then (when the catalog is on) every prebuilt type whose
     * code the organization does not have yet. A blank organization or a disabled feature does nothing. A type that
     * already exists under a code is never changed, whoever created it.
     *
     * @return the organization's default type as stored (present or just created), or empty when the feature is off
     */
    public Optional<DocumentType> ensureFor(String tenantId) {
        if (!settings.enabled() || !StringUtils.hasText(tenantId)) {
            return Optional.empty();
        }
        Optional<DocumentType> defaultType = ensureDefault(tenantId);
        if (settings.prebuiltCatalog()) {
            ensurePrebuilt(tenantId);
        }
        confirmed.add(tenantId);
        return defaultType;
    }

    private Optional<DocumentType> ensureDefault(String tenantId) {
        Optional<DocumentType> existing = repository.findByCode(tenantId, DocumentTypeConstants.DEFAULT_TYPE_CODE);
        if (existing.isPresent()) {
            return existing;
        }
        DocumentType created = DefaultDocumentTypes.create(tenantId, UUID.randomUUID().toString(), clock.instant(),
                settings.maxFileSizeMb(), settings.retentionDays());
        Optional<DocumentType> saved = insertUnlessTaken(created);
        if (saved.isPresent()) {
            log.info("dms.documenttype.default.created tenant={}", tenantId);
            return saved;
        }
        return repository.findByCode(tenantId, DocumentTypeConstants.DEFAULT_TYPE_CODE);
    }

    /**
     * Creates each catalog type the organization has no type of that code for (sizes capped by the configured max), and
     * widens an untouched system-created type to formats the catalog added later
     * ({@link PrebuiltDocumentTypes#widenedToCatalog}).
     */
    private void ensurePrebuilt(String tenantId) {
        int created = 0;
        int widened = 0;
        for (PrebuiltDocumentTypes.Entry entry : PrebuiltDocumentTypes.entries()) {
            Optional<DocumentType> existing = repository.findByCode(tenantId, entry.code());
            if (existing.isPresent()) {
                DocumentType candidate = PrebuiltDocumentTypes.widenedToCatalog(entry, existing.get(), clock.instant()).orElse(existing.get());
                candidate = PrebuiltDocumentTypes.withProviderDefaults(entry, candidate, clock.instant()).orElse(candidate);
                if (candidate != existing.get() && insertUnlessTaken(candidate).isPresent()) {
                    widened++;
                }
                continue;
            }
            DocumentType type = PrebuiltDocumentTypes.create(entry, tenantId, UUID.randomUUID().toString(), clock.instant(),
                    settings.maxFileSizeMb());
            if (insertUnlessTaken(type).isPresent()) {
                created++;
            }
        }
        if (created > 0) {
            log.info("dms.documenttype.prebuilt.created tenant={} count={}", tenantId, created);
        }
        if (widened > 0) {
            log.info("dms.documenttype.prebuilt.widened tenant={} count={}", tenantId, widened);
        }
    }

    /**
     * Saves a new or widened type. A CONFLICT (unique organization + code, or a stale lock version) means another
     * instance or an admin wrote it between our read and our write; that write wins. Anything else is not swallowed.
     *
     * @return the stored type when this call saved it, empty when another writer did
     */
    private Optional<DocumentType> insertUnlessTaken(DocumentType type) {
        try {
            return Optional.of(repository.save(type));
        } catch (DocumentTypeException e) {
            if (e.kind() != DocumentTypeException.Kind.CONFLICT) {
                throw e;
            }
            return Optional.empty();
        }
    }

    /** Like {@link #ensureFor} but skips the lookup for an organization this instance already confirmed. */
    public void ensureOnce(String tenantId) {
        if (settings.enabled() && StringUtils.hasText(tenantId) && !confirmed.contains(tenantId)) {
            ensureFor(tenantId);
        }
    }
}
