package im.skn.daydreamerquoth;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.res.Resources;
import android.graphics.Typeface;
import android.os.BatteryManager;
import android.os.Handler;
import android.os.Looper;
import android.service.dreams.DreamService;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.widget.ImageView;
import android.widget.RelativeLayout;
import android.widget.TextView;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class DayDreamerQuoth extends DreamService {
    protected static final boolean DEBUG = true; /* DEBUG is set to protected so as to be accessible from unit test */
    private static final long DEBUG_DELAY_QUOTE = 8000L;
    private static final int TEXT_SIZE_AUTHOR_LARGE = 34;
    private static final int TEXT_SIZE_AUTHOR_MEDIUM = 29;
    private static final int TEXT_SIZE_AUTHOR_SMALL = 24;
    private static final int TEXT_SIZE_AUTHOR_TINY = 18;
    private static final int TEXT_SIZE_BODY_LARGE = 38;
    private static final int TEXT_SIZE_BODY_MEDIUM = 33;
    private static final int TEXT_SIZE_BODY_SMALL = 28;
    private static final int TEXT_SIZE_BODY_TINY = 22;
    private static final int TEXT_SIZE_DIFF_AUTH_TIME = 1;
    private static final long DEFAULT_DELAY = 60000L;
    private static final int DEFAULT_SWITCH_ANIM_DURATION = 2000;
    private static final int DEFAULT_BODY_TEXT_SIZE = TEXT_SIZE_BODY_SMALL;
    private static final int DEFAULT_AUTH_TEXT_SIZE = TEXT_SIZE_AUTHOR_SMALL;
    private static final String DEFAULT_REGULAR_TYPEFACE = "fonts/Santana-Bold.ttf";
    private static final String DEFAULT_LIGHT_TYPEFACE = "fonts/Santana.ttf";
    private static final String NO_FILE_ERR_MSG = "Could not find the embedded quotes file. Spit it out, the one who ate it! -- Daydreamer";
    private boolean animateSecond;
    private long delay;
    private View firstContent;
    private final Handler handler;
    private View secondContent;
    private int shortAnimationDuration;
    private final Runnable showQuoteRunnable;
    private View toHide;
    private int numberOfQuotes;
    private boolean showBatteryPct;
    private boolean showBatteryStatus;
    private List<String> quotes;
    BroadcastReceiver mBatteryLevelReceiver = new mBatteryLevelReceiver();
    private static final Random random = new Random();

    public DayDreamerQuoth() {
    	super();
        handler = new Handler(Looper.getMainLooper());
        delay = DEFAULT_DELAY;
        animateSecond = false;
        showQuoteRunnable = this::showQuote;
    }

    private void loadQuotes() throws IOException {
        quotes = new ArrayList<>();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                this.getResources().openRawResource(R.raw.quotes)))) {
            String line;
            while ((line = reader.readLine()) != null) {
                quotes.add(line);
            }
        }
        numberOfQuotes = quotes.size();
        if (DEBUG) {
            Log.i("lineNumbers", String.valueOf(quotes.size()));
        }
    }
    private String randLineFromFile() {
        if (quotes == null) {  // Lazy-load if quotes have not been loaded yet.
            try {
                loadQuotes();
            } catch (IOException e) {
                return NO_FILE_ERR_MSG;
            }
        }
        if (quotes.isEmpty()) {
            return NO_FILE_ERR_MSG;
        }
        return quotes.get(random.nextInt(numberOfQuotes));
    }

    private void setQuote() {

        Resources resources;
        String qline;
        String[] qlineparts;
        String quoteStr;
        String authStr;
        String finalQuoteStr;
        String finalAuthStr;

        View toShow;
        if (animateSecond) {
            animateSecond = false;
            toShow = secondContent;
            toHide = firstContent;
        } else {
            animateSecond = true;
            toShow = firstContent;
            toHide = secondContent;
        }

        qline = randLineFromFile();

        qlineparts = qline.split(" -- ", 2);
        quoteStr = qlineparts[0];
        authStr = (qlineparts.length > 1) ? qlineparts[1] : "Unknown";

        resources = getResources();
        finalQuoteStr = resources.getString(R.string.lbl_quote_body, quoteStr);
        finalAuthStr = resources.getString(R.string.lbl_quote_author, authStr);

        ((TextView) toShow.findViewById(R.id.quote_body)).setText(finalQuoteStr);
        ((TextView) toShow.findViewById(R.id.quote_author)).setText(finalAuthStr);

        AnimatorSet animatorSet = new AnimatorSet();
        animatorSet.playTogether(
                ObjectAnimator.ofFloat(toShow, "alpha", 0f, 1f).setDuration(shortAnimationDuration),
                ObjectAnimator.ofFloat(toHide, "alpha", 1f, 0f).setDuration(shortAnimationDuration)
        );
        animatorSet.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                toHide.setVisibility(View.GONE);
            }
        });
        toShow.setVisibility(View.VISIBLE);
        animatorSet.start();

    }

    private void batteryLevelRcvr() {
        IntentFilter batteryLevelFilter = new IntentFilter(Intent.ACTION_BATTERY_CHANGED);
        registerReceiver(mBatteryLevelReceiver, batteryLevelFilter);
    }

    public class mBatteryLevelReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            int level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
            int scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1);
            int batteryPct = level * 100 / scale;

            IntentFilter ifilter = new IntentFilter(Intent.ACTION_BATTERY_CHANGED);
            Intent batteryStatus = registerReceiver(null, ifilter);
            assert batteryStatus != null;
            int status = batteryStatus.getIntExtra(BatteryManager.EXTRA_STATUS, -1);

            setBatteryDetails(status, batteryPct, batteryStatus);
        }
    }

    private void setBatteryDetails(int status, int batteryPct,Intent batteryStatus) {
        if (showBatteryPct) {
            String finalBatteryPct = getResources().getString(R.string.lbl_battery_pct, batteryPct);
            ((TextView) findViewById(R.id.batteryPct)).setText(finalBatteryPct);
        }
        if (showBatteryStatus) {
            ImageView batteryStatusImageView = findViewById(R.id.batteryStatus);
            TextView batteryChrgTypeTextView = findViewById(R.id.batteryChrgType);
            if (status == BatteryManager.BATTERY_STATUS_CHARGING) {
                if (batteryPct <= 20) {
                    batteryStatusImageView.setImageResource(R.drawable.ic_battery_charging_20);
                } else if (batteryPct <= 30) {
                    batteryStatusImageView.setImageResource(R.drawable.ic_battery_charging_30);
                } else if (batteryPct <= 50) {
                    batteryStatusImageView.setImageResource(R.drawable.ic_battery_charging_50);
                } else if (batteryPct <= 60) {
                    batteryStatusImageView.setImageResource(R.drawable.ic_battery_charging_60);
                } else if (batteryPct <= 80) {
                    batteryStatusImageView.setImageResource(R.drawable.ic_battery_charging_80);
                } else if (batteryPct <= 90) {
                    batteryStatusImageView.setImageResource(R.drawable.ic_battery_charging_90);
                } else {
                    batteryStatusImageView.setImageResource(R.drawable.ic_battery_charging_full);
                }
                switch (batteryStatus.getIntExtra(BatteryManager.EXTRA_PLUGGED, -1)) {
                    case BatteryManager.BATTERY_PLUGGED_AC:
                        batteryChrgTypeTextView.setText("a");
                        break;
                    case BatteryManager.BATTERY_PLUGGED_USB:
                        batteryChrgTypeTextView.setText("u");
                        break;
                    case BatteryManager.BATTERY_PLUGGED_WIRELESS:
                        batteryChrgTypeTextView.setText("w");
                        break;
                }
            } else if (status == BatteryManager.BATTERY_STATUS_DISCHARGING || status == BatteryManager.BATTERY_STATUS_NOT_CHARGING) {
                if (batteryPct <= 20) {
                    batteryStatusImageView.setImageResource(R.drawable.ic_battery_20);
                } else if (batteryPct <= 30) {
                    batteryStatusImageView.setImageResource(R.drawable.ic_battery_30);
                } else if (batteryPct <= 50) {
                    batteryStatusImageView.setImageResource(R.drawable.ic_battery_50);
                } else if (batteryPct <= 60) {
                    batteryStatusImageView.setImageResource(R.drawable.ic_battery_60);
                } else if (batteryPct <= 80) {
                    batteryStatusImageView.setImageResource(R.drawable.ic_battery_80);
                } else if (batteryPct <= 90) {
                    batteryStatusImageView.setImageResource(R.drawable.ic_battery_90);
                } else {
                    batteryStatusImageView.setImageResource(R.drawable.ic_battery_full);
                }
            } else if (status == BatteryManager.BATTERY_STATUS_FULL) {
                batteryStatusImageView.setImageResource(R.drawable.ic_battery_full);
            } else if (status == BatteryManager.BATTERY_STATUS_UNKNOWN) {
                batteryStatusImageView.setImageResource(R.drawable.ic_battery_unknown);
            }
        }
    }

    private void showQuote() {
        setQuote();
        long l = delay;
        handler.postDelayed(showQuoteRunnable, l);
    }

    public void onAttachedToWindow() {
        super.onAttachedToWindow();
        setInteractive(true);
        setFullscreen(true);
        setContentView(R.layout.dream_quotes);
        Typeface regularTypeface = Typeface.createFromAsset(getAssets(), DEFAULT_REGULAR_TYPEFACE);
        Typeface lightTypeface = Typeface.createFromAsset(getAssets(), DEFAULT_LIGHT_TYPEFACE);
        int quote_text_size = DEFAULT_BODY_TEXT_SIZE;
        int author_text_size = DEFAULT_AUTH_TEXT_SIZE;
        SharedPreferences prefs = QuothPrefs.get(this);
        String delay_txt = prefs.getString("PREF_DELAY_BETWEEN_QUOTES", null);
        String txt_size = prefs.getString("PREF_TEXT_SIZE", null);
        String font_family = prefs.getString("PREF_FONT_FAMILY", null);
        
        TextView firstContentBodyTextview;
        TextView firstContentAuthTextview;
        TextView secondContentBodyTextview;
        TextView secondContentAuthTextview;

        TextView contentTimeView;
        TextView contentDateView;
        TextView contentBatteryPctView;

        //ImageView batteryStatusView;
        TextView chargeTypeView;

        if (!TextUtils.isEmpty(font_family)) {
        	if ("Roboto".equals(font_family)) {
                regularTypeface = Typeface.createFromAsset(getAssets(), "fonts/Roboto-Regular.ttf");
                lightTypeface = Typeface.createFromAsset(getAssets(), "fonts/Roboto-Light.ttf");
            } else
            if ("Santana".equals(font_family)) {
                regularTypeface = Typeface.createFromAsset(getAssets(), "fonts/Santana-Bold.ttf");
                lightTypeface = Typeface.createFromAsset(getAssets(), "fonts/Santana.ttf");
            } else
            if ("DroidSerif".equals(font_family)) {
            	regularTypeface = Typeface.createFromAsset(getAssets(), "fonts/DroidSerif-Bold.ttf");
                lightTypeface = Typeface.createFromAsset(getAssets(), "fonts/DroidSerif.ttf");
            } else
            if ("OpenSans".equals(font_family)) {
            	regularTypeface = Typeface.createFromAsset(getAssets(), "fonts/OpenSans-Regular.ttf");
                lightTypeface = Typeface.createFromAsset(getAssets(), "fonts/OpenSans-Light.ttf");
            }
            else
            if ("Typewriter".equals(font_family)) {
                regularTypeface = Typeface.createFromAsset(getAssets(), "fonts/MaszynaAEG.ttf");
                lightTypeface = Typeface.createFromAsset(getAssets(), "fonts/MaszynaRoyalLight.ttf");
            }
        }
        if (!TextUtils.isEmpty(txt_size)) {
            if ("0".equals(txt_size)) {
                quote_text_size = TEXT_SIZE_BODY_TINY;
                author_text_size = TEXT_SIZE_AUTHOR_TINY;
            } else
            if ("1".equals(txt_size)) {
                quote_text_size = TEXT_SIZE_BODY_SMALL;
                author_text_size = TEXT_SIZE_AUTHOR_SMALL;
            } else
            if ("2".equals(txt_size)) {
                quote_text_size = TEXT_SIZE_BODY_MEDIUM;
                author_text_size = TEXT_SIZE_AUTHOR_MEDIUM;
            } else
            if ("3".equals(txt_size)) {
                quote_text_size = TEXT_SIZE_BODY_LARGE;
                author_text_size = TEXT_SIZE_AUTHOR_LARGE;
            }
        }
        if (DEBUG) {
        	delay = DEBUG_DELAY_QUOTE;
        } else {
        	try {
                assert delay_txt != null;
                delay = Long.parseLong(delay_txt);
        	}
        	catch (NumberFormatException numberformatexception) {
        		delay = DEFAULT_DELAY;
        	}
        }

        firstContent = findViewById(R.id.quote_content_first);
        firstContentBodyTextview = firstContent.findViewById(R.id.quote_body);
        firstContentAuthTextview = firstContent.findViewById(R.id.quote_author);
        firstContentBodyTextview.setTypeface(regularTypeface);
        firstContentAuthTextview.setTypeface(lightTypeface);
        firstContentBodyTextview.setTextSize(2, quote_text_size);
        firstContentAuthTextview.setTextSize(2, author_text_size);

        secondContent = findViewById(R.id.quote_content_second);
        secondContentBodyTextview = secondContent.findViewById(R.id.quote_body);
        secondContentAuthTextview = secondContent.findViewById(R.id.quote_author);
        secondContentBodyTextview.setTypeface(regularTypeface);
        secondContentAuthTextview.setTypeface(lightTypeface);
        secondContentBodyTextview.setTextSize(2, quote_text_size);
        secondContentAuthTextview.setTextSize(2, author_text_size);

        contentTimeView = findViewById(R.id.time);
        contentTimeView.setTypeface(regularTypeface);
        contentTimeView.setTextSize(2, author_text_size - TEXT_SIZE_DIFF_AUTH_TIME);

        contentDateView = findViewById(R.id.date);
        contentDateView.setTypeface(regularTypeface);
        contentDateView.setTextSize(2, author_text_size - TEXT_SIZE_DIFF_AUTH_TIME);

        View contentBatteryStatusView = findViewById(R.id.batteryStatus_content);

        contentBatteryPctView = findViewById(R.id.batteryPct);
        contentBatteryPctView.setTypeface(regularTypeface);
        contentBatteryPctView.setTextSize(2, author_text_size - 4*TEXT_SIZE_DIFF_AUTH_TIME);

        chargeTypeView = contentBatteryStatusView.findViewById(R.id.batteryChrgType);
        chargeTypeView.setTypeface(regularTypeface);
        chargeTypeView.setTextSize(2, author_text_size - 4*TEXT_SIZE_DIFF_AUTH_TIME);

        boolean showTime = prefs.getBoolean("PREF_SHOW_TIME", true);
        if (!showTime){
            contentTimeView.setVisibility(View.GONE);
        }
        else {
            contentTimeView.setVisibility(View.VISIBLE);
            contentTimeView.setTextColor(0XFFFFFFFF);
        }
        boolean showDate = prefs.getBoolean("PREF_SHOW_DATE", true);
        if (!showDate){
            contentDateView.setVisibility(View.GONE);
            if (showTime) {
                // If time is shown but not date
                RelativeLayout.LayoutParams timeLayoutParams = new RelativeLayout.LayoutParams(
                        RelativeLayout.LayoutParams.WRAP_CONTENT, RelativeLayout.LayoutParams.WRAP_CONTENT);
                // remove alignment to the date since date is not shown
                timeLayoutParams.removeRule(RelativeLayout.ABOVE);
                // instead align the time text just like the date - center and bottom of layout
                timeLayoutParams.addRule(RelativeLayout.ALIGN_PARENT_BOTTOM);
                timeLayoutParams.addRule(RelativeLayout.CENTER_HORIZONTAL);
                contentTimeView.setLayoutParams(timeLayoutParams);
            }
        }
        else {
            contentDateView.setVisibility(View.VISIBLE);
            contentDateView.setTextColor(0XFFFFFFFF);
        }

        showBatteryPct = prefs.getBoolean("PREF_SHOW_BATTERY_PCT", true);
        if (!showBatteryPct){
            contentBatteryPctView.setVisibility(View.GONE);
        }
        else {
            contentBatteryPctView.setVisibility(View.VISIBLE);
            contentBatteryPctView.setTextColor(0XFFFFFFFF);
        }

        showBatteryStatus = prefs.getBoolean("PREF_SHOW_BATTERY_STATUS", true);
        if (!showBatteryStatus){
            contentBatteryStatusView.setVisibility(View.GONE);
        }
        else {
            contentBatteryStatusView.setVisibility(View.VISIBLE);
        }

        shortAnimationDuration = DEFAULT_SWITCH_ANIM_DURATION;

        // Are we charging / charged?
        IntentFilter ifilter = new IntentFilter(Intent.ACTION_BATTERY_CHANGED);
        Intent batteryStatus = registerReceiver(null, ifilter);
        assert batteryStatus != null;
        int status = batteryStatus.getIntExtra(BatteryManager.EXTRA_STATUS, -1);

        int level = batteryStatus.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
        int scale = batteryStatus.getIntExtra(BatteryManager.EXTRA_SCALE, -1);
        int batteryPct = level * 100 / scale;
        setBatteryDetails(status, batteryPct, batteryStatus);
        batteryLevelRcvr();

        showQuote();
    }

    public void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        handler.removeCallbacks(showQuoteRunnable);
        this.unregisterReceiver(mBatteryLevelReceiver);
    }
}
