package com.nearer.app

import android.view.View
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MainActivityLaunchTest {
    @Test
    fun mainActivityLaunchesAndCreatesContentView() {
        val scenario = ActivityScenario.launch(MainActivity::class.java)
        try {
            InstrumentationRegistry.getInstrumentation().waitForIdleSync()
            scenario.onActivity { activity ->
                assertFalse("MainActivity unexpectedly finished during startup", activity.isFinishing)
                val content = activity.findViewById<View>(android.R.id.content)
                assertNotNull("Android content root was not created", content)
                assertNotNull("Activity decor view was not created", activity.window?.decorView)
            }
        } finally {
            scenario.close()
        }
    }
}
