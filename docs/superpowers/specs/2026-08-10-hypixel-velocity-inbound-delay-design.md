# Hypixel Velocity — 5-tick delay + AttackSlow re-arm — Design

**Date:** 2026-08-11  
**Status:** Implemented  
**Repo:** gnuclient-recode  
**Scope:** `HypixelVelocity` only

## Goal

On self `S12`, hold that packet + `S00`/`S32` for **5 ticks**, then flush.

## Extra reduce

**Hurt-time AttackSlow re-arm** — after attack while `hurtTime > 0`, next PRE sets sprint **key only**.

No jump-reset.

Do **not** gate on `hurtTime` — velocity often arrives before hurt is applied on the client, which made the delay look intermittent.

Every self `S12` is delayed **except** during the **3-tick** grace after `S08` (setback velocity).
