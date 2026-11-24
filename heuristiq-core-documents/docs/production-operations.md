# RailCab DMS Production Operations

## Observability

- Every response includes `X-Correlation-Id`.
- If a caller sends `X-Correlation-Id`, DMS preserves it; otherwise DMS generates one.
- Access logs include `event=http_request`, `correlationId`, HTTP method, path, status, latency, user id, role, and remote address.
- Prometheus metrics are exposed at `/api/v1/actuator/prometheus`.
- Grafana dashboard: `ops/grafana/railcab-dms-dashboard.json`.
- Prometheus alerts: `ops/prometheus/alerts.yaml`.

## Autoscaling

- Kubernetes deployment and service: `ops/k8s/deployment.yaml`.
- HorizontalPodAutoscaler: `ops/k8s/hpa.yaml`.
- Default production shape is 3 to 12 replicas.
- HPA targets 65 percent CPU and 75 percent memory.

## Backup And Restore

- Daily MongoDB backup CronJob: `ops/k8s/mongo-backup-cronjob.yaml`.
- Restore template: `ops/k8s/mongo-restore-job.yaml`.
- Before running restore, replace `BACKUP_S3_URI` with the exact backup archive URI.
- Restore job uses `--drop`; run it only in an approved restore window.

## Retention

- Soft-deleted document metadata is purged by the in-app scheduled job.
- Configure with:
  - `DMS_PURGE_JOB_ENABLED`
  - `DMS_PURGE_JOB_CRON`
  - `DMS_PURGE_DELETED_AFTER_DAYS`
- Legal-hold and retention checks prevent document deletion before purge is relevant.
