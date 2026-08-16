package gnu.client.mixin.impl.entity;

import gnu.client.module.modules.player.NoSlowModule;
import net.minecraft.client.entity.EntityPlayerSP;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(EntityPlayerSP.class)
public abstract class MixinEntityPlayerSPNoSlow {

    @Redirect(
            method = "onLivingUpdate",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/entity/EntityPlayerSP;isUsingItem()Z"
            )
    )
    private boolean gnuNoSlowIsUsing(EntityPlayerSP self) {
        NoSlowModule noSlow = NoSlowModule.instance();
        if (noSlow != null && noSlow.isEnabled()) {
            if (noSlow.isMiauAnyActive()) {
                return !noSlow.shouldCancelMiauSlowdown();
            }
            if (noSlow.isAnyActive()) {
                return false;
            }
        }
        return self.isUsingItem();
    }
}