# shared-protocol module

Versioned wire protocol definitions.

## Related docs

- [protocol-spec.md](../docs/protocol-spec.md)
- [architecture-overview.md](../docs/architecture-overview.md)
- [security-validation-spec.md](../docs/security-validation-spec.md)
- [release-artifact-spec.md](../docs/release-artifact-spec.md)

## Current scope

- JSON schema v1 envelope: `shared-protocol/schema/rts-protocol-v1.schema.json`
- cross-language golden payloads: `shared-protocol/golden/`
  - handshake, handshake(resume), handshakeAck, commandBatch, snapshot, commandAck, matchEnd envelopes
  - command payload coverage: `move`, `attack`, `build`, `queue`, `surrender`
- envelope metadata fields:
  - `protocolVersion` (wire compatibility)
  - `simVersion` (rule/data version)
  - `buildHash` (build identity)
- cross-language model parity tests:
  - Kotlin golden tests: [ProtocolModelsTest.kt](../sim/src/test/kotlin/starkraft/sim/protocol/ProtocolModelsTest.kt)
  - Go golden tests: [models_test.go](../server/pkg/protocol/models_test.go)
  - Go client golden tests: [golden_test.go](../client/pkg/protocol/golden_test.go)

## Compatibility policy

- equal `protocolVersion` => compatible
- remote higher => client must upgrade
- remote lower => server must upgrade

## Next

- add protocol v2 draft with compatibility matrix documentation
- add optional binary transport schema/interop plan
