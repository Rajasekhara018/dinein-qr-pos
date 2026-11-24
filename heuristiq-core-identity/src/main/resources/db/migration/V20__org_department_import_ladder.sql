-- Position ladder on the department import (docs/ORG_DEPARTMENT_IMPORT_DESIGN.md): the CSV may name a ladder per department, and the batch
-- counts the departments left without one. V19 had already been applied to databases before these two columns existed, and an applied
-- migration is never edited (Flyway checks its checksum), so they are added here. Safe to run again.

alter table org_dept_import add column if not exists without_ladder integer not null default 0;
alter table org_dept_import_row add column if not exists ladder varchar(200);
