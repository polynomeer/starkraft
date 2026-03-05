# shared-protocol module

Versioned wire protocol definitions.

## Current scope

- JSON schema v1 envelope: `shared-protocol/schema/rts-protocol-v1.schema.json`
- cross-language golden payloads: `shared-protocol/golden/`
  - handshake, commandBatch, snapshot envelopes
- envelope metadata fields:
  - `protocolVersion` (wire compatibility)
  - `simVersion` (rule/data version)
  - `buildHash` (build identity)

## Compatibility policy

- equal `protocolVersion` => compatible
- remote higher => client must upgrade
- remote lower => server must upgrade

## Next

- add Go and Kotlin protocol model parity checks
- add room/session and command ack schema variants
