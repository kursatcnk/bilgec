package com.bilgec.app

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.bilgec.app.quiz.SampleQuestions
import com.bilgec.app.tracking.TrackerService
import com.bilgec.app.ui.theme.BilGecTheme

class QuestionActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            BilGecTheme {
                QuestionOverlay(onContinue = { continueToPreviousApp() })
            }
        }
    }

    private fun continueToPreviousApp() {
        startService(
            Intent(this, TrackerService::class.java)
                .setAction(TrackerService.ACTION_CONTINUE),
        )
        finishAndRemoveTask()
    }
}

@Composable
private fun QuestionOverlay(onContinue: () -> Unit) {
    val question = SampleQuestions.phaseZero
    var selectedIndex by rememberSaveable { mutableStateOf<Int?>(null) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.55f))
            .padding(20.dp),
        contentAlignment = Alignment.Center,
    ) {
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(question.category, color = MaterialTheme.colorScheme.primary)
                Text(question.prompt, style = MaterialTheme.typography.headlineSmall)

                question.options.forEachIndexed { index, option ->
                    OutlinedButton(
                        onClick = { selectedIndex = index },
                        enabled = selectedIndex == null,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(option)
                    }
                }

                selectedIndex?.let { selected ->
                    val isCorrect = selected == question.correctIndex
                    Text(
                        if (isCorrect) "Doğru!" else "Doğru cevap: ${question.options[question.correctIndex]}",
                        style = MaterialTheme.typography.titleMedium,
                        color = if (isCorrect) Color(0xFF2E7D32) else MaterialTheme.colorScheme.error,
                    )
                    Text(question.explanation)
                    Button(
                        onClick = onContinue,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("Devam")
                    }
                }
            }
        }
    }
}

