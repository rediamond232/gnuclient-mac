package gnu.client.module.modules.player;

import gnu.client.module.Category;
import gnu.client.module.Module;
import gnu.client.module.ModuleManager;
import gnu.client.module.modules.combat.KillAuraModule;
import gnu.client.module.setting.BoolSetting;
import gnu.client.module.setting.ModeSetting;
import gnu.client.module.setting.SliderSetting;
import gnu.client.runtime.mc.Mc;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.item.EnumAction;
import net.minecraft.item.ItemPotion;
import net.minecraft.item.ItemStack;

import java.util.Arrays;
import java.util.List;

/**
 * NoSlow — OpenMyau settings/API with wsamiaw Grim slot-spoof behavior.
 */
public final class NoSlowModule extends Module {

    public static final int MODE_NONE = 0;
    public static final int MODE_VANILLA = 1;
    public static final int MODE_GRIM = 2;

    private static final List<String> MODES = Arrays.asList("NONE", "VANILLA", "GRIM");

    private final ModeSetting swordMode = addSetting(new ModeSetting("sword-mode", MODE_VANILLA, MODES));
    private final SliderSetting swordMotion = addSetting(new SliderSetting("sword-motion", 100f, 0f, 100f, 1f));
    private final BoolSetting swordSprint = addSetting(new BoolSetting("sword-sprint", true));
    private final BoolSetting killAuraOnly = addSetting(new BoolSetting("killaura-only", false));

    private final ModeSetting foodMode = addSetting(new ModeSetting("food-mode", MODE_NONE, MODES));
    private final SliderSetting foodMotion = addSetting(new SliderSetting("food-motion", 100f, 0f, 100f, 1f));
    private final BoolSetting foodSprint = addSetting(new BoolSetting("food-sprint", true));

    private final ModeSetting bowMode = addSetting(new ModeSetting("bow-mode", MODE_NONE, MODES));
    private final SliderSetting bowMotion = addSetting(new SliderSetting("bow-motion", 100f, 0f, 100f, 1f));
    private final BoolSetting bowSprint = addSetting(new BoolSetting("bow-sprint", true));

    private final GrimNoSlowController grimController = new GrimNoSlowController();
    private final GrimFoodNoSlowController foodController = new GrimFoodNoSlowController();

    public NoSlowModule() {
        super("NoSlow", "Cancel item-use slowdown (sword/food/bow)", Category.PLAYER);
        swordMotion.visibleWhen(() -> swordMode.getValue() == MODE_VANILLA);
        swordSprint.visibleWhen(() -> swordMode.getValue() != MODE_NONE);
        killAuraOnly.visibleWhen(() -> swordMode.getValue() != MODE_NONE);
        foodMotion.visibleWhen(() -> foodMode.getValue() == MODE_VANILLA);
        foodSprint.visibleWhen(() -> foodMode.getValue() != MODE_NONE);
        bowMotion.visibleWhen(() -> bowMode.getValue() == MODE_VANILLA);
        bowSprint.visibleWhen(() -> bowMode.getValue() != MODE_NONE);
    }

    public static NoSlowModule instance() {
        Module module = ModuleManager.instance().getModule("NoSlow");
        return module instanceof NoSlowModule ? (NoSlowModule) module : null;
    }

    public static void onGrimPreMovement() {
        NoSlowModule inst = instance();
        if (inst != null && inst.isEnabled())
            inst.grimController.onGrimPreMovement(inst);
    }

    public boolean isSwordActive() {
        if (killAuraOnly.getValue()) {
            Module ka = ModuleManager.instance().getModule("KillAura");
            if (!(ka instanceof KillAuraModule) || !ka.isEnabled())
                return false;
            if (KillAuraModule.getCurrentTarget() == null)
                return false;
        }
        return swordMode.getValue() != MODE_NONE && Mc.isHoldingSword();
    }

    public boolean isFoodActive() {
        return foodMode.getValue() != MODE_NONE && isEating();
    }

    public boolean isBowActive() {
        return bowMode.getValue() != MODE_NONE && Mc.isHoldingBow();
    }

    /** Sword Path A GRIM — independent of food GRIM. */
    public boolean isSwordGrimActive() {
        return isSwordGrimActive(swordMode.getValue(), isSwordActive());
    }

    /** Food-mode setting is GRIM (ignores whether currently holding food). */
    public boolean isFoodGrimMode() {
        return foodMode.getValue() == MODE_GRIM;
    }

    /** Food-mode GRIM selected while holding a consumable (not FSM EATING yet). */
    public boolean isFoodGrimSelected() {
        return isFoodGrimSelected(foodMode.getValue(), isEating());
    }

    static boolean isSwordGrimActive(int swordModeValue, boolean swordActive) {
        return swordModeValue == MODE_GRIM && swordActive;
    }

    static boolean isFoodGrimSelected(int foodModeValue, boolean eating) {
        return foodModeValue == MODE_GRIM && eating;
    }

    /**
     * Any GRIM category selected for current item. Sword Path A uses {@link #isSwordGrimActive()};
     * food Via path uses {@link #isFoodGrimSelected()} + food controller full-speed.
     */
    public boolean isGrimMode() {
        return isSwordGrimActive() || isFoodGrimSelected();
    }

    public boolean isAnyActive() {
        if (isSwordGrimActive())
            return Mc.isUsingItem();
        if (isFoodGrimSelected() && foodFullSpeed())
            return true;
        return Mc.isUsingItem() && (isSwordActive() || isFoodActive() || isBowActive());
    }

    /** Food Via FSM EATING. */
    boolean foodFullSpeed() {
        return foodController.shouldFullSpeed();
    }

    public boolean canSprint() {
        if (isSwordGrimActive() || foodFullSpeed())
            return true;
        return (isSwordActive() && swordSprint.getValue())
            || (isFoodActive() && foodSprint.getValue())
            || (isBowActive() && bowSprint.getValue());
    }

    public int getMotionMultiplier() {
        if (isSwordGrimActive() || foodFullSpeed()) {
            if (Mc.isHoldingSword())
                return Math.round(swordMotion.getValue());
            if (isEating())
                return Math.round(foodMotion.getValue());
        }
        if (Mc.isHoldingSword())
            return Math.round(swordMotion.getValue());
        if (isEating())
            return Math.round(foodMotion.getValue());
        if (Mc.isHoldingBow())
            return Math.round(bowMotion.getValue());
        return 100;
    }

    @Override
    public void onEnable() {
        grimController.onEnable();
        foodController.onEnable();
    }

    @Override
    public void onDisable() {
        grimController.onDisable();
        foodController.onDisable();
    }

    @Override
    public void onTickStart() {
        grimController.onClientTickStart();
        foodController.onClientTickStart();
    }

    @Override
    public void onTick() {
        foodController.onTick(this);
    }
    static int nextGrimSlot(int currentSlot, int swapSlot, boolean toggle, int lastSentSlot) {
        return GrimNoSlowController.nextSlot(currentSlot, swapSlot, toggle, lastSentSlot);
    }

    /**
     * OpenMyau {@code ItemUtil.isEating} — EAT/DRINK use action, splash potions excluded.
     * Does not require {@code isUsingItem} (gated by {@link #isAnyActive}).
     */
    static boolean isEating() {
        EntityPlayerSP player = Mc.player();
        if (player == null)
            return false;
        return isEatingStack(player.getHeldItem());
    }

    /** OpenMyau eating check on a held stack (no using-item gate). */
    public static boolean isEatingStack(ItemStack itemStack) {
        if (itemStack == null)
            return false;
        boolean splash = ItemPotion.isSplash(itemStack.getItem().getMetadata(itemStack));
        return matchesEatingUseAction(itemStack.getItemUseAction(), splash);
    }

    /**
     * Pure OpenMyau eating predicate for unit tests.
     * Splash potions never count; otherwise EAT or DRINK.
     */
    public static boolean matchesEatingUseAction(EnumAction action, boolean splashPotion) {
        if (splashPotion)
            return false;
        return action == EnumAction.EAT || action == EnumAction.DRINK;
    }

    @Override
    public String[] getSuffix() {
        if (swordMode.getValue() != MODE_NONE)
            return new String[] { swordMode.getCurrentMode() };
        if (foodMode.getValue() != MODE_NONE)
            return new String[] { foodMode.getCurrentMode() };
        if (bowMode.getValue() != MODE_NONE)
            return new String[] { bowMode.getCurrentMode() };
        return new String[0];
    }
}
