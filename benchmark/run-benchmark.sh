#!/bin/bash
set -euo pipefail

BENCHMARK_DIR="$(cd "$(dirname "$0")" && pwd)"
K6_DIR="${BENCHMARK_DIR}/k6"
RESULTS_DIR="${K6_DIR}/results"
mkdir -p "$RESULTS_DIR"

TIMESTAMP=$(date +%Y%m%d_%H%M%S)
BASE_URL="${BASE_URL:-http://localhost:8080}"
PROMETHEUS_URL="${PROMETHEUS_URL:-http://localhost:9091}"

GREEN='\033[0;32m'
YELLOW='\033[1;33m'
RED='\033[0;31m'
BOLD='\033[1m'
NC='\033[0m'

log_section() {
    echo ""
    echo -e "${BOLD}============================================================${NC}"
    echo -e "${BOLD}  $1${NC}"
    echo -e "${BOLD}============================================================${NC}"
}

log_step() {
    echo -e "  ${GREEN}→${NC} $1"
}

scale_to_two() {
    log_section "SCALING TO 2 PODS (CONSTITUTIONAL MINIMUM)"
    log_step "Scaling deployment to replicas=2..."
    kubectl scale deployment/payment-service --replicas=2 -n payments

    log_step "Waiting for HPA to respect minReplicas=2 and pods to be ready..."
    local waited=0
    while [ $waited -lt 120 ]; do
        local ready=$(kubectl get pods -n payments -l app=payment-service --field-selector=status.phase=Running -o jsonpath='{.items[?(@.status.containerStatuses[0].ready==true)].metadata.name}' 2>/dev/null | wc -w)
        local total=$(kubectl get pods -n payments -l app=payment-service --no-headers 2>/dev/null | wc -l)
        if [ "$ready" -ge 2 ] && [ "$total" -le 3 ]; then
            echo -e "  ${GREEN}✓${NC} 2 pods ready (total: ${total})"
            sleep 5
            return 0
        fi
        sleep 5
        waited=$((waited + 5))
    done
    echo -e "  ${YELLOW}⚠${NC} Timeout waiting for 2 pods. Proceeding anyway..."
    return 1
}

run_test() {
    local name="$1"
    local script="$2"
    local extra_args="${3:-}"
    local output="${RESULTS_DIR}/${TIMESTAMP}_${name}"

    log_section "RUNNING: ${name}"
    log_step "Script:  ${script}"
    log_step "Output:  ${output}"

    k6 run \
        --out json="${output}.json" \
        --out csv="${output}.csv" \
        -e BASE_URL="${BASE_URL}" \
        -e PROMETHEUS_URL="${PROMETHEUS_URL}" \
        ${extra_args} \
        --summary-export="${output}_summary.json" \
        "${K6_DIR}/${script}" 2>&1 | tee "${output}_raw.log"

    local exit_code=${PIPESTATUS[0]}
    if [ $exit_code -eq 0 ]; then
        echo -e "${GREEN}  ✓ ${name} completed${NC}"
    else
        echo -e "${RED}  ✗ ${name} failed (exit ${exit_code})${NC}"
    fi

    echo "$exit_code" > "${output}_exit_code"
    return $exit_code
}

COMMAND="${1:-all}"

case "$COMMAND" in
    all)
        log_section "FULL SCALING TEST SUITE"
        log_step "HPA thresholds: CPU > 80% | Memory > 80%"
        log_step "Grafana:  http://localhost:3000 (admin/admin)"
        log_step "Base URL: ${BASE_URL}"
        echo ""

        scale_to_two
        run_test "cpu-stress" "cpu-stress-test.js"
        echo ""
        scale_to_two
        run_test "memory-stress" "memory-stress-test.js"
        echo ""
        scale_to_two
        run_test "max-capacity" "max-capacity-test.js"

        log_section "ALL TESTS COMPLETE"
        echo -e "  Results: ${RESULTS_DIR}/${TIMESTAMP}_*"
        echo -e "  ${BOLD}Grafana: http://localhost:3000${NC} → Payment Service Dashboard"
        ;;

    cpu)
        scale_to_two
        run_test "cpu-stress" "cpu-stress-test.js"
        ;;

    mem)
        scale_to_two
        run_test "memory-stress" "memory-stress-test.js"
        ;;

    tp)
        scale_to_two
        run_test "max-capacity" "max-capacity-test.js"
        ;;

    load)
        scale_to_two
        run_test "load" "payment-service-load-test.js" "-e TARGET_VUS=${VUS:-200} -e TEST_DURATION=${DURATION:-10m}"
        ;;

    *)
        echo "Usage: $0 {all|cpu|mem|tp|load}"
        echo ""
        echo "  all   — Run full suite (CPU stress + Memory stress + Max Capacity)"
        echo "  cpu   — CPU stress test (triggers HPA at CPU > 80%)"
        echo "  mem   — Memory stress test (triggers HPA at Memory > 60%)"
        echo "  tp    — Max capacity test (5,000 VUs, 15min ramp, 1h total)"
        echo "  load  — General load test for baseline benchmarking"
        echo ""
        echo "Environment:"
        echo "  BASE_URL        = ${BASE_URL}"
        echo "  PROMETHEUS_URL  = ${PROMETHEUS_URL}"
        echo "  VUS             = VUs for load test (default 200)"
        echo "  DURATION        = duration for load test (default 10m)"
        exit 1
        ;;
esac
