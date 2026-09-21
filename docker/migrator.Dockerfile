# syntax=docker/dockerfile:1
#
# Runs Flyway migrations as a Helm pre-install/pre-upgrade hook Job
# (deploy/helm/kairon/templates/migration-job.yaml). Version pinned to match
# whatever Spring Boot's dependency management resolves for flyway-core /
# flyway-database-postgresql in backend/build.gradle.kts — re-check this tag
# whenever Spring Boot is bumped (see docs/milestones/M7-hardening-prod.md D5).
FROM flyway/flyway:12.4.0-alpine
COPY backend/src/main/resources/db/migration/ /flyway/sql/
