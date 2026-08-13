package gnu.client.module.setting;

import com.google.gson.JsonElement;

import java.util.function.BooleanSupplier;

public abstract class Setting<T> {

    private final String name;
    private T value;
    /** Base flag — {@link #setVisible(boolean)} for always-hidden settings. */
    private boolean visible = true;
    /** Optional live predicate (mode/bool gates). Evaluated every GUI frame. */
    private BooleanSupplier visibilityCondition;
    /** Optional live predicate — when true the setting is shown but greyed/locked. */
    private BooleanSupplier disabledCondition;
    /** Fired after {@link #setValue} when the stored value actually changes. */
    private Runnable changeListener;

    protected Setting(String name, T value) {
        this.name = name;
        this.value = value;
    }

    public String getName() {
        return name;
    }

    public T getValue() {
        return value;
    }

    public void setValue(T value) {
        T previous = this.value;
        this.value = value;
        if (changeListener == null) {
            return;
        }
        if (previous == null ? value != null : !previous.equals(value)) {
            changeListener.run();
        }
    }

    /**
     * Invoke {@code listener} after a real value change (GUI, config load, code).
     *
     * @return {@code this} for fluent use with {@code addSetting}
     */
    @SuppressWarnings("unchecked")
    public final <S extends Setting<?>> S onChanged(Runnable listener) {
        this.changeListener = listener;
        return (S) this;
    }

    /**
     * Whether this setting should be shown in the GUI.
     * Hidden when {@link #setVisible(boolean) setVisible(false)} or when a
     * {@link #visibleWhen(BooleanSupplier)} predicate returns false.
     */
    public boolean isVisible() {
        if (!visible)
            return false;
        return visibilityCondition == null || visibilityCondition.getAsBoolean();
    }

    /**
     * Force show/hide regardless of predicate. Prefer {@link #visibleWhen} for
     * mode-tied settings so future modules stay declarative.
     */
    public void setVisible(boolean visible) {
        this.visible = visible;
    }

    /**
     * Show this setting only while {@code condition} is true (OpenMyau-style).
     * Call from field initializers after the parent setting exists, e.g.
     * {@code addSetting(new SliderSetting(...).visibleWhen(() -> mode.getValue() == 1))}.
     *
     * @return {@code this} for fluent use with {@code addSetting}
     */
    @SuppressWarnings("unchecked")
    public final <S extends Setting<?>> S visibleWhen(BooleanSupplier condition) {
        this.visibilityCondition = condition;
        return (S) this;
    }

    /**
     * Grey out and lock this setting while {@code condition} is true. Used for toggles
     * that conflict with an external mod (e.g. OptiFine Fast Render owning the render
     * path). The setting stays visible but cannot be toggled until the condition clears.
     *
     * @return {@code this} for fluent use with {@code addSetting}
     */
    @SuppressWarnings("unchecked")
    public final <S extends Setting<?>> S disabledWhen(BooleanSupplier condition) {
        this.disabledCondition = condition;
        return (S) this;
    }

    /** True when a {@link #disabledWhen} predicate is currently active. */
    public boolean isDisabled() {
        return disabledCondition != null && disabledCondition.getAsBoolean();
    }

    public abstract JsonElement serialize();

    public abstract void deserialize(JsonElement element);
}
