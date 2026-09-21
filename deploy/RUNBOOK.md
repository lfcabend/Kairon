# Kairon — Operations runbook

Copy-pasteable commands for the operational tasks around the xbmc deployment
(single-node home k3s, reached over Tailscale Funnel). See
[`docs/milestones/M7-hardening-prod.md`](../docs/milestones/M7-hardening-prod.md)
for the decisions behind these mechanisms.

All commands below assume `--kube-context xbmc --namespace kairon` unless noted;
set those once with:

```sh
kubectl config use-context xbmc
kubectl config set-context --current --namespace=kairon
```

---

## Restore from backup

Backups are gzipped `pg_dump` files on the `kairon-backups` PVC, written daily by
the `kairon-backup` CronJob (`deploy/helm/kairon/templates/backup-cronjob.yaml`).

1. Find a pod that has the PVC mounted (the backup CronJob's own pod, if one ran
   recently, or start a throwaway one):
   ```sh
   kubectl run backup-shell --rm -it --restart=Never \
     --image=postgres:16-alpine \
     --overrides='{"spec":{"containers":[{"name":"backup-shell","image":"postgres:16-alpine","command":["sleep","3600"],"volumeMounts":[{"name":"backups","mountPath":"/backups"}]}],"volumes":[{"name":"backups","persistentVolumeClaim":{"claimName":"kairon-backups"}}]}}' \
     -- sleep 3600
   ```
2. In another terminal, list and copy the dump you want:
   ```sh
   kubectl exec backup-shell -- ls -la /backups
   kubectl cp backup-shell:/backups/kairon-20260101T030000.sql.gz ./kairon-backup.sql.gz
   ```
3. Restore into a **scratch database first** to inspect before touching real
   data (swap the target for the real `kairon` database only once you're sure):
   ```sh
   gunzip -c kairon-backup.sql.gz | kubectl exec -i backup-shell -- \
     psql "postgresql://kairon:<password>@my-postgres-postgresql.postgres.svc.cluster.local:5432/kairon_restore_check"
   ```
4. Clean up: `kubectl delete pod backup-shell` (the `--rm` above does this
   automatically once you exit its shell).

## Rotate the JWT secret

The chart's `templates/secret.yaml` reuses whatever `KAIRON_JWT_SECRET` is
already in the cluster on every `helm upgrade` (so upgrades don't silently drop
every live session). To force a rotation:

```sh
kubectl delete secret kairon-app
helm upgrade --install kairon deploy/helm/kairon \
  --kube-context xbmc --namespace kairon \
  -f deploy/helm/kairon/values-xbmc.yaml --set image.tag=<current-tag>
```

This mints a fresh random secret and restarts the app pod. **Every existing
access and refresh token is invalidated** — every logged-in session/device has
to log in again.

## Rotate the DB password

1. Change the password at the database itself (connect as the `postgres`
   superuser to the Bitnami install and `ALTER ROLE kairon WITH PASSWORD '...'`).
2. Update the out-of-band Secret the chart points at (`database.existingSecret:
   kairon-db`):
   ```sh
   kubectl create secret generic kairon-db \
     --from-literal=SPRING_DATASOURCE_PASSWORD=<the new password> \
     --dry-run=client -o yaml | kubectl apply -f -
   ```
3. Restart the app pod so it picks up the new value (the Secret isn't
   hot-reloaded into a running container):
   ```sh
   kubectl rollout restart deployment/kairon
   ```

## Create the required secrets on a fresh cluster

Before the first `helm upgrade --install` against a brand-new cluster:

```sh
kubectl create namespace kairon
kubectl -n kairon create secret generic kairon-db \
  --from-literal=SPRING_DATASOURCE_PASSWORD=<the kairon role's password>
```

`security.existingSecret` (holding `KAIRON_JWT_SECRET`) is optional — leave it
unset and the chart generates + manages one itself (see "Rotate the JWT secret"
above). Set it only if you want the JWT secret managed outside Helm entirely.

## Roll back a Helm release

```sh
helm history kairon
helm rollback kairon <revision>
```

**The migration Job does not run on rollback** — Helm hooks are
install/upgrade-only, so `helm rollback` restores the previous app version's
Deployment but leaves the database schema exactly as it is. If the revision
you're rolling back to needs an older schema, that's a manual Flyway `undo` (a
paid feature) or a hand-written down-migration — the same caveat every
Flyway-based project has. In practice: prefer rolling forward with a fix over
rolling back across a migration boundary.

## Read logs

```sh
kubectl logs deployment/kairon -f
kubectl logs job/kairon-migrate           # the most recent migration run
kubectl logs cronjob/kairon-backup        # not directly loggable; find the pod:
kubectl get pods -l job-name=kairon-backup-<timestamp>
```

The `prod` Spring profile logs structured ECS JSON to stdout. Every request
carries a `correlationId` field (from `CorrelationIdFilter`) that's also echoed
to the browser as the `X-Request-Id` response header — when a user reports an
issue, grab that header value from their browser's network tab and grep for it:

```sh
kubectl logs deployment/kairon | grep '"correlationId":"<the-id>"'
```

## Known risk: backups are on-node

The `kairon-backups` PVC (`local-path` StorageClass) lives on the **same node**
as the database it's backing up. A dead node loses the live database and every
backup of it simultaneously — this is a known, accepted single point of
failure (M7 D8), not an oversight. No off-node replication is wired up.

Mitigation, not yet automated: periodically copy the newest dump off the node
by hand (`kubectl cp` per the restore steps above, onto another machine or a
NAS share). A scheduled off-node copy step is a reasonable future addition to
`backup-cronjob.yaml` if this box is ever actually exercised for a real
disaster, but isn't built today.
