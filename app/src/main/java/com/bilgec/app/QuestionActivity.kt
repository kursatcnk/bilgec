package com.bilgec.app

import android.content.Intent
import android.graphics.BitmapFactory
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import com.bilgec.app.data.BilGecDatabase
import com.bilgec.app.data.QuestionRepository
import com.bilgec.app.quiz.QuizQuestion
import com.bilgec.app.settings.AppPreferences
import com.bilgec.app.tracking.TrackerService
import com.bilgec.app.ui.theme.BilGecTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class QuestionActivity : ComponentActivity() {
    private lateinit var preferences: AppPreferences
    private lateinit var repository: QuestionRepository
    private var questionState by mutableStateOf<QuizQuestion?>(null)
    private var errorState by mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        preferences = AppPreferences(this)
        repository = QuestionRepository(BilGecDatabase.get(this).questionDao())

        setContent {
            BilGecTheme(preferences.darkMode) {
                QuestionOverlay(
                    question = questionState,
                    error = errorState,
                    onAnswer = ::recordAnswer,
                    onContinue = ::continueToPreviousApp,
                )
            }
        }
        loadQuestion()
    }

    private fun loadQuestion() {
        lifecycleScope.launch {
            runCatching {
                withContext(Dispatchers.IO) {
                    val savedId = preferences.currentQuestionId
                    (if (savedId != AppPreferences.noQuestion) repository.byId(savedId) else null)
                        ?: repository.next(preferences.lastCategory).also {
                            preferences.currentQuestionId = it.id
                        }
                }
            }.onSuccess { questionState = it }
                .onFailure { errorState = "Soru yüklenemedi. BilGeç'i açıp yeniden deneyin." }
        }
    }

    private fun recordAnswer(question: QuizQuestion, selectedIndex: Int, onSaved: () -> Unit) {
        lifecycleScope.launch {
            withContext(Dispatchers.IO) {
                repository.markSeen(question)
                preferences.lastCategory = question.category
                preferences.recordAnswer(selectedIndex == question.correctIndex)
            }
            onSaved()
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
private fun QuestionOverlay(
    question: QuizQuestion?,
    error: String?,
    onAnswer: (QuizQuestion, Int, () -> Unit) -> Unit,
    onContinue: () -> Unit,
) {
    var selectedIndex by remember(question?.id) { mutableStateOf<Int?>(null) }
    var savingAnswer by remember(question?.id) { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.62f))
            .padding(20.dp),
        contentAlignment = Alignment.Center,
    ) {
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier
                    .heightIn(max = 720.dp)
                    .verticalScroll(rememberScrollState())
                    .padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                when {
                    error != null -> {
                        Text(error, color = MaterialTheme.colorScheme.error)
                        Button(onClick = onContinue, modifier = Modifier.fillMaxWidth()) {
                            Text("Kapat")
                        }
                    }

                    question == null -> CircularProgressIndicator(Modifier.align(Alignment.CenterHorizontally))
                    else -> {
                        Text(question.category, color = MaterialTheme.colorScheme.primary)
                        QuestionImage(question)
                        Text(question.prompt, style = MaterialTheme.typography.headlineSmall)

                        question.options.forEachIndexed { index, option ->
                            OutlinedButton(
                                onClick = {
                                    savingAnswer = true
                                    onAnswer(question, index) {
                                        selectedIndex = index
                                        savingAnswer = false
                                    }
                                },
                                enabled = selectedIndex == null && !savingAnswer,
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
                            Button(onClick = onContinue, modifier = Modifier.fillMaxWidth()) {
                                Text("Devam")
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun QuestionImage(question: QuizQuestion) {
    val context = LocalContext.current
    val bitmap = remember(question.imageAsset) {
        question.imageAsset?.let { path ->
            runCatching {
                context.assets.open(path).use(BitmapFactory::decodeStream)?.asImageBitmap()
            }.getOrNull()
        }
    }
    bitmap?.let {
        Image(
            bitmap = it,
            contentDescription = "Soruyla ilgili görsel",
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 180.dp),
            contentScale = ContentScale.Fit,
        )
    }
}
