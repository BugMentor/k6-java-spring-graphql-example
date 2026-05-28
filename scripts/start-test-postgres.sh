#!/bin/bash
set -euo pipefail

CONTAINER_NAME="payment-test-postgres"
PORT=55432

if docker ps --format '{{.Names}}' | grep -q "^${CONTAINER_NAME}$"; then
    echo "PostgreSQL test container already running"
    exit 0
fi

if docker ps -a --format '{{.Names}}' | grep -q "^${CONTAINER_NAME}$"; then
    echo "Removing stopped PostgreSQL test container..."
    docker rm -f "${CONTAINER_NAME}" >/dev/null 2>&1 || true
fi

echo "Starting PostgreSQL test container on port ${PORT}..."
docker run -d \
    --name "${CONTAINER_NAME}" \
    -p "${PORT}:5432" \
    -e POSTGRES_DB=payment_db \
    -e POSTGRES_USER=payment_user \
    -e POSTGRES_PASSWORD=payment_pass \
    postgres:16-alpine

echo "Waiting for PostgreSQL to be ready..."
for i in $(seq 1 30); do
    if docker exec "${CONTAINER_NAME}" pg_isready -U payment_user -d payment_db >/dev/null 2>&1; then
        echo "PostgreSQL is ready!"
        exit 0
    fi
    sleep 1
done

echo "ERROR: PostgreSQL failed to start"
exit 1
