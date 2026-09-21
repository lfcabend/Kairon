{{/* Chart name, overridable. */}}
{{- define "kairon.name" -}}
{{- default .Chart.Name .Values.nameOverride | trunc 63 | trimSuffix "-" -}}
{{- end -}}

{{/* Fully qualified app name. */}}
{{- define "kairon.fullname" -}}
{{- if .Values.fullnameOverride -}}
{{- .Values.fullnameOverride | trunc 63 | trimSuffix "-" -}}
{{- else -}}
{{- $name := default .Chart.Name .Values.nameOverride -}}
{{- if contains $name .Release.Name -}}
{{- .Release.Name | trunc 63 | trimSuffix "-" -}}
{{- else -}}
{{- printf "%s-%s" .Release.Name $name | trunc 63 | trimSuffix "-" -}}
{{- end -}}
{{- end -}}
{{- end -}}

{{- define "kairon.chart" -}}
{{- printf "%s-%s" .Chart.Name .Chart.Version | replace "+" "_" | trunc 63 | trimSuffix "-" -}}
{{- end -}}

{{- define "kairon.labels" -}}
helm.sh/chart: {{ include "kairon.chart" . }}
{{ include "kairon.selectorLabels" . }}
app.kubernetes.io/version: {{ .Chart.AppVersion | quote }}
app.kubernetes.io/managed-by: {{ .Release.Service }}
{{- end -}}

{{- define "kairon.selectorLabels" -}}
app.kubernetes.io/name: {{ include "kairon.name" . }}
app.kubernetes.io/instance: {{ .Release.Name }}
{{- end -}}

{{- define "kairon.serviceAccountName" -}}
{{- if .Values.serviceAccount.create -}}
{{- default (include "kairon.fullname" .) .Values.serviceAccount.name -}}
{{- else -}}
{{- default "default" .Values.serviceAccount.name -}}
{{- end -}}
{{- end -}}

{{/* Image reference; tag falls back to the chart appVersion. */}}
{{- define "kairon.image" -}}
{{- $tag := .Values.image.tag | default .Chart.AppVersion -}}
{{- printf "%s:%s" .Values.image.repository $tag -}}
{{- end -}}

{{/* Migrator image (M7 D5): Flyway CLI + this repo's SQL migrations, run by
     templates/migration-job.yaml. Repository defaults to "<image.repository>-migrator";
     tag defaults to the app image's own tag — the two are always built and pushed
     together (Taskfile.yml, .github/workflows/deploy.yml), so a bare `--set
     image.tag=<sha>` is enough to pin both without repeating the flag. */}}
{{- define "kairon.migratorImage" -}}
{{- $repo := .Values.migrator.image.repository | default (printf "%s-migrator" .Values.image.repository) -}}
{{- $tag := .Values.migrator.image.tag | default (.Values.image.tag | default .Chart.AppVersion) -}}
{{- printf "%s:%s" $repo $tag -}}
{{- end -}}

{{/* Secret name holding the DB password (existing one wins). */}}
{{- define "kairon.databaseSecretName" -}}
{{- if .Values.database.existingSecret -}}
{{- .Values.database.existingSecret -}}
{{- else -}}
{{- printf "%s-db" (include "kairon.fullname" .) -}}
{{- end -}}
{{- end -}}

{{/* Secret name holding app secrets (KAIRON_JWT_SECRET). An existing one wins. */}}
{{- define "kairon.appSecretName" -}}
{{- if .Values.security.existingSecret -}}
{{- .Values.security.existingSecret -}}
{{- else -}}
{{- printf "%s-app" (include "kairon.fullname" .) -}}
{{- end -}}
{{- end -}}

{{/* JDBC URL: explicit override wins; otherwise built from database.host / .port /
     .name, where an empty database.host falls back to the bundled postgresql
     subchart ({release}-postgresql). Set database.host to an FQDN to reuse an
     external PostgreSQL install. */}}
{{- define "kairon.jdbcUrl" -}}
{{- if .Values.config.SPRING_DATASOURCE_URL -}}
{{- .Values.config.SPRING_DATASOURCE_URL -}}
{{- else -}}
{{- $host := .Values.database.host | default (printf "%s-postgresql" .Release.Name) -}}
{{- $port := .Values.database.port | default 5432 -}}
{{- printf "jdbc:postgresql://%s:%v/%s" $host $port .Values.database.name -}}
{{- end -}}
{{- end -}}
