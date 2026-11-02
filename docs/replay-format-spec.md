# Replay Format Spec

## 목적

replay는 경기 재현, 결정론 검증, 회귀 분석의 기준 산출물이다.

## 요구사항

- 재실행 가능해야 한다.
- hash 검증 가능해야 한다.
- 사람이 읽을 수 있는 디버그 경로를 제공해야 한다.
- 장기적으로 버전 구분이 가능해야 한다.

## 현재 형식

프로젝트에는 서로 다른 두 replay 형식이 있으며, hash 체계도 서로 호환되지 않는다.

| 구분 | sim replay | server replay |
|---|---|---|
| 생성 | `:sim:run`의 `--record` / `--replayOut` / `--replayDump <file>` (`sim/src/main/kotlin/replay/ReplayIO.kt`) | 서버 `STARKRAFT_REPLAY_PATH` (`server/pkg/authoritative/replay.go`) |
| 형식 | 단일 JSON 객체 (legacy: JSON array) | JSONL (한 줄에 record 하나) |
| 명령 | `sim` 내부 `Command` 이벤트 | 와이어 `WireCommand` + 적용 결과 `CommandAck` |
| hash | command stream 기준 `replayHash` | keyframe의 서버 room `worldHash` |

### Sim replay (JSON)

```json
{"schema":1,"replayHash":123,"seed":null,"mapId":null,"buildVersion":null,"winnerFaction":null,"matchEndReason":null,"events":[{"type":"move","tick":10,"units":[1],"x":5.0,"y":5.0, ...}]}
```

- `schema`: 현재 `1`. `0`(legacy)과 `1` 외의 값은 로드 시 거부한다.
- `replayHash`: 이벤트 목록으로 계산한 `ReplayHashRecorder` 값.
- `seed`, `mapId`, `buildVersion`, `winnerFaction`, `matchEndReason`(`elimination`/`surrender`/`timeout`/`draw`): 스키마상 선택(null 가능). `:sim:run`으로 저장하면 seed/mapId/buildVersion과 종료 결과가 채워진다.
- `events[]`: `type`, `tick` 및 명령별 필드(`units`, `x`, `y`, `target`, `faction`, `typeId`, `archetype`, `label`, `labelId`, `queued` 등).
  - type 예: `move`, `patrol`, `attackMove`, `hold`, `attack`, `harvest`, `construct`, `spawn`, `spawnNode`, `build`, `train`, `research`, `cancelTrain`, `cancelBuild`, `cancelResearch`, `rally`, `surrenderFaction` (및 `*Faction`/`*Type`/`*Archetype` 변형).
- protocol version, sim version, start tick, request id, 발행 플레이어 필드는 없다.

### Server replay (JSONL)

서버 시작 시 파일을 새로 만들고(기존 파일 덮어씀) record마다 flush한다. 서버 프로세스 하나가 모든 room을 같은 파일에 기록하며 record에 room id는 없다.

| `recordType` | 필드 | 기록 시점 |
|---|---|---|
| `header` | `timestampUtc`, `protocolVersion`, `simVersion`, `buildHash`(선택), `tickMs` | 서버 시작 시 1회 |
| `command` | `clientId`, `command`(WireCommand), `ack`(CommandAck) | room tick에서 명령이 적용/거부될 때 |
| `keyframe` | `tick`, `worldHash`, `units[]` | tick이 `STARKRAFT_KEYFRAME_EVERY`(기본 50)의 배수일 때 (0 이하이면 비활성) |
| `matchEnd` | `tick`, `winnerId`(선택) | room 매치 종료 시 1회 |

- ingress 단계에서 거부된 batch(`batchTooLarge`, `invalidPayload`, `unsupportedCommand`, `queueFull`)는 기록되지 않는다.
- seed, map id, start tick 필드는 없다. 명령의 tick은 `ack.tick`(적용된 room tick)이다.
- `worldHash`는 `room.go`의 자체 Go unit 모델 hash다. 서버는 Kotlin `sim`을 실행하지 않으므로 이 값은 sim world hash와 비교할 수 없다.

## 검증 방식

### Sim replay hash

- 저장 시 command stream 기준 `replayHash`를 기록한다.
- `ReplayIO.load`는 schema 1이면 항상 hash를 재계산해 불일치 시 실패한다.

### Server replay 검증

- `server/cmd/replaycheck` (`VerifyReplayFile`): header가 첫 줄이고 정확히 1개, `command`의 `ack.tick` 비감소, keyframe tick 엄격 증가, keyframe `worldHash` 재계산 일치, `matchEnd` 최대 1개이며 tick 역행 없음, 알 수 없는 `recordType` 거부.
- `tools replay verify-ndjson`: keyframe `worldHash`를 서버와 같은 방식으로 재계산해 불일치 수를 보고한다.

### Sim Rerun

- `tools replay verify` / `fast-forward`는 sim replay를 재실행해 최종 world hash를 출력한다. 단, 현재 사용하는 재실행 하네스(`fastForwardReplay`)는 20x20 맵과 고정 worker 8기, 이동 계열 시스템만 돌리는 축소 환경이므로 `:sim:run --dumpWorldHash` 결과와 같은 값이 아니다.
- 서버 replay를 `sim`에 재주입하는 경로는 없다 (미구현).

## 레거시 처리

- schema `0` 또는 단순 JSON array 형식은 backward-compat 로더로 처리한다 (hash 없음).
- strict 모드(`--strictHash`)에서는 hash 누락 replay를 거부한다.

## 도구 지원

`tools` 모듈 (`./gradlew :tools:run --args="replay <sub> <file> ..."`):

- `meta [--json]`: sim replay metadata (schema, legacy, events, sizeBytes, replayHash, seed, mapId, buildVersion, winnerFaction, matchEndReason)
- `stats [--json]`: sim replay를 먼저 시도하고 실패하면 server JSONL로 해석해 record 수와 keyframe hash 불일치를 보고
- `verify [--strictHash] [--json]`: sim replay hash 검증 + 재실행 world hash
- `verify-ndjson [--json]`: server JSONL keyframe hash 검증
- `fast-forward [--ticks N] [--json]`: sim replay 재실행

## 운영 원칙

- 서버 authoritative 매치는 서버가 replay를 기록한다.
- 수동 테스트 replay도 seed와 version을 기록한다 (`:sim:run` 저장 경로는 자동 기록, server replay에는 seed 필드 없음).
- golden replay는 CI에서 재검증 가능한 위치에 유지한다.

## 관련 코드

- `sim/src/main/kotlin/replay`
  - `ReplayIO`, `ReplayHashRecorder`
- `server/pkg/authoritative`
  - `replay.go` (writer), `replay_verify.go` (검증)
- `server/cmd/replaycheck`
  - server replay 검증 CLI
- `tools/src/main/kotlin`
  - replay meta/stats/verify/verify-ndjson/fast-forward CLI
- `shared-protocol`
  - server replay가 참조하는 protocol/version 기준
