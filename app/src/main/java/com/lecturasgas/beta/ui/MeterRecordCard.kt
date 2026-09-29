package com.lecturasgas.beta.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.lecturasgas.beta.data.model.MeterRecord
import com.lecturasgas.beta.viewmodel.ReadingViewModel

@Composable
fun MeterRecordCard(
    record: MeterRecord,
    duplicate: Boolean,
    onClick: () -> Unit,
    onViewMap: () -> Unit,
    vm: ReadingViewModel = viewModel()
) {
    val consumption =
        if (record.currentReading != null && record.previousReading != null) {
            record.currentReading - record.previousReading
        } else {
            null
        }

    val correction = vm.getMeterCorrection(record.rowNumber)

    var correctionExpanded by remember(record.rowNumber) {
        mutableStateOf(false)
    }

    var physicalMeterText by remember(record.rowNumber, correction?.physicalMeter) {
        mutableStateOf(correction?.physicalMeter ?: record.meter)
    }

    var noteText by remember(record.rowNumber, correction?.note) {
        mutableStateOf(correction?.note.orEmpty())
    }

    val cardColor =
        if (record.currentReading == null) {
            Color(0xFFFFF8E1)
        } else {
            Color(0xFFE3F2FD)
        }

    Card(
        modifier =
            Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = cardColor
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp)
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                Text(
                    record.meter,
                    style = MaterialTheme.typography.titleLarge
                )

                Text(
                    record.user,
                    style = MaterialTheme.typography.bodyLarge
                )

                Text(
                    record.address,
                    style = MaterialTheme.typography.bodyMedium
                )

                Spacer(Modifier.height(5.dp))

                if (record.currentReading == null) {
                    Text(
                        "🔴 Pendiente  •  Anterior: " +
                                (record.previousReading ?: "—"),
                        style = MaterialTheme.typography.bodyMedium
                    )
                } else {
                    Text(
                        "✓ Lectura tomada: " +
                                record.currentReading,
                        style = MaterialTheme.typography.titleMedium
                    )

                    Text(
                        "Anterior: " +
                                (record.previousReading ?: "—"),
                        style = MaterialTheme.typography.bodyMedium
                    )
                }

                if (duplicate) {
                    Spacer(Modifier.height(3.dp))

                    Text(
                        "⚠ Medidor repetido: este registro es independiente.",
                        style = MaterialTheme.typography.labelSmall
                    )
                }

                if (record.observation.isNotBlank()) {
                    Spacer(Modifier.height(3.dp))

                    Text(
                        "Observación: " +
                                record.observation,
                        style = MaterialTheme.typography.bodySmall
                    )
                }

                if (correction != null) {
                    Spacer(Modifier.height(4.dp))

                    Text(
                        "⚠ Medidor con corrección",
                        style = MaterialTheme.typography.labelMedium
                    )

                    Text(
                        "Excel: ${correction.excelMeter}  →  Físico: ${correction.physicalMeter}",
                        style = MaterialTheme.typography.bodySmall
                    )

                    if (correction.note.isNotBlank()) {
                        Text(
                            "Nota: ${correction.note}",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }

                Spacer(Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    Button(onClick = onViewMap) {
                        Text("Ver en mapa")
                    }
                }

                Spacer(Modifier.height(4.dp))

                OutlinedButton(
                    onClick = {
                        correctionExpanded = !correctionExpanded
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        if (correctionExpanded) {
                            "▲ Corrección y notas"
                        } else {
                            "▼ Corrección y notas"
                        }
                    )
                }

                if (correctionExpanded) {
                    Spacer(Modifier.height(4.dp))

                    Text(
                        "Medidor en Excel / base de datos",
                        style = MaterialTheme.typography.labelMedium
                    )

                    Text(
                        record.meter,
                        style = MaterialTheme.typography.bodyLarge
                    )

                    Spacer(Modifier.height(6.dp))

                    OutlinedTextField(
                        value = physicalMeterText,
                        onValueChange = { physicalMeterText = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Medidor físico") },
                        singleLine = true
                    )

                    Spacer(Modifier.height(6.dp))

                    OutlinedTextField(
                        value = noteText,
                        onValueChange = { noteText = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Descripción / nota") },
                        minLines = 2
                    )

                    Spacer(Modifier.height(8.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = {
                                vm.saveMeterCorrection(
                                    rowNumber = record.rowNumber,
                                    physicalMeter = physicalMeterText,
                                    note = noteText
                                )
                                correctionExpanded = false
                            },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Guardar cambios")
                        }

                        if (correction != null) {
                            OutlinedButton(
                                onClick = {
                                    vm.deleteMeterCorrection(record.rowNumber)
                                    physicalMeterText = record.meter
                                    noteText = ""
                                    correctionExpanded = false
                                },
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("Eliminar")
                            }
                        }
                    }
                }
            }

            Column(
                modifier = Modifier.align(Alignment.TopEnd),
                horizontalAlignment = Alignment.End
            ) {
                Text(
                    "Fila ${record.rowNumber}",
                    style = MaterialTheme.typography.labelSmall
                )

                if (consumption != null) {
                    Spacer(Modifier.height(3.dp))

                    Text(
                        "Consumo: $consumption m³",
                        style = MaterialTheme.typography.labelSmall
                    )
                }
            }
        }
    }
}
