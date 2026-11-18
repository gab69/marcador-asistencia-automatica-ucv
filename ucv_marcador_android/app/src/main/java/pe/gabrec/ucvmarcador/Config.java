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

    /** Días en orden de lunes a domingo (constantes de Calendar.DAY_OF_WEEK). */
    public static final int[] DIAS = {java.util.Calendar.MONDAY, java.util.Calendar.TUESDAY,
            java.util.Calendar.WEDNESDAY, java.util.Calendar.THURSDAY, java.util.Calendar.FRIDAY,
            java.util.Calendar.SATURDAY, java.util.Calendar.SUNDAY};
    public static final String[] DIAS_LETRA = {"L", "M", "X", "J", "V", "S", "D"};
    public static final String[] DIAS_NOMBRE = {"lunes", "martes", "miércoles", "jueves", "viernes",
            "sábado", "domingo"};

    /** true si las marcaciones se repiten ese día (Calendar.DAY_OF_WEEK). Por defecto, todos. */
    public static boolean diaActivo(Context c, int diaSemana) {
        return prefs(c).getBoolean("dia_" + diaSemana, true);
    }

    public static void setDiaActivo(Context c, int diaSemana, boolean v) {
        prefs(c).edit().putBoolean("dia_" + diaSemana, v).apply();
    }

    public static boolean hayDiasActivos(Context c) {
        for (int d : DIAS) if (diaActivo(c, d)) return true;
        return false;
    }
}
