package im.skn.daydreamerquoth;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager.NameNotFoundException;
import android.os.Bundle;
import android.preference.PreferenceActivity;
import android.preference.PreferenceFragment;
import android.preference.PreferenceManager;
import android.util.Log;

public class QuothPrefs extends PreferenceActivity {

    public static final String PREF_DELAY_BETWEEN_QUOTES = "PREF_DELAY_BETWEEN_QUOTES";
    public static final String PREF_TEXT_SIZE = "PREF_TEXT_SIZE";
    public static final String PREF_FONT_FAMILY = "PREF_FONT_FAMILY";
    public static final String KEY_ABOUT = "about";
	private static SharedPreferences prefs;
	private static String summary;
	
    public QuothPrefs() {

    }

    public static SharedPreferences get(Context context) {
        return PreferenceManager.getDefaultSharedPreferences(context);
    }

	public void onCreate(Bundle bundle) {
        super.onCreate(bundle);
        getFragmentManager().beginTransaction().replace(android.R.id.content, new MyPreferenceFragment()).commit();
//		prefs = findPreference(KEY_ABOUT);
//	    String versionName = getVersionName(this);
//	    int versionNumber = getVersionCode(this);
//	    summary = "Version" + " " + versionName + " (" + String.valueOf(versionNumber) + ")";
//        prefs.setSummary(summary);
    }
    
    public static class MyPreferenceFragment extends PreferenceFragment {
        @Override
        public void onCreate(final Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);
            addPreferencesFromResource(R.xml.dream_settings);
        }
    }

    // Gets version code of given application.
    public static int getVersionCode(Context context) {
        PackageInfo pinfo;
        try {
            pinfo = context.getPackageManager().getPackageInfo(context.getPackageName(), 0);
            return pinfo.versionCode;
        } catch (NameNotFoundException e) {
            Log.e(context.getApplicationInfo().name, "Version code not available.");
        }
        return 0;
    }

    // Gets version name of given application.
    public static String getVersionName(Context context) {
        PackageInfo pinfo;
        try {
            pinfo = context.getPackageManager().getPackageInfo(context.getPackageName(), 0);
            return  pinfo.versionName;
        } catch (NameNotFoundException e) {
            Log.e(context.getApplicationInfo().name, "Version name not available.");
        }
        return null;
    }
}
