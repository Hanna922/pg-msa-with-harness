-- Card Authorization Service test data
-- Acquirer A: Visa only
INSERT INTO cards (id, card_number, card_type, card_status, expiry_date, credit_limit, used_amount, pin, customer_id, created_at, version) VALUES
(1, '4111111111111111', 'DEBIT', 'ACTIVE', '2027-12-31', NULL, NULL, '$2a$10$vg3Gcw.lMS4okoXKpIML.eaRt5Sni8zObcwADbcm1SZS0ymxJXkli', 'CUST-001', NOW(), 0),
(7, '4012888888881881', 'DEBIT', 'ACTIVE', '2024-12-31', NULL, NULL, '$2a$10$vg3Gcw.lMS4okoXKpIML.eaRt5Sni8zObcwADbcm1SZS0ymxJXkli', 'CUST-007', NOW(), 0);
