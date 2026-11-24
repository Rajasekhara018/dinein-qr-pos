-- privilege.json permission descriptions now exceed 255 characters (longest is ~450), which made
-- UamBootstrapRunner.seedPermissions fail with "value too long for type character varying(255)"
-- and stopped UAM from starting in the postgres profile.
alter table permissions alter column description type varchar(2048);
