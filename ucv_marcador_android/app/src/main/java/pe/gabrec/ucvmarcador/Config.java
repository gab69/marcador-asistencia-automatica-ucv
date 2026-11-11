package pe.gabrec.ucvmarcador;

import android.content.Context;
import android.content.SharedPreferences;

/** Configuración persistente (SharedPreferences). */
public final class Config {
    public static final String URL = "https://ucvtalent.ucv.edu.pe/dashboards/main-page";
    public static final String[] TURNOS = {"Mañana", "Tarde"};
    public static final String[] TIPOS = {"Entrada", "Salida"};

    private Config() {}

    private static SharedPreferences prefs(Context c) {
        return c.getApplicationContext().getSharedPreferences("config", Context.MODE_PRIVATE);
    }

    private static String clave(String turno) {
        return "Mañana".equals(turno) ? "manana" : "tarde";
    }

    public static boolean turnoActivo(Context c, String turno) {
        return prefs(c).getBoolean("activo_" + clave(turno), true);
    }

    public static void setTurnoActivo(Context c, String turno, boolean v) {
        prefs(c).edit().putBoolean("activo_" + clave(turno), v).apply();
    }

    /** Devuelve "HH:mm". */
    public static String hora(Context c, String turno, String tipo) {
        String def;
        if ("Mañana".equals(turno)) def = "Entrada".equals(tipo) ? "08:00" : "12:55";
        else def = "Entrada".equals(tipo) ? "14:00" : "17:55";
        return prefs(c).getString("hora_" + clave(turno) + "_" + tipo.toLowerCase(), def);
    }

    public static void setHora(Context c, String turno, String tipo, int h, int m) {
        prefs(c).edit().putString("hora_" + clave(turno) + "_" + tipo.toLowerCase(),
                String.format(java.util.Locale.US, "%02d:%02d", h, m)).apply();
    }

    public static boolean soloEvidencia(Context c) {
        return prefs(c).getBoolean("solo_evidencia", true);
    }

    public static void setSoloEvidencia(Context c, boolean v) {
        prefs(c).edit().putBoolean("solo_evidencia", v).apply();
    }
}
