package gnu.client.mixin.impl.accessors;

import net.minecraft.client.resources.FallbackResourceManager;
import net.minecraft.client.resources.IResourcePack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.List;

@Mixin(FallbackResourceManager.class)
public interface IAccessorFallbackResourceManager {
    @Accessor("resourcePacks")
    List<IResourcePack> getResourcePacks();
}
