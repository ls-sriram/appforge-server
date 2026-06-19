# ─── Build stage ──────────────────────────────────────────────────────────
FROM gradle:8-jdk17 AS build
COPY --chown=gradle:gradle . /home/gradle/src
WORKDIR /home/gradle/src
RUN gradle installDist --no-daemon -x test

# ─── Run stage ────────────────────────────────────────────────────────────
FROM eclipse-temurin:17-jre-jammy

LABEL org.opencontainers.image.source="https://github.com/your-org/appforge-backend"
LABEL org.opencontainers.image.description="AppForge Backend - Generic Platform Backend"

EXPOSE 8080
WORKDIR /app

# Copy built application
COPY --from=build /home/gradle/src/build/install/appforge-backend /app

# Health check intentionally disabled for local/dev noise reduction.
# Re-enable later if needed, for example:
# HEALTHCHECK --interval=30s --timeout=5s --start-period=10s --retries=3 \
#     CMD curl -f http://localhost:8080/health || exit 1

# Run as non-root user
RUN addgroup --system --gid 1001 appgroup && \
    adduser --system --uid 1001 --gid 1001 appuser
USER appuser

ENTRYPOINT ["/app/bin/appforge-backend"]
