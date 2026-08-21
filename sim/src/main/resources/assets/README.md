# Visual Assets

Add optional art assets under this resources tree and reference them from
`/data/visuals.json`.

Recommended layout:

- `assets/sprites/units/*.png`
- `assets/sprites/buildings/*.png`
- `assets/sprites/resources/*.png`
- `assets/sprites/terrain/*.png`

Example `visuals.json` entries:

```json
{
  "units": {
    "Marine": {
      "asset": "assets/sprites/units/marine.png",
      "scale": 1.15
    }
  },
  "unitArchetypes": {
    "worker": {
      "asset": "assets/sprites/units/worker.png",
      "scale": 1.0,
      "teamTintStrength": 0.15
    }
  },
  "buildings": {
    "Depot": {
      "asset": "assets/sprites/buildings/depot.png"
    }
  },
  "resources": {
    "gas": {
      "asset": "assets/sprites/resources/gas.png"
    },
    "minerals": {
      "asset": "assets/sprites/resources/minerals.png"
    }
  },
  "terrain": {
    "base": {
      "asset": "assets/sprites/terrain/base-tile.png"
    },
    "blocked": {
      "asset": "assets/sprites/terrain/blocked-tile.png"
    },
    "occupied": {
      "asset": "assets/sprites/terrain/occupied-tile.png"
    }
  }
}
```

When an asset path is missing or the file does not exist, the graphical client
falls back to the existing procedural shape rendering.

This repository now includes sample sprites at:

- `assets/sprites/units/worker.png`
- `assets/sprites/units/marine.png`
- `assets/sprites/units/zergling.png`
- `assets/sprites/buildings/depot.png`
- `assets/sprites/resources/minerals.png`
- `assets/sprites/resources/gas.png`
- `assets/sprites/terrain/base-tile.png`
- `assets/sprites/terrain/blocked-tile.png`
- `assets/sprites/terrain/occupied-tile.png`
