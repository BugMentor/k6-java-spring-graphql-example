#!/bin/bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
FLOCI_ENDPOINT="${FLOCI_ENDPOINT:-http://localhost:4566}"
CLUSTER_NAME="${CLUSTER_NAME:-payment-eks}"
REGION="${REGION:-us-east-1}"
PAYMENT_IMAGE="${PAYMENT_IMAGE:-payment-service:1.0.0}"
NAMESPACE="payments"

GREEN='\033[0;32m'
YELLOW='\033[1;33m'
RED='\033[0;31m'
BOLD='\033[1m'
NC='\033[0m'

export AWS_ACCESS_KEY_ID=test
export AWS_SECRET_ACCESS_KEY=test
export AWS_DEFAULT_REGION="${REGION}"

aws_cmd() {
    aws --endpoint-url "${FLOCI_ENDPOINT}" --no-cli-pager "$@"
}

log_section() {
    echo ""
    echo -e "${BOLD}============================================================${NC}"
    echo -e "${BOLD}  $1${NC}"
    echo -e "${BOLD}============================================================${NC}"
}

log_step()  { echo -e "  ${GREEN}->${NC} $1"; }
log_warn()  { echo -e "  ${YELLOW}!${NC} $1"; }
log_error() { echo -e "  ${RED}X${NC} $1"; }

wait_for_floci() {
    log_section "WAITING FOR FLOCI"
    local max_attempts=30
    local attempt=1
    while [ $attempt -le $max_attempts ]; do
        if curl -sf "${FLOCI_ENDPOINT}/_localstack/health" > /dev/null 2>&1; then
            log_step "Floci is ready (attempt ${attempt})"
            return 0
        fi
        echo -n "."
        sleep 2
        attempt=$((attempt + 1))
    done
    log_error "Floci did not become ready"
    exit 1
}

create_eks_cluster() {
    log_section "CREATING EKS CLUSTER: ${CLUSTER_NAME}"

    if aws_cmd eks describe-cluster --name "${CLUSTER_NAME}" > /dev/null 2>&1; then
        log_warn "Cluster '${CLUSTER_NAME}' already exists"
    fi

    log_step "Creating EKS cluster..."
    aws_cmd eks create-cluster \
        --name "${CLUSTER_NAME}" \
        --role-arn "arn:aws:iam::000000000000:role/eks-service-role" \
        --resources-vpc-config "subnetIds=subnet-00000000000000000,securityGroupIds=sg-00000000000000000"

    log_step "Waiting for ACTIVE..."
    aws_cmd eks wait cluster-active --name "${CLUSTER_NAME}"
    log_step "Cluster '${CLUSTER_NAME}' is ACTIVE"

    local k3s_container
    k3s_container=$(docker ps --format '{{.Names}}' --filter "name=floci-eks-${CLUSTER_NAME}" | head -1)
    K3S_PORT=$(docker port "${k3s_container}" 6443 2>/dev/null | awk -F: '{print $2}' | head -1)
    log_step "k3s API exposed on port: ${K3S_PORT}"
}

load_image() {
    log_section "LOADING PAYMENT-SERVICE IMAGE INTO k3s"
    local k3s_container
    k3s_container=$(docker ps --format '{{.Names}}' --filter "name=floci-eks-${CLUSTER_NAME}" | head -1)
    docker save "${PAYMENT_IMAGE}" | docker exec -i "${k3s_container}" ctr image import -
    log_step "Image loaded: ${PAYMENT_IMAGE}"
}

setup_kubeconfig() {
    log_section "SETTING UP KUBECONFIG"
    local k3s_container
    k3s_container=$(docker ps --format '{{.Names}}' --filter "name=floci-eks-${CLUSTER_NAME}" | head -1)
    K3S_PORT=$(docker port "${k3s_container}" 6443 2>/dev/null | awk -F: '{print $2}' | head -1)
    local kubeconfig="${SCRIPT_DIR}/kubeconfig-${CLUSTER_NAME}.yaml"

    local cert_data key_data
    cert_data=$(docker exec "${k3s_container}" cat /etc/rancher/k3s/k3s.yaml | grep 'client-certificate-data' | awk '{print $2}')
    key_data=$(docker exec "${k3s_container}" cat /etc/rancher/k3s/k3s.yaml | grep 'client-key-data' | awk '{print $2}')

    cat > "${kubeconfig}" << KUBECONF
apiVersion: v1
kind: Config
clusters:
- cluster:
    insecure-skip-tls-verify: true
    server: https://localhost:${K3S_PORT}
  name: ${CLUSTER_NAME}
contexts:
- context:
    cluster: ${CLUSTER_NAME}
    user: ${CLUSTER_NAME}-admin
  name: ${CLUSTER_NAME}
current-context: ${CLUSTER_NAME}
users:
- name: ${CLUSTER_NAME}-admin
  user:
    client-certificate-data: ${cert_data}
    client-key-data: ${key_data}
KUBECONF

    export KUBECONFIG="${kubeconfig}"
    kubectl get nodes --request-timeout=10s
    log_step "Kubeconfig: ${kubeconfig}"
}

deploy_workload() {
    log_section "DEPLOYING POSTGRESQL + PAYMENT-SERVICE + ELB"
    kubectl apply -f "${SCRIPT_DIR}/payment-service-elb.yaml"

    log_step "Waiting for PostgreSQL..."
    kubectl wait --for=condition=ready pod -l app=postgres -n payments --timeout=60s

    log_step "Waiting for payment-service (startup may take ~60s)..."
    kubectl wait --for=condition=ready pod -l app=payment-service -n payments --timeout=180s

    log_step "Workload deployed"
}

wait_for_metrics_server() {
    log_section "WAITING FOR METRICS SERVER"
    local max_attempts=20
    local attempt=1
    while [ $attempt -le $max_attempts ]; do
        if kubectl top pods -n payments 2>/dev/null | grep -v NAME | head -1 > /dev/null 2>&1; then
            log_step "Metrics server ready"
            kubectl top pods -n payments
            return 0
        fi
        echo -n "."
        sleep 3
        attempt=$((attempt + 1))
    done
    log_warn "Metrics server not ready - HPA will show <unknown> until metrics appear"
}

apply_hpa() {
    log_section "APPLYING HPA (CPU 80% | Memory 60%)"
    kubectl apply -f "${SCRIPT_DIR}/hpa.yaml"

    sleep 10
    log_step "HPA status:"
    kubectl get hpa -n payments -o wide
}

show_external_ip() {
    log_section "WAITING FOR ELB (LoadBalancer IP)"

    local max_attempts=15
    local attempt=1
    while [ $attempt -le $max_attempts ]; do
        local ext_ip
        ext_ip=$(kubectl get svc payment-service -n payments -o jsonpath='{.status.loadBalancer.ingress[0].ip}' 2>/dev/null || echo "")

        if [ -n "$ext_ip" ] && [ "$ext_ip" != "<none>" ]; then
            break
        fi
        echo -n "."
        sleep 3
        attempt=$((attempt + 1))
    done

    local svc_ip
    svc_ip=$(kubectl get svc payment-service -n payments -o jsonpath='{.status.loadBalancer.ingress[0].ip}')

    echo ""
    echo ""
    echo -e "${BOLD}============================================================${NC}"
    echo -e "${BOLD}  FLOCI EKS + ELB + HPA SIMULATION READY${NC}"
    echo -e "${BOLD}============================================================${NC}"
    echo ""
    echo -e "  EKS Cluster:     ${GREEN}${CLUSTER_NAME}${NC}"
    echo -e "  ELB External IP: ${GREEN}${svc_ip}${NC}"
    echo -e "  Kubeconfig:      ${SCRIPT_DIR}/kubeconfig-${CLUSTER_NAME}.yaml"
    echo ""
    echo -e "  ${YELLOW}HPA Thresholds:${NC}"
    echo -e "    CPU:    > 80% average utilization -> scale up"
    echo -e "    Memory: > 60% average utilization -> scale up"
    echo -e "    Min: 2  |  Max: 20"
    echo ""
    echo -e "  ${YELLOW}Access the app (port-forward):${NC}"
    echo -e "    KUBECONFIG=${SCRIPT_DIR}/kubeconfig-${CLUSTER_NAME}.yaml \\"
    echo -e "    kubectl port-forward svc/payment-service 8081:80 -n payments"
    echo -e "    curl http://localhost:8081/actuator/health"
    echo ""
    echo -e "  ${YELLOW}Watch HPA scaling:${NC}"
    echo -e "    KUBECONFIG=${SCRIPT_DIR}/kubeconfig-${CLUSTER_NAME}.yaml \\"
    echo -e "    kubectl get hpa payment-service-hpa -n payments --watch"
    echo ""
    echo -e "  ${YELLOW}List AWS resources in Floci:${NC}"
    echo -e "    aws --endpoint-url ${FLOCI_ENDPOINT} eks list-clusters"
    echo -e "    aws --endpoint-url ${FLOCI_ENDPOINT} elbv2 describe-load-balancers"
    echo ""
}

verify_aws_resources() {
    log_section "AWS RESOURCES IN FLOCI"
    log_step "EKS Clusters:"
    aws_cmd eks list-clusters --query 'clusters[]' --output text
}

main() {
    echo ""
    echo -e "${BOLD}+----------------------------------------------------------+${NC}"
    echo -e "${BOLD}|   Floci - EKS + ELB + HPA Simulation Setup              |${NC}"
    echo -e "${BOLD}|   Cluster: ${CLUSTER_NAME}                                    |${NC}"
    echo -e "${BOLD}|   CPU: 80%  |  Memory: 60%   (min=2, max=20)            |${NC}"
    echo -e "${BOLD}+----------------------------------------------------------+${NC}"

    wait_for_floci
    create_eks_cluster
    load_image
    setup_kubeconfig
    deploy_workload
    wait_for_metrics_server
    apply_hpa
    verify_aws_resources
    show_external_ip
}

main "$@"
