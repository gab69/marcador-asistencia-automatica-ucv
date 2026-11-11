package pe.gabrec.ucvmarcador;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/** Se dispara a la hora exacta configurada e inicia la marcación. */
public class AlarmReceiver extends BroadcastReceiver {
    /** Si el teléfono estaba apagado y la hora pasó hace más de esto, no se marca tarde. */
    private static final long TOLERANCIA_MS = 2 * 60 * 1000;

    @Override
    public void onReceive(Context c, Intent intent) {
        String turno = intent.getStringExtra(Programador.EXTRA_TURNO);
        String tipo = intent.getStringExtra(Programador.EXTRA_TIPO);
        long hora = intent.getLongExtra(Programador.EXTRA_HORA, System.currentTimeMillis());
        long ahora = System.currentTimeMillis();

        if (turno != null && tipo != null) {
            String hhmm = new SimpleDateFormat("HH:mm", Locale.US).format(new Date(hora));
            if (ahora - hora > TOLERANCIA_MS) {
                Registro.log(c, "⏭ Omitido " + turno + " " + tipo + " (" + hhmm + "): el teléfono estaba apagado.");
            } else {
                Registro.log(c, "⏰ " + hhmm + " — ejecutando " + turno + " " + tipo);
                MarcadorService.iniciar(c, turno, tipo);
            }
        }
        // Programar la siguiente (estrictamente después de esta)
        Programador.programarDesde(c, Math.max(ahora, hora) + 1000);
    }
}
