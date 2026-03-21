-- Card Authorization Service 2 test data
-- Acquirer B: Mastercard, Amex, Discover, JCB only
INSERT INTO cards (id, card_number, card_type, card_status, expiry_date, credit_limit, used_amount, pin, customer_id, created_at, version) VALUES
(2, '5555555555554444', 'DEBIT', 'ACTIVE', '2027-12-31', NULL, NULL, '$2a$10$vg3Gcw.lMS4okoXKpIML.eaRt5Sni8zObcwADbcm1SZS0ymxJXkli', 'CUST-002', NOW(), 0),
(3, '378282246310005', 'DEBIT', 'ACTIVE', '2027-12-31', NULL, NULL, '$2a$10$vg3Gcw.lMS4okoXKpIML.eaRt5Sni8zObcwADbcm1SZS0ymxJXkli', 'CUST-003', NOW(), 0),
(4, '6011111111111117', 'CREDIT', 'ACTIVE', '2027-12-31', 5000000.00, 0.00, '$2a$10$vg3Gcw.lMS4okoXKpIML.eaRt5Sni8zObcwADbcm1SZS0ymxJXkli', 'CUST-004', NOW(), 0),
(5, '3530111333300000', 'CREDIT', 'ACTIVE', '2027-12-31', 3000000.00, 2500000.00, '$2a$10$vg3Gcw.lMS4okoXKpIML.eaRt5Sni8zObcwADbcm1SZS0ymxJXkli', 'CUST-005', NOW(), 0),
(6, '5105105105105100', 'DEBIT', 'SUSPENDED', '2027-12-31', NULL, NULL, '$2a$10$vg3Gcw.lMS4okoXKpIML.eaRt5Sni8zObcwADbcm1SZS0ymxJXkli', 'CUST-006', NOW(), 0);
