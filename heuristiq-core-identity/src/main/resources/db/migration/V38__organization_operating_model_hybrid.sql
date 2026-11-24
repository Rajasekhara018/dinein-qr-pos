-- A third operating model, HYBRID (docs/OPERATING_MODEL_DESIGN.md, section 10): a place covered by an active franchise is
-- operated by that franchise, every other place by the organization. V36 is already published, so its CHECK constraint is
-- replaced here instead of edited. Existing rows are DIRECT or FRANCHISE and pass the wider check.
alter table organization_operating_model_changes drop constraint if exists ck_organization_operating_model_changes_model;
alter table organization_operating_model_changes
    add constraint ck_organization_operating_model_changes_model check (model in ('DIRECT', 'FRANCHISE', 'HYBRID'));
