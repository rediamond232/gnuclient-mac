
### ROLLOUT-20260809-190311-WF-001

- Rollout action: workflow_synthesis
- Change summary: Grim NoSlow Path A for sword + consumables. Root cause of old GRIM mode: C09 in `onTickStart` ran before use packets, so Grim Path A never cleared `isSlowedByUsingItem`; KA autoblock also suppressed spoof. New `GrimNoSlowController` sends C09 at start of `beforeWalkingPlayer` (after KA preUpdate, before C03), skips PacketOrderE conflict ticks (C02/C07/sprint-sneak C0B), gates KA phase skips on pre-tick `attackAllowed`, and owns PacketEvents observation independent of KA.
- Workflow task type: feature
- Sequence: researcher → architect → coder → reviewer → tester (with layer-3 checkers)
- Subtasks:
  - researcher: Grim NoSlow/Path A/PacketOrderE facts; mixin-only fails; KA double-failure (revised after checker)
  - architect: Path A controller + pre-tick KA phase + controller-owned C02/C07/C0B (revised after checker)
  - coder: GrimNoSlowController + hook move + KA pre-tick capture; revision for stale phase-2 skip; compileJava OK
  - reviewer: request changes on stale preGrimPhase (approved after checker withdrew Scaffold false positive)
  - tester: GrimNoSlowControllerTest (14) + NoSlowModeTest; verify exit 0
- Verification evidence:
  - `./gradlew test --tests gnu.client.module.modules.player.NoSlowModeTest --tests gnu.client.module.modules.player.GrimNoSlowControllerTest --rerun-tasks` → BUILD SUCCESSFUL (exit 0)
- Ralph: ralph-goal.sh missing; goal in harness/memory/ralph-goal.md; verify exit 0 → mark complete
- Residual: Manual Grim 1.8.9 smoke (food/sword/KA GRIM + KeepSprint C0B same-tick) not run in this workflow; set sword-mode/food-mode to GRIM explicitly (defaults are VANILLA/NONE)

### ROLLOUT-20260809-183249-WF-001

- Rollout action: workflow_synthesis
- Change summary: KeepSprint dual-mode (StopWalk + Grim). Grim mode never stops sprint; accepts vanilla ×0.6 to match Grim AttackSlow / stay Simulation-clean. StopWalk (default) preserves existing walk-C03 stop pulse. Spec from Grim analysis under ~/Grim (`PacketPlayerAttack.lastSprinting`).
- Workflow task type: feature
- Sequence: researcher → architect → coder → reviewer → tester (with layer-3 checkers)
- Subtasks:
  - researcher: Grim AttackSlow / lastSprinting facts; no flagless no-slow continuous path (revised after checker)
  - architect: ModeSetting StopWalk/Grim hook matrix + eager clearStopWalkState (revised after checker)
  - coder: implemented KeepSprintModule/Logic + tests; BUILD SUCCESSFUL
  - reviewer: approve with nits; yield windows may emit STOP (accepted residual risk)
  - tester: extended unit tests; all KeepSprint* tests pass
- Verification evidence:
  - `./gradlew test --tests gnu.client.module.modules.combat.KeepSprintLogicTest --tests gnu.client.module.modules.combat.KeepSprintModuleTest --tests gnu.client.module.modules.combat.KeepSprintStopStateTest` → BUILD SUCCESSFUL (exit 0)
- Ralph: harness/scripts/ralph-goal.sh missing; goal tracked in harness/memory/ralph-goal.md; verify exit 0 → mark complete
- Residual: Grim+WTap/hurtTime yield skips restore → walking sync may STOP; in-game Simulation not run

