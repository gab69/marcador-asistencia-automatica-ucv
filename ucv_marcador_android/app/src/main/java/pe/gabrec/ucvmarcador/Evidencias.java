package pe.gabrec.ucvmarcador;

import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Rect;
import android.media.MediaScannerConnection;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.MediaStore;
import android.util.Size;
import android.view.View;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/** Guarda capturas de la página como evidencia en Galería › Pictures/UCVMarcador. */
public final class Evidencias {
    private Evidencias() {}

    private static final String CARPETA = "UCVMarcador";

    /** Una captura guardada (para la lista de recientes). */
    public static final class Captura {
        public final Uri uri;
        public final String nombre;
        public final long fecha;

        Captura(Uri uri, String nombre, long fecha) {
            this.uri = uri;
            this.nombre = nombre;
            this.fecha = fecha;
        }
    }

    public static String capturar(Context c, View vista, String etiqueta) {
        return capturar(c, vista, etiqueta, null);
    }

    /** Captura la vista; si "recorte" (px de la vista) es válido, guarda solo esa zona. */
    public static String capturar(Context c, View vista, String etiqueta, Rect recorte) {
        try {
            int w = vista.getWidth(), h = vista.getHeight();
            if (w <= 0 || h <= 0) return null;
            Bitmap bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
            Canvas canvas = new Canvas(bmp);
            canvas.drawColor(Color.WHITE);
            // El WebView dibuja su contenido desplazado según su scroll: sin esta
            // traslación la zona visible queda fuera del bitmap y la captura sale en blanco.
            canvas.translate(-vista.getScrollX(), -vista.getScrollY());
            vista.draw(canvas);
            if (esBlanca(bmp)) {
                Registro.log(c, "⚠ La captura salió en blanco (la página no se dibujó).");
            }
            if (recorte != null && recorte.intersect(0, 0, w, h)
                    && recorte.width() > 50 && recorte.height() > 50) {
                Bitmap rec = Bitmap.createBitmap(bmp, recorte.left, recorte.top, recorte.width(), recorte.height());
                bmp.recycle();
                bmp = rec;
            }
            String nombre = etiqueta.replace(' ', '_') + "_"
                    + new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(new Date()) + ".png";
            String ruta = guardar(c, bmp, nombre);
            bmp.recycle();
            if (ruta != null) Registro.log(c, "📸 Evidencia: " + ruta);
            return ruta;
        } catch (Exception e) {
            Registro.log(c, "⚠ No se pudo guardar la captura: " + e.getMessage());
            return null;
        }
    }

    /** Muestrea una rejilla de píxeles: true si todos son blancos. */
    private static boolean esBlanca(Bitmap b) {
        int w = b.getWidth(), h = b.getHeight();
        for (int y = 0; y < 12; y++) {
            for (int x = 0; x < 12; x++) {
                int p = b.getPixel(x * (w - 1) / 11, y * (h - 1) / 11);
                if ((p & 0xFFFFFF) != 0xFFFFFF) return false;
            }
        }
        return true;
    }

    private static String guardar(Context c, Bitmap bmp, String nombre) throws Exception {
        long ahora = System.currentTimeMillis();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ContentValues v = new ContentValues();
            v.put(MediaStore.Images.Media.DISPLAY_NAME, nombre);
            v.put(MediaStore.Images.Media.MIME_TYPE, "image/png");
            v.put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/" + CARPETA);
            // Fechas para que la Galería la ordene entre las «Recientes».
            v.put(MediaStore.Images.Media.DATE_TAKEN, ahora);
            v.put(MediaStore.Images.Media.DATE_ADDED, ahora / 1000);
            v.put(MediaStore.Images.Media.DATE_MODIFIED, ahora / 1000);
            v.put(MediaStore.Images.Media.IS_PENDING, 1);
            ContentResolver cr = c.getContentResolver();
            Uri uri = cr.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, v);
            if (uri == null) return null;
            try (OutputStream out = cr.openOutputStream(uri)) {
                bmp.compress(Bitmap.CompressFormat.PNG, 100, out);
            } catch (Exception e) {
                cr.delete(uri, null, null);
                throw e;
            }
            ContentValues fin = new ContentValues();
            fin.put(MediaStore.Images.Media.IS_PENDING, 0);
            cr.update(uri, fin, null, null);
            return "Pictures/" + CARPETA + "/" + nombre;
        }
        File dir = carpetaAntigua(c);
        //noinspection ResultOfMethodCallIgnored
        dir.mkdirs();
        File f = new File(dir, nombre);
        try (FileOutputStream out = new FileOutputStream(f)) {
            bmp.compress(Bitmap.CompressFormat.PNG, 100, out);
        }
        MediaScannerConnection.scanFile(c, new String[]{f.getAbsolutePath()}, new String[]{"image/png"}, null);
        return f.getAbsolutePath();
    }

    private static File carpetaAntigua(Context c) {
        return new File(c.getExternalFilesDir(Environment.DIRECTORY_PICTURES), CARPETA);
    }

    /** Últimas capturas guardadas por la app, la más reciente primero. */
    public static List<Captura> recientes(Context c, int max) {
        List<Captura> lista = new ArrayList<>();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            String[] cols = {MediaStore.Images.Media._ID, MediaStore.Images.Media.DISPLAY_NAME,
                    MediaStore.Images.Media.DATE_ADDED};
            String sel = MediaStore.Images.Media.RELATIVE_PATH + " LIKE ?";
            String[] args = {Environment.DIRECTORY_PICTURES + "/" + CARPETA + "%"};
            try (Cursor cur = c.getContentResolver().query(MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                    cols, sel, args, MediaStore.Images.Media.DATE_ADDED + " DESC")) {
                while (cur != null && cur.moveToNext() && lista.size() < max) {
                    Uri uri = ContentUris.withAppendedId(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, cur.getLong(0));
                    lista.add(new Captura(uri, cur.getString(1), cur.getLong(2) * 1000));
                }
            } catch (Exception e) {
                Registro.log(c, "⚠ No se pudieron leer las capturas: " + e.getMessage());
            }
        } else {
            File[] fs = carpetaAntigua(c).listFiles((d, n) -> n.endsWith(".png"));
            if (fs != null) {
                Arrays.sort(fs, (a, b) -> Long.compare(b.lastModified(), a.lastModified()));
                for (int i = 0; i < fs.length && i < max; i++) {
                    lista.add(new Captura(Uri.fromFile(fs[i]), fs[i].getName(), fs[i].lastModified()));
                }
            }
        }
        return lista;
    }

    /** Miniatura de una captura (llamar fuera del hilo principal). */
    public static Bitmap miniatura(Context c, Captura cap, int lado) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && "content".equals(cap.uri.getScheme())) {
                return c.getContentResolver().loadThumbnail(cap.uri, new Size(lado, lado), null);
            }
            BitmapFactory.Options o = new BitmapFactory.Options();
            o.inJustDecodeBounds = true;
            BitmapFactory.decodeFile(cap.uri.getPath(), o);
            int m = Math.max(o.outWidth, o.outHeight);
            o.inSampleSize = Math.max(1, Integer.highestOneBit(Math.max(1, m / lado)));
            o.inJustDecodeBounds = false;
            return BitmapFactory.decodeFile(cap.uri.getPath(), o);
        } catch (Exception e) {
            return null;
        }
    }
}
