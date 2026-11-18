package pe.gabrec.ucvmarcador;

import android.content.Context;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/** Registro de marcaciones: una entrada por ejecución, con su resultado y la hora exacta. */
public final class Historial {
    private static final int MAX = 60;

    private Historial() {}

    public static final class Entrada {
        /** Momento en que terminó la ejecución. */
        public final long fecha;
        /** Momento exacto en que se pulsó «Marcar»; 0 si no se llegó a pulsar. */
        public final long horaMarcacion;
        public final String turno, tipo, resumen;
        public final boolean exito;

        Entrada(long fecha, long horaMarcacion, String turno, String tipo, boolean exito, String resumen) {
            this.fecha = fecha;
            this.horaMarcacion = horaMarcacion;
            this.turno = turno;
            this.tipo = tipo;
            this.exito = exito;
            this.resumen = resumen;
        }

        /** La hora de la marcación si la hubo; si no, la de la ejecución. */
        public long hora() {
            return horaMarcacion > 0 ? horaMarcacion : fecha;
        }
    }

    /** «HH:mm:ss». */
    public static String hms(long ms) {
        return new SimpleDateFormat("HH:mm:ss", Locale.US).format(new Date(ms));
    }

    private static File archivo(Context c) {
        return new File(c.getApplicationContext().getFilesDir(), "marcaciones.json");
    }

    private static JSONArray leer(Context c) {
        try {
            File f = archivo(c);
            if (!f.exists()) return new JSONArray();
            return new JSONArray(new String(Files.readAllBytes(f.toPath()), StandardCharsets.UTF_8));
        } catch (Exception e) {
            return new JSONArray();
        }
    }

    public static synchronized void agregar(Context c, String turno, String tipo, boolean exito,
                                            long horaMarcacion, String resumen) {
        try {
            JSONArray todas = leer(c);
            JSONObject o = new JSONObject();
            o.put("fecha", System.currentTimeMillis());
            o.put("hora_marcacion", horaMarcacion);
            o.put("turno", turno);
            o.put("tipo", tipo);
            o.put("exito", exito);
            o.put("resumen", resumen);
            todas.put(o);
            JSONArray recorte = new JSONArray();
            for (int i = Math.max(0, todas.length() - MAX); i < todas.length(); i++) recorte.put(todas.get(i));
            try (FileOutputStream out = new FileOutputStream(archivo(c), false)) {
                out.write(recorte.toString().getBytes(StandardCharsets.UTF_8));
            }
        } catch (Exception e) {
            Registro.log(c, "⚠ No se pudo guardar el registro de marcaciones: " + e.getMessage());
        }
    }

    /** Marcaciones del día actual, la más reciente primero. */
    public static synchronized List<Entrada> deHoy(Context c) {
        Calendar cal = Calendar.getInstance();
        cal.set(Calendar.HOUR_OF_DAY, 0);
        cal.set(Calendar.MINUTE, 0);
        cal.set(Calendar.SECOND, 0);
        cal.set(Calendar.MILLISECOND, 0);
        long inicioHoy = cal.getTimeInMillis();
        cal.add(Calendar.DAY_OF_YEAR, 1);
        long inicioManana = cal.getTimeInMillis();

        List<Entrada> lista = new ArrayList<>();
        JSONArray todas = leer(c);
        for (int i = todas.length() - 1; i >= 0; i--) {
            JSONObject o = todas.optJSONObject(i);
            if (o == null) continue;
            long fecha = o.optLong("fecha");
            if (fecha < inicioHoy || fecha >= inicioManana) continue;
            lista.add(new Entrada(fecha, o.optLong("hora_marcacion", 0), o.optString("turno"),
                    o.optString("tipo"), o.optBoolean("exito"), o.optString("resumen")));
        }
        return lista;
    }

    public static synchronized void limpiar(Context c) {
        //noinspection ResultOfMethodCallIgnored
        archivo(c).delete();
    }
}
