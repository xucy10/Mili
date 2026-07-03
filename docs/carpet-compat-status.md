### Carpet / Compatibility Overview

This document summarizes which Carpet rules and TIS/AMS additions Mili currently implements or maps to equivalent behavior. Use this as a quick compatibility reference.

Status key:
- Mapped: behavior implemented or forwarded to an equivalent Mili config/feature
- Removed: behavior intentionally removed or disabled
- Equivalent: structural or semantic equivalent implemented

Carpet core rules (selected)
- `language` — Mapped (forwards to `Mili.function.language.lang`)
- `commandTick` — Mapped (forwards to existing tick command patches)
- `creativeNoClip` — Mapped (creative flight/no-clip behavior)
- `placementRotationFix` — Removed (placement rotation uses player body orientation)
- `explosionNoBlockDamage` — Removed (explosions still damage entities but not blocks)
- `xpNoCooldown` — Removed (experience orbs can be collected in the same tick)

Carpet TIS additions (selected)
- `yeetUpdateSuppressionCrash` — Mapped (forwards to Mili crash fix)
- `instantBlockUpdaterReintroduced` — Mapped (`Mili.experiment.redstone.instant-block-updater`)
- `optimizedDragonRespawn` — Mapped (Luminol dragon respawn optimization)
- `totallyNoBlockUpdate` — Removed (neighbor and shape updates short-circuited centrally)
- `tntDupingFix` — Removed (piston TNT duplication path controlled via compatibility rules)

Carpet Org / AMS additions (selected)
- `hopperNoItemCost` — Removed (hopper wool trick supported)
- `creativeOneHitKill` — Removed (creative one-hit kill behavior)
- `fakePlayerAutoReplenishmentFormShulkerBox` — Mapped (fake player replenishment supports shulker box)

Mili extensions
- `commandBot` — Mapped (`/bot` command enabled)
- `fakePlayerResident` — Mapped (persistent fake-player mode)
- `openFakePlayerInventory` — Mapped (open fake-player inventory)

Notes
- This file provides a high-level overview; for exact behavior and config keys, search the codebase for `Mili.experiment` and `ConfigsInstance` entries or open an issue for clarification.


