package im.skn.daydreamerquoth;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.PreferenceActivity;
import android.preference.PreferenceFragment;
import android.preference.PreferenceManager;

public class QuothPrefs extends PreferenceActivity {

    public static final String PREF_DELAY_BETWEEN_QUOTES = "PREF_DELAY_BETWEEN_QUOTES";
    public static final String PREF_TEXT_SIZE = "PREF_TEXT_SIZE";
    public static final String PREF_FONT_FAMILY = "PREF_FONT_FAMILY";

    public QuothPrefs() {

    }

    public static SharedPreferences get(Context context) {
        return PreferenceManager.getDefaultSharedPreferences(context);
    }

	public void onCreate(Bundle bundle) {
        super.onCreate(bundle);
        getFragmentManager().beginTransaction().replace(android.R.id.content, new MyPreferenceFragment()).commit();
    }
    
    public static class MyPreferenceFragment extends PreferenceFragment {
        @Override
        public void onCreate(final Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);
            addPreferencesFromResource(R.xml.dream_settings);
        }
    }
}
