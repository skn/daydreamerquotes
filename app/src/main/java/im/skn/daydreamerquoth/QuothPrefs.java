package im.skn.daydreamerquoth;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.util.TypedValue;
import android.view.View;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.preference.PreferenceManager;
import androidx.appcompat.app.AppCompatActivity;
import androidx.preference.PreferenceFragmentCompat;

public class QuothPrefs extends AppCompatActivity {

    public static final String PREF_DELAY_BETWEEN_QUOTES = "PREF_DELAY_BETWEEN_QUOTES";
    public static final String PREF_TEXT_SIZE = "PREF_TEXT_SIZE";
    public static final String PREF_FONT_FAMILY = "PREF_FONT_FAMILY";
    public static final String PREF_READING_SPEED = "PREF_READING_SPEED";

    public QuothPrefs() {

    }

    public static SharedPreferences get(Context context) {
        return PreferenceManager.getDefaultSharedPreferences(context);
    }
    @Override
	public void onCreate(Bundle bundle) {
        super.onCreate(bundle);
        getSupportFragmentManager().beginTransaction().replace(android.R.id.content, new MySettingsFragment()).commit();

        // targetSdk 35+ forces edge-to-edge, which draws the status bar and the
        // classic ActionBar as transparent overlays on top of the content instead
        // of reserving space for them. Pad the content view by both so the
        // preference list starts below them instead of underneath them.
        View content = findViewById(android.R.id.content);
        ViewCompat.setOnApplyWindowInsetsListener(content, (v, windowInsets) -> {
            Insets systemBars = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars());

            TypedValue actionBarSizeValue = new TypedValue();
            int actionBarHeight = 0;
            if (getTheme().resolveAttribute(androidx.appcompat.R.attr.actionBarSize, actionBarSizeValue, true)) {
                actionBarHeight = TypedValue.complexToDimensionPixelSize(
                        actionBarSizeValue.data, getResources().getDisplayMetrics());
            }

            v.setPadding(systemBars.left, systemBars.top + actionBarHeight, systemBars.right, systemBars.bottom);
            return windowInsets;
        });
    }

    public static class MySettingsFragment extends PreferenceFragmentCompat {
        @Override
        public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
            setPreferencesFromResource(R.xml.dream_settings, rootKey);
        }
    }
}
