package im.skn.daydreamerquoth;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
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
import android.view.View;
import android.widget.ImageView;
import android.widget.RelativeLayout;
import android.widget.TextView;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.Random;

public class DayDreamerQuoth extends DreamService {
    protected static final boolean DEBUG = false; /* DEBUG is set to protected so as to be accessible from unit test */
    private static final long DEBUG_DELAY_QUOTE = 8000L;

    private static final int TEXT_SIZE_AUTHOR_LARGE = 34;
    private static final int TEXT_SIZE_AUTHOR_MEDIUM = 29;
    private static final int TEXT_SIZE_AUTHOR_SMALL = 24;
    private static final int TEXT_SIZE_BODY_LARGE = 38;
    private static final int TEXT_SIZE_BODY_MEDIUM = 33;
    private static final int TEXT_SIZE_BODY_SMALL = 28;
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
    private View toShow;
    private Context ctx;
    private int numberOfQuotes;
    BroadcastReceiver mBatteryLevelReceiver = new mBatteryLevelReceiver();

    public DayDreamerQuoth() {
    	super();
        handler = new Handler(Looper.getMainLooper());
        delay = DEFAULT_DELAY;
        animateSecond = false;
        showQuoteRunnable = new Runnable() {
        	public void run() {
        		showQuote();
        	}
        };
    }
    
    private int numberOfLines() throws IOException {
    	InputStream is = ctx.getResources().openRawResource(R.raw.quotes);
        try {
            byte[] c = new byte[1024];
            int count = 0;
            int readChars = 0;
            boolean empty = true;
            while ((readChars = is.read(c)) != -1) {
                empty = false;
                for (int i = 0; i < readChars; ++i) {
                    if (c[i] == '\n') {
                        ++count;
                    }
                }
            }
            is.close();
            return (count == 0 && !empty) ? 1 : count;
        } finally {
            is.close();
        }
    }
    
    private String randLineFromFile() throws IOException {
    	String theLine="";
        if (numberOfQuotes > 0 ) {
        	InputStream inputStream = ctx.getResources().openRawResource(R.raw.quotes);
            InputStreamReader inputreader = new InputStreamReader(inputStream);
            BufferedReader br = new BufferedReader(inputreader);
	    	Random r = new Random();
	    	int desiredLine = r.nextInt(numberOfQuotes);

	    	int lineCtr = 0;
	    	try {
				while ((theLine = br.readLine()) != null) {
					if (lineCtr == desiredLine) {
						break;
					}
					lineCtr++;
				}
		    	inputStream.close();
		    	inputreader.close();
		    	br.close();
	    	} catch (IOException readLineException) {
				theLine = NO_FILE_ERR_MSG;
			} finally {
		    	inputStream.close();
		    	inputreader.close();
		    	br.close();
	    	}
        } else {
        	theLine = NO_FILE_ERR_MSG;
        }
    	return theLine;
    }

    private void setQuote() {

        Resources resources;
        String qline;
        String[] qlineparts;
        String quoteStr = "";
        String authStr = "";
        String finalQuoteStr;
        String finalAuthStr;

        if (animateSecond) {
            animateSecond = false;
            toShow = secondContent;
            toHide = firstContent;
        } else {
            animateSecond = true;
            toShow = firstContent;
            toHide = secondContent;
        }

		try {
			qline = randLineFromFile();
		} catch (IOException e) {
			qline = NO_FILE_ERR_MSG;
		}
		try {
			qlineparts = qline.split(" -- ");
        	quoteStr = qlineparts[0];
        	authStr = qlineparts[1];
		} catch (ArrayIndexOutOfBoundsException e)
		{
			System.err.println("ArrayIndexOutOfBoundsException: " + e.getMessage());
			System.err.println("Line: " + qline);
		}

        resources = getResources();
        finalQuoteStr = resources.getString(R.string.lbl_quote_body, quoteStr);
        finalAuthStr = resources.getString(R.string.lbl_quote_author, authStr);

        // Are we charging / charged?
        IntentFilter ifilter = new IntentFilter(Intent.ACTION_BATTERY_CHANGED);
        Intent batteryStatus = registerReceiver(null, ifilter);
        int status = batteryStatus.getIntExtra(BatteryManager.EXTRA_STATUS, -1);

        int level = batteryStatus.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
        int scale = batteryStatus.getIntExtra(BatteryManager.EXTRA_SCALE, -1);
        int batteryPct = level * 100 / scale;

        setBatteryDetails(status, batteryPct);

        batteryLevelRcvr();

        ((TextView) toShow.findViewById(R.id.quote_body)).setText(finalQuoteStr);
        ((TextView) toShow.findViewById(R.id.quote_author)).setText(finalAuthStr);

        //https://developer.android.com/develop/ui/views/animations/reveal-or-hide-view#CrossfadeViews
        // Initially hide the toShow view.
        toShow.setVisibility(View.GONE);
        // Set the toShow view to 0% opacity but visible, so that it is visible
        // (but fully transparent) during the animation.
        toShow.setAlpha(0.0F);
        toShow.setVisibility(View.VISIBLE);
        // Animate the toShow view to 100% opacity, and clear any animation
        // listener set on the view.
        toShow.animate()
                .alpha(1.0F)
                .setDuration(shortAnimationDuration)
                .setListener(null);

        // Animate the toHide view to 0% opacity. After the animation ends,
        // set its visibility to GONE as an optimization step (it won't
        // participate in layout passes, etc.)
        toHide.animate()
                .alpha(0.0F)
                .setDuration(shortAnimationDuration)
                .setListener(new AnimatorListenerAdapter() {
                    @Override
        	        public void onAnimationEnd(Animator animator) {
                        toHide.setVisibility(View.GONE);
        	        }
                });
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
            int status = batteryStatus.getIntExtra(BatteryManager.EXTRA_STATUS, -1);

            setBatteryDetails(status, batteryPct);
        }
    };

    private void setBatteryDetails(int status, int batteryPct) {
        String finalBatteryPct = getResources().getString(R.string.lbl_battery_pct, batteryPct);
        ((TextView) findViewById(R.id.batteryPct)).setText(finalBatteryPct);
        if (status == BatteryManager.BATTERY_STATUS_CHARGING) {
            if (batteryPct <= 20) {
                ((ImageView) findViewById(R.id.batteryStatus)).setImageResource(R.drawable.ic_battery_charging_20);
            }
            else if (batteryPct <= 30) {
                ((ImageView) findViewById(R.id.batteryStatus)).setImageResource(R.drawable.ic_battery_charging_30);
            }
            else if (batteryPct <= 50) {
                ((ImageView) findViewById(R.id.batteryStatus)).setImageResource(R.drawable.ic_battery_charging_50);
            }
            else if (batteryPct <= 60) {
                ((ImageView) findViewById(R.id.batteryStatus)).setImageResource(R.drawable.ic_battery_charging_60);
            }
            else if (batteryPct <= 80) {
                ((ImageView) findViewById(R.id.batteryStatus)).setImageResource(R.drawable.ic_battery_charging_80);
            }
            else if (batteryPct <= 90) {
                ((ImageView) findViewById(R.id.batteryStatus)).setImageResource(R.drawable.ic_battery_charging_90);
            }
            else {
                ((ImageView) findViewById(R.id.batteryStatus)).setImageResource(R.drawable.ic_battery_charging_full);
            }
        }
        else if (status == BatteryManager.BATTERY_STATUS_DISCHARGING || status == BatteryManager.BATTERY_STATUS_NOT_CHARGING) {
            if (batteryPct <= 20) {
                ((ImageView) findViewById(R.id.batteryStatus)).setImageResource(R.drawable.ic_battery_20);
            }
            else if (batteryPct <= 30) {
                ((ImageView) findViewById(R.id.batteryStatus)).setImageResource(R.drawable.ic_battery_30);
            }
            else if (batteryPct <= 50) {
                ((ImageView) findViewById(R.id.batteryStatus)).setImageResource(R.drawable.ic_battery_50);
            }
            else if (batteryPct <= 60) {
                ((ImageView) findViewById(R.id.batteryStatus)).setImageResource(R.drawable.ic_battery_60);
            }
            else if (batteryPct <= 80) {
                ((ImageView) findViewById(R.id.batteryStatus)).setImageResource(R.drawable.ic_battery_80);
            }
            else if (batteryPct <= 90) {
                ((ImageView) findViewById(R.id.batteryStatus)).setImageResource(R.drawable.ic_battery_90);
            }
            else {
                ((ImageView) findViewById(R.id.batteryStatus)).setImageResource(R.drawable.ic_battery_full);
            }
        }
        else if (status == BatteryManager.BATTERY_STATUS_FULL) {
            ((ImageView) findViewById(R.id.batteryStatus)).setImageResource(R.drawable.ic_battery_full);
        }
        else if (status == BatteryManager.BATTERY_STATUS_UNKNOWN) {
            ((ImageView) findViewById(R.id.batteryStatus)).setImageResource(R.drawable.ic_battery_unknown);
        }
    }

    private void showQuote() {
        setQuote();
        long l = delay;
        handler.postDelayed(showQuoteRunnable, l);
    }

    public void onAttachedToWindow() {
        super.onAttachedToWindow();
        ctx = this;
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
        ImageView contentBatteryStatusView;


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
        }
        if (!TextUtils.isEmpty(txt_size)) {
            if ("0".equals(txt_size)) {
                quote_text_size = TEXT_SIZE_BODY_SMALL;
                author_text_size = TEXT_SIZE_AUTHOR_SMALL;
            } else
            if ("1".equals(txt_size)) {
                quote_text_size = TEXT_SIZE_BODY_MEDIUM;
                author_text_size = TEXT_SIZE_AUTHOR_MEDIUM;
            } else
            if ("2".equals(txt_size)) {
                quote_text_size = TEXT_SIZE_BODY_LARGE;
                author_text_size = TEXT_SIZE_AUTHOR_LARGE;
            }
        }
        if (DEBUG) {
        	delay = DEBUG_DELAY_QUOTE;
        } else {
        	try {
                delay = Long.valueOf(delay_txt);
        	}
        	catch (NumberFormatException numberformatexception) {
        		delay = DEFAULT_DELAY;
        	}
        }
        try {
			numberOfQuotes = numberOfLines();
		} catch (IOException e) {
			numberOfQuotes = 0;
		}
        firstContent = findViewById(R.id.quote_content_first);
        firstContentBodyTextview = (TextView)firstContent.findViewById(R.id.quote_body);
        firstContentAuthTextview = (TextView)firstContent.findViewById(R.id.quote_author);
        firstContentBodyTextview.setTypeface(regularTypeface);
        firstContentAuthTextview.setTypeface(lightTypeface);
        firstContentBodyTextview.setTextSize(2, quote_text_size);
        firstContentAuthTextview.setTextSize(2, author_text_size);

        secondContent = findViewById(R.id.quote_content_second);
        secondContentBodyTextview = (TextView)secondContent.findViewById(R.id.quote_body);
        secondContentAuthTextview = (TextView)secondContent.findViewById(R.id.quote_author);
        secondContentBodyTextview.setTypeface(regularTypeface);
        secondContentAuthTextview.setTypeface(lightTypeface);
        secondContentBodyTextview.setTextSize(2, quote_text_size);
        secondContentAuthTextview.setTextSize(2, author_text_size);

        contentTimeView = (TextView)findViewById(R.id.time);
        contentTimeView.setTypeface(regularTypeface);
        contentTimeView.setTextSize(2, author_text_size - TEXT_SIZE_DIFF_AUTH_TIME);

        contentDateView = (TextView)findViewById(R.id.date);
        contentDateView.setTypeface(regularTypeface);
        contentDateView.setTextSize(2, author_text_size - TEXT_SIZE_DIFF_AUTH_TIME);

        contentBatteryPctView = (TextView)findViewById(R.id.batteryPct);
        contentBatteryPctView.setTypeface(regularTypeface);

        contentBatteryStatusView = (ImageView)findViewById(R.id.batteryStatus);

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

        boolean showBatteryPct = prefs.getBoolean("PREF_SHOW_BATTERY_PCT", true);
        if (!showBatteryPct){
            contentBatteryPctView.setVisibility(View.GONE);
        }
        else {
            contentBatteryPctView.setVisibility(View.VISIBLE);
            contentBatteryPctView.setTextColor(0XFFFFFFFF);
        }

        boolean showBatteryStatus = prefs.getBoolean("PREF_SHOW_BATTERY_STATUS", true);
        if (!showBatteryStatus){
            contentBatteryStatusView.setVisibility(View.GONE);
        }
        else {
            contentBatteryStatusView.setVisibility(View.VISIBLE);
        }

        shortAnimationDuration = DEFAULT_SWITCH_ANIM_DURATION;
        showQuote();
    }

    public void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        handler.removeCallbacks(showQuoteRunnable);
        this.unregisterReceiver(mBatteryLevelReceiver);
    }
}
