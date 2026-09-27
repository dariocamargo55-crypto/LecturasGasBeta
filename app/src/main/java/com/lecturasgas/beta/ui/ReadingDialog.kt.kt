package com.lecturasgas.beta.ui


import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import com.lecturasgas.beta.data.model.MeterRecord
import com.lecturasgas.beta.viewmodel.ReadingViewModel

@Composable
fun ReadingDialog(
    record: MeterRecord,
    vm: ReadingViewModel,
    onDismiss: () -> Unit
) {
    var reading by remember(
        record.rowNumber
    ) {
        mutableStateOf("")
    }

    val alreadyRead =
        record.currentReading != null

    AlertDialog(
        onDismissRequest = {
            onDismiss()
        },

        title = {
            Text(
                record.meter
            )
        },

        text = {
            Column(
                verticalArrangement =
                    Arrangement.spacedBy(
                        6.dp
                    )
            ) {

                Text(
                    "Usuario: ${record.user}"
                )

                Text(
                    "Dirección: ${record.address}"
                )

                Text(
                    "Barrio: ${record.neighborhood}"
                )

                Text(
                    "Lectura anterior: " +
                            (
                                    record.previousReading
                                        ?: "—"
                                    )
                )

                if (
                    record.observation
                        .isNotBlank()
                ) {

                    Text(
                        "Observación: " +
                                record.observation
                    )
                }

                if (
                    alreadyRead
                ) {

                    Text(
                        "✓ LECTURA YA LEÍDA",
                        style =
                            MaterialTheme
                                .typography
                                .titleMedium
                    )

                    Text(
                        "Lectura registrada: " +
                                record.currentReading
                    )

                } else {

                    OutlinedTextField(

                        value =
                            reading,

                        onValueChange = {

                            reading =
                                it.filter(
                                    Char::isDigit
                                )
                        },

                        label = {

                            Text(
                                "Lectura actual"
                            )
                        },

                        keyboardOptions =
                            KeyboardOptions(
                                keyboardType =
                                    KeyboardType.Number
                            ),

                        singleLine = true
                    )
                }
            }
        },

        confirmButton = {

            if (!alreadyRead) {

                Button(

                    enabled =
                        reading.isNotBlank(),

                    onClick = {

                        if (
                            vm.saveReading(
                                record.rowNumber,
                                reading
                            )
                        ) {

                            onDismiss()
                        }
                    }

                ) {

                    Text(
                        "Guardar lectura"
                    )
                }
            }
        },

        dismissButton = {

            TextButton(

                onClick = {
                    onDismiss()
                }

            ) {

                Text(
                    "Cerrar"
                )
            }
        }
    )
}

