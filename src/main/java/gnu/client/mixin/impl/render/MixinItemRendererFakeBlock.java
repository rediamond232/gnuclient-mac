package gnu.client.mixin.impl.render;

import gnu.client.module.modules.combat.KillAuraModule;
import net.minecraft.client.entity.AbstractClientPlayer;
import net.minecraft.client.renderer.ItemRenderer;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@SideOnly(Side.CLIENT)
@Mixin(ItemRenderer.class)
public abstract class MixinItemRendererFakeBlock {

    /**
     * When KillAura reports fake-blocking, treat use-count as active so the
     * sword BLOCK first-person branch runs. Scoped to this method only.
     * <p>
     * Must target {@link AbstractClientPlayer} — that is the invoke owner in
     * 1.8.9 {@code renderItemInFirstPerson}, not {@code EntityPlayer}.
     */
    @Redirect(
            method = "renderItemInFirstPerson",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/entity/AbstractClientPlayer;getItemInUseCount()I"))
    private int gnu$fakeBlockItemInUseCount(AbstractClientPlayer player) {
        int real = player.getItemInUseCount();
        if (real > 0) {
            return real;
        }
        if (KillAuraModule.isFakeBlocking()) {
            return 1;
        }
        return 0;
    }
}
