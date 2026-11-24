-- An application's description may be up to 1000 characters (ApplicationEntity declares @Size(max = 1000) and the form lets
-- people type that much), but the column was created as varchar(255), so saving a longer description failed with
-- "value too long for type character varying(255)".
alter table applications alter column description type varchar(1000);
