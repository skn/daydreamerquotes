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
import android.util.Log;
import android.view.View;
import android.widget.ImageView;
import android.widget.RelativeLayout;
import android.widget.TextView;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * TypefaceManager - Singleton class to cache and manage Typeface objects
 * Prevents memory leaks from repeatedly creating Typeface instances
 */
class TypefaceManager {
    private static TypefaceManager instance;
    private final Map<String, Typeface> typefaceCache = new HashMap<>();
    
    private TypefaceManager() {}
    
    public static TypefaceManager getInstance() {
        if (instance == null) {
            instance = new TypefaceManager();
        }
        return instance;
    }
    
    public Typeface getTypeface(Context context, String fontPath) {
        if (fontPath == null) return Typeface.DEFAULT;
        
        Typeface typeface = typefaceCache.get(fontPath);
        if (typeface == null) {
            try {
                typeface = Typeface.createFromAsset(context.getAssets(), fontPath);
                typefaceCache.put(fontPath, typeface);
            } catch (Exception e) {
                Log.w("TypefaceManager", "Failed to load font: " + fontPath, e);
                typeface = Typeface.DEFAULT;
            }
        }
        return typeface;
    }
    
    
    public int getCacheSize() {
        return typefaceCache.size();
    }
    
    /**
     * Get a pair of typefaces (regular and light) for a given font family
     */
    public Typeface[] getTypefacePair(Context context, String fontFamily) {
        String regularPath, lightPath;
        
        switch (fontFamily != null ? fontFamily : "Santana") {
            case "Roboto":
                regularPath = "fonts/Roboto-Regular.ttf";
                lightPath = "fonts/Roboto-Light.ttf";
                break;
            case "Santana":
                regularPath = "fonts/Santana-Bold.ttf";
                lightPath = "fonts/Santana.ttf";
                break;
            case "DroidSerif":
                regularPath = "fonts/DroidSerif-Bold.ttf";
                lightPath = "fonts/DroidSerif.ttf";
                break;
            case "OpenSans":
                regularPath = "fonts/OpenSans-Regular.ttf";
                lightPath = "fonts/OpenSans-Light.ttf";
                break;
            case "Typewriter":
                regularPath = "fonts/MaszynaAEG.ttf";
                lightPath = "fonts/MaszynaRoyalLight.ttf";
                break;
            default:
                regularPath = "fonts/Santana-Bold.ttf";
                lightPath = "fonts/Santana.ttf";
                break;
        }
        
        return new Typeface[]{
            getTypeface(context, regularPath),
            getTypeface(context, lightPath)
        };
    }
}

public class DayDreamerQuoth extends DreamService {
    // Debug flags - can be set independently for different testing scenarios:
    // To enable logging: Set DEBUG = true
    // To test quick quote cycling: Set DEBUG_FAST_QUOTES = true  
    // Both can be enabled simultaneously if needed
    protected static final boolean DEBUG = false; /* General debug logging - accessible from unit tests */
    protected static final boolean DEBUG_FAST_QUOTES = false; /* Quick quote iteration for testing - overrides timing preferences */
    private static final long DEBUG_DELAY_QUOTE = 3000L;
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
    
    // Smart timing constants
    private static final int DEFAULT_READING_WPM = 200;        // Average adult reading speed
    private static final long MIN_DISPLAY_TIME = 5000L;        // 5 seconds minimum
    private static final long MAX_DISPLAY_TIME = 180000L;      // 3 minutes maximum
    private static final float REFLECTION_TIME_RATIO = 0.4f;   // 40% additional time for reflection
    private static final float COMPLEXITY_MULTIPLIER_BASE = 1.0f;
    private static final float LONG_WORD_PENALTY = 0.3f;       // 30% more time for complex vocabulary
    private static final float PUNCTUATION_PENALTY = 0.2f;     // 20% more time for complex sentences
    private static final float DIALOGUE_PENALTY = 0.1f;       // 10% more time for dialogue
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
    
    // Asynchronous quotes loading state management
    private volatile boolean isQuotesLoaded = false;
    private volatile boolean isLoadingQuotes = false;
    private ExecutorService quotesExecutor;
    private boolean pendingQuoteRequest = false;
    
    // Cached view references to avoid repeated findViewById calls
    private TextView firstContentBodyTextView;
    private TextView firstContentAuthTextView;
    private TextView secondContentBodyTextView;
    private TextView secondContentAuthTextView;
    private TextView contentTimeView;
    private TextView contentDateView;
    private TextView contentBatteryPctView;
    private ImageView batteryStatusImageView;
    private TextView batteryChrgTypeTextView;
    private View contentBatteryStatusView;
    
    // Smart timing variables
    private String currentQuoteText = "";
    
    private static final Random random = new Random();

    public DayDreamerQuoth() {
    	super();
        handler = new Handler(Looper.getMainLooper());
        delay = DEFAULT_DELAY;
        animateSecond = false;
        showQuoteRunnable = this::showQuote;
    }

    /**
     * Synchronous quotes loading method (used by background thread)
     */
    private List<String> loadQuotesFromFile() throws IOException {
        List<String> loadedQuotes = new ArrayList<>();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                this.getResources().openRawResource(R.raw.quotes)))) {
            String line;
            while ((line = reader.readLine()) != null) {
                loadedQuotes.add(line);
            }
        }
        return loadedQuotes;
    }
    
    /**
     * Asynchronous quotes loading using ExecutorService
     * Loads quotes in background thread and updates UI when complete
     */
    private void loadQuotesAsync() {
        if (isLoadingQuotes || isQuotesLoaded) {
            return; // Already loading or loaded
        }
        
        isLoadingQuotes = true;
        
        // Clean up any shutdown executor to allow garbage collection
        if (quotesExecutor != null && quotesExecutor.isShutdown()) {
            quotesExecutor = null;
        }
        
        if (quotesExecutor == null) {
            quotesExecutor = Executors.newSingleThreadExecutor();
        }
        
        if (DEBUG) {
            Log.d("DayDreamerQuoth", "Starting async quotes loading...");
        }
        
        quotesExecutor.execute(() -> {
            try {
                // Load quotes on background thread
                List<String> loadedQuotes = loadQuotesFromFile();
                
                // Switch to main thread for UI update
                handler.post(() -> {
                    quotes = loadedQuotes;
                    numberOfQuotes = quotes.size();
                    isQuotesLoaded = true;
                    isLoadingQuotes = false;
                    
                    if (DEBUG) {
                        Log.i("DayDreamerQuoth", "Quotes loaded asynchronously: " + numberOfQuotes + " quotes");
                    }
                    
                    // If there was a pending quote request, fulfill it now
                    if (pendingQuoteRequest) {
                        if (DEBUG) {
                            Log.d("DayDreamerQuoth", "Loading completed, refreshing quote immediately");
                        }
                        pendingQuoteRequest = false;
                        // Cancel any existing delayed quote update and show immediately
                        handler.removeCallbacks(showQuoteRunnable);
                        setQuote(); // Show the first real quote immediately
                        // Resume normal quote cycling with proper delay calculation
                        long properDelay = calculateNextDelay();
                        handler.postDelayed(showQuoteRunnable, properDelay);
                    } else {
                        if (DEBUG) {
                            Log.d("DayDreamerQuoth", "Loading completed but no pending request");
                        }
                    }
                });
                
            } catch (IOException e) {
                // Handle error on main thread
                handler.post(() -> {
                    Log.e("DayDreamerQuoth", "Error loading quotes asynchronously", e);
                    isLoadingQuotes = false;
                    // Keep quotes as null to trigger fallback message
                });
            }
        });
    }
    /**
     * Get random quote with asynchronous loading support
     * Returns placeholder message while quotes are loading in background
     */
    private String randLineFromFile() {
        // If quotes are fully loaded, return random quote
        if (isQuotesLoaded && quotes != null && !quotes.isEmpty()) {
            return quotes.get(random.nextInt(numberOfQuotes));
        }
        
        // If not loading yet, start async loading
        if (!isLoadingQuotes && !isQuotesLoaded) {
            if (DEBUG) {
                Log.d("DayDreamerQuoth", "Triggering async quotes loading");
            }
            loadQuotesAsync();
            // Only set pending if not already set
            if (!pendingQuoteRequest) {
                pendingQuoteRequest = true;
            }
        }
        
        // Return appropriate message based on loading state
        if (isLoadingQuotes) {
            return "Loading inspirational quotes... ⏳ -- Daydreamer";
        } else if (quotes != null && quotes.isEmpty()) {
            return "No quotes found in file -- Daydreamer";
        } else {
            return "Unable to load quotes at this time -- Daydreamer";
        }
    }

    /**
     * Calculate smart delay based on quote complexity and reading speed
     */
    private long calculateSmartDelay(String quoteText) {
        if (quoteText == null || quoteText.isEmpty()) {
            return DEFAULT_DELAY;
        }
        
        // Get user's reading speed preference
        int readingSpeedWPM = getUserReadingSpeed();
        
        // Analyze quote complexity
        int wordCount = getWordCount(quoteText);
        float complexityMultiplier = calculateComplexityMultiplier(quoteText, wordCount);
        
        // Calculate base reading time in milliseconds
        long baseReadingTime = (long) ((wordCount / (float) readingSpeedWPM) * 60 * 1000);
        
        // Apply complexity adjustments
        long adjustedReadingTime = (long) (baseReadingTime * complexityMultiplier);
        
        // Add reflection time
        long smartDelay = (long) (adjustedReadingTime * (1 + REFLECTION_TIME_RATIO));
        
        // Apply bounds
        return Math.max(MIN_DISPLAY_TIME, Math.min(MAX_DISPLAY_TIME, smartDelay));
    }
    
    /**
     * Get word count from quote text
     */
    private int getWordCount(String text) {
        if (text == null || text.trim().isEmpty()) {
            return 0;
        }
        return text.trim().split("\\s+").length;
    }
    
    /**
     * Calculate complexity multiplier based on various factors
     */
    private float calculateComplexityMultiplier(String quoteText, int wordCount) {
        float multiplier = COMPLEXITY_MULTIPLIER_BASE;
        
        if (wordCount == 0) return multiplier;
        
        // Factor 1: Long words (7+ characters indicate complex vocabulary)
        String[] words = quoteText.trim().split("\\s+");
        int longWordCount = 0;
        for (String word : words) {
            // Remove punctuation for length calculation
            String cleanWord = word.replaceAll("[^a-zA-Z]", "");
            if (cleanWord.length() > 7) {
                longWordCount++;
            }
        }
        if (longWordCount > wordCount * 0.3) { // More than 30% long words
            multiplier += LONG_WORD_PENALTY;
        }
        
        // Factor 2: Punctuation complexity (indicates complex sentence structure)
        int punctuationCount = quoteText.replaceAll("[^,;:()\\-]", "").length();
        if (punctuationCount > 2) {
            multiplier += PUNCTUATION_PENALTY;
        }
        
        // Factor 3: Dialogue detection
        if (containsDialogue(quoteText)) {
            multiplier += DIALOGUE_PENALTY;
        }
        
        return multiplier;
    }
    
    /**
     * Check if quote contains dialogue (quotation marks)
     */
    private boolean containsDialogue(String text) {
        return text.contains("\"") || text.contains("'") || 
               text.contains("\u201C") || text.contains("\u201D") ||
               text.contains("\u2018") || text.contains("\u2019");
    }
    
    /**
     * Get user's reading speed preference in Words Per Minute
     */
    private int getUserReadingSpeed() {
        SharedPreferences prefs = QuothPrefs.get(this);
        String readingSpeedStr = prefs.getString(QuothPrefs.PREF_READING_SPEED, String.valueOf(DEFAULT_READING_WPM));
        try {
            return Integer.parseInt(readingSpeedStr);
        } catch (NumberFormatException e) {
            return DEFAULT_READING_WPM;
        }
    }
    
    /**
     * Parse timing preference and extract delay and mode
     */
    private void parseTimingPreference() {
        SharedPreferences prefs = QuothPrefs.get(this);
        String timingPref = prefs.getString(QuothPrefs.PREF_DELAY_BETWEEN_QUOTES, "60000:fixed");
        
        // Parse format: "delay:mode" (e.g., "60000:fixed", "0:smart", "300000:hybrid")
        String[] parts = timingPref.split(":");
        if (parts.length == 2) {
            try {
                delay = Long.parseLong(parts[0]);
                // Mode will be determined dynamically in calculateNextDelay()
            } catch (NumberFormatException e) {
                delay = DEFAULT_DELAY;
            }
        } else {
            // Fallback for old preference format
            try {
                delay = Long.parseLong(timingPref);
            } catch (NumberFormatException e) {
                delay = DEFAULT_DELAY;
            }
        }
    }
    
    /**
     * Calculate next delay based on timing mode preference
     */
    private long calculateNextDelay() {
        if (DEBUG_FAST_QUOTES) {
            return DEBUG_DELAY_QUOTE; // Use fast delay for quick quote iteration testing
        }
        
        SharedPreferences prefs = QuothPrefs.get(this);
        String timingPref = prefs.getString(QuothPrefs.PREF_DELAY_BETWEEN_QUOTES, "60000:fixed");
        
        // Parse format: "delay:mode"
        String[] parts = timingPref.split(":");
        if (parts.length != 2) {
            return delay; // Fallback to basic delay
        }
        
        String mode = parts[1];
        long baseDelay;
        try {
            baseDelay = Long.parseLong(parts[0]);
        } catch (NumberFormatException e) {
            return delay; // Fallback to default
        }
        
        switch (mode) {
            case "smart":
                return calculateSmartDelay(currentQuoteText);
                
            case "hybrid":
                long smartDelay = calculateSmartDelay(currentQuoteText);
                return Math.max(smartDelay, baseDelay);
                
            case "fixed":
            default:
                return baseDelay;
        }
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
        currentQuoteText = qline; // Store for smart timing calculation

        qlineparts = qline.split(" -- ", 2);
        quoteStr = qlineparts[0];
        authStr = (qlineparts.length > 1) ? qlineparts[1] : "Unknown";

        resources = getResources();
        finalQuoteStr = resources.getString(R.string.lbl_quote_body, quoteStr);
        finalAuthStr = resources.getString(R.string.lbl_quote_author, authStr);

        // Use cached view references instead of findViewById
        TextView bodyTextView = (toShow == firstContent) ? firstContentBodyTextView : secondContentBodyTextView;
        TextView authTextView = (toShow == firstContent) ? firstContentAuthTextView : secondContentAuthTextView;
        
        if (bodyTextView != null) {
            bodyTextView.setText(finalQuoteStr);
            // Subtle text shadow for better readability
            bodyTextView.setShadowLayer(2f, 1f, 1f, 0x30000000);
        }
        if (authTextView != null) {
            authTextView.setText(finalAuthStr);
            // Slightly more subtle shadow for author
            authTextView.setShadowLayer(1f, 0.5f, 0.5f, 0x20000000);
        }
        
        // https://developer.android.com/develop/ui/views/animations/reveal-or-hide-view#CrossfadeViews
        // Initially hide the toShow view.
        toShow.setVisibility(View.GONE);
        // Set the toShow view to 0% opacity but visible, so that it is visible
        // (but fully transparent) during the animation.
        toShow.setAlpha(0f);
        toShow.setVisibility(View.VISIBLE);
        // Animate the toShow view to 100% opacity, and clear any animation
        // listener set on the view.
        toShow.animate()
                .alpha(1f)
                .setDuration(shortAnimationDuration)
                .setListener(null);
        // Animate the toHide view to 0% opacity. After the animation ends,
        // set its visibility to GONE as an optimization step (it won't
        // participate in layout passes, etc.)
        toHide.animate()
                .alpha(0f)
                .setDuration(shortAnimationDuration)
                .setListener(new AnimatorListenerAdapter() {
                    @Override
                    public void onAnimationEnd(Animator animation) {
                        super.onAnimationEnd(animation);
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
            int batteryPct = (scale > 0) ? (level * 100 / scale) : 0;

            IntentFilter ifilter = new IntentFilter(Intent.ACTION_BATTERY_CHANGED);
            Intent batteryStatus = registerReceiver(null, ifilter);
            if (batteryStatus == null) {
                Log.w("DayDreamerQuoth", "Failed to get battery status intent");
                return;
            }
            int status = batteryStatus.getIntExtra(BatteryManager.EXTRA_STATUS, -1);

            setBatteryDetails(status, batteryPct, batteryStatus);
        }
    }

    private void setBatteryDetails(int status, int batteryPct,Intent batteryStatus) {
        if (showBatteryPct && contentBatteryPctView != null) {
            String finalBatteryPct = getResources().getString(R.string.lbl_battery_pct, batteryPct);
            contentBatteryPctView.setText(finalBatteryPct);
        }
        if (showBatteryStatus && batteryStatusImageView != null && batteryChrgTypeTextView != null) {
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
        
        // Determine next delay based on timing mode and loading state
        long nextDelay;
        if (isLoadingQuotes && !isQuotesLoaded) {
            // Check every 2 seconds while loading instead of calculated delay
            nextDelay = 2000L;
            if (DEBUG) {
                Log.d("DayDreamerQuoth", "Using short delay while loading quotes");
            }
        } else if (isQuotesLoaded && pendingQuoteRequest) {
            // Failsafe: If quotes are loaded but we still have a pending request, clear it
            if (DEBUG) {
                Log.d("DayDreamerQuoth", "Failsafe: Clearing stale pending request");
            }
            pendingQuoteRequest = false;
            nextDelay = calculateNextDelay();
        } else {
            // Normal operation - use smart timing or fixed delay based on preference
            nextDelay = calculateNextDelay();
            if (DEBUG) {
                Log.d("DayDreamerQuoth", "Next delay calculated: " + nextDelay + "ms for quote: " + 
                    (currentQuoteText.length() > 50 ? currentQuoteText.substring(0, 50) + "..." : currentQuoteText));
            }
        }
        
        handler.postDelayed(showQuoteRunnable, nextDelay);
    }

    public void onAttachedToWindow() {
        super.onAttachedToWindow();
        setInteractive(true);     // Allow touch events
        setScreenBright(true);    // Keep screen bright/awake
        setFullscreen(true);
        setContentView(R.layout.dream_quotes);
        // Get cached typefaces efficiently
        SharedPreferences prefs = QuothPrefs.get(this);
        String delay_txt = prefs.getString("PREF_DELAY_BETWEEN_QUOTES", null);
        String txt_size = prefs.getString("PREF_TEXT_SIZE", null);
        String font_family = prefs.getString("PREF_FONT_FAMILY", null);
        
        // Use TypefaceManager to get cached typefaces
        TypefaceManager typefaceManager = TypefaceManager.getInstance();
        Typeface[] typefaces = typefaceManager.getTypefacePair(this, font_family);
        Typeface regularTypeface = typefaces[0];
        Typeface lightTypeface = typefaces[1];
        
        int quote_text_size = DEFAULT_BODY_TEXT_SIZE;
        int author_text_size = DEFAULT_AUTH_TEXT_SIZE;
        if (!TextUtils.isEmpty(txt_size)) {
            switch (txt_size) {
                case "0":
                    quote_text_size = TEXT_SIZE_BODY_TINY;
                    author_text_size = TEXT_SIZE_AUTHOR_TINY;
                    break;
                case "1":
                    // Variable is already assigned this value by default
                    // But still assigning here again for completeness
                    quote_text_size = TEXT_SIZE_BODY_SMALL;
                    author_text_size = TEXT_SIZE_AUTHOR_SMALL;
                    break;
                case "2":
                    quote_text_size = TEXT_SIZE_BODY_MEDIUM;
                    author_text_size = TEXT_SIZE_AUTHOR_MEDIUM;
                    break;
                case "3":
                    quote_text_size = TEXT_SIZE_BODY_LARGE;
                    author_text_size = TEXT_SIZE_AUTHOR_LARGE;
                    break;
            }
        }
        // Parse the combined timing preference format
        parseTimingPreference();

        // Cache all view references for performance
        firstContent = findViewById(R.id.quote_content_first);
        if (firstContent != null) {
            firstContentBodyTextView = firstContent.findViewById(R.id.quote_body);
            firstContentAuthTextView = firstContent.findViewById(R.id.quote_author);
            if (firstContentBodyTextView != null) {
                firstContentBodyTextView.setTypeface(regularTypeface);
                firstContentBodyTextView.setTextSize(2, quote_text_size);
            }
            if (firstContentAuthTextView != null) {
                firstContentAuthTextView.setTypeface(lightTypeface);
                firstContentAuthTextView.setTextSize(2, author_text_size);
            }
        }

        secondContent = findViewById(R.id.quote_content_second);
        if (secondContent != null) {
            secondContentBodyTextView = secondContent.findViewById(R.id.quote_body);
            secondContentAuthTextView = secondContent.findViewById(R.id.quote_author);
            if (secondContentBodyTextView != null) {
                secondContentBodyTextView.setTypeface(regularTypeface);
                secondContentBodyTextView.setTextSize(2, quote_text_size);
            }
            if (secondContentAuthTextView != null) {
                secondContentAuthTextView.setTypeface(lightTypeface);
                secondContentAuthTextView.setTextSize(2, author_text_size);
            }
        }

        contentTimeView = findViewById(R.id.time);
        if (contentTimeView != null) {
            contentTimeView.setTypeface(regularTypeface);
            contentTimeView.setTextSize(2, author_text_size - TEXT_SIZE_DIFF_AUTH_TIME);
        }

        contentDateView = findViewById(R.id.date);
        if (contentDateView != null) {
            contentDateView.setTypeface(regularTypeface);
            contentDateView.setTextSize(2, author_text_size - TEXT_SIZE_DIFF_AUTH_TIME);
        }

        contentBatteryStatusView = findViewById(R.id.batteryStatus_content);

        contentBatteryPctView = findViewById(R.id.batteryPct);
        if (contentBatteryPctView != null) {
            contentBatteryPctView.setTypeface(regularTypeface);
            contentBatteryPctView.setTextSize(2, author_text_size - 4*TEXT_SIZE_DIFF_AUTH_TIME);
        }

        if (contentBatteryStatusView != null) {
            batteryChrgTypeTextView = contentBatteryStatusView.findViewById(R.id.batteryChrgType);
            if (batteryChrgTypeTextView != null) {
                batteryChrgTypeTextView.setTypeface(regularTypeface);
                batteryChrgTypeTextView.setTextSize(2, author_text_size - 4*TEXT_SIZE_DIFF_AUTH_TIME);
            }
        }

        // Cache battery status ImageView for setBatteryDetails
        batteryStatusImageView = findViewById(R.id.batteryStatus);

        boolean showTime = prefs.getBoolean("PREF_SHOW_TIME", true);
        if (contentTimeView != null) {
            if (!showTime){
                contentTimeView.setVisibility(View.GONE);
            }
            else {
                contentTimeView.setVisibility(View.VISIBLE);
                contentTimeView.setTextColor(0XFFFFFFFF);
            }
        }
        boolean showDate = prefs.getBoolean("PREF_SHOW_DATE", true);
        if (contentDateView != null) {
            if (!showDate){
                contentDateView.setVisibility(View.GONE);
                if (showTime && contentTimeView != null) {
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
        }

        showBatteryPct = prefs.getBoolean("PREF_SHOW_BATTERY_PCT", true);
        if (contentBatteryPctView != null) {
            if (!showBatteryPct){
                contentBatteryPctView.setVisibility(View.GONE);
            }
            else {
                contentBatteryPctView.setVisibility(View.VISIBLE);
                contentBatteryPctView.setTextColor(0XFFFFFFFF);
            }
        }

        showBatteryStatus = prefs.getBoolean("PREF_SHOW_BATTERY_STATUS", true);
        if (contentBatteryStatusView != null) {
            if (!showBatteryStatus){
                contentBatteryStatusView.setVisibility(View.GONE);
            }
            else {
                contentBatteryStatusView.setVisibility(View.VISIBLE);
            }
        }

        shortAnimationDuration = DEFAULT_SWITCH_ANIM_DURATION;

        // Are we charging / charged?
        IntentFilter ifilter = new IntentFilter(Intent.ACTION_BATTERY_CHANGED);
        Intent batteryStatus = registerReceiver(null, ifilter);
        if (batteryStatus == null) {
            Log.w("DayDreamerQuoth", "Failed to get battery status intent in onAttachedToWindow");
            // Continue without battery status - not critical for app functionality
        } else {
            int status = batteryStatus.getIntExtra(BatteryManager.EXTRA_STATUS, -1);
            
            int level = batteryStatus.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
            int scale = batteryStatus.getIntExtra(BatteryManager.EXTRA_SCALE, -1);
            int batteryPct = (scale > 0) ? (level * 100 / scale) : 0;
            setBatteryDetails(status, batteryPct, batteryStatus);
        }
        batteryLevelRcvr();

        // Start loading quotes asynchronously as early as possible
        if (!isQuotesLoaded && !isLoadingQuotes) {
            if (DEBUG) {
                Log.d("DayDreamerQuoth", "Pre-loading quotes in onAttachedToWindow");
            }
            loadQuotesAsync();
            // Set pending request flag early to ensure loading completion triggers refresh
            pendingQuoteRequest = true;
        }

        // Log memory usage baseline at startup
        if (DEBUG) {
            Runtime runtime = Runtime.getRuntime();
            long usedMemory = runtime.totalMemory() - runtime.freeMemory();
            long maxMemory = runtime.maxMemory();
            Log.d("DayDreamerQuoth", String.format("Startup Memory - Used: %.1fMB / Max: %.1fMB (%.1f%%)", 
                usedMemory / 1024.0 / 1024.0,
                maxMemory / 1024.0 / 1024.0,
                (usedMemory * 100.0) / maxMemory));
        }

        showQuote();
    }

    public void onDetachedFromWindow() {
        // Clean up Handler callbacks FIRST to stop quote cycling
        if (handler != null) {
            handler.removeCallbacks(showQuoteRunnable);
            handler.removeCallbacksAndMessages(null);
        }
        
        // Clean up BroadcastReceiver safely
        try {
            if (mBatteryLevelReceiver != null) {
                this.unregisterReceiver(mBatteryLevelReceiver);
            }
        } catch (IllegalArgumentException e) {
            // Receiver was not registered - this is expected sometimes
            if (DEBUG) {
                Log.w("DayDreamerQuoth", "BroadcastReceiver was not registered", e);
            }
        }
        
        // Cancel any ongoing animations and clear listeners to prevent callbacks after detachment
        try {
            if (firstContent != null) {
                firstContent.clearAnimation();
                firstContent.animate().setListener(null).cancel();
            }
            if (secondContent != null) {
                secondContent.clearAnimation();
                secondContent.animate().setListener(null).cancel();
            }
            if (toHide != null) {
                toHide.clearAnimation();
                toHide.animate().setListener(null).cancel();
            }
        } catch (Exception e) {
            if (DEBUG) {
                Log.w("DayDreamerQuoth", "Error clearing animations", e);
            }
        }
        
        // Clean up ExecutorService
        if (quotesExecutor != null && !quotesExecutor.isShutdown()) {
            quotesExecutor.shutdown();
            quotesExecutor = null;
            if (DEBUG) {
                Log.d("DayDreamerQuoth", "ExecutorService shutdown");
            }
        }
        
        // Reset loading state
        isLoadingQuotes = false;
        pendingQuoteRequest = false;
        // Note: Keep isQuotesLoaded=true and quotes data for reuse across activations
        
        // Clear cached view references to prevent memory leaks
        firstContentBodyTextView = null;
        firstContentAuthTextView = null;
        secondContentBodyTextView = null;
        secondContentAuthTextView = null;
        contentTimeView = null;
        contentDateView = null;
        contentBatteryPctView = null;
        batteryStatusImageView = null;
        batteryChrgTypeTextView = null;
        contentBatteryStatusView = null;
        
        // Optional: Clear quotes if memory is critical (usually not needed)
        // if (quotes != null) {
        //     quotes.clear();
        //     quotes = null;
        //     isQuotesLoaded = false;
        // }
        
        // Optional: Log TypefaceManager cache status and memory usage for debugging
        if (DEBUG) {
            TypefaceManager typefaceManager = TypefaceManager.getInstance();
            Log.d("DayDreamerQuoth", "TypefaceManager cache size: " + typefaceManager.getCacheSize());
            
            // Memory usage monitoring
            Runtime runtime = Runtime.getRuntime();
            long totalMemory = runtime.totalMemory();
            long freeMemory = runtime.freeMemory();
            long usedMemory = totalMemory - freeMemory;
            long maxMemory = runtime.maxMemory();
            
            Log.d("DayDreamerQuoth", String.format("Memory - Used: %.1fMB, Free: %.1fMB, Total: %.1fMB, Max: %.1fMB", 
                usedMemory / 1024.0 / 1024.0,
                freeMemory / 1024.0 / 1024.0,
                totalMemory / 1024.0 / 1024.0,
                maxMemory / 1024.0 / 1024.0));
        }
        
        // Call super last
        super.onDetachedFromWindow();
    }
}
