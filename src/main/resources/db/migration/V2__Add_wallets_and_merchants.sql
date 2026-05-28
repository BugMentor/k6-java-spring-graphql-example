CREATE TABLE merchants (
    id UUID PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    api_key VARCHAR(255) NOT NULL UNIQUE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE wallets (
    id UUID PRIMARY KEY,
    user_id VARCHAR(255) NOT NULL UNIQUE,
    balance DECIMAL(19, 4) NOT NULL DEFAULT 0.0000,
    currency VARCHAR(10) NOT NULL,
    version BIGINT NOT NULL DEFAULT 0
);

-- Update payments table with Foreign Keys
ALTER TABLE payments ADD COLUMN merchant_id UUID;
ALTER TABLE payments ADD COLUMN wallet_id UUID;

ALTER TABLE payments ADD CONSTRAINT fk_payment_merchant FOREIGN KEY (merchant_id) REFERENCES merchants(id);
ALTER TABLE payments ADD CONSTRAINT fk_payment_wallet FOREIGN KEY (wallet_id) REFERENCES wallets(id);

-- Performance indices for new relations
CREATE INDEX idx_wallets_user_id ON wallets (user_id);
CREATE INDEX idx_payments_merchant_id ON payments (merchant_id);
CREATE INDEX idx_payments_wallet_id ON payments (wallet_id);
