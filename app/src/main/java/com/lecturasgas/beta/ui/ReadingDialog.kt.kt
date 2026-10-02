package com.lecturasgas.beta.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.lecturasgas.beta.data.model.MeterRecord
import com.lecturasgas.beta.viewmodel.ReadingViewModel

@Composable
fun ReadingDialog(
    record: MeterRecord,
    vm: ReadingViewModel,
    onDismiss: () -> Unit
) {
    val alreadyRead =
        record.currentReading != null

    var editing by remember(record.rowNumber) {
        mutableStateOf(false)
    }

    var reading by remember(
        record.rowNumber,
        record.currentReading
    ) {
        mutableStateOf(
            record.currentReading?.toString().orEmpty()
        )
    }

    var showEditConfirmation by remember(record.rowNumber) {
        mutableStateOf(false)
    }

    val currentValue =
        reading.toLongOrNull()

    val previousValue =
        record.previousReading

    val consumption =
        if (
            currentValue != null &&
            previousValue != null &&
            currentValue >= previousValue
        ) {
            currentValue - previousValue
        } else {
            null
        }

    val readingIsLower =
        currentValue != null &&
                previousValue != null &&
                currentValue < previousValue

    AlertDialog(
        onDismissRequest = {
            onDismiss()
        },

        title = {
            Column {
                Text(
                    "TOMA DE LECTURA",
                    style = MaterialTheme.typography.titleMedium
                )

                Spacer(Modifier.height(4.dp))

                Text(
                    record.meter,
                    style = MaterialTheme.typography.headlineSmall
                )
            }
        },

        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text("Usuario: ${record.user}")
                Text("Dirección: ${record.address}")
                Text("Barrio: ${record.neighborhood}")

                Spacer(Modifier.height(4.dp))

                Text(
                    "LECTURA ANTERIOR",
                    style = MaterialTheme.typography.labelMedium
                )

                Text(
                    previousValue?.toString() ?: "—",
                    style = MaterialTheme.typography.headlineSmall
                )

                if (record.observation.isNotBlank()) {
                    Spacer(Modifier.height(4.dp))

                    Text(
                        "Observación: " +
                                record.observation
                    )
                }

                if (alreadyRead && !editing) {
                    Spacer(Modifier.height(4.dp))

                    Text(
                        "✓ LECTURA YA LEÍDA",
                        style = MaterialTheme.typography.titleMedium
                    )

                    Text(
                        "Lectura registrada: " +
                                record.currentReading
                    )
                } else {
                    Spacer(Modifier.height(4.dp))

                    OutlinedTextField(
                        value = reading,
                        onValueChange = {
                            reading = it.filter(Char::isDigit)
                        },
                        modifier = Modifier.fillMaxWidth(),
                        label = {
                            Text(
                                if (editing) {
                                    "Nueva lectura"
                                } else {
                                    "Lectura actual"
                                }
                            )
                        },
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Number
                        ),
                        singleLine = true
                    )

                    if (readingIsLower) {
                        Spacer(Modifier.height(4.dp))

                        Text(
                            "⚠ La lectura actual es menor que la lectura anterior.",
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    } else if (consumption != null) {
                        Spacer(Modifier.height(4.dp))

                        Text(
                            "Consumo: $consumption",
                            style = MaterialTheme.typography.titleMedium
                        )
                    }
                }
            }
        },

        confirmButton = {
            if (!alreadyRead) {
                Button(
                    enabled =
                        reading.isNotBlank() &&
                                !readingIsLower,
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
                    Text("GUARDAR LECTURA")
                }
            } else if (editing) {
                Button(
                    enabled =
                        reading.isNotBlank() &&
                                !readingIsLower,
                    onClick = {
                        showEditConfirmation = true
                    }
                ) {
                    Text("CONFIRMAR EDICIÓN")
                }
            } else {
                Button(
                    onClick = {
                        editing = true
                        reading =
                            record.currentReading
                                ?.toString()
                                .orEmpty()
                    }
                ) {
                    Text("EDITAR LECTURA")
                }
            }
        },

        dismissButton = {
            TextButton(
                onClick = {
                    onDismiss()
                }
            ) {
                Text("Cerrar")
            }
        }
    )

    if (showEditConfirmation) {
        AlertDialog(
            onDismissRequest = {
                showEditConfirmation = false
            },

            title = {
                Text("Confirmar edición")
            },

            text = {
                Column(
                    verticalArrangement =
                        Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        "Vas a modificar la lectura registrada."
                    )

                    Text(
                        "Lectura anterior: " +
                                record.currentReading
                    )

                    Text(
                        "Nueva lectura: " +
                                reading
                    )

                    Text(
                        "¿Deseas guardar este cambio?"
                    )
                }
            },

            confirmButton = {
                Button(
                    onClick = {
                        if (
                            vm.editReading(
                                record.rowNumber,
                                reading
                            )
                        ) {
                            showEditConfirmation = false
                            onDismiss()
                        }
                    }
                ) {
                    Text("CONFIRMAR")
                }
            },

            dismissButton = {
                TextButton(
                    onClick = {
                        showEditConfirmation = false
                    }
                ) {
                    Text("CANCELAR")
                }
            }
        )
    }
}