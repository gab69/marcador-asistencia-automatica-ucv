package pe.gabrec.ucvmarcador;

import android.content.Context;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/** Registro de actividad guardado en un archivo (últimas 300 líneas). */
public final class Registro {
    private static final int MAX_LINEAS = 300;

    private Registro() {}

    private static File archivo(Context c) {
        return new File(c.getApplicationContext().getFilesDir(), "registro.txt");
    }

    public static synchronized void log(Context c, String msg) {
        String linea = "[" + new SimpleDateFormat("dd/MM HH:mm:ss", Locale.US).format(new Date()) + "] " + msg;
        List<String> lineas = new ArrayList<>(Arrays.asList(leer(c).split("\n")));
        lineas.removeIf(String::isEmpty);
        lineas.add(linea);
        if (lineas.size() > MAX_LINEAS) lineas = lineas.subList(lineas.size() - MAX_LINEAS, lineas.size());
        try (FileOutputStream out = new FileOutputStream(archivo(c), false)) {
            out.write(String.join("\n", lineas).getBytes(StandardCharsets.UTF_8));
        } catch (Exception ignored) {
        }
    }

    public static synchronized String leer(Context c) {
        try {
            File f = archivo(c);
            if (!f.exists()) return "";
            return new String(Files.readAllBytes(f.toPath()), StandardCharsets.UTF_8);
        } catch (Exception e) {
            return "";
        }
    }

    /** Últimas n líneas, la más reciente arriba. */
    public static String ultimas(Context c, int n) {
        String[] l = leer(c).split("\n");
        StringBuilder sb = new StringBuilder();
        for (int i = l.length - 1, k = 0; i >= 0 && k < n; i--) {
            if (l[i].isEmpty()) continue;
            sb.append(l[i]).append('\n');
            k++;
        }
        return sb.length() == 0 ? "Sin actividad todavía." : sb.toString().trim();
    }

    public static synchronized void limpiar(Context c) {
        //noinspection ResultOfMethodCallIgnored
        archivo(c).delete();
    }
}
