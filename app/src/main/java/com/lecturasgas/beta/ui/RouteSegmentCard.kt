package com.lecturasgas.beta.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
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

    val statusColor by
    animateColorAsState(
        targetValue =
            if (completed) {
                Color(0xFF2E7D68)
            } else {
                MaterialTheme
                    .colorScheme
                    .error
            },
        label = "statusColor"
    )

    val statusBackground by
    animateColorAsState(
        targetValue =
            if (completed) {
                Color(0xFFEAF5F1)
            } else {
                MaterialTheme
                    .colorScheme
                    .errorContainer
            },
        label = "statusBackground"
    )

    val verticalPadding by
    animateDpAsState(
        targetValue =
            if (expanded) {
                16.dp
            } else {
                14.dp
            },
        label = "verticalPadding"
    )

    Card(
        modifier =
            Modifier
                .fillMaxWidth()
                .clickable(
                    onClick = onClick
                ),
        colors =
            CardDefaults.cardColors(
                containerColor =
                    MaterialTheme
                        .colorScheme
                        .surface
            ),
        elevation =
            CardDefaults.cardElevation(
                defaultElevation =
                    if (expanded) {
                        3.dp
                    } else {
                        1.dp
                    }
            )
    ) {

        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(
                        horizontal = 16.dp,
                        vertical = verticalPadding
                    ),
            verticalAlignment =
                Alignment.CenterVertically
        ) {

            // -------------------------------------------------------------
            // INDICADOR DE ESTADO
            // -------------------------------------------------------------

            Surface(
                modifier =
                    Modifier.size(10.dp),
                shape =
                    CircleShape,
                color =
                    statusColor
            ) {}

            Spacer(
                Modifier.size(12.dp)
            )

            // -------------------------------------------------------------
            // INFORMACIÓN DEL BARRIO
            // -------------------------------------------------------------

            Column(
                modifier =
                    Modifier.weight(1f),
                verticalArrangement =
                    Arrangement.spacedBy(3.dp)
            ) {

                Text(
                    text =
                        segment.displayName,
                    style =
                        MaterialTheme
                            .typography
                            .titleMedium
                )

                Text(
                    text =
                        "${segment.total} medidores",
                    style =
                        MaterialTheme
                            .typography
                            .bodySmall,
                    color =
                        MaterialTheme
                            .colorScheme
                            .onSurfaceVariant
                )

                Row(
                    horizontalArrangement =
                        Arrangement.spacedBy(10.dp)
                ) {

                    Text(
                        text =
                            "✓ ${segment.read} leídos",
                        style =
                            MaterialTheme
                                .typography
                                .bodySmall,
                        color =
                            MaterialTheme
                                .colorScheme
                                .primary
                    )

                    Surface(
                        color =
                            statusBackground,
                        shape =
                            MaterialTheme
                                .shapes
                                .small
                    ) {

                        Text(
                            text =
                                "${segment.pending} pendientes",
                            modifier =
                                Modifier.padding(
                                    horizontal = 7.dp,
                                    vertical = 2.dp
                                ),
                            style =
                                MaterialTheme
                                    .typography
                                    .labelSmall,
                            color =
                                statusColor
                        )
                    }
                }
            }

            // -------------------------------------------------------------
            // INDICADOR DE EXPANSIÓN
            // -------------------------------------------------------------

            Text(
                text =
                    if (expanded) {
                        "⌃"
                    } else {
                        "⌄"
                    },
                style =
                    MaterialTheme
                        .typography
                        .titleLarge,
                color =
                    MaterialTheme
                        .colorScheme
                        .onSurfaceVariant
            )
        }
    }
}