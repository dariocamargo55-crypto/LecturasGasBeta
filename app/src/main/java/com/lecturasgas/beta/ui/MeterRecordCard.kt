package com.lecturasgas.beta.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.lecturasgas.beta.data.model.MeterRecord

@Composable
fun MeterRecordCard(
    record: MeterRecord,
    duplicate: Boolean,
    onClick: () -> Unit
) {

    Card(

        modifier =
            Modifier
                .fillMaxWidth()
                .clickable(
                    onClick = onClick
                )

    ) {

        Column(
            Modifier.padding(12.dp)
        ) {

            Text(

                record.meter,

                style =
                    MaterialTheme
                        .typography
                        .titleMedium
            )

            Text(
                record.user
            )

            Text(
                record.address
            )

            Spacer(
                Modifier.height(4.dp)
            )

            Text(

                if (
                    record.currentReading == null
                ) {

                    "🔴 Pendiente  •  Anterior: " +
                            (
                                    record.previousReading
                                        ?: "—"
                                    )

                } else {

                    "✓ Lectura ya leída: " +
                            record.currentReading
                }
            )

            if (
                duplicate
            ) {

                Spacer(
                    Modifier.height(4.dp)
                )

                Text(

                    "⚠ Medidor repetido: " +
                            "este registro es independiente.",

                    style =
                        MaterialTheme
                            .typography
                            .labelSmall
                )
            }

            if (
                record.observation
                    .isNotBlank()
            ) {

                Spacer(
                    Modifier.height(4.dp)
                )

                Text(

                    "Observación: " +
                            record.observation
                )
            }
        }
    }
}