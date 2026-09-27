package com.lecturasgas.beta.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.lecturasgas.beta.data.model.RouteSegment

@Composable
fun RouteSegmentCard(
    segment: RouteSegment,
    expanded: Boolean,
    onClick: () -> Unit
) {

    val completed =
        segment.pending == 0

    val containerColor =
        if (completed) {
            Color(0xFFE8F5E9)
        } else {
            Color(0xFFFFEBEE)
        }

    Card(

        modifier =
            Modifier
                .fillMaxWidth()
                .clickable(
                    onClick = onClick
                ),

        colors =
            CardDefaults
                .cardColors(
                    containerColor =
                        containerColor
                )

    ) {

        Row(

            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(12.dp),

            horizontalArrangement =
                Arrangement.SpaceBetween

        ) {

            Column {

                Text(

                    segment.displayName,

                    style =
                        MaterialTheme
                            .typography
                            .titleMedium
                )

                Text(

                    "${segment.total} medidores  •  " +
                            "✓ ${segment.read} leídos  •  " +
                            "🔴 ${segment.pending} pendientes",

                    style =
                        MaterialTheme
                            .typography
                            .bodySmall
                )
            }

            Text(
                if (expanded) {
                    "▲"
                } else {
                    "▼"
                }
            )
        }
    }
}