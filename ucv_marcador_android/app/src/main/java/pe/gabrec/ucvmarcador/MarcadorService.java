package pe.gabrec.ucvmarcador;

import android.Manifest;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ServiceInfo;
import android.os.Build;
import android.os.IBinder;
import android.os.PowerManager;
import android.util.DisplayMetrics;
import android.view.View;
import android.webkit.WebView;

/**
 * Servicio en primer plano que marca en segundo plano: crea un WebView que no se
 * muestra en pantalla, ejecuta el motor de marcación y luego se detiene.
 */
public class MarcadorService extends Service {
    private static final int ID_NOTIF = 4101;
    private WebView web;
    private Marcador marcador;
    private PowerManager.WakeLock wakeLock;

    public static void iniciar(Context c, String turno, String tipo) {
        Intent i = new Intent(c, MarcadorService.class)
                .putExtra(Programador.EXTRA_TURNO, turno)
                .putExtra(Programador.EXTRA_TIPO, tipo);
        try {
            c.startForegroundService(i);
        } catch (Exception e) {
            Registro.log(c, "❌ No se pudo iniciar la marcación en segundo plano: " + e.getMessage());
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        String turno = intent != null ? intent.getStringExtra(Programador.EXTRA_TURNO) : null;
        String tipo = intent != null ? intent.getStringExtra(Programador.EXTRA_TIPO) : null;
        iniciarPrimerPlano("Marcando " + turno + " " + tipo + "…");

        if (turno == null || tipo == null) {
            detener();
            return START_NOT_STICKY;
        }
        if (marcador != null) {
            Registro.log(this, "⚠ Ya hay una marcación en curso; se omite " + turno + " " + tipo + ".");
            return START_NOT_STICKY;
        }

        PowerManager pm = getSystemService(PowerManager.class);
        wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "UCVMarcador:marcacion");
        wakeLock.acquire(3 * 60 * 1000L);

        // WebView fuera de pantalla con el tamaño de la pantalla del teléfono
        web = Marcador.crearWebView(this);
        DisplayMetrics dm = getResources().getDisplayMetrics();
        web.measure(View.MeasureSpec.makeMeasureSpec(dm.widthPixels, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(dm.heightPixels, View.MeasureSpec.EXACTLY));
        web.layout(0, 0, dm.widthPixels, dm.heightPixels);

        final String t = turno, ti = tipo;
        marcador = new Marcador(this, web, turno, tipo, Config.soloEvidencia(this), (exito, resumen) -> {
            Notificaciones.resultado(this, exito, t + " " + ti, resumen);
            // Destruir el WebView fuera de su propio callback
            new android.os.Handler(android.os.Looper.getMainLooper()).post(this::detener);
        });
        marcador.iniciar();
        return START_NOT_STICKY;
    }

    private void iniciarPrimerPlano(String texto) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            int tipo = ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC;
            if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
                    || checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
                tipo |= ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION;
            }
            try {
                startForeground(ID_NOTIF, Notificaciones.enCurso(this, texto), tipo);
            } catch (Exception e) {
                // Si el tipo "ubicación" no está permitido en este momento, solo sincronización.
                startForeground(ID_NOTIF, Notificaciones.enCurso(this, texto),
                        ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC);
            }
        } else {
            startForeground(ID_NOTIF, Notificaciones.enCurso(this, texto));
        }
    }

    private void detener() {
        if (marcador != null) marcador.cancelar();
        marcador = null;
        if (web != null) {
            web.stopLoading();
            web.destroy();
            web = null;
        }
        if (wakeLock != null && wakeLock.isHeld()) wakeLock.release();
        stopForeground(STOP_FOREGROUND_REMOVE);
        stopSelf();
    }

    @Override
    public void onDestroy() {
        if (marcador != null) marcador.cancelar();
        if (web != null) web.destroy();
        if (wakeLock != null && wakeLock.isHeld()) wakeLock.release();
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
