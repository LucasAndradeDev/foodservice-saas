-- Persists the QR code data Woovi returns at charge-creation time (previously only ever held in
-- one browser's React state) so a still-PENDING charge can be re-displayed after a page reload or
-- from a poll - needed for split-comanda payments where several Pix charges can be pending on the
-- same tab at once (see docs/PIX_PAYMENT.md).
ALTER TABLE pix_charges
    ADD COLUMN br_code TEXT,
    ADD COLUMN qr_code_image TEXT,
    ADD COLUMN payment_link_url TEXT;
