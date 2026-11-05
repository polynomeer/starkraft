# P0 시험 구현

C# 코어와 Godot 4(.NET)가 이 게임에 맞는지 확인하는 임시 코드입니다. 기존 Kotlin 코드와 연결되지 않으며, 결론이 나면 정식 구조로 옮기거나 삭제합니다. 판단 기준과 결과는 [개선안](../docs/architecture-renewal-proposal.md)에 있습니다.

| 경로 | 내용 |
| --- | --- |
| `Samjae.Core/` | 고정소수점 규칙 코어. 엔진, 입출력, 부동소수점 금지 |
| `Samjae.Check/` | 외부 의존성 없는 테스트, 해시, 코어 벤치마크 실행기 |
| `godot/` | 아이소메트릭 맵, 유닛, 건물, HUD 기준 장면 |

필요한 도구는 .NET SDK 10과 Godot 4.7 .NET판입니다.

```bash
./check.sh
```

```bash
dotnet run -c Release --project Samjae.Check -- hash
```

```bash
dotnet build godot/Samjae.Client.csproj -c Debug && godot-mono --path godot
```

게임 조작은 좌클릭 또는 드래그로 선택, 우클릭으로 이동, 방향키로 화면 이동, 휠로 확대·축소입니다.

Godot은 Debug 빌드 결과물을 읽습니다. 코드를 고친 뒤 Debug로 다시 빌드하지 않으면 이전 코드가 실행됩니다.

```bash
godot-mono --headless --path godot -- --pick-test
```

```bash
godot-mono --path godot --resolution 1440x900 -- --bench 30 --select 6 --out result.json --shot result.png
```

벤치마크 옵션은 `--units N`, `--bench-cam crowd|map`입니다. 렌더러 비교는 `--rendering-method forward_plus`를 `--` 앞에 붙입니다.
