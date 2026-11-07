#!/usr/bin/env bash
# Generates a local dev CA + an nginx server certificate (self-signed chain) in infra/certs/out/.
# Output is gitignored. NEVER commit the generated keys. Production uses real CA-issued certs
# mounted at the same paths (server.crt = full chain, server.key).
#
# Usage: infra/certs/gen-dev-certs.sh [extra-dns-name ...]
#   MEDIFLOW_CERT_DAYS (default 825)   MEDIFLOW_CERT_FORCE=1 to regenerate
set -euo pipefail

DIR="$(cd "$(dirname "$0")" && pwd)/out"
DAYS="${MEDIFLOW_CERT_DAYS:-825}"
mkdir -p "$DIR"
chmod 700 "$DIR"

if [[ -f "$DIR/server.crt" && "${MEDIFLOW_CERT_FORCE:-0}" != "1" ]]; then
  echo "certs already exist in $DIR (set MEDIFLOW_CERT_FORCE=1 to regenerate)"
  exit 0
fi

command -v openssl >/dev/null || { echo "openssl is required" >&2; exit 1; }

SANS="DNS:localhost,DNS:mediflow.local,DNS:*.mediflow.local,IP:127.0.0.1,IP:::1"
for n in "$@"; do SANS="$SANS,DNS:$n"; done

umask 077

# 1. Dev CA (EC P-256)
openssl ecparam -name prime256v1 -genkey -noout -out "$DIR/ca.key"
openssl req -x509 -new -key "$DIR/ca.key" -sha256 -days "$DAYS" \
  -subj "/O=MediFlow Dev/CN=MediFlow Dev Root CA" \
  -addext "basicConstraints=critical,CA:TRUE,pathlen:0" \
  -addext "keyUsage=critical,keyCertSign,cRLSign" \
  -out "$DIR/ca.crt"

# 2. nginx server cert signed by the dev CA
openssl ecparam -name prime256v1 -genkey -noout -out "$DIR/server.key"
openssl req -new -key "$DIR/server.key" -subj "/O=MediFlow Dev/CN=localhost" -out "$DIR/server.csr"
cat > "$DIR/server.ext" <<EOF
basicConstraints=critical,CA:FALSE
keyUsage=critical,digitalSignature
extendedKeyUsage=serverAuth
subjectAltName=$SANS
EOF
openssl x509 -req -in "$DIR/server.csr" -CA "$DIR/ca.crt" -CAkey "$DIR/ca.key" -CAcreateserial \
  -days "$DAYS" -sha256 -extfile "$DIR/server.ext" -out "$DIR/server.leaf.crt"
cat "$DIR/server.leaf.crt" "$DIR/ca.crt" > "$DIR/server.crt"
rm -f "$DIR/server.csr" "$DIR/server.ext" "$DIR/ca.srl"

# Files are bind-mounted individually into non-root containers (nginx uid 101), so the
# server key must be readable by "other"; the parent dir (700) keeps it private on the host.
chmod 644 "$DIR/server.crt" "$DIR/server.leaf.crt" "$DIR/ca.crt" "$DIR/server.key"
chmod 600 "$DIR/ca.key"

echo "Generated in $DIR:"
echo "  ca.crt      - import into your browser/OS trust store for local testing"
echo "  server.crt  - nginx certificate chain (SAN: $SANS)"
echo "  server.key  - nginx private key (do not commit)"
