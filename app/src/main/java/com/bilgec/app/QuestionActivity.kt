package com.bilgec.app

import android.content.Intent
import android.graphics.BitmapFactory
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Key
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.lifecycleScope
import com.bilgec.app.data.BilGecDatabase
import com.bilgec.app.data.QuestionRepository
import com.bilgec.app.quiz.QuizQuestion
import com.bilgec.app.settings.AppPreferences
import com.bilgec.app.tracking.TrackerService
import com.bilgec.app.ui.theme.BilGecInk
import com.bilgec.app.ui.theme.BilGecRed
import com.bilgec.app.ui.theme.BilGecSuccess
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
                    // Servisin hazırladığı soru varsa onu aç, yoksa yeni bir tane seç.
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
        // Sayaç sıfırlanıyor, bu ekran kapanınca kullanıcı kaldığı yere dönüyor.
        startService(Intent(this, TrackerService::class.java).setAction(TrackerService.ACTION_CONTINUE))
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
    val progress by animateFloatAsState(
        targetValue = if (selectedIndex == null) 0.52f else 1f,
        label = "questionProgress",
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.76f))
            .padding(horizontal = 14.dp, vertical = 22.dp),
        contentAlignment = Alignment.Center,
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 780.dp)
                .animateContentSize(),
            shape = RoundedCornerShape(34.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.background),
            elevation = CardDefaults.cardElevation(defaultElevation = 18.dp),
        ) {
            Column(
                modifier = Modifier
                    .verticalScroll(rememberScrollState())
                    .padding(22.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                QuestionBrandBar()
                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier.fillMaxWidth().height(6.dp).clip(CircleShape),
                    color = BilGecRed,
                    trackColor = MaterialTheme.colorScheme.surfaceVariant,
                )

                when {
                    error != null -> ErrorContent(error = error, onContinue = onContinue)
                    question == null -> LoadingContent()
                    else -> {
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.primaryContainer,
                            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                        ) {
                            Text(
                                question.category.uppercase(),
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                                style = MaterialTheme.typography.labelMedium,
                            )
                        }
                        QuestionImage(question)
                        Text(question.prompt, style = MaterialTheme.typography.headlineMedium)
                        Text(
                            "Doğru seçeneği işaretle ve kaldığın yere dön.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )

                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            question.options.forEachIndexed { index, option ->
                                AnswerOption(
                                    index = index,
                                    text = option,
                                    selectedIndex = selectedIndex,
                                    correctIndex = question.correctIndex,
                                    enabled = selectedIndex == null && !savingAnswer,
                                    onClick = {
                                        savingAnswer = true
                                        onAnswer(question, index) {
                                            selectedIndex = index
                                            savingAnswer = false
                                        }
                                    },
                                )
                            }
                        }

                        if (savingAnswer) {
                            CircularProgressIndicator(
                                modifier = Modifier.align(Alignment.CenterHorizontally).size(28.dp),
                                color = BilGecRed,
                                strokeWidth = 3.dp,
                            )
                        }

                        selectedIndex?.let { selected ->
                            ResultPanel(
                                isCorrect = selected == question.correctIndex,
                                correctAnswer = question.options[question.correctIndex],
                                explanation = question.explanation,
                            )
                            Button(
                                onClick = onContinue,
                                modifier = Modifier.fillMaxWidth().height(56.dp),
                                shape = RoundedCornerShape(18.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = BilGecInk,
                                    contentColor = Color.White,
                                ),
                            ) {
                                Text("Uygulamaya devam et")
                                Spacer(Modifier.size(8.dp))
                                Icon(Icons.AutoMirrored.Filled.ArrowForward, null)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun QuestionBrandBar() {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier.size(38.dp).clip(RoundedCornerShape(13.dp)).background(BilGecRed),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Default.Key, null, tint = Color.White, modifier = Modifier.size(21.dp))
            }
            Column {
                Text("BilGeç", style = MaterialTheme.typography.titleMedium)
                Text("BİLGİ MOLASI", style = MaterialTheme.typography.labelMedium, color = BilGecRed)
            }
        }
        Text("1 / 1", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun AnswerOption(
    index: Int,
    text: String,
    selectedIndex: Int?,
    correctIndex: Int,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    val revealed = selectedIndex != null
    val isCorrect = revealed && index == correctIndex
    val isWrongSelection = revealed && index == selectedIndex && index != correctIndex
    val container = when {
        isCorrect -> Color(0xFFDDF5E9)
        isWrongSelection -> Color(0xFFFFE1DE)
        else -> MaterialTheme.colorScheme.surface
    }
    val border = when {
        isCorrect -> BilGecSuccess
        isWrongSelection -> MaterialTheme.colorScheme.error
        else -> MaterialTheme.colorScheme.outlineVariant
    }

    Surface(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        color = container,
        border = BorderStroke(if (isCorrect || isWrongSelection) 2.dp else 1.dp, border),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 15.dp, vertical = 14.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Surface(
                shape = CircleShape,
                color = when {
                    isCorrect -> BilGecSuccess
                    isWrongSelection -> MaterialTheme.colorScheme.error
                    else -> MaterialTheme.colorScheme.surfaceVariant
                },
                contentColor = if (isCorrect || isWrongSelection) Color.White else MaterialTheme.colorScheme.onSurface,
            ) {
                Box(Modifier.size(34.dp), contentAlignment = Alignment.Center) {
                    Text(('A'.code + index).toChar().toString(), fontWeight = FontWeight.Bold)
                }
            }
            Text(text, modifier = Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
            if (isCorrect) Icon(Icons.Default.Check, null, tint = BilGecSuccess)
            if (isWrongSelection) Icon(Icons.Default.Close, null, tint = MaterialTheme.colorScheme.error)
        }
    }
}

@Composable
private fun ResultPanel(isCorrect: Boolean, correctAnswer: String, explanation: String) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        color = if (isCorrect) Color(0xFFDDF5E9) else Color(0xFFFFE1DE),
    ) {
        Column(Modifier.padding(17.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
            Text(
                if (isCorrect) "Harika, doğru!" else "Doğru cevap: $correctAnswer",
                style = MaterialTheme.typography.titleLarge,
                color = if (isCorrect) Color(0xFF075E40) else Color(0xFF8C1111),
            )
            Text(explanation, color = BilGecInk, style = MaterialTheme.typography.bodyLarge)
        }
    }
}

@Composable
private fun LoadingContent() {
    Column(
        modifier = Modifier.fillMaxWidth().padding(vertical = 56.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        CircularProgressIndicator(color = BilGecRed)
        Text("Sıradaki bilgi hazırlanıyor…", color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun ErrorContent(error: String, onContinue: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(vertical = 30.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        Text("Bir şey ters gitti", style = MaterialTheme.typography.headlineMedium, textAlign = TextAlign.Center)
        Text(error, textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.error)
        Button(onClick = onContinue, modifier = Modifier.fillMaxWidth()) { Text("Kapat") }
    }
}

@Composable
private fun QuestionImage(question: QuizQuestion) {
    val context = LocalContext.current
    val bitmap = remember(question.imageAsset) {
        question.imageAsset?.let { path ->
            runCatching { context.assets.open(path).use(BitmapFactory::decodeStream)?.asImageBitmap() }.getOrNull()
        }
    }
    bitmap?.let {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(24.dp),
            color = Color.White,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        ) {
            Image(
                bitmap = it,
                contentDescription = "Soruyla ilgili görsel",
                modifier = Modifier.fillMaxWidth().heightIn(max = 190.dp).padding(14.dp),
                contentScale = ContentScale.Fit,
            )
        }
    }
}
