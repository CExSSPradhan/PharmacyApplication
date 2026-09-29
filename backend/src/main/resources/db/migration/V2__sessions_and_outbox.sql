CREATE TABLE auth_tokens (
 token_hash VARCHAR(64) PRIMARY KEY,
 user_id BIGINT NOT NULL REFERENCES users(id),
 expires_at TIMESTAMPTZ NOT NULL
);
CREATE INDEX idx_token_expiry ON auth_tokens(expires_at);
CREATE TABLE rate_limits (
 bucket VARCHAR(200) PRIMARY KEY,
 hits INTEGER NOT NULL,
 window_end TIMESTAMPTZ NOT NULL
);
CREATE TABLE outbox_events (
 id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
 event_type VARCHAR(80) NOT NULL,
 payload TEXT NOT NULL,
 created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
 published_at TIMESTAMPTZ
);
CREATE TABLE notifications (
 event_id BIGINT PRIMARY KEY,
 message TEXT NOT NULL,
 created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX idx_outbox_pending ON outbox_events(id) WHERE published_at IS NULL;
CREATE INDEX idx_sale_date ON sales(sale_date);
CREATE INDEX idx_purchase_date ON purchases(purchase_date);
CREATE INDEX idx_sale_items_sale ON sale_items(sale_id);
CREATE INDEX idx_purchase_items_purchase ON purchase_items(purchase_id);
CREATE INDEX idx_movements_batch ON stock_movements(batch_id, created_at);
