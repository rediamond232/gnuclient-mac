package gnu.client.module.setting;

import org.junit.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.assertEquals;

public class SettingOnChangedTest {

    @Test
    public void onChangedFiresOnlyWhenValueChanges() {
        AtomicInteger hits = new AtomicInteger();
        BoolSetting setting = new BoolSetting("t", false);
        setting.onChanged(hits::incrementAndGet);

        setting.setValue(false);
        assertEquals(0, hits.get());

        setting.setValue(true);
        assertEquals(1, hits.get());

        setting.setValue(true);
        assertEquals(1, hits.get());

        setting.setValue(false);
        assertEquals(2, hits.get());
    }
}
