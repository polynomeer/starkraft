# 삼파전 시안 원본

[디자인 시안](https://claude.ai/artifact/53Te38p8czaacWt5EKnbBu)을 만드는 생성기와 규격이다. 적용 순서는 [작업계획서](../../docs/sampajeon-application-plan.md)를 본다.

| 경로 | 내용 |
| --- | --- |
| `design2/SPEC*.md` | 시안 규격. `SPEC.md`부터 순서대로 누적되며, 종족 정의는 `SPEC_TJI.md`가 최신이다 |
| `gen2/` | 아트보드 생성기(Python 표준 라이브러리만 사용). `gen2/README_build.md`에 크기 제한 절차가 있다 |
| `export_sprites.py` | 게임용 PNG 추출기 |

아트보드 생성:

```bash
cd art/sampajeon/gen2 && python3 race_r3.py
```

결과는 `design2/project/`에 생기며 저장소에 넣지 않는다. 일부 생성기(`race_r1.py`, `race_r2.py`)는 import만 해도 이 폴더에 파일을 쓴다.

게임용 스프라이트 추출(헤드리스 Chrome 필요):

```bash
python3 art/sampajeon/export_sprites.py
```

결과는 `sim/src/main/resources/assets/sprites/sampajeon/`에 생기며, 게임에서 쓰는 파일이므로 저장소에 넣는다.
