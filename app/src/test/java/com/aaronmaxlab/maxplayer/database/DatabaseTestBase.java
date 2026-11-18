package com.aaronmaxlab.maxplayer.database;

import org.junit.After;
import org.junit.Before;
import org.robolectric.RuntimeEnvironment;

import static org.junit.Assert.fail;

/** Shared setup for isolated SqlDbHelper tests. */
abstract class DatabaseTestBase {

    protected TestDatabaseContext context;
    protected SqlDbHelper helper;

    @Before
    public void setUpDatabase() throws Exception {
        context = new TestDatabaseContext(
                RuntimeEnvironment.getApplication(),
                getClass().getSimpleName()
        );
        helper = new SqlDbHelper(context);
        helper.getWritableDatabase();
    }

    @After
    public void tearDownDatabase() throws Exception {
        if (helper != null) {
            helper.close();
            helper = null;
        }
        if (context != null) {
            context.cleanup();
            context = null;
        }
    }

    protected interface ThrowingRunnable {
        void run() throws Exception;
    }

    protected static void assertFails(ThrowingRunnable action) {
        try {
            action.run();
            fail("Expected an exception");
        } catch (AssertionError e) {
            throw e;
        } catch (Exception expected) {
            // Expected.
        }
    }
}
