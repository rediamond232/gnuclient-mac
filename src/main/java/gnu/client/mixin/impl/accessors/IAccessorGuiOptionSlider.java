package gnu.client.mixin.impl.accessors;

import net.minecraft.client.gui.GuiOptionSlider;
import net.minecraft.client.settings.GameSettings;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(GuiOptionSlider.class)
public interface IAccessorGuiOptionSlider {

    @Accessor("sliderValue")
    float getSliderValue();

    @Accessor("sliderValue")
    void setSliderValue(float value);

    @Accessor("options")
    GameSettings.Options getOptions();
}
