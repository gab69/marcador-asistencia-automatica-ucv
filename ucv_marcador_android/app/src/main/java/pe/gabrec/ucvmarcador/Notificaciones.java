package pe.gabrec.ucvmarcador;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;

public final class Notificaciones {
    public static final String CANAL_SERVICIO = "servicio";
    public static final String CANAL_RESULTADOS = "resultados";

    private Notificaciones() {}

    public static void crearCanales(Context c) {
        NotificationManager nm = c.getSystemService(NotificationManager.class);
        nm.createNotificationChannel(new NotificationChannel(
                CANAL_SERVICIO, "Marcación en curso", NotificationManager.IMPORTANCE_LOW));
        nm.createNotificationChannel(new NotificationChannel(
                CANAL_RESULTADOS, "Resultados de marcación", NotificationManager.IMPORTANCE_HIGH));
    }

    private static PendingIntent abrirApp(Context c) {
        return PendingIntent.getActivity(c, 0, new Intent(c, MainActivity.class),
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    public static Notification enCurso(Context c, String texto) {
        crearCanales(c);
        return new Notification.Builder(c, CANAL_SERVICIO)
                .setSmallIcon(R.drawable.ic_notificacion)
                .setContentTitle("UCV Marcador")
                .setContentText(texto)
                .setContentIntent(abrirApp(c))
                .setOngoing(true)
                .build();
    }

    public static void resultado(Context c, boolean exito, String titulo, String texto) {
        crearCanales(c);
        Notification n = new Notification.Builder(c, CANAL_RESULTADOS)
                .setSmallIcon(R.drawable.ic_notificacion)
                .setContentTitle((exito ? "✔ " : "✖ ") + titulo)
                .setContentText(texto)
                .setStyle(new Notification.BigTextStyle().bigText(texto))
                .setContentIntent(abrirApp(c))
                .setAutoCancel(true)
                .build();
        try {
            c.getSystemService(NotificationManager.class).notify((int) (System.currentTimeMillis() % 100000), n);
        } catch (SecurityException ignored) {
            // Sin permiso de notificaciones: el resultado queda en el registro.
        }
    }
}
