package pe.gabrec.ucvmarcador;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

import java.util.Calendar;

/** Calcula la próxima marcación y programa una alarma exacta para esa hora. */
public final class Programador {
    public static final String EXTRA_TURNO = "turno";
    public static final String EXTRA_TIPO = "tipo";
    public static final String EXTRA_HORA = "hora";

    private Programador() {}

    public static final class Evento {
        public final long cuando;
        public final String turno, tipo;

        Evento(long cuando, String turno, String tipo) {
            this.cuando = cuando;
            this.turno = turno;
            this.tipo = tipo;
        }
    }

    /** Próximo evento estrictamente posterior a "desde" (ms), o null. */
    public static Evento proximo(Context c, long desde) {
        Evento mejor = null;
        for (String turno : Config.TURNOS) {
            if (!Config.turnoActivo(c, turno)) continue;
            for (String tipo : Config.TIPOS) {
                String[] hm = Config.hora(c, turno, tipo).split(":");
                for (int dia = 0; dia <= 1; dia++) {
                    Calendar cal = Calendar.getInstance();
                    cal.setTimeInMillis(desde);
                    cal.add(Calendar.DAY_OF_YEAR, dia);
                    cal.set(Calendar.HOUR_OF_DAY, Integer.parseInt(hm[0]));
                    cal.set(Calendar.MINUTE, Integer.parseInt(hm[1]));
                    cal.set(Calendar.SECOND, 0);
                    cal.set(Calendar.MILLISECOND, 0);
                    long t = cal.getTimeInMillis();
                    if (t > desde && (mejor == null || t < mejor.cuando)) mejor = new Evento(t, turno, tipo);
                }
            }
        }
        return mejor;
    }

    public static boolean puedeAlarmasExactas(Context c) {
        AlarmManager am = c.getSystemService(AlarmManager.class);
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.S || am.canScheduleExactAlarms();
    }

    /** Programa la próxima marcación (reemplaza la anterior). */
    public static Evento programar(Context c) {
        return programarDesde(c, System.currentTimeMillis());
    }

    public static Evento programarDesde(Context c, long desde) {
        AlarmManager am = c.getSystemService(AlarmManager.class);
        Intent i = new Intent(c, AlarmReceiver.class);
        Evento ev = proximo(c, desde);
        if (ev == null) {
            PendingIntent pi = PendingIntent.getBroadcast(c, 1, i,
                    PendingIntent.FLAG_NO_CREATE | PendingIntent.FLAG_IMMUTABLE);
            if (pi != null) am.cancel(pi);
            return null;
        }
        i.putExtra(EXTRA_TURNO, ev.turno);
        i.putExtra(EXTRA_TIPO, ev.tipo);
        i.putExtra(EXTRA_HORA, ev.cuando);
        PendingIntent pi = PendingIntent.getBroadcast(c, 1, i,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        if (puedeAlarmasExactas(c)) {
            // setAlarmClock: exacta incluso en modo Doze y permite iniciar el servicio.
            PendingIntent abrir = PendingIntent.getActivity(c, 2, new Intent(c, MainActivity.class),
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
            am.setAlarmClock(new AlarmManager.AlarmClockInfo(ev.cuando, abrir), pi);
        } else {
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, ev.cuando, pi);
            Registro.log(c, "⚠ Sin permiso de alarmas exactas: la marcación podría retrasarse.");
        }
        return ev;
    }
}
