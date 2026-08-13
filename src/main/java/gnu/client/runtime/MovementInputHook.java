package gnu.client.runtime;

import gnu.client.module.modules.combat.KeepSprintModule;
import gnu.client.module.modules.combat.KillAuraModule;
import gnu.client.module.modules.combat.MoreKBModule;
import gnu.client.module.modules.combat.VelocityModule;
import gnu.client.module.modules.combat.WTapModule;
import gnu.client.module.modules.movement.StasisModule;
import gnu.client.module.modules.player.scaffold.ScaffoldModule;
import gnu.client.script.ScriptManager;

/**
 * Called at {@code MovementInputFromOptions.updatePlayerMoveState} RETURN via
 * {@link gnu.client.mixin.impl.client.MixinMovementInputFromOptions}.
 */
public final class MovementInputHook {

    private MovementInputHook() {}

    public static void afterUpdatePlayerMoveState(Object movementInput) {
        VelocityModule.patchMovementInput(movementInput);
        WTapModule.patchMovementInput(movementInput);
        MoreKBModule.patchMovementInput(movementInput);
        KeepSprintModule.patchMovementInput(movementInput);
        KillAuraModule.patchMovementInput(movementInput);
        ScriptManager.instance().patchMovementInput(movementInput);
        StasisModule.patchPlayerInput(movementInput);
        ScaffoldModule.patchMovementInput(movementInput);
    }
}
