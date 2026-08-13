# Hypixel Velocity — 5-tick delay + wall absorb — Design

**Date:** 2026-08-12  
**Status:** Implemented  
**Repo:** gnuclient-recode  
**Scope:** `HypixelVelocity` only

## Goal

On self `S12`, hold that packet + `S00`/`S32` for **5 ticks**, then flush.

## Extra reduce

**Wall absorb** — after flush, for up to 10 PRE ticks while `hurtTime > 0` and the player BB (expanded ~0.35) hits a solid, scale `motionX`/`motionZ` by **0.35**.

No jump-reset. No sprint / AttackSlow re-arm.

Do **not** gate delay on `hurtTime` — velocity often arrives before hurt is applied on the client.

Every self `S12` is delayed **except** during the **3-tick** grace after `S08` (setback velocity).
