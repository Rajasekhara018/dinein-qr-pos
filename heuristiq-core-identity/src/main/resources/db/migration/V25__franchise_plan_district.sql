-- A franchise plan can name the district (or city) it covers by its code as well as its name. The code is the district's
-- code in the geography reference list (GeoNames' second-level code); blank when the plan covers a whole state or country,
-- or names a place typed by hand. Portable SQL only (the migration is also exercised on H2 in tests).

alter table org_franchise_plan add column if not exists district_code varchar(20) not null default '';
