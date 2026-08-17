-- Links a Payment back to the CardCharge that produced it (only ever set by the Mercado Pago
-- webhook handler, never by staff-entered manual payments) so a later void can trigger an actual
-- gateway refund instead of just editing the internal ledger. A nullable FK, not a lookup by
-- tab+amount+timestamp: two people splitting a bill evenly can land on the exact same amount,
-- which would make that heuristic ambiguous - and worse, silently wrong - in a way a direct
-- reference never is. See docs/CARD_PAYMENT.md.
ALTER TABLE payments ADD COLUMN card_charge_id UUID REFERENCES card_charges(id) ON DELETE SET NULL;
