-- V3: multi-gateway support — payments.gateway records which of RAZORPAY/PAYU/TOUCAN/PINELABS an
-- online payment was routed through (null for CASH). See Payment.gateway's javadoc for the full
-- method-vs-gateway relationship.
ALTER TABLE payments ADD COLUMN gateway VARCHAR(32);
