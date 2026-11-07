package br.com.toindo.service

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import br.com.toindo.R
import br.com.toindo.data.RepositorioToIndo
import br.com.toindo.domain.Geo
import br.com.toindo.domain.MotorAutomacao
import br.com.toindo.model.EstadoPresenca
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority

/**
 * Serviço em primeiro plano que monitora a localização e dispara a automação por presença
 * (RF03–RF05; RNF01 — usa prioridade balanceada, não GPS contínuo de alta precisão).
 *
 * Mantém a máquina de estados do [MotorAutomacao] e delega as ações ao [RepositorioToIndo].
 * Para o protótipo, roda enquanto o app está ativo; a evolução é migrar para geofencing
 * puro + WorkManager para funcionar 100% com o app fechado (seção 6 / risco de background).
 */
class PresencaService : Service() {

    private val fused by lazy { LocationServices.getFusedLocationProviderClient(this) }
    private var presencaAtual = EstadoPresenca.EM_CASA
    private var inicioConfirmacao: Long? = null

    private val callback = object : LocationCallback() {
        override fun onLocationResult(resultado: LocationResult) {
            val loc = resultado.lastLocation ?: return
            avaliar(loc.latitude, loc.longitude)
        }
    }

    override fun onCreate() {
        super.onCreate()
        startForeground(NOTIF_ID, notificacao())
        presencaAtual = RepositorioToIndo.presenca.value
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (temPermissaoLocalizacao()) solicitarAtualizacoes()
        return START_STICKY
    }

    private fun solicitarAtualizacoes() {
        val req = LocationRequest.Builder(Priority.PRIORITY_BALANCED_POWER_ACCURACY, INTERVALO_MS)
            .setMinUpdateIntervalMillis(INTERVALO_MIN_MS)
            .build()
        try {
            fused.requestLocationUpdates(req, callback, mainLooper)
        } catch (_: SecurityException) {
            stopSelf()
        }
    }

    private fun avaliar(lat: Double, lon: Double) {
        val casa = RepositorioToIndo.residencia.value
        val config = RepositorioToIndo.configuracao.value
        val distancia = Geo.distanciaM(lat, lon, casa.latitude, casa.longitude)

        // RN01: começa a contar o tempo de confirmação ao sair do raio.
        if (presencaAtual == EstadoPresenca.EM_CASA && distancia > casa.raioSaidaM && inicioConfirmacao == null) {
            inicioConfirmacao = System.currentTimeMillis()
        }

        val proxima = MotorAutomacao.proximaPresenca(
            atual = presencaAtual,
            distanciaM = distancia,
            raioSaidaM = casa.raioSaidaM,
            raioAproximacaoM = casa.raioAproximacaoM,
            inicioConfirmacaoMillis = inicioConfirmacao,
            agoraMillis = System.currentTimeMillis(),
            tempoConfirmacaoMin = config.tempoConfirmacaoSaidaMin,
        )

        if (proxima != presencaAtual) {
            RepositorioToIndo.aplicarTransicaoPresenca(presencaAtual, proxima)
            presencaAtual = proxima
            if (proxima == EstadoPresenca.EM_CASA || proxima == EstadoPresenca.FORA) inicioConfirmacao = null
        }
    }

    private fun temPermissaoLocalizacao() =
        ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED

    private fun notificacao(): Notification {
        val gerenciador = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            gerenciador.createNotificationChannel(
                NotificationChannel(CANAL, "Automação por presença", NotificationManager.IMPORTANCE_LOW)
            )
        }
        return NotificationCompat.Builder(this, CANAL)
            .setContentTitle("Tô indo! ativo")
            .setContentText("Cuidando dos seus objetos pela sua localização.")
            .setSmallIcon(R.drawable.ic_logo)
            .setOngoing(true)
            .build()
    }

    override fun onDestroy() {
        fused.removeLocationUpdates(callback)
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        private const val CANAL = "presenca"
        private const val NOTIF_ID = 42
        private const val INTERVALO_MS = 60_000L      // 1 min (RNF01: economiza bateria)
        private const val INTERVALO_MIN_MS = 30_000L

        fun iniciar(context: Context) {
            val intent = Intent(context, PresencaService::class.java)
            ContextCompat.startForegroundService(context, intent)
        }

        fun parar(context: Context) {
            context.stopService(Intent(context, PresencaService::class.java))
        }
    }
}
