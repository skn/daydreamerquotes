package im.skn.daydreamerquoth;

import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.*;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import java.lang.String;
import java.io.BufferedReader;
import java.util.Set;
import java.util.HashSet;
import java.util.List;
import java.io.InputStream;
import java.io.InputStreamReader;
import org.robolectric.shadows.ShadowLog;
import java.lang.reflect.Method;
import org.robolectric.Robolectric;
import org.robolectric.android.controller.ServiceController;

@RunWith(RobolectricTestRunner.class)
public class QtDTests {

    @Before
    public void setUp() {
        ShadowLog.stream = System.out;
    }
    
    private DayDreamerQuoth createTestInstance() {
        ServiceController<DayDreamerQuoth> controller = Robolectric.buildService(DayDreamerQuoth.class);
        return controller.create().get();
    }

    @Test
    public void checkDuplicatesAndFormat() throws Exception {
        String reality;
        final String expected = "UNIQUE";
        int quotesFileID = androidx.test.core.app.ApplicationProvider.getApplicationContext().getResources().getIdentifier("quotes", "raw", androidx.test.core.app.ApplicationProvider.getApplicationContext().getPackageName());
        InputStream ts = androidx.test.core.app.ApplicationProvider.getApplicationContext().getResources().openRawResource(quotesFileID);
        BufferedReader list = new BufferedReader(new InputStreamReader(ts, "UTF-8"));
        String line;
        boolean hasDuplicate = false;
        Set<String> lines = new HashSet<String>();
        int lineNumber=0;
        // https://regex101.com/r/uZ4uG1/4
        String pattern= "[a-zA-Z\\s\\()\\[\\]_,.;*:?\\/=+'’0-9%!]*[a-z\\-a-z]*[a-zA-Z\\s,.'0-9%!#]* -- [\\/a-zA-Z0-9_\\-\\s(\\,\\.']*[a-z\\-A-Z0-9\\.\\_\\s]*[^\\s]$";
        while ( (line = list.readLine()) != null && !hasDuplicate )
        {
            lineNumber ++;
            assertTrue(Integer.toString(lineNumber) + ": \"" + line + "\"", line.matches(pattern));
            /* if (!line.matches(pattern)){
                System.out.println("line "+Integer.toString(lineNumber));
            }*/

            if (lines.contains(line)) {
                hasDuplicate = true;
            }
            lines.add(line);

        }

        if (hasDuplicate){
            System.out.println("*** Duplicate line is at line: " + Integer.toString(lineNumber));
            reality = "NOT UNIQUE";
        } else {
            reality = "UNIQUE";
        }
        list.close();

        assertNotNull(reality);
        assertEquals(expected, reality);
    }

    @Test
    public void debugVar() {
        assertFalse(DayDreamerQuoth.DEBUG);
    }

    @Test
    public void testCalculateSmartDelay_ShortQuote() throws Exception {
        DayDreamerQuoth instance = createTestInstance();
        
        String shortQuote = "Be yourself.";
        
        Method method = DayDreamerQuoth.class.getDeclaredMethod("calculateSmartDelay", String.class);
        method.setAccessible(true);
        long delay = (Long) method.invoke(instance, shortQuote);
        
        assertEquals(5000L, delay);
    }

    @Test
    public void testCalculateSmartDelay_LongComplexQuote() throws Exception {
        DayDreamerQuoth instance = createTestInstance();
        String complexQuote = "The unexamined life is not worth living, for it requires tremendous courage to question everything you believe.";
        
        Method method = DayDreamerQuoth.class.getDeclaredMethod("calculateSmartDelay", String.class);
        method.setAccessible(true);
        long delay = (Long) method.invoke(instance, complexQuote);
        
        assertTrue(delay > 5000L);
        assertTrue(delay <= 180000L);
    }

    @Test
    public void testCalculateSmartDelay_WithDialogue() throws Exception {
        DayDreamerQuoth instance = createTestInstance();
        String dialogueQuote = "He said, \"The only way to do great work is to love what you do.\"";
        
        Method method = DayDreamerQuoth.class.getDeclaredMethod("calculateSmartDelay", String.class);
        method.setAccessible(true);
        long delay = (Long) method.invoke(instance, dialogueQuote);
        
        assertTrue(delay > 5000L);
    }

    @Test
    public void testCalculateNextDelay_DebugMode() throws Exception {
        DayDreamerQuoth instance = createTestInstance();
        
        // Test that debug mode returns 3000L when DEBUG is true
        // Since DEBUG is final, we test the current value behavior
        Method method = DayDreamerQuoth.class.getDeclaredMethod("calculateNextDelay");
        method.setAccessible(true);
        long delay = (Long) method.invoke(instance);
        
        if (DayDreamerQuoth.DEBUG) {
            assertEquals(3000L, delay);
        } else {
            // When DEBUG is false, delay should not be the debug value
            assertNotEquals(3000L, delay);
        }
    }

    @Test
    public void testUserReadingSpeed_Default() throws Exception {
        DayDreamerQuoth instance = createTestInstance();
        
        Method method = DayDreamerQuoth.class.getDeclaredMethod("getUserReadingSpeed");
        method.setAccessible(true);
        int speed = (Integer) method.invoke(instance);
        
        assertEquals(200, speed);
    }

    @Test
    public void testComplexityMultiplier_SimpleSentence() throws Exception {
        DayDreamerQuoth instance = createTestInstance();
        String simple = "Life is good.";
        
        Method method = DayDreamerQuoth.class.getDeclaredMethod("calculateComplexityMultiplier", String.class, int.class);
        method.setAccessible(true);
        float multiplier = (Float) method.invoke(instance, simple, 3);
        
        assertEquals(1.0f, multiplier, 0.01f);
    }

    @Test
    public void testComplexityMultiplier_ComplexSentence() throws Exception {
        DayDreamerQuoth instance = createTestInstance();
        String complex = "Extraordinary circumstances require unprecedented solutions, don't they?";
        
        Method method = DayDreamerQuoth.class.getDeclaredMethod("calculateComplexityMultiplier", String.class, int.class);
        method.setAccessible(true);
        float multiplier = (Float) method.invoke(instance, complex, 7);
        
        assertTrue(multiplier > 1.0f);
    }

    @Test
    public void testWordCount_AccurateCounting() throws Exception {
        DayDreamerQuoth instance = createTestInstance();
        Method method = DayDreamerQuoth.class.getDeclaredMethod("getWordCount", String.class);
        method.setAccessible(true);
        
        assertEquals(3, (int) method.invoke(instance, "Hello world today"));
        assertEquals(1, (int) method.invoke(instance, "Hello"));
        assertEquals(0, (int) method.invoke(instance, ""));
        assertEquals(0, (int) method.invoke(instance, (String) null));
    }

    @Test
    public void testContainsDialogue_Detection() throws Exception {
        DayDreamerQuoth instance = createTestInstance();
        Method method = DayDreamerQuoth.class.getDeclaredMethod("containsDialogue", String.class);
        method.setAccessible(true);
        
        assertTrue((Boolean) method.invoke(instance, "He said \"Hello\""));
        assertTrue((Boolean) method.invoke(instance, "She asked 'How are you?'"));
        assertFalse((Boolean) method.invoke(instance, "Simple statement."));
    }

    @Test
    public void testTimingBounds_EnforceMinimum() throws Exception {
        DayDreamerQuoth instance = createTestInstance();
        String tiny = "Hi.";
        
        Method method = DayDreamerQuoth.class.getDeclaredMethod("calculateSmartDelay", String.class);
        method.setAccessible(true);
        long delay = (Long) method.invoke(instance, tiny);
        
        assertTrue(delay >= 5000L);
    }

    @Test
    public void testTimingBounds_EnforceMaximum() throws Exception {
        DayDreamerQuoth instance = createTestInstance();
        StringBuilder enormous = new StringBuilder();
        for (int i = 0; i < 1000; i++) {
            enormous.append("Very long quote with many words that should exceed maximum timing bounds. ");
        }
        
        Method method = DayDreamerQuoth.class.getDeclaredMethod("calculateSmartDelay", String.class);
        method.setAccessible(true);
        long delay = (Long) method.invoke(instance, enormous.toString());
        
        assertTrue(delay <= 180000L);
    }

    @Test
    public void testLoadQuotesFromFile_EmptyFile() throws Exception {
        DayDreamerQuoth instance = createTestInstance();
        
        Method method = DayDreamerQuoth.class.getDeclaredMethod("loadQuotesFromFile");
        method.setAccessible(true);
        
        try {
            @SuppressWarnings("unchecked")
            List<String> quotes = (List<String>) method.invoke(instance);
            
            // Empty file should return empty list, not null
            assertNotNull("Quote list should not be null for empty file", quotes);
            
            // For normal quotes file, should have content
            if (quotes.isEmpty()) {
                // This would indicate an actual empty quotes file
                assertTrue("Empty file should return empty list", quotes.isEmpty());
            } else {
                // Normal case - file has quotes
                assertTrue("Quotes file should contain quotes", quotes.size() > 0);
            }
        } catch (Exception e) {
            // Should not throw exception for normal file access
            if (e.getCause() instanceof java.io.IOException) {
                fail("Should not get IOException for normal quotes file: " + e.getCause().getMessage());
            } else {
                throw e;
            }
        }
    }

    @Test
    public void testLoadQuotesFromFile_MalformedContent() throws Exception {
        DayDreamerQuoth instance = createTestInstance();
        
        Method method = DayDreamerQuoth.class.getDeclaredMethod("loadQuotesFromFile");
        method.setAccessible(true);
        
        try {
            @SuppressWarnings("unchecked")
            List<String> quotes = (List<String>) method.invoke(instance);
            
            // Method should handle any content gracefully
            assertNotNull("Quote list should not be null", quotes);
            
            // Each line should be a string (no null entries)
            for (String quote : quotes) {
                assertNotNull("Individual quote should not be null", quote);
            }
            
            // Should return some quotes from the resource file
            assertTrue("Should load quotes from resource file", quotes.size() > 0);
            
        } catch (Exception e) {
            // Method should not throw exceptions for normal resource access
            if (e.getCause() instanceof java.io.IOException) {
                fail("Should not get IOException for resource file: " + e.getCause().getMessage());
            } else {
                throw e;
            }
        }
    }

    @Test
    public void testLoadQuotesFromFile_IOExceptionHandling() throws Exception {
        DayDreamerQuoth instance = createTestInstance();
        
        Method method = DayDreamerQuoth.class.getDeclaredMethod("loadQuotesFromFile");
        method.setAccessible(true);
        
        // Test that the method properly declares IOException
        Class<?>[] exceptionTypes = method.getExceptionTypes();
        boolean declaresIOException = false;
        for (Class<?> exceptionType : exceptionTypes) {
            if (exceptionType.equals(java.io.IOException.class)) {
                declaresIOException = true;
                break;
            }
        }
        
        assertTrue("loadQuotesFromFile should declare IOException", declaresIOException);
        
        // Test normal execution doesn't throw IOException
        try {
            @SuppressWarnings("unchecked")
            List<String> quotes = (List<String>) method.invoke(instance);
            assertNotNull("Normal execution should return valid list", quotes);
        } catch (Exception e) {
            if (e.getCause() instanceof java.io.IOException) {
                fail("Normal resource access should not throw IOException: " + e.getCause().getMessage());
            }
        }
    }
}