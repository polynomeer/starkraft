# 삼파전 — natural limbs + individual faces pass (addendum to SPEC_CHAR.md / SPEC_HERO.md)

User feedback on the previous pass: "얼굴이 다 똑같고 팔다리도 관절이 너무 끊어지는데 더 자연스럽게" — every face looks the same, and arms and legs look broken apart at the joints.

## What changed in gen2/figure_kit.py (already done, do NOT edit the file)
- `K.arm(...)` and `K.leg(...)` keep the same signatures but now draw ONE continuous tube through shoulder→elbow→wrist and hip→knee→ankle (`K.tube(pts, widths, mat, bumps, crease_at=(1,))`).
  - Shading uses stacked strokes offset toward the light, so elbows and knees bend smoothly, with a soft crease on the inside of the bend.
  - The old segmented versions survive as `arm_v1` / `leg_v1`. Do not use them.
- `K.tube(pts, widths, mat, bumps=[(t, amt, side)], crease_at=())` works for ANY organic tube through any number of points: tails, necks, fingers, sleeves, ropes, fox tails, robes' hanging sleeves.
- `K.face(cx, cy, r, mat, look)` → `(svg, d)` draws an individually designed face.
  - Its parameters are documented in its docstring: shape, jaw, cheek, turn, eye, eye_size, lid, gaze, brow, nose, nose_len, mouth, lip, age, fem, beard, beard_col, scar, blush.
  - New skin materials: `skin_fair`, `skin_tan`, `skin_weathered`, `skin_ruddy` (plus `skin`, `skin_dark`).
  - Demo: `gen2/face_demo.py` → shots/face_demo.png.

## Rules for this pass
1. **Remove every segmented limb.**
   - Anywhere a limb is built from per-segment `form()` capsules plus joint `ball()`s, replace it with `K.arm` / `K.leg` / `K.tube` through the joints. This applies to your own subclasses too, for example R2Kit's underlay override (now redundant, so delete it) and hand-rolled hero limbs.
   - Joints must read as continuous flesh or cloth, never as plates.
   - Armour pieces (pauldrons, greaves, lamellar) may sit ON TOP of a continuous limb.
2. **Every character gets its own face.**
   - Use `K.face` (or extend it in your module) with a deliberately different `look` per character: face shape, eyes, brows, nose, mouth/expression, age, facial hair and skin tone.
   - No two heroes may share a look, and generic unit types also differ from each other.
   - Repeated units (crowds, squads, battlefield) vary deterministically: pick look variations from a seeded RNG per instance.
   - Hats, hair and helmets go on top as before, and the hat brim casts AO on the forehead.
3. **Research-based hero faces.**
   - We do not trace or copy portraits. The modern 표준영정 are copyrighted, and external images are not allowed in the artifact.
   - Instead, base each face on recorded facts (age at the depicted event, descriptions in sources) and design it ourselves:
     - **이순신:** 유성룡 『징비록』 says he spoke and laughed little, and that his face was refined and composed, like a scholar disciplining himself. Give him a long, refined face, a calm and composed expression (NOT shouting), sharp but quiet eyes, a thin long beard and mustache, and about 50 years of age.
     - **장영실:** no physical record. He is a low-born genius artisan in his 40s, with intelligent, curious eyes, a slight focused smile, a goatee and soot or goggle marks.
     - **최무선:** born 1325, so in his 50s–60s at 진포 (1380). He is weathered, with a grey full beard, a burn scar, a broad nose and a battle shout.
     - **김유신:** 52 at 비담의 난 (647). He is a mature general with a square, ruddy face, fierce eyes, a mustache and short beard, and a commanding shout.
     - **김정호:** commoner geographer, a lean, weathered traveller in his 50s. He has gaunt cheekbones, droopy kind eyes and a sparse long grey-black beard.
     - **김만덕:** born 1739, so about 56 during the 1795 relief. She is a dignified, warm, mature woman: an oval face with gentle smile lines, kind almond eyes and neat hair.
     - **비형랑:** a youthful, handsome half-spirit with pale cool skin, sharp slanted eyes and a faint mysterious smile.
     - **두두리:** an ancient goblin smith with a huge jaw, tusks, a wide grin, bushy brows, soot and an old face.
     - **길달:** a narrow, fox-like face with a pointed chin, a sly smirk and narrow sharp eyes.
4. Hero full-body figures should come out at least as tall as before. Keep the panel layout.
5. **Process:**
   - Back up each generator you change as `*_v3.py` first.
   - Regenerate, then check: no duplicate ids, every `url(#)`/`href` resolves, tags balanced.
   - Take 1× screenshots plus 2× crops of faces and limbs, VIEW them, and iterate until joints are seamless and faces are clearly different.
   - Save the final shots to shots/v3/<Name>.png.
   - Do not publish, and do not edit canvas.json or figure_kit.py.
