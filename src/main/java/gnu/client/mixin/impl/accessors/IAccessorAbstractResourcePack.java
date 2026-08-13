package gnu.client.mixin.impl.accessors;

import net.minecraft.client.resources.AbstractResourcePack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.io.File;

@Mixin(AbstractResourcePack.class)
public interface IAccessorAbstractResourcePack {
    @Accessor("resourcePackFile")
    File getResourcePackFile();
}
