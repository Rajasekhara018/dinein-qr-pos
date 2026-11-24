package com.heuristiq.uam.application;

import com.heuristiq.uam.common.PageResult;
import com.heuristiq.uam.enums.UamEnums.ApplicationType;

import java.util.List;

/**
 * Applications of the session organization. An application's code must be in the
 * {@link com.heuristiq.uam.application.catalogue.ApplicationCatalogue}; creating one links it to the session organization
 * (tenant_applications), and every read, change and removal only sees the applications linked to that organization.
 */
public interface ApplicationServiceUseCase {

    record CreateInput(UpsertApplicationRequest request, String actor) {
        public CreateInput(UpsertApplicationRequest request) {
            this(request, null);
        }
    }
    record CreateOutput(Application application) {}
    /** Registers/links the application and provisions its own business privilege definitions. */
    CreateOutput create(CreateInput input);

    record UpdateInput(String id, UpsertApplicationRequest request, String actor) {
        public UpdateInput(String id, UpsertApplicationRequest request) {
            this(id, request, null);
        }
    }
    record UpdateOutput(Application application) {}
    /**
     * Changes the editable fields; the code never changes and the type stays the catalogue's. Name and description belong to the
     * row every organization shares, so only a SUPER_ADMIN may change them (others get {@link SharedApplicationFieldsForbiddenException}).
     * {@code active} in the request switches the application on or off for the session organization only (its link).
     */
    UpdateOutput update(UpdateInput input);

    record GetInput(String id) {}
    record GetOutput(Application application) {}
    GetOutput get(GetInput input);

    record ListInput() {}
    record ListOutput(List<Application> applications) {}
    ListOutput list(ListInput input);

    /** code/name/type are optional filters (null/blank = no constraint); when all are absent this
     *  behaves exactly like the unfiltered list. */
    record ListPageInput(int page, int size, String code, String name, ApplicationType type) {
        public ListPageInput(int page, int size) {
            this(page, size, null, null, null);
        }
    }
    record ListPageOutput(PageResult<Application> applications) {}
    ListPageOutput list(ListPageInput input);

    record DeleteInput(String id, String actor) {
        public DeleteInput(String id) {
            this(id, null);
        }
    }
    record DeleteOutput() {}
    /** Removes the application from the session organization; the platform row goes too when no other organization uses it. */
    DeleteOutput delete(DeleteInput input);

    /**
     * The platform row of a catalogue application, created from the catalogue defaults when it does not exist yet (used at
     * start-up for the default organization's applications). Never links it to an organization and never changes an existing row.
     */
    Application ensureCatalogueApplication(String code);

    record CatalogueOutput(List<ApplicationCatalogueItem> items) {}
    /** Every catalogue entry, in display order, marked {@code created} when the session organization already has it. */
    CatalogueOutput catalogue();

    record LookupOutput(List<ApplicationLookupItem> applications) {}
    /** The session organization's usable applications: active, actively linked and in the catalogue, by display order. */
    LookupOutput lookup();
}
