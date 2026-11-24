package dev.rikunode.settings;

import android.content.Context;
import android.content.Intent;
import android.util.Log;

/** Uses the Preference API retained in Hatena Bookmark 5.80. */
public final class HoshioriSettingsBridge {
    private static final String TAG = "HoshioriSettings";
    private HoshioriSettingsBridge() {}

    public static void attach(Object fragment) {
        try {
            Context context = (Context) fragment.getClass().getMethod("requireContext").invoke(fragment);
            Class<?> preference = Class.forName("androidx.preference.Preference");
            Object row = preference.getConstructor(Context.class).newInstance(context);
            // R8 removed Preference.setKey(String) in the target APK.
            // This non-persistent navigation row needs no preference key.
            preference.getMethod("setTitle", CharSequence.class).invoke(row, "Hoshiori");
            preference.getMethod("setSummary", CharSequence.class).invoke(row, "非公式パッチの設定");
            preference.getMethod("setIntent", Intent.class).invoke(row,
                    new Intent(context, HoshioriSettingsActivity.class));
            Object screen = fragment.getClass().getMethod("getPreferenceScreen").invoke(fragment);
            Class<?> group = Class.forName("androidx.preference.PreferenceGroup");
            if (screen == null) {
                Log.e(TAG, "PreferenceScreen is unavailable");
                return;
            }
            if (!Boolean.TRUE.equals(group.getMethod("addPreference", preference).invoke(screen, row))) {
                Log.e(TAG, "PreferenceScreen rejected Hoshiori entry");
                return;
            }
            Log.i(TAG, "Hoshiori preference added");
        } catch (ReflectiveOperationException | RuntimeException failure) {
            Log.e(TAG, "Could not add Hoshiori preference", failure);
        }
    }
}
