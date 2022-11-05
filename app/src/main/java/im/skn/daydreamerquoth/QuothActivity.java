package im.skn.daydreamerquoth;

import android.app.Activity;
import androidx.appcompat.app.AppCompatActivity;
import android.content.Intent;
import android.os.Bundle;

public class QuothActivity extends Activity {

    public QuothActivity() {
    }

    public void onCreate(Bundle bundle) {
        super.onCreate(bundle);
        startActivity(new Intent("android.settings.DREAM_SETTINGS"));
        finish();
    }
}
