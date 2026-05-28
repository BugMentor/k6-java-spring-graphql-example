# LGTM Stack Installation via Helm

To deploy the full LGTM observability stack in your Kubernetes cluster:

## 1. Add Grafana Helm Repository
```bash
helm repo add grafana https://grafana.github.io/helm-charts
helm repo update
```

## 2. Install Loki (Logs)
```bash
helm upgrade --install loki grafana/loki-stack \
  --set loki.persistence.enabled=true \
  --set loki.persistence.size=10Gi
```

## 3. Install Tempo (Traces)
```bash
helm upgrade --install tempo grafana/tempo \
  --set tempo.storage.trace.backend=local \
  --set tempo.storage.trace.local.path=/var/tempo/traces \
  --set tempo.persistence.enabled=true
```

## 4. Install Mimir (Metrics)
```bash
helm upgrade --install mimir grafana/mimir-distributed \
  --set mimir.structure.monolithic=true
```

## 5. Install Grafana (Dashboard)
```bash
helm upgrade --install grafana grafana/grafana \
  --set persistence.enabled=true \
  --set adminPassword=admin \
  --set service.type=LoadBalancer
```

## 6. Configure Data Sources in Grafana
Once Grafana is running, add the following endpoints as Data Sources:
- **Mimir**: `http://mimir.default.svc.cluster.local:9009/prometheus`
- **Loki**: `http://loki.default.svc.cluster.local:3100`
- **Tempo**: `http://tempo.default.svc.cluster.local:3100`
