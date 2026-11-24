-- Finding which organizations link one application (used when an organization removes an application, to decide whether
-- the platform row may be deleted too).
create index if not exists idx_tenant_applications_code on tenant_applications (application_code, status);
