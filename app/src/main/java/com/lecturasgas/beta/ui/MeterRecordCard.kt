package com.lecturasgas.beta.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.lecturasgas.beta.data.model.MeterRecord

@Composable
fun MeterRecordCard(
    record: MeterRecord,
    duplicate: Boolean,
    onClick: () -> Unit
) {
    val consumption =
        if (record.currentReading != null && record.previousReading != null) {
            record.currentReading - record.previousReading
        } else {
            null
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
                        "✓ Lectura tomada: " + record.currentReading,
                        style = MaterialTheme.typography.titleMedium
                    )

                    Text(
                        "Anterior: " + (record.previousReading ?: "—"),
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
                        "Observación: " + record.observation,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }

            if (consumption != null) {
                Text(
                    "Consumo: $consumption m³",
                    modifier = Modifier.align(Alignment.TopEnd),
                    style = MaterialTheme.typography.labelSmall
                )
            }
        }
    }
}