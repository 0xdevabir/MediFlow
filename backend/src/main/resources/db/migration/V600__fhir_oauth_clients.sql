-- T12 FHIR R4 facade: machine clients for client-credentials access.
CREATE TABLE IF NOT EXISTS fhir_oauth_client (
    id              UUID PRIMARY KEY,
    client_id       VARCHAR(128) NOT NULL UNIQUE,
    client_secret_hash VARCHAR(255) NOT NULL,
    display_name    VARCHAR(255) NOT NULL,
    active          BOOLEAN NOT NULL DEFAULT TRUE,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    version         BIGINT NOT NULL DEFAULT 0
);

CREATE TABLE IF NOT EXISTS fhir_access_token (
    id              UUID PRIMARY KEY,
    client_id       UUID NOT NULL REFERENCES fhir_oauth_client (id),
    token_hash      VARCHAR(128) NOT NULL UNIQUE,
    expires_at      TIMESTAMPTZ NOT NULL,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_fhir_access_token_expires ON fhir_access_token (expires_at);
