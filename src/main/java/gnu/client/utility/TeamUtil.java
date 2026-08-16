package gnu.client.utility;

import gnu.client.runtime.mc.Mc;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.item.EntityArmorStand;

/**
 * OpenMyau {@code TeamUtil} subset used by NoSlow.
 */
public final class TeamUtil {

    private TeamUtil() {}

    public static boolean isShop(EntityLivingBase entity) {
        Minecraft mc = Mc.mc();
        if (entity == null || entity == Mc.player())
            return false;
        if (mc == null || mc.theWorld == null)
            return false;
        EntityArmorStand armorStand = mc.theWorld.findNearestEntityWithinAABB(
                EntityArmorStand.class, entity.getEntityBoundingBox(), (EntityArmorStand) null);
        if (armorStand == null)
            return false;
        String displayName = armorStand.getName();
        if (displayName.contains("RIGHT CLICK")) return true;
        if (displayName.contains("ITEM SHOP")) return true;
        if (displayName.contains("UPGRADES")) return true;
        if (displayName.contains("BANKER")) return true;
        return displayName.contains("STREAK POWERS");
    }
}