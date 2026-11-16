package quest.uuid.core;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PremiumUuidTest {

    @Test
    void acceptsVersion4WithValidName() {
        assertTrue(PremiumUuid.isPremium(UUID.fromString("069a79f4-44e9-4726-a5be-fca90e38aaf5"), "Notch"));
        assertTrue(PremiumUuid.isPremium(TestSupport.v4(), "a"));
        assertTrue(PremiumUuid.isPremium(TestSupport.v4(), "Abc_123_xyz_4567"));
    }

    @Test
    void rejectsOfflineModeUuids() {
        assertFalse(PremiumUuid.isPremium(TestSupport.v3("Notch"), "Notch"));
    }

    @Test
    void rejectsOtherVariants() {
        // Version 4 but NCS variant (top bits 0).
        assertFalse(PremiumUuid.isPremium(UUID.fromString("069a79f4-44e9-4726-25be-fca90e38aaf5"), "Notch"));
    }

    @Test
    void rejectsNamesNoAccountCanHave() {
        UUID uuid = TestSupport.v4();
        assertFalse(PremiumUuid.isPremium(uuid, null));
        assertFalse(PremiumUuid.isPremium(uuid, ""));
        assertFalse(PremiumUuid.isPremium(uuid, "Abc_123_xyz_45678"));
        assertFalse(PremiumUuid.isPremium(uuid, "§cShop"));
        assertFalse(PremiumUuid.isPremium(uuid, "two words"));
        assertFalse(PremiumUuid.isPremium(null, "Notch"));
    }

}
