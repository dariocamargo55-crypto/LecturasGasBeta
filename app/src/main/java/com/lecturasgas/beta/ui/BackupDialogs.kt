package com.lecturasgas.beta.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lecturasgas.beta.ui.theme.AppColors
import com.lecturasgas.beta.util.TimeUtil

/**
 * Todo lo de respaldo en un solo lugar: el panel principal, la confirmación antes de
 * restaurar y el aviso final (que reinicia la app).
 */
@Composable
fun BackupDialogs(
    main: MainViewModel,
    onSaveFile: () -> Unit,
    onShare: () -> Unit,
    onRestorePick: () -> Unit,
    onPickFolder: () -> Unit
) {
    val open by main.backupDialog.collectAsStateWithLifecycle()
    val status by main.backupStatus.collectAsStateWithLifecycle()
    val prompt by main.restorePrompt.collectAsStateWithLifecycle()
    val restored by main.restored.collectAsStateWithLifecycle()

    if (open) {
        AlertDialog(
            onDismissRequest = { main.closeBackup() },
            title = { Text("Respaldo y traspaso") },
            text = {
                Column(
                    Modifier.verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text("RESPALDO AUTOMÁTICO", fontWeight = FontWeight.Black, fontSize = 13.sp, color = AppColors.Muted)
                    Text(
                        if (status.lastAutoAt > 0L)
                            "Último: ${TimeUtil.dateTime(status.lastAutoAt)}"
                        else "Todavía no se ha hecho ninguno (se hace solo cuando guardas lecturas).",
                        fontSize = 14.sp
                    )
                    Text(
                        "Se guarda solo cada vez que registras lecturas o GPS. Se conservan los últimos 7 días.",
                        fontSize = 13.sp, color = AppColors.Muted
                    )
                    if (status.folderName != null) {
                        Text("Carpeta externa: ${status.folderName}", fontSize = 14.sp, fontWeight = FontWeight.Bold)
                    } else {
                        Text(
                            "Solo hay copia dentro de la app. Si desinstalas la app o pierdes el celular, esa copia se pierde. " +
                                    "Elige una carpeta (Drive, tarjeta SD, Descargas…) para estar seguro.",
                            fontSize = 13.sp, color = AppColors.Warning
                        )
                    }
                    status.lastError?.let { Text(it, fontSize = 13.sp, color = AppColors.Error) }
                    OutlinedButton(onClick = onPickFolder, modifier = Modifier.fillMaxWidth()) {
                        Text(if (status.folderName == null) "Elegir carpeta de respaldo" else "Cambiar carpeta")
                    }
                    OutlinedButton(onClick = { main.backupNow() }, modifier = Modifier.fillMaxWidth()) {
                        Text("Respaldar ahora")
                    }

                    HorizontalDivider(Modifier.padding(vertical = 4.dp))
                    Text("PASAR TODO A OTRO CELULAR", fontWeight = FontWeight.Black, fontSize = 13.sp, color = AppColors.Muted)
                    Text(
                        "1) Aquí: «Enviar respaldo» (WhatsApp, correo, Drive).\n" +
                                "2) En el otro celular: instala la app, abre esta misma pantalla y toca «Restaurar».",
                        fontSize = 13.sp
                    )
                    Button(onClick = onShare, modifier = Modifier.fillMaxWidth()) { Text("Enviar respaldo…") }
                    OutlinedButton(onClick = onSaveFile, modifier = Modifier.fillMaxWidth()) { Text("Guardar respaldo en un archivo") }
                    OutlinedButton(onClick = onRestorePick, modifier = Modifier.fillMaxWidth()) { Text("Restaurar desde un respaldo") }
                }
            },
            confirmButton = { TextButton(onClick = { main.closeBackup() }) { Text("Cerrar", fontWeight = FontWeight.Bold) } }
        )
    }

    prompt?.let { p ->
        AlertDialog(
            onDismissRequest = { main.cancelRestore() },
            title = { Text("¿Restaurar este respaldo?") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    main.backupLines(p.info).forEach { Text("• $it") }
                    Text(
                        "Esto REEMPLAZA todo lo que hay ahora en la app (lecturas, GPS, entregas y el Excel de la ruta). " +
                                "Antes se guarda una copia de lo actual por si te arrepientes.",
                        color = AppColors.Warning, fontSize = 13.sp, modifier = Modifier.padding(top = 8.dp)
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { main.confirmRestore() }) { Text("Restaurar", color = AppColors.Error, fontWeight = FontWeight.Bold) }
            },
            dismissButton = { TextButton(onClick = { main.cancelRestore() }) { Text("Cancelar") } }
        )
    }

    restored?.let { info ->
        AlertDialog(
            onDismissRequest = { },
            title = { Text("Respaldo restaurado") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    main.backupLines(info).forEach { Text("• $it") }
                    Text("La app se va a reiniciar para cargar los datos.", modifier = Modifier.padding(top = 8.dp))
                }
            },
            confirmButton = { TextButton(onClick = { main.restartApp() }) { Text("Reiniciar", fontWeight = FontWeight.Bold) } }
        )
    }
}
