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

    public static final String PREF_DELAY_BETWEEN_QUOTES = "PREF_DELAY_BETWEEN_QUOTES";
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
            setPreferencesFromResource(R.xml.dream_settings, rootKey);

            ListPreference delayPref = findPreference(PREF_DELAY_BETWEEN_QUOTES);
            Preference readingSpeedPref = findPreference(PREF_READING_SPEED);
            if (delayPref == null || readingSpeedPref == null) {
                return;
            }

            readingSpeedPref.setVisible(usesReadingSpeed(delayPref.getValue()));
            delayPref.setOnPreferenceChangeListener((preference, newValue) -> {
                readingSpeedPref.setVisible(usesReadingSpeed((String) newValue));
                return true;
            });
        }

        // Reading speed only affects the "smart"/"hybrid" timing modes (see
        // DayDreamerQuoth.calculateNextDelay) - a "fixed" delay ignores it entirely,
        // so hide the preference rather than leave a setting that does nothing.
        private boolean usesReadingSpeed(String timingPref) {
            if (timingPref == null) {
                return false;
            }
            String[] parts = timingPref.split(":");
            return parts.length == 2 && ("smart".equals(parts[1]) || "hybrid".equals(parts[1]));
        }
    }
}
