-- E-commerce and Inventory Management are one product now: Best Purchase (BEST_PURCHASE_APP), which also carries the
-- inventory privileges (IMS_*). ECOMMERCE_APP is renamed; INVENTORY_APP is folded into it. Where an organization or the
-- platform already has the merged row, the left-over inventory row is switched off rather than deleted, so history stays.

-- 1. The platform application row: e-commerce first, inventory only when no Best Purchase row exists yet.
update applications set code = 'BEST_PURCHASE_APP',
       name = case when name in ('E-commerce', 'Inventory Management') then 'Best Purchase' else name end
 where code = 'ECOMMERCE_APP'
   and not exists (select 1 from applications a where a.code = 'BEST_PURCHASE_APP');
update applications set code = 'BEST_PURCHASE_APP',
       name = case when name in ('E-commerce', 'Inventory Management') then 'Best Purchase' else name end
 where code = 'INVENTORY_APP'
   and not exists (select 1 from applications a where a.code = 'BEST_PURCHASE_APP');
update applications set active = false, updated_at = current_timestamp
 where code = 'INVENTORY_APP';

-- 2. Which organizations use it: the same order, one link per organization.
update tenant_applications set application_code = 'BEST_PURCHASE_APP'
 where application_code = 'ECOMMERCE_APP'
   and not exists (select 1 from tenant_applications o
                    where o.tenant_id = tenant_applications.tenant_id and o.application_code = 'BEST_PURCHASE_APP');
update tenant_applications set application_code = 'BEST_PURCHASE_APP'
 where application_code = 'INVENTORY_APP'
   and not exists (select 1 from tenant_applications o
                    where o.tenant_id = tenant_applications.tenant_id and o.application_code = 'BEST_PURCHASE_APP');
update tenant_applications set status = 'CANCELLED', updated_at = current_timestamp
 where application_code = 'INVENTORY_APP';

-- 3. The inventory privileges now belong to Best Purchase.
update permissions set application_code = 'BEST_PURCHASE_APP'
 where application_code in ('ECOMMERCE_APP', 'INVENTORY_APP');
