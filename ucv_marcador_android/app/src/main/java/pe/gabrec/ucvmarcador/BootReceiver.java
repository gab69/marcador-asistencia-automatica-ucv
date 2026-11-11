package pe.gabrec.ucvmarcador;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/** Reprograma la alarma tras reiniciar el teléfono, actualizar la app o cambiar la hora. */
public class BootReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context c, Intent intent) {
        Programador.programar(c);
    }
}
