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

{{/* Secret name holding the DB password (existing one wins). */}}
{{- define "kairon.databaseSecretName" -}}
{{- if .Values.database.existingSecret -}}
{{- .Values.database.existingSecret -}}
{{- else -}}
{{- printf "%s-db" (include "kairon.fullname" .) -}}
{{- end -}}
{{- end -}}

{{/* JDBC URL: explicit override, else point at the in-chart postgresql release. */}}
{{- define "kairon.jdbcUrl" -}}
{{- if .Values.config.SPRING_DATASOURCE_URL -}}
{{- .Values.config.SPRING_DATASOURCE_URL -}}
{{- else -}}
{{- printf "jdbc:postgresql://%s-postgresql:5432/%s" .Release.Name .Values.database.name -}}
{{- end -}}
{{- end -}}
