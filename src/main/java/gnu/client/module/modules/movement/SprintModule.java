package gnu.client.module.modules.movement;

import gnu.client.module.Category;
import gnu.client.module.Module;
import gnu.client.module.modules.combat.KeepSprintModule;
import gnu.client.module.modules.combat.WTapModule;
import gnu.client.runtime.mc.Mc;
import net.minecraft.client.entity.EntityPlayerSP;

/**
 * Auto-sprint via sprint keybind (OpenMyau {@code Sprint}).
 *
 * <p>Holds the sprint key every tick START so sprint persists through jumps.
 * Yields to {@link WTapModule} and {@link KeepSprintModule} while they own sprint.
 */
public final class SprintModule extends Module {

    public SprintModule() {
        super("Sprint", "Auto-sprint via keybind (packet-safe)", Category.PLAYER);
    }

    @Override
    public void onEnable() {}

    @Override
    public void onDisable() {
        Mc.setSprintKeyState(false);
    }

    @Override
    public void onTickStart() {
        if (!isEnabled())
            return;
        EntityPlayerSP player = Mc.player();
        if (player == null)
            return;
        if (WTapModule.shouldSuppressSprintKey() || KeepSprintModule.shouldSuppressSprintKey()) {
            Mc.setSprintKeyState(false);
            return;
        }
        Mc.setSprintKeyState(true);
    }
}
