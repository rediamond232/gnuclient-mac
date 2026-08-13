package gnu.client.mixin.impl.accessors;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.multiplayer.PlayerControllerMP;
import net.minecraft.client.multiplayer.WorldClient;
import net.minecraft.client.renderer.EntityRenderer;
import net.minecraft.client.renderer.RenderGlobal;
import net.minecraft.entity.Entity;
import net.minecraft.util.MovingObjectPosition;
import net.minecraft.util.Session;
import net.minecraft.util.Timer;
import net.minecraft.util.MouseHelper;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(Minecraft.class)
public interface IAccessorMinecraft {
    @Accessor("timer")
    Timer getTimer();

    @Accessor("rightClickDelayTimer")
    int getRightClickDelayTimer();

    @Accessor("rightClickDelayTimer")
    void setRightClickDelayTimer(int delay);

    @Accessor("leftClickCounter")
    int getLeftClickCounter();

    @Accessor("leftClickCounter")
    void setLeftClickCounter(int counter);

    @Accessor("objectMouseOver")
    MovingObjectPosition getObjectMouseOver();

    @Accessor("objectMouseOver")
    void setObjectMouseOver(MovingObjectPosition mop);

    @Accessor("pointedEntity")
    Entity getPointedEntity();

    @Accessor("pointedEntity")
    void setPointedEntity(Entity entity);

    @Accessor("mouseHelper")
    MouseHelper getMouseHelper();

    @Accessor("renderGlobal")
    RenderGlobal getRenderGlobal();

    @Accessor("entityRenderer")
    EntityRenderer getEntityRenderer();

    @Accessor("thePlayer")
    net.minecraft.client.entity.EntityPlayerSP getThePlayer();

    @Accessor("theWorld")
    WorldClient getTheWorld();

    @Accessor("currentScreen")
    GuiScreen getCurrentScreen();

    @Accessor("playerController")
    PlayerControllerMP getPlayerController();

    @Accessor("session")
    Session getSessionField();

    @Accessor("session")
    void setSession(Session session);
}
