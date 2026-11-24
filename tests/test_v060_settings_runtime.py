"""Focused Hoshiori v0.6.0 runtime settings / drawer regression guards."""
from pathlib import Path
import json
import unittest

ROOT = Path(__file__).resolve().parents[1]
PATCH = ROOT / "patches/src/main/kotlin/dev/rikunode/hatenabookmark"
SET = ROOT / "extensions/settings/src/main/java/dev/rikunode/settings"

def source(path):
    return path.read_text(encoding="utf-8")

class HoshioriRuntimeTests(unittest.TestCase):
    def test_settings_entry_and_main_bootstrap_guard(self):
        p = source(PATCH / "HoshioriSettingsPatch.kt")
        for expected in (
            'name = "Hoshiori in-app settings"',
            "impl.registerCount != 7", "offset != 847",
            "returns.single().second != 0x2cc",
            "mainImpl.registerCount != 11",
            "mainInsns.sumOf { it.codeUnits } != 412",
            "HoshioriSettingsBridge;->attach",
            "HoshioriRuntime;->init",
            "dev.rikunode.settings.HoshioriDrawerSettingsActivity",
            'activity.setAttribute("android:exported", "false")',
        ): self.assertIn(expected, p)
        bridge = source(SET / "HoshioriSettingsBridge.java")
        self.assertIn("setOnPreferenceClickListener", bridge)
        self.assertIn("getPreferenceScreen", bridge)
        self.assertIn("HoshioriSettingsActivity.class", bridge)

    def test_six_feature_controls_and_manager_defaults(self):
        runtime = source(SET / "HoshioriRuntime.java")
        self.assertIn('"hoshiori_settings"', runtime)
        self.assertIn("getBoolean(key, true)", runtime)
        ui = source(SET / "HoshioriSettingsActivity.java")
        for key in ("hideNativePrEntries", "hideWeeklyRanking", "blockInAppBrowserAds",
                    "updateAdBlockLists", "preserveArticleWebStorage",
                    "openArticleFromBookmarkCard"):
            self.assertIn('"' + key + '"', ui)
        self.assertNotIn('toggle("クローン化"', ui)
        for filename in ("HideWeeklyRankingPatch.kt", "BlockAdsInAppBrowserPatch.kt",
                         "PreserveArticleWebStoragePatch.kt", "OpenArticleFromBookmarkCardPatch.kt"):
            self.assertIn("default = true", source(PATCH / filename))
        self.assertIn("dependsOn(hoshioriSettingsEntryPatch)",
                      source(PATCH / "HideNativePrEntriesPatch.kt"))

    def test_original_cookie_calls_are_conditionally_preserved(self):
        src = source(PATCH / "PreserveArticleWebStoragePatch.kt")
        self.assertEqual(src.count('replaceInstruction('), 2)
        self.assertIn("preserveWebStorage(Landroid/content/Context;)Z", src)
        self.assertIn("WebViewViewModel;->clearWebViewCookie()V", src)
        self.assertIn("Ljava/lang/Thread;->start()V", src)
        self.assertIn("destroy.second", src)
        self.assertIn("startup.second", src)
        self.assertIn('if (preserveArticleWebStorage != true) return@execute', src)

    def test_runtime_filter_and_card_read_setting(self):
        for name, key in [
            ("HideNativePrEntriesPatch.kt", "hideNativePrEntries"),
            ("HideWeeklyRankingPatch.kt", "hideWeeklyRanking"),
        ]:
            self.assertIn('"'+key+'"', source(PATCH / name))
        ad = source(ROOT / "extensions/adblock/src/main/java/dev/rikunode/adblock/AdBlockHelper.java")
        self.assertIn('hoshioriEnabled(view.getContext(), "blockInAppBrowserAds")', ad)
        self.assertIn('hoshioriEnabled(view.getContext(), "updateAdBlockLists")', ad)
        card = source(ROOT / "extensions/cardclick/src/main/java/dev/rikunode/cardclick/BookmarkCardClickHelper.java")
        self.assertIn('.getBoolean("openArticleFromBookmarkCard", true)', card)

    def test_drawer_preserves_destinations_and_settings(self):
        p = source(PATCH / "HoshioriDrawerPatch.kt")
        self.assertIn("impl.registerCount != 8", p)
        self.assertIn("insns.sumOf { it.codeUnits } != 28", p)
        self.assertIn("HoshioriDrawerBridge;->apply", p)
        bridge = source(SET / "HoshioriDrawerBridge.java")
        self.assertIn('getIdentifier("nav_config"', bridge)
        self.assertIn("menu.add(src.group, src.id", bridge)
        self.assertIn("item.setVisible(src.id == configId", bridge)
        activity = source(SET / "HoshioriDrawerSettingsActivity.java")
        self.assertIn("items.get(pos).group != items.get(destination).group", activity)
        self.assertIn("toggle.setEnabled(entry.id != config)", activity)

    def test_pr_tab_unshipped_after_failed_feasibility(self):
        ui = source(SET / "HoshioriSettingsActivity.java")
        self.assertIn("PR記事専用タブ：未搭載", ui)
        self.assertNotIn('toggle("PR記事専用タブを表示"', ui)
        index = json.loads((ROOT / "patches-list.json").read_text(encoding="utf-8"))
        self.assertFalse(any("PR article tab" in p["name"] for p in index["patches"]))

if __name__ == "__main__":
    unittest.main()
