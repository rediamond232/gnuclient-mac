package gnu.client.mixin.impl.accessors;

import net.minecraft.client.settings.KeyBinding;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(KeyBinding.class)
public interface IAccessorKeyBinding {
    @Accessor("pressed")
    void setPressed(boolean pressed);

    @Accessor("pressed")
    boolean getPressed();
}