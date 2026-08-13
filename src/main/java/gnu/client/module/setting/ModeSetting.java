package gnu.client.module.setting;

import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;

import java.util.Collections;
import java.util.List;

public final class ModeSetting extends Setting<Integer> {

    private final List<String> modes;

    public ModeSetting(String name, int index, List<String> modes) {
        super(name, index);
        this.modes = Collections.unmodifiableList(modes);
    }

    public ModeSetting(String name, String defaultMode, String... modes) {
        this(name, java.util.Arrays.asList(modes).indexOf(defaultMode) >= 0 ? java.util.Arrays.asList(modes).indexOf(defaultMode) : 0, java.util.Arrays.asList(modes));
    }

    public ModeSetting(String name, int defaultIndex, String... modes) {
        this(name, defaultIndex, java.util.Arrays.asList(modes));
    }

    public List<String> getModes() {
        return modes;
    }

    public String getCurrentMode() {
        int idx = getIndex();
        if (idx < 0 || idx >= modes.size())
            return modes.isEmpty() ? "" : modes.get(0);
        return modes.get(idx);
    }

    /** Safe mode index for {@code visibleWhen} predicates (never null). */
    public int getIndex() {
        Integer v = getValue();
        if (v == null)
            return 0;
        int idx = v;
        if (idx < 0)
            return 0;
        if (idx >= modes.size())
            return Math.max(0, modes.size() - 1);
        return idx;
    }

    @Override
    public void setValue(Integer value) {
        if (value == null) {
            super.setValue(0);
            return;
        }
        int idx = value;
        if (idx < 0)
            idx = 0;
        else if (idx >= modes.size())
            idx = Math.max(0, modes.size() - 1);
        super.setValue(idx);
    }

    @Override
    public JsonElement serialize() {
        return new JsonPrimitive(getIndex());
    }

    @Override
    public void deserialize(JsonElement element) {
        setValue(element.getAsInt());
    }
}
