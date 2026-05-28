CREATE TABLE payments (
    id UUID PRIMARY KEY,
    customer_id VARCHAR(255) NOT NULL,
    amount DECIMAL(19, 4) NOT NULL,
    type VARCHAR(50) NOT NULL,
    status VARCHAR(50) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    version BIGINT NOT NULL DEFAULT 0
);

-- Indices for optimized searching and filtering (K6 Stress Prep)
CREATE INDEX idx_payments_customer_status ON payments (customer_id, status);
CREATE INDEX idx_payments_created_at ON payments (created_at);
CREATE INDEX idx_payments_amount ON payments (amount);
