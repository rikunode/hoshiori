package dev.rikunode.settings;

import android.content.Context;
import android.content.Intent;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;

/** Runtime hook for the existing AndroidX preference screen without bundling AndroidX twice. */
public final class HoshioriSettingsBridge {
    private HoshioriSettingsBridge() {}

    public static void attach(Object fragment) {
        try {
            Context context = (Context) fragment.getClass().getMethod("requireContext").invoke(fragment);
            Class<?> preference = Class.forName("androidx.preference.Preference");
            Constructor<?> ctor = preference.getConstructor(Context.class);
            Object row = ctor.newInstance(context);
            preference.getMethod("setKey", String.class).invoke(row, "hoshiori_settings_entry");
            preference.getMethod("setTitle", CharSequence.class).invoke(row, "Hoshiori");
            preference.getMethod("setSummary", CharSequence.class).invoke(row, "非公式パッチの設定");
            Object screen = fragment.getClass().getMethod("getPreferenceScreen").invoke(fragment);
            screen.getClass().getMethod("addPreference", preference).invoke(screen, row);
            Class<?> listener = Class.forName("androidx.preference.Preference$OnPreferenceClickListener");
            Object callback = Proxy.newProxyInstance(listener.getClassLoader(),
                    new Class<?>[]{listener}, (proxy, method, args) -> {
                        if ("onPreferenceClick".equals(method.getName())) {
                            context.startActivity(new Intent(context, HoshioriSettingsActivity.class));
                            return true;
                        }
                        return false;
                    });
            preference.getMethod("setOnPreferenceClickListener", listener).invoke(row, callback);
        } catch (ReflectiveOperationException | RuntimeException ignored) {
            // Do not break the application's original settings if their layout changes.
        }
    }
}
