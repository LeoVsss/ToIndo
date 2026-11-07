package br.com.toindo.prototipo

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import br.com.toindo.R
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * GPS REAL do protótipo. Alimenta o [EstadoPrototipo] com a posição física do celular,
 * no lugar do slider. Roda num serviço em primeiro plano para continuar recebendo
 * posições com a tela apagada / app em segundo plano (senão o Android corta o GPS).
 */
object GpsPrototipo {
    private val _ativo = MutableStateFlow(false)
    val ativo: StateFlow<Boolean> = _ativo.asStateFlow()

    /** Precisão da última leitura, em metros (null = ainda sem sinal). */
    private val _precisaoM = MutableStateFlow<Float?>(null)
    val precisaoM: StateFlow<Float?> = _precisaoM.asStateFlow()

    /** Se verdadeiro, a próxima leitura boa vira a localização da casa. */
    @Volatile var definirCasaNaProximaLeitura = false

    /** Leituras piores que isso (m) são ignoradas, para o GPS "pulando" não simular uma saída. */
    const val PRECISAO_MAX_M = 40f

    fun iniciar(context: Context) {
        ContextCompat.startForegroundService(context, Intent(context, PrototipoGpsService::class.java))
        _ativo.value = true
    }

    fun parar(context: Context) {
        context.stopService(Intent(context, PrototipoGpsService::class.java))
        _ativo.value = false
        _precisaoM.value = null
    }

    internal fun aoReceber(lat: Double, lon: Double, precisao: Float) {
        _precisaoM.value = precisao
        if (precisao > PRECISAO_MAX_M) return
        if (definirCasaNaProximaLeitura) {
            definirCasaNaProximaLeitura = false
            EstadoPrototipo.moverPara(lat, lon)
            EstadoPrototipo.definirCasa(lat, lon)
        } else {
            EstadoPrototipo.moverPara(lat, lon)
        }
    }
}

class PrototipoGpsService : Service() {
    private val fused by lazy { LocationServices.getFusedLocationProviderClient(this) }

    private val callback = object : LocationCallback() {
        override fun onLocationResult(resultado: LocationResult) {
            val l = resultado.lastLocation ?: return
            GpsPrototipo.aoReceber(l.latitude, l.longitude, l.accuracy)
        }
    }

    override fun onCreate() {
        super.onCreate()
        startForeground(NOTIF_ID, notificacao())
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val req = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 3_000L)
            .setMinUpdateIntervalMillis(1_500L)
            .build()
        try {
            fused.requestLocationUpdates(req, callback, mainLooper)
        } catch (_: SecurityException) {
            stopSelf()
        }
        return START_NOT_STICKY
    }

    private fun notificacao(): Notification {
        val gerenciador = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            gerenciador.createNotificationChannel(
                NotificationChannel(CANAL, "Protótipo GPS", NotificationManager.IMPORTANCE_LOW)
            )
        }
        return NotificationCompat.Builder(this, CANAL)
            .setContentTitle("Tô indo! – teste de presença")
            .setContentText("Usando o GPS para detectar quando você sai de casa.")
            .setSmallIcon(R.drawable.ic_logo)
            .setOngoing(true)
            .build()
    }

    override fun onDestroy() {
        fused.removeLocationUpdates(callback)
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private companion object {
        const val CANAL = "prototipo_gps"
        const val NOTIF_ID = 43
    }
}
