package com.xianzhun.tuner

import android.media.AudioManager
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.lifecycle.Lifecycle
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Rule
import org.junit.Test

class TunerScreenTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    @Test fun initialStateDoesNotStartMicrophoneAndManualSelectionWorks() {
        compose.onNodeWithTag("listen_toggle").assertIsDisplayed().assertTextEquals("开始调音", "→")
        compose.onNodeWithTag("string_6").performScrollTo().performClick()
        compose.onNodeWithText("目标 82.41 Hz").performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag("mode_auto").performScrollTo().performClick()
        compose.onNodeWithText("等待识别频率").performScrollTo().assertIsDisplayed()
    }

    @Test fun helpExplainsLocalAudioProcessing() {
        compose.onNodeWithTag("help").performClick()
        compose.onNodeWithText("让每根弦，回到音准").assertIsDisplayed()
        compose.onNodeWithText("知道了").performClick()
        compose.onNodeWithTag("tuning_status").assertTextEquals("点击下方，开始调音")
    }

    @Test fun captureCanStartPauseAndSurviveBackgroundTransition() {
        val automation = InstrumentationRegistry.getInstrumentation().uiAutomation
        automation.executeShellCommand("pm grant com.xianzhun.tuner android.permission.RECORD_AUDIO").close()
        val audio = InstrumentationRegistry.getInstrumentation().targetContext.getSystemService(AudioManager::class.java)
        compose.onNodeWithTag("listen_toggle").performClick()
        compose.waitUntil(5_000) { audio.activeRecordingConfigurations.isNotEmpty() }
        compose.onNodeWithText("暂停聆听").assertIsDisplayed()
        compose.activityRule.scenario.moveToState(Lifecycle.State.CREATED)
        compose.waitUntil(5_000) { audio.activeRecordingConfigurations.isEmpty() }
        compose.activityRule.scenario.moveToState(Lifecycle.State.RESUMED)
        compose.waitUntil(5_000) { audio.activeRecordingConfigurations.isNotEmpty() }
        compose.onNodeWithText("暂停聆听").assertIsDisplayed()
        compose.onNodeWithTag("listen_toggle").performClick()
        compose.waitUntil(5_000) { audio.activeRecordingConfigurations.isEmpty() }
        compose.onNodeWithText("开始调音").assertIsDisplayed()
    }
}
