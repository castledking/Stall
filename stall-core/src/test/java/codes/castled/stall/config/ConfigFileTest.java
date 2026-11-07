package codes.castled.stall.config;

import codes.castled.stall.offer.OfferStatus;
import codes.castled.stall.shop.ShopContainers;
import codes.castled.stall.shop.ShopLimits;
import codes.castled.stall.store.StoreLog;
import codes.castled.stall.trade.TradeFailure;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConfigFileTest
{
    private static final StoreLog QUIET = new StoreLog()
    {
        @Override
        public void info(String message)
        {
        }

        @Override
        public void warn(String message, Throwable error)
        {
        }
    };

    @Test
    void yamlParsesNestedMapsListsScalarsAndComments() throws Exception
    {
        String text = """
                # Top-level comment
                config-version: 1
                shops:
                  default-limit: 5 # inline comment
                  disabled-worlds: []
                  allowed: [one, "two # not a comment", 'three''s']
                  lore:
                    - "&7Line one"
                    - '&7Line two: # still string'
                protection:
                  explosions: true
                  hopper-extract: false
                """;

        Map<String, Object> root = Yaml.parse(text);
        assertEquals(1, root.get("config-version"));

        @SuppressWarnings("unchecked")
        Map<String, Object> shops = (Map<String, Object>) root.get("shops");
        assertEquals(5, shops.get("default-limit"));
        assertEquals(List.of(), shops.get("disabled-worlds"));
        assertEquals(List.of("one", "two # not a comment", "three's"), shops.get("allowed"));
        assertEquals(List.of("&7Line one", "&7Line two: # still string"), shops.get("lore"));

        @SuppressWarnings("unchecked")
        Map<String, Object> protection = (Map<String, Object>) root.get("protection");
        assertEquals(true, protection.get("explosions"));
        assertEquals(false, protection.get("hopper-extract"));
    }

    @Test
    void yamlReportsLineNumbersOnSyntaxErrors()
    {
        YamlException ex = assertThrows(YamlException.class, () -> Yaml.parse("a: 1\n\tb: 2\n"));
        assertEquals(2, ex.line());
    }

    @Test
    void configFileCreatesDefaultAndLeavesCurrentFileUntouched(@TempDir Path dir) throws Exception
    {
        String template = """
                # Header comment
                config-version: 1

                shops:
                  # Default limit comment
                  default-limit: 3
                  names:
                    - "Alpha"
                    - "Beta"
                """;
        Path file = dir.resolve("config.yml");

        Map<String, Object> first = ConfigFile.load(file, template, List.of(), QUIET);
        assertTrue(Files.exists(file));
        assertFalse(Files.exists(dir.resolve("config.yml.bak")));
        assertEquals(3, first.get("shops.default-limit"));
        assertEquals(List.of("Alpha", "Beta"), first.get("shops.names"));

        String onDisk = Files.readString(file);
        assertTrue(onDisk.contains("# Header comment"));
        assertTrue(onDisk.contains("# Default limit comment"));

        Map<String, Object> second = ConfigFile.load(file, template, List.of(), QUIET);
        assertFalse(Files.exists(dir.resolve("config.yml.bak")));
        assertEquals(3, second.get("shops.default-limit"));
    }

    @Test
    void configFileUpgradesOlderVersionPreservingUserValuesAndCommentsAndRunningMigrations(@TempDir Path dir) throws Exception
    {
        Path file = dir.resolve("config.yml");
        Files.writeString(file, """
                config-version: 1
                shops:
                  old-limit: 7
                  extra-list:
                    - "Custom line 1"
                    - "Custom line 2"
                """);

        String templateV2 = """
                # Managed config
                config-version: 2

                shops:
                  # Renamed from old-limit in v2
                  default-limit: 3
                  max-offers: 6
                  extra-list:
                    - "Default line"
                  empty-by-default: []
                """;

        List<Migration> migrations = List.of(
                Migration.rename(2, "shops.old-limit", "shops.default-limit")
        );

        Map<String, Object> upgraded = ConfigFile.load(file, templateV2, migrations, QUIET);
        assertEquals(2, upgraded.get("config-version"));
        assertEquals(7, upgraded.get("shops.default-limit"));
        assertEquals(6, upgraded.get("shops.max-offers"));
        assertEquals(List.of("Custom line 1", "Custom line 2"), upgraded.get("shops.extra-list"));
        assertEquals(List.of(), upgraded.get("shops.empty-by-default"));

        assertTrue(Files.exists(dir.resolve("config.yml.bak")));
        String updatedDisk = Files.readString(file);
        assertTrue(updatedDisk.contains("# Renamed from old-limit in v2"));
        assertTrue(updatedDisk.contains("default-limit: 7"));
        assertTrue(updatedDisk.contains("max-offers: 6"));
    }

    @Test
    void configFileBackfillsMissingKeysEvenAtSameVersion(@TempDir Path dir) throws Exception
    {
        Path file = dir.resolve("config.yml");
        Files.writeString(file, """
                config-version: 1
                shops:
                  default-limit: 9
                """);

        String template = """
                config-version: 1
                shops:
                  default-limit: 3
                  max-offers: 6
                """;

        Map<String, Object> loaded = ConfigFile.load(file, template, List.of(), QUIET);
        assertTrue(Files.exists(dir.resolve("config.yml.bak")));
        assertEquals(9, loaded.get("shops.default-limit"));
        assertEquals(6, loaded.get("shops.max-offers"));
    }

    @Test
    void legacyTextFormatsCodesAndMatchesExpectedItemSnbt()
    {
        String tokenName = LegacyText.translate("&6Stall Token");
        assertEquals("§6Stall Token", tokenName);
        assertEquals("Stall Token", LegacyText.strip(tokenName));

        assertEquals(
                "{text:\"Stall Token\",color:\"gold\",italic:false}",
                LegacyText.itemSnbt(tokenName)
        );
        assertEquals(
                "{text:\"Use it on a barrel to turn the\",color:\"gray\",italic:false}",
                LegacyText.itemSnbt(LegacyText.translate("&7Use it on a barrel to turn the"))
        );

        String hexSnbt = LegacyText.itemSnbt(LegacyText.translate("&#ff5500Custom"));
        assertEquals("{text:\"Custom\",color:\"#FF5500\",italic:false}", hexSnbt);
    }

    @Test
    void stallConfigLoadsDefaultsAndNormalizesDisabledWorlds(@TempDir Path dir) throws Exception
    {
        StallConfig config = StallConfig.load(dir, QUIET);
        assertEquals(3, config.defaultShopLimit());
        assertEquals(100, config.maxLimitTier());
        assertEquals(6, config.maxOffers());
        assertEquals(15L, config.offerRemoveCooldownTicks());
        assertTrue(config.isContainerAllowed(ShopContainers.BARREL));
        assertTrue(config.isContainerAllowed(ShopContainers.CHEST));
        assertTrue(config.isContainerAllowed(ShopContainers.TRAPPED_CHEST));
        assertTrue(config.doubleChestsAllowed());
        assertTrue(config.largeBarrelsAllowed());
        assertFalse(config.isWorldDisabled("minecraft:overworld"));
        assertTrue(config.protectExplosions());
        assertTrue(config.protectHopperExtract());
        assertTrue(config.allowHopperInsert());
        assertEquals(4L, config.buyCooldownTicks());
        assertFalse(config.notifyOwnerOnSale());
        assertEquals(20, config.menuRefreshTicks());
        assertEquals(64, config.maxTokensPerCommand());
        assertFalse(config.consumeTokenInCreative());
        assertTrue(config.tokenGlint());

        Path configFile = dir.resolve("config.yml");
        String custom = Files.readString(configFile).replace(
                "disabled-worlds: []",
                "disabled-worlds: [world, world_the_end, \"minecraft:the_nether\"]"
        );
        Files.writeString(configFile, custom);
        StallConfig reloaded = StallConfig.load(dir, QUIET);
        String expanded = Files.readString(configFile);
        assertTrue(expanded.contains("disabled-worlds:\n    - \"world\"\n    - \"world_the_end\"\n    - \"minecraft:the_nether\""));
        assertTrue(reloaded.isWorldDisabled("world"));
        assertTrue(reloaded.isWorldDisabled("minecraft:overworld"));
        assertTrue(reloaded.isWorldDisabled("minecraft:overworld", "world"));
        assertTrue(reloaded.isWorldDisabled("world_the_end"));
        assertTrue(reloaded.isWorldDisabled("minecraft:the_end"));
        assertTrue(reloaded.isWorldDisabled("minecraft:world_the_end"));
        assertTrue(reloaded.isWorldDisabled("the_nether"));
        assertTrue(reloaded.isWorldDisabled("world_nether"));
        assertTrue(reloaded.isWorldDisabled("minecraft:the_nether"));
        assertFalse(reloaded.isWorldDisabled("custom_world"));
    }

    @Test
    void stallLangLoadsDefaultsFormatsPlaceholdersAndSupportsSilencing(@TempDir Path dir) throws Exception
    {
        StallLang lang = StallLang.load(dir, QUIET);
        assertEquals("", lang.text("prefix"));
        assertEquals("§6Stall Token", lang.text("token.name"));
        assertEquals(
                List.of("§7Use it on a barrel to turn the", "§7barrel into your shop."),
                lang.lines("token.lore")
        );
        assertEquals("barrel", lang.container(ShopContainers.BARREL));
        assertEquals("no limit", lang.limit(ShopLimits.UNLIMITED));
        assertEquals("3", lang.limit(3));
        assertEquals("§aIn stock", lang.status(OfferStatus.AVAILABLE));
        assertEquals(
                "§cThis shop is out of stock for that trade.",
                lang.tradeFailure(TradeFailure.OUT_OF_STOCK)
        );
        assertEquals(
                "§eBuyer bought from your shop at 10, 64, 10.",
                lang.chat("trades.owner-sold", "buyer", "Buyer", "location", "10, 64, 10")
        );

        // Silencing a chat message by setting it to "" returns empty string even when prefix is set
        Path langFile = dir.resolve("lang.yml");
        String custom = Files.readString(langFile)
                .replace("prefix: \"\"", "prefix: \"&6[Stall] &r\"")
                .replace("complete: \"&aTrade complete.\"", "complete: \"\"");
        Files.writeString(langFile, custom);
        StallLang reloaded = StallLang.load(dir, QUIET);
        assertEquals("", reloaded.chat("trades.complete"));
        assertEquals("§6[Stall] §r§cThis shop is out of stock for that trade.", reloaded.tradeFailure(TradeFailure.OUT_OF_STOCK));
    }
}
