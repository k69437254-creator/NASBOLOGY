
package com.aram.nasbology

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
internal fun QuestionEditorScreen(
    questions: List<EditableQuestion>,
    onQuestionsChange: (List<EditableQuestion>) -> Unit,
    onSave: () -> Unit,
    onBack: () -> Unit
) {
    var editedQuestions by remember(questions) {
        mutableStateOf(questions)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Text(
            text = "تعديل الأسئلة",
            fontSize = 24.sp,
            style = MaterialTheme.typography.headlineSmall
        )

        Spacer(modifier = Modifier.height(16.dp))

        LazyColumn(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            itemsIndexed(editedQuestions) { index, question ->
                Card(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp)
                    ) {
                        Text("السؤال ${index + 1}")

                        OutlinedTextField(
                            value = question.question,
                            onValueChange = { value ->
                                editedQuestions = editedQuestions.toMutableList().also {
                                    it[index] = question.copy(question = value)
                                }
                            },
                            label = { Text("نص السؤال") },
                            modifier = Modifier.fillMaxWidth()
                        )

                        OutlinedTextField(
                            value = question.yesResult,
                            onValueChange = { value ->
                                editedQuestions = editedQuestions.toMutableList().also {
                                    it[index] = question.copy(yesResult = value)
                                }
                            },
                            label = { Text("نتيجة الإجابة بنعم") },
                            modifier = Modifier.fillMaxWidth()
                        )

                        OutlinedTextField(
                            value = question.noResult,
                            onValueChange = { value ->
                                editedQuestions = editedQuestions.toMutableList().also {
                                    it[index] = question.copy(noResult = value)
                                }
                            },
                            label = { Text("نتيجة الإجابة بلا") },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        Button(
            onClick = {
                onQuestionsChange(editedQuestions)
                onSave()
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("حفظ التعديلات")
        }

        OutlinedButton(
            onClick = onBack,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("رجوع")
        }
    }
}

