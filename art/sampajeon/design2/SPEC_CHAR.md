# 삼재 — character shading & anatomy pass (addendum to SPEC.md + SPEC_HQ.md)

The user's feedback on version 4 was that the shading (명암) and the arm and leg detail (팔다리 디테일) are far too weak. They want the art to reference 천년의 신화, 거상 and 임진록2 directly. Emulate their RENDERING TECHNIQUE only, which is the pre-rendered 3D sprite look of late-90s Korean 2.5D games. Never copy their characters, costumes-as-designed, UI, logos or text. Our units stay as defined in SPEC.md.

## What the references do, and what we must match
1. **Every form is a shaded volume.**
   - Each limb, torso, head, hat and weapon shaft is a cylinder or sphere, and the tones run in this order: lit edge, specular, light, midtone, core shadow, reflected bounce light at the shadow edge.
   - The core shadow is deep, around 60% darker than the base, never just a slightly darker fill.
   - The light is a strong key light from the top-left.
2. **Real anatomy.**
   - Bodies are 6–7 heads tall for humans. Dokkaebi are stockier but still muscular and articulated.
   - Draw the upper arm, the elbow, a tapering forearm, the wrist and a hand with knuckles, fingers and a thumb.
   - Draw the thigh, the knee, the calf bulge, the ankle and a foot or boot with a sole.
   - Show the deltoid, biceps and calf as bulges on the silhouette.
   - The arms hold the weapon: fingers wrap the shaft, and the two hands sit at believable places on it.
3. **Cloth and armour rendered as material.**
   - Sleeves and baggy 바지 have folds that wrap the limb, each fold a dark crease with a light ridge.
   - Leg wraps (행전) have spiral bands; belts and sashes have knots and hanging ends.
   - Lamellar or studded armour has each plate or stud catching its own specular light.
   - Metal is high-contrast: bright specular next to near-black reflections.
   - Leather is semi-glossy.
4. **Ambient occlusion.**
   - Darken every junction: armpit, under the chin, under the hat brim, between the legs, under the belt, and where an arm overlaps the torso.
   - Add a contact shadow at the feet plus a soft cast shadow toward the lower right.
5. **Depth.** Far-side limbs are darker and less saturated (`Mat.back()`), and the near-side limbs overlap the torso.
6. **Size.**
   - On race sheet cards the main figure should fill the card art, about 170–200px tall for 1×1 units.
   - Hero-scale detail must survive at 1× zoom, so check every figure at 2× too.

## The tool: gen2/figure_kit.py
- Use it for all organic figures: `Kit(prefix)`, `MATS`, `Mat`, `form`, `ball`, `arm`, `leg`, `hand`, `foot`, `torso`, `neck`, `head`, `folds`, `bands`, `clip_overlay`, `stud`, `ao`, `cast_shadow`.
- See `gen2/kit_demo.py` for a worked example of a human soldier and a dokkaebi brute.
- You may extend the kit in a copy or a subclass inside your own generator, for example with hats, lamellar plates or a better face. **Do not edit figure_kit.py itself**, because other agents use it at the same time. Put your extensions in your own module.
- Kit prefixes must be unique in the document:
  - R1: `r1k`; R2: `r2k`; R3: `r3k`
  - Match: `s3k`; Main: `s1k`; Lobby: `s2k`; Results: `s4k`
  - Effects: `ek` (if needed)
- `K.defs_svg()` must be emitted in a `<defs>` once per Kit after all drawing calls. Gradients are generated per shape, so call it last.
- Vehicles and machines (화차, 거북 전차, 비거, 돛배, 연) do not need the kit's anatomy, but they still need the same lighting: cylinder and sphere gradients on wheels, barrels and hull planks, deep core shadows, specular on metal, and AO in joins. Any crew or rider on them is drawn with the kit.

## Process
- Rewrite your artboard(s) in place: same file names in design2/project, same id prefixes, and generators updated in gen2/ (keep a `_v2` backup of the current generator first).
- Run the checks:
  - no duplicate ids;
  - tags balanced;
  - every `url(#…)` resolves;
  - a headless Chrome screenshot at 1× AND a 2× crop of the figures, which you view and iterate on until limbs and shading read clearly.
- Save final screenshots to shots/char/<Name>.png.
- Do not publish, and do not touch canvas.json or other agents' artboards.
