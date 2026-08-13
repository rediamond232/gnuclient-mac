package gnu.client.mixin.impl.accessors;

import net.minecraft.client.entity.EntityPlayerSP;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(EntityPlayerSP.class)
public interface IAccessorEntityPlayerSP {
    @Accessor("lastReportedYaw")
    float getLastReportedYaw();

    @Accessor("lastReportedYaw")
    void setLastReportedYaw(float yaw);

    @Accessor("lastReportedPitch")
    float getLastReportedPitch();

    @Accessor("lastReportedPitch")
    void setLastReportedPitch(float pitch);

    @Accessor("serverSprintState")
    boolean getServerSprintState();

    @Accessor("serverSprintState")
    void setServerSprintState(boolean sprinting);

    @Accessor("serverSneakState")
    boolean getServerSneakState();

    @Accessor("serverSneakState")
    void setServerSneakState(boolean sneaking);

    @Accessor("sprintToggleTimer")
    int getSprintToggleTimer();

    @Accessor("sprintToggleTimer")
    void setSprintToggleTimer(int ticks);
}
