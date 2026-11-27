package dev.letsgoaway.geyserextras.core.features.skins;

import com.google.gson.Gson;
import dev.letsgoaway.geyserextras.core.preferences.PreferencesData;
import org.geysermc.geyser.GeyserImpl;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class SkinPreferenceTest {
    @Test
    void missingLegacyPreferenceUsesServerDefaultAndExplicitFalseSurvivesJsonAndCopy() {
        GeyserImpl geyser = mock(GeyserImpl.class, RETURNS_DEEP_STUBS);
        try (var global = mockStatic(GeyserImpl.class)) {
            global.when(GeyserImpl::getInstance).thenReturn(geyser);
            Gson gson = new Gson();
            PreferencesData legacy = gson.fromJson("{}", PreferencesData.class);
            assertTrue(legacy.useLinkedJavaSkin(true));
            assertFalse(legacy.useLinkedJavaSkin(false));
            assertTrue(legacy.useLinkedJavaCape(true));
            assertFalse(legacy.useLinkedJavaCape(false));
            legacy.setUseLinkedJavaSkin(false);
            legacy.setUseLinkedJavaCape(true);
            PreferencesData reloaded = gson.fromJson(gson.toJson(legacy), PreferencesData.class);
            PreferencesData copied = new PreferencesData();
            copied.copyFrom(reloaded);
            assertEquals(Boolean.FALSE, copied.getUseLinkedJavaSkin());
            assertFalse(copied.useLinkedJavaSkin(true));
            assertTrue(copied.useLinkedJavaCape(false));
            copied.setUseLinkedJavaCape(false);
            copied.setUseLinkedJavaSkin(true);
            assertTrue(copied.useLinkedJavaSkin(false));
            assertFalse(copied.useLinkedJavaCape(true));
        }
    }
}
