# 삼재 — high-fidelity pass (addendum to SPEC.md)

The user says the current art is too simple. They want the graphics and design quality of classic Korean 2.5D games such as *임진록2 조선의 반격* (RTS) and *거상* (online RPG). Use those games only as a QUALITY and GENRE benchmark: 2.5D isometric, painterly "pre-rendered" sprites, and ornate traditional UI. Do NOT copy any of their specific characters, buildings, UI layouts, logos, icons or text. Everything stays our own 삼재 content from SPEC.md.

## What "higher quality" means here
1. **Projection.** Switch the battlefield, terrain, units and buildings to **2.5D isometric (2:1 dimetric)**.
   - The ground is made of diamond tiles (64×32 base, scaled up for display).
   - Buildings show the roof plus TWO walls (south-west face lit, south-east face in shade).
   - Units are seen from about 45° above.
   - Do not use flat top-down icons.
2. **Painterly pre-rendered look.** Every surface is painted with many layers:
   - a base gradient, then a material texture (feTurbulence + feColorMatrix / feDisplacementMap, or pattern);
   - ambient occlusion in creases, highlights on edges, and a specular accent;
   - a 1px dark outline (#120d08 at about 60% opacity) to separate sprites from the ground;
   - a warm key light from the top-left and a cool bounce in the shadows.
3. **Material detail** (draw individual elements, not single shapes):
   - **Giwa roofs:** individual tile rows (암키와/수키와 ridges), the ridge beam, end tiles, upturned eave corners (처마 곡선), dancheong bands under the eaves.
   - **Walls:** timber posts and beams, hanji lattice windows (문살), plastered panels, stone foundations with individual stones.
   - **Wood:** grain lines and plank seams. **Brass:** rivets and studs. **Cloth:** folds and stitching. **Rope:** twists.
4. **Characters:**
   - Proportions are about 5–6 heads tall. They are small people, not icons or chibi.
   - Each has a readable costume: hats (전립, 벙거지, 패랭이), robes or armour (철릭, studded brigandine), belts, sashes, footwear.
   - Each holds its weapon in a readable pose, has a face with a few strokes of detail, and casts a ground shadow ellipse.
   - Draw each unit large on the sheets (about 160–200px tall in the card) so the detail shows.
5. **Ground:** dense, textured and natural.
   - Blend transitions between grass, dirt and stone.
   - Scatter clumps of grass, wildflowers, pebbles, fallen leaves, puddles and wheel ruts.
   - Use a late-autumn palette: ochre grass, red maples, dark green pines.
   - Add cast shadows from every object.
6. **UI chrome:** ornate traditional frames (our own design).
   - Carved dark lacquered wood frames with brass corner fittings and rivets.
   - Inset parchment or hanji panels with paper grain.
   - Gold titles with a dark stroke and drop shadow.
   - Cloud-pattern (구름문) and lattice ornaments; embossed, beveled buttons; painted portraits in framed medallions.
   - Do not use any swastika (卍) motif.
   - It must stay legible, so keep text contrast at 4.5:1 or better.
7. **Density target.**
   - Main units: about 150–300 SVG elements each.
   - Main buildings: about 300+ elements each.
   - Battlefield and terrain scenes: thousands of elements are fine. Generate repetition (roof tiles, stones, grass) with loops in the Python generator, using a seeded RNG so output is deterministic.
   - File size up to about 3 MB per artboard is acceptable.

## Process
- Rewrite your artboard(s) in place: same file names in design2/project, same id prefixes, and update your generator in gen2/.
- Keep the SPEC.md file skeleton, fonts, palette and faction content.
- Checks:
  - no duplicate ids;
  - tags balanced;
  - every url(#) resolves;
  - a headless Chrome screenshot (command in SPEC tasks), which you view and fix. Also zoom in, for example with --force-device-scale-factor=2 or by cropping, to judge detail.
- Do not publish, and do not touch canvas.json or other artboards.
