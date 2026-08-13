package gnu.client.mixin.impl.accessors;

import net.minecraft.client.resources.FallbackResourceManager;
import net.minecraft.client.resources.SimpleReloadableResourceManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.Map;

@Mixin(SimpleReloadableResourceManager.class)
public interface IAccessorSimpleReloadableResourceManager {
    @Accessor("domainResourceManagers")
    Map<String, FallbackResourceManager> getDomainResourceManagers();
}
