-- Who paid, as entered at checkout. Optional: payments created by other services (ride completion,
-- dispatch) carry only the customer id, so these stay NULL for them.
ALTER TABLE payments ADD COLUMN customer_name VARCHAR(255);
ALTER TABLE payments ADD COLUMN customer_email VARCHAR(255);
ALTER TABLE payments ADD COLUMN customer_mobile VARCHAR(32);
