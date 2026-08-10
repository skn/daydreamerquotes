package im.skn.daydreamerquoth;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.preference.ListPreference;
import androidx.preference.Preference;
import androidx.preference.PreferenceManager;
import androidx.appcompat.app.AppCompatActivity;
import androidx.preference.PreferenceFragmentCompat;
import im.skn.daydreamerquoth.databinding.ActivityQuothPrefsBinding;

public class QuothPrefs extends AppCompatActivity {

    // Kept only as a migration source for installs that persisted a value
    // before the mode/seconds split below - no longer read on the hot path.
    public static final String PREF_DELAY_BETWEEN_QUOTES = "PREF_DELAY_BETWEEN_QUOTES";
    public static final String PREF_DELAY_MODE = "PREF_DELAY_MODE";
    public static final String PREF_DELAY_SECONDS = "PREF_DELAY_SECONDS";
    public static final String PREF_TEXT_SIZE = "PREF_TEXT_SIZE";
    public static final String PREF_FONT_FAMILY = "PREF_FONT_FAMILY";
    public static final String PREF_READING_SPEED = "PREF_READING_SPEED";
    public static final String PREF_SHOW_TIME = "PREF_SHOW_TIME";
    public static final String PREF_SHOW_DATE = "PREF_SHOW_DATE";
    public static final String PREF_SHOW_BATTERY_PCT = "PREF_SHOW_BATTERY_PCT";
    public static final String PREF_SHOW_BATTERY_STATUS = "PREF_SHOW_BATTERY_STATUS";

    public QuothPrefs() {

    }

    public static SharedPreferences get(Context context) {
        return PreferenceManager.getDefaultSharedPreferences(context);
    }

    private static final long LEGACY_DEFAULT_DELAY_MS = 60000L;
    static final int DEFAULT_DELAY_SECONDS = 60;
    static final int MIN_DELAY_SECONDS = 30;
    static final int MAX_DELAY_SECONDS = 300;
    private static final int DELAY_SECONDS_STEP = 30;

    /**
     * One-time migration from the old combined "<delayMs>:<mode>" string
     * (PREF_DELAY_BETWEEN_QUOTES) to the PREF_DELAY_MODE / PREF_DELAY_SECONDS
     * pair. No-op once PREF_DELAY_MODE has been written - safe to call from
     * multiple entry points (DayDreamerQuoth and QuothPrefs both need the
     * migrated values available, whichever runs first wins).
     */
    static void migrateLegacyDelayPreferenceIfNeeded(Context context) {
        SharedPreferences prefs = get(context);
        if (prefs.contains(PREF_DELAY_MODE)) {
            return;
        }

        String legacy = prefs.getString(PREF_DELAY_BETWEEN_QUOTES, null);
        if (legacy == null) {
            // Fresh install, nothing to migrate - go straight to the new defaults.
            prefs.edit()
                    .putString(PREF_DELAY_MODE, "smart")
                    .putInt(PREF_DELAY_SECONDS, DEFAULT_DELAY_SECONDS)
                    .apply();
            return;
        }

        long delayMs;
        String mode;
        String[] parts = legacy.split(":");
        if (parts.length == 2) {
            try {
                delayMs = Long.parseLong(parts[0]);
                mode = parts[1];
            } catch (NumberFormatException e) {
                delayMs = LEGACY_DEFAULT_DELAY_MS;
                mode = "fixed";
            }
        } else {
            // Old bare-number format predates Smart Timing, so it never had a mode.
            try {
                delayMs = Long.parseLong(legacy);
            } catch (NumberFormatException e) {
                delayMs = LEGACY_DEFAULT_DELAY_MS;
            }
            mode = "fixed";
        }

        prefs.edit()
                .putString(PREF_DELAY_MODE, mode)
                .putInt(PREF_DELAY_SECONDS, clampToSliderRange(Math.round(delayMs / 1000f)))
                .apply();
    }

    private static int clampToSliderRange(int seconds) {
        int rounded = Math.round(seconds / (float) DELAY_SECONDS_STEP) * DELAY_SECONDS_STEP;
        return Math.max(MIN_DELAY_SECONDS, Math.min(MAX_DELAY_SECONDS, rounded));
    }

    @Override
	public void onCreate(Bundle bundle) {
        super.onCreate(bundle);
        ActivityQuothPrefsBinding binding = ActivityQuothPrefsBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        setSupportActionBar(binding.settingsToolbar);
        getSupportFragmentManager().beginTransaction()
                .replace(binding.settingsContainer.getId(), new MySettingsFragment()).commit();

        // The Toolbar above reserves its own space in the layout, so it can no
        // longer overlap content the way the classic overlay ActionBar could.
        // The status bar is still edge-to-edge under targetSdk 35+, so pad for it.
        ViewCompat.setOnApplyWindowInsetsListener(binding.settingsRoot, (v, windowInsets) -> {
            Insets systemBars = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return windowInsets;
        });
    }

    public static class MySettingsFragment extends PreferenceFragmentCompat {
        @Override
        public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
            migrateLegacyDelayPreferenceIfNeeded(requireContext());
            setPreferencesFromResource(R.xml.dream_settings, rootKey);

            ListPreference delayModePref = findPreference(PREF_DELAY_MODE);
            Preference delaySecondsPref = findPreference(PREF_DELAY_SECONDS);
            Preference readingSpeedPref = findPreference(PREF_READING_SPEED);
            if (delayModePref == null || delaySecondsPref == null || readingSpeedPref == null) {
                return;
            }

            applyDelayModeVisibility(delayModePref.getValue(), delaySecondsPref, readingSpeedPref);
            delayModePref.setOnPreferenceChangeListener((preference, newValue) -> {
                applyDelayModeVisibility((String) newValue, delaySecondsPref, readingSpeedPref);
                return true;
            });
        }

        // The seconds slider means "the delay" in fixed mode or "the minimum" in
        // hybrid mode, so it's irrelevant (and hidden) only in smart mode. Reading
        // speed only affects the "smart"/"hybrid" timing modes (see
        // DayDreamerQuoth.calculateNextDelay) - a "fixed" delay ignores it entirely.
        private void applyDelayModeVisibility(String mode, Preference delaySecondsPref, Preference readingSpeedPref) {
            delaySecondsPref.setVisible(!"smart".equals(mode));
            readingSpeedPref.setVisible(usesReadingSpeed(mode));
        }

        private boolean usesReadingSpeed(String mode) {
            return "smart".equals(mode) || "hybrid".equals(mode);
        }
    }
}
