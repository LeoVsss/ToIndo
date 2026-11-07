package br.com.toindo.prototipo

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import br.com.toindo.MainActivity
import br.com.toindo.R
import kotlinx.coroutines.flow.MutableStateFlow

/** Pedido de navegação vindo de uma notificação: abrir a tela de detalhe desta saída. */
object PedidoAbrirSaida {
    const val EXTRA = "prototipo_saida_id"
    val pendente = MutableStateFlow<Long?>(null)
}

object NotificacaoPrototipo {
    private const val CANAL = "prototipo_saidas"
    private const val ID_SAIDA = 1001
    private const val ID_VOLTA = 1002

    /** Notifica "Dispositivos desligados" ao sair do radar. Tocar abre o detalhe da saída. */
    fun saida(context: Context, saidaId: Long, desligados: List<String>, mantidos: List<String>, offline: List<String>) {
        val titulo = if (desligados.isEmpty()) "Você saiu de casa" else "Dispositivos desligados"
        val resumo = if (desligados.isEmpty()) "Nenhum dispositivo precisou ser desligado."
        else desligados.joinToString(", ")
        val detalhe = buildString {
            append(if (desligados.isEmpty()) "Nenhum dispositivo desligado." else "Desligados: ${desligados.joinToString(", ")}.")
            if (mantidos.isNotEmpty()) append("\nContinuam ligados: ${mantidos.joinToString(", ")}.")
            if (offline.isNotEmpty()) append("\nOffline: ${offline.joinToString(", ")}.")
            append("\nToque para ver os detalhes.")
        }
        enviar(context, ID_SAIDA, saidaId, titulo, resumo, detalhe)
    }

    /** Notifica os dispositivos que ligaram sozinhos ao chegar. */
    fun volta(context: Context, saidaId: Long, ligados: List<String>) {
        if (ligados.isEmpty()) return
        enviar(
            context, ID_VOLTA, saidaId, "Dispositivos ligados", ligados.joinToString(", "),
            "Ligados ao chegar: ${ligados.joinToString(", ")}.\nToque para ver os detalhes.",
        )
    }

    private fun enviar(context: Context, notifId: Int, saidaId: Long, titulo: String, resumo: String, detalhe: String) {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) return

        val gerenciador = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            gerenciador.createNotificationChannel(
                NotificationChannel(CANAL, "Saídas e chegadas", NotificationManager.IMPORTANCE_HIGH)
            )
        }
        val intent = Intent(context, MainActivity::class.java).apply {
            putExtra(PedidoAbrirSaida.EXTRA, saidaId)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pi = PendingIntent.getActivity(
            context, notifId, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val n = NotificationCompat.Builder(context, CANAL)
            .setSmallIcon(R.drawable.ic_logo)
            .setContentTitle(titulo)
            .setContentText(resumo)
            .setStyle(NotificationCompat.BigTextStyle().bigText(detalhe))
            .setContentIntent(pi)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()
        gerenciador.notify(notifId, n)
    }
}
