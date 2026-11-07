package br.com.toindo.service

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import br.com.toindo.MainActivity
import br.com.toindo.R
import br.com.toindo.data.RepositorioToIndo
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority

/**
 * Serviço em primeiro plano que lê o GPS REAL do celular e repassa cada posição ao
 * [RepositorioToIndo.avaliarPosicao], que decide se você saiu/voltou e aplica a automação.
 *
 * Protótipo: GPS de alta precisão a cada ~3 s, para conseguir testar com raios pequenos
 * (a partir de 5 m). O custo é bateria; a evolução é usar geofencing (seção 6 do PLAN).
 * Continua funcionando com a tela apagada e o app em segundo plano (notificação fixa).
 */
class PresencaService : Service() {

    private val fused by lazy { LocationServices.getFusedLocationProviderClient(this) }
    private var ativo = false

    private val callback = object : LocationCallback() {
        override fun onLocationResult(resultado: LocationResult) {
            val loc = resultado.lastLocation ?: return
            RepositorioToIndo.avaliarPosicao(
                loc.latitude, loc.longitude, if (loc.hasAccuracy()) loc.accuracy.toInt() else null,
            )
        }
    }

    override fun onCreate() {
        super.onCreate()
        val tipo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION else 0
        ServiceCompat.startForeground(this, NOTIF_ID, notificacao(), tipo)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (!temPermissaoLocalizacao()) {
            stopSelf()
            return START_NOT_STICKY
        }
        if (!ativo) solicitarAtualizacoes()
        return START_STICKY
    }

    private fun solicitarAtualizacoes() {
        val req = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, INTERVALO_MS)
            .setMinUpdateIntervalMillis(INTERVALO_MIN_MS)
            .setWaitForAccurateLocation(false)
            .build()
        try {
            fused.requestLocationUpdates(req, callback, mainLooper)
            ativo = true
        } catch (_: SecurityException) {
            stopSelf()
        }
    }

    private fun temPermissaoLocalizacao() =
        ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED

    private fun notificacao(): Notification {
        val gerenciador = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            gerenciador.createNotificationChannel(
                NotificationChannel(CANAL, "Monitoramento de presença", NotificationManager.IMPORTANCE_LOW)
            )
        }
        val abrir = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE,
        )
        return NotificationCompat.Builder(this, CANAL)
            .setContentTitle("Tô indo! ativo")
            .setContentText("Cuidando dos seus objetos pela sua localização.")
            .setSmallIcon(R.drawable.ic_logo)
            .setContentIntent(abrir)
            .setOngoing(true)
            .build()
    }

    override fun onDestroy() {
        fused.removeLocationUpdates(callback)
        ativo = false
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        private const val CANAL = "presenca"
        private const val NOTIF_ID = 42
        private const val INTERVALO_MS = 3_000L
        private const val INTERVALO_MIN_MS = 2_000L

        fun iniciar(context: Context) {
            ContextCompat.startForegroundService(context, Intent(context, PresencaService::class.java))
        }

        fun parar(context: Context) {
            context.stopService(Intent(context, PresencaService::class.java))
        }
    }
}
