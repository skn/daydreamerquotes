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
import java.io.InputStream;
import java.io.InputStreamReader;
import org.robolectric.shadows.ShadowLog;

@RunWith(RobolectricTestRunner.class)
public class QtDTests {

    @Before
    public void setUp() {
        ShadowLog.stream = System.out;
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
}