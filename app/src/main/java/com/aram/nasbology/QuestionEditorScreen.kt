
package com.aram.nasbology

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aram.nasbology.engine.EditableQuestion

@Composable
internal fun QuestionEditorScreen(
    questions: List<EditableQuestion>,
    onQuestionsChange: (List<EditableQuestion>) -> Unit,
    onSave: () -> Unit,
    onBack: () -> Unit
) {
    var editedQuestions by remember {
        mutableStateOf(questions)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Text(
            text = "تعديل الأسئلة",
            fontSize = 24.sp
        )

        Spacer(Modifier.height(12.dp))

        LazyColumn(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(
                items = editedQuestions,
                key = { it.number }
            ) { question ->

                Card(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text("السؤال ${question.number}")

                        OutlinedTextField(
                            value = question.prompt,
                            onValueChange = { value ->
                                editedQuestions = editedQuestions.map {
                                    if (it.number == question.number)
                                        it.copy(prompt = value)
                                    else it
                                }
                            },
                            label = { Text("نص السؤال") },
                            modifier = Modifier.fillMaxWidth()
                        )

                        OutlinedTextField(
                            value = question.yesConclusion,
                            onValueChange = { value ->
                                editedQuestions = editedQuestions.map {
                                    if (it.number == question.number)
                                        it.copy(yesConclusion = value)
                                    else it
                                }
                            },
                            label = { Text("نتيجة نعم") },
                            modifier = Modifier.fillMaxWidth()
                        )

                        OutlinedTextField(
                            value = question.noConclusion,
                            onValueChange = { value ->
                                editedQuestions = editedQuestions.map {
                                    if (it.number == question.number)
                                        it.copy(noConclusion = value)
                                    else it
                                }
                            },
                            label = { Text("نتيجة لا") },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(12.dp))

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
