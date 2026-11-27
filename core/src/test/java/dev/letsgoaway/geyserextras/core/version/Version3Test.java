package dev.letsgoaway.geyserextras.core.version;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class Version3Test {
    @Test
    void olderPublicationCannotBeOfferedAsAnUpgradeWhenReleaseChannelsLag() {
        Version3 current = Version3.fromString("2.2.0");
        assertFalse(Version3.fromString("2.1.0").isNewer(current));
        assertFalse(Version3.fromString("2.1.99").isNewer(current));
        assertFalse(Version3.fromString("1.99.99").isNewer(current));
        assertFalse(Version3.fromString("2.2.0").isNewer(current));
        assertTrue(Version3.fromString("2.2.1").isNewer(current));
        assertTrue(Version3.fromString("2.3.0").isNewer(current));
        assertTrue(Version3.fromString("3.0.0").isNewer(current));
    }
}
