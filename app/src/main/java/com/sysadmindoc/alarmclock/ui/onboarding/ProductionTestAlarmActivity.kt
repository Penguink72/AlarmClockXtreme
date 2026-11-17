package com.sysadmindoc.alarmclock.ui.onboarding

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.sysadmindoc.alarmclock.R
import com.sysadmindoc.alarmclock.data.readiness.ProductionTestAlarmScheduler
import com.sysadmindoc.alarmclock.data.readiness.ProductionTestSessionStore
import com.sysadmindoc.alarmclock.service.AlarmService
import com.sysadmindoc.alarmclock.ui.theme.AlarmClockXtremeTheme

/**
 * Separate from AlarmFiringActivity so a diagnostic cannot run real dismiss
 * challenges, send webhooks, alter snooze caps or record an AlarmEvent.
 */
class ProductionTestAlarmActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val testId = intent.getStringExtra(ProductionTestAlarmScheduler.EXTRA_SESSION_ID).orEmpty()
        val proof = ProductionTestSessionStore.read(this, System.currentTimeMillis())
        if (proof == null || proof.session.sessionId != testId) {
            finish()
            return
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON)
        }
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        setContent {
            AlarmClockXtremeTheme {
                ProductionTestScreen(
                    canSnooze = proof.session.nextOccurrence == 0,
                    onSnooze = { dispatch(AlarmService.ACTION_PRODUCTION_TEST_SNOOZE, testId) },
                    onDismiss = { dispatch(AlarmService.ACTION_PRODUCTION_TEST_DISMISS, testId) }
                )
            }
        }
    }

    private fun dispatch(actionName: String, testId: String) {
        val proof = ProductionTestSessionStore.read(this, System.currentTimeMillis()) ?: run {
            finish()
            return
        }
        if (proof.session.sessionId != testId) {
            finish()
            return
        }
        val intent = Intent(this, AlarmService::class.java).apply {
            action = actionName
            putExtra(AlarmService.EXTRA_PRODUCTION_TEST_SESSION_ID, testId)
            putExtra(ProductionTestAlarmScheduler.EXTRA_TEST_FIRE_ID, proof.session.fireId())
        }
        startService(intent)
        finish()
    }
}

@Composable
private fun ProductionTestScreen(canSnooze: Boolean, onSnooze: () -> Unit, onDismiss: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(stringResource(R.string.b2_test_title))
        Text(
            stringResource(R.string.b2_test_description),
            modifier = Modifier.padding(vertical = 20.dp)
        )
        if (canSnooze) {
            OutlinedButton(onClick = onSnooze, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.b2_test_snooze))
            }
        }
        Button(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.b2_test_dismiss))
        }
    }
}
