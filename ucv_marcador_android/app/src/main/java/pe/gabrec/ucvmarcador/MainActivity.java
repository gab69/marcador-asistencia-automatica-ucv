package pe.gabrec.ucvmarcador;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.TimePickerDialog;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.PowerManager;
import android.provider.Settings;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.Spinner;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;

public class MainActivity extends Activity {
    private static final int REQ_NOTIF = 1, REQ_UBIC = 2, REQ_UBIC_FONDO = 3;

    private final Handler h = new Handler(Looper.getMainLooper());
    private TextView tvProx, tvProxDet, chipModo, tvPermisos, tvLog, tvSinCapturas;
    private Spinner spTurno, spTipo;
    private android.widget.LinearLayout llRecientes;
    private final java.util.concurrent.ExecutorService fondo = java.util.concurrent.Executors.newSingleThreadExecutor();
    private String firmaRecientes = "";

    private final Runnable refrescoLog = new Runnable() {
        @Override
        public void run() {
            tvLog.setText(Registro.ultimas(MainActivity.this, 40));
            actualizarProxima();
            cargarRecientes();
            h.postDelayed(this, 3000);
        }
    };

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        setContentView(R.layout.activity_main);
        android.view.ViewGroup raiz = (android.view.ViewGroup) ((android.view.ViewGroup) findViewById(android.R.id.content)).getChildAt(0);
        raiz.setBackgroundColor(getColor(R.color.azul_oscuro));
        raiz.getChildAt(1).setBackgroundColor(getColor(R.color.fondo));
        Pantallas.respetarBarras(raiz);
        Notificaciones.crearCanales(this);

        tvProx = findViewById(R.id.tvProx);
        tvProxDet = findViewById(R.id.tvProxDet);
        chipModo = findViewById(R.id.chipModo);
        tvPermisos = findViewById(R.id.tvPermisos);
        tvLog = findViewById(R.id.tvLog);
        spTurno = findViewById(R.id.spTurno);
        spTipo = findViewById(R.id.spTipo);
        llRecientes = findViewById(R.id.llRecientes);
        tvSinCapturas = findViewById(R.id.tvSinCapturas);

        findViewById(R.id.btnLogin).setOnClickListener(v -> startActivity(new Intent(this, LoginActivity.class)));

        configurarTurno(R.id.swManana, R.id.btnMEnt, R.id.btnMSal, "Mañana");
        configurarTurno(R.id.swTarde, R.id.btnTEnt, R.id.btnTSal, "Tarde");

        Switch swSolo = findViewById(R.id.swSoloEv);
        swSolo.setChecked(Config.soloEvidencia(this));
        swSolo.setOnCheckedChangeListener((s, v) -> {
            Config.setSoloEvidencia(this, v);
            Registro.log(this, v ? "Modo solo evidencia activado." : "Marcación real activada.");
            actualizarProxima();
        });

        spTurno.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, Config.TURNOS));
        spTipo.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, Config.TIPOS));
        findViewById(R.id.btnPruebaVisible).setOnClickListener(v -> startActivity(
                new Intent(this, PruebaActivity.class)
                        .putExtra(Programador.EXTRA_TURNO, (String) spTurno.getSelectedItem())
                        .putExtra(Programador.EXTRA_TIPO, (String) spTipo.getSelectedItem())));
        findViewById(R.id.btnPruebaFondo).setOnClickListener(v -> {
            Registro.log(this, "▶ Prueba en segundo plano: " + spTurno.getSelectedItem() + " " + spTipo.getSelectedItem());
            MarcadorService.iniciar(this, (String) spTurno.getSelectedItem(), (String) spTipo.getSelectedItem());
            Toast.makeText(this, "Marcación iniciada en segundo plano. Revisa el registro.", Toast.LENGTH_SHORT).show();
        });

        findViewById(R.id.btnPermisos).setOnClickListener(v -> pedirSiguientePermiso());
        findViewById(R.id.btnLimpiar).setOnClickListener(v -> {
            Registro.limpiar(this);
            tvLog.setText(Registro.ultimas(this, 40));
        });

        String version = "";
        try {
            version = getPackageManager().getPackageInfo(getPackageName(), 0).versionName;
        } catch (Exception ignored) {
        }
        ((TextView) findViewById(R.id.tvVersion)).setText("UCV Marcador para Android · versión " + version);
        findViewById(R.id.btnDesinstalar).setOnClickListener(v -> new AlertDialog.Builder(this)
                .setTitle("Desinstalar")
                .setMessage("Se eliminará UCV Marcador junto con su configuración y la sesión guardada. "
                        + "Las capturas de la Galería se conservan.")
                .setPositiveButton("Desinstalar", (d, w) -> {
                    Intent i = new Intent(Intent.ACTION_DELETE, Uri.parse("package:" + getPackageName()));
                    startActivity(i);
                })
                .setNegativeButton("Cancelar", null)
                .show());

        Programador.programar(this);
    }

    @Override
    protected void onResume() {
        super.onResume();
        Programador.programar(this);
        actualizarPermisos();
        h.post(refrescoLog);
    }

    @Override
    protected void onPause() {
        h.removeCallbacks(refrescoLog);
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        fondo.shutdownNow();
        super.onDestroy();
    }

    // ---------------- Capturas recientes ----------------
    private void cargarRecientes() {
        if (fondo.isShutdown()) return;
        fondo.execute(() -> {
            java.util.List<Evidencias.Captura> lista = Evidencias.recientes(this, 12);
            StringBuilder firma = new StringBuilder();
            for (Evidencias.Captura c : lista) firma.append(c.uri).append(';');
            if (firma.toString().equals(firmaRecientes)) return; // sin cambios
            int lado = Pantallas.dp(this, 96);
            java.util.List<android.graphics.Bitmap> minis = new java.util.ArrayList<>();
            for (Evidencias.Captura c : lista) minis.add(Evidencias.miniatura(this, c, lado * 2));
            h.post(() -> {
                if (isDestroyed()) return;
                firmaRecientes = firma.toString();
                mostrarRecientes(lista, minis, lado);
            });
        });
    }

    private void mostrarRecientes(java.util.List<Evidencias.Captura> lista,
                                  java.util.List<android.graphics.Bitmap> minis, int lado) {
        llRecientes.removeAllViews();
        tvSinCapturas.setVisibility(lista.isEmpty() ? android.view.View.VISIBLE : android.view.View.GONE);
        SimpleDateFormat f = new SimpleDateFormat("dd/MM HH:mm", Locale.US);
        for (int i = 0; i < lista.size(); i++) {
            Evidencias.Captura c = lista.get(i);
            android.widget.LinearLayout item = new android.widget.LinearLayout(this);
            item.setOrientation(android.widget.LinearLayout.VERTICAL);
            android.widget.LinearLayout.LayoutParams lp = new android.widget.LinearLayout.LayoutParams(
                    lado, android.view.ViewGroup.LayoutParams.WRAP_CONTENT);
            lp.setMarginEnd(Pantallas.dp(this, 8));

            android.widget.ImageView img = new android.widget.ImageView(this);
            img.setScaleType(android.widget.ImageView.ScaleType.CENTER_CROP);
            img.setBackgroundResource(R.drawable.log);
            img.setClipToOutline(true);
            if (minis.get(i) != null) img.setImageBitmap(minis.get(i));
            item.addView(img, new android.widget.LinearLayout.LayoutParams(lado, lado));

            TextView t = new TextView(this);
            t.setText(f.format(new Date(c.fecha)));
            t.setTextSize(11);
            t.setTextColor(getColor(R.color.texto_suave));
            t.setGravity(android.view.Gravity.CENTER);
            item.addView(t);

            item.setOnClickListener(v -> verCaptura(c));
            llRecientes.addView(item, lp);
        }
    }

    private void verCaptura(Evidencias.Captura c) {
        fondo.execute(() -> {
            android.graphics.Bitmap bmp = Evidencias.miniatura(this, c,
                    getResources().getDisplayMetrics().widthPixels);
            h.post(() -> {
                if (isDestroyed()) return;
                if (bmp == null) {
                    Toast.makeText(this, "No se pudo abrir la captura.", Toast.LENGTH_SHORT).show();
                    return;
                }
                android.widget.ImageView img = new android.widget.ImageView(this);
                img.setAdjustViewBounds(true);
                img.setImageBitmap(bmp);
                android.widget.ScrollView sv = new android.widget.ScrollView(this);
                sv.addView(img);
                AlertDialog.Builder d = new AlertDialog.Builder(this)
                        .setTitle(c.nombre)
                        .setView(sv)
                        .setPositiveButton("Cerrar", null);
                if ("content".equals(c.uri.getScheme())) {
                    d.setNeutralButton("Abrir en Galería", (x, y) -> {
                        try {
                            startActivity(new Intent(Intent.ACTION_VIEW)
                                    .setDataAndType(c.uri, "image/png")
                                    .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION));
                        } catch (Exception e) {
                            Toast.makeText(this, "No hay una app para ver imágenes.", Toast.LENGTH_SHORT).show();
                        }
                    });
                }
                d.show();
            });
        });
    }

    // ---------------- Horarios ----------------
    private void configurarTurno(int idSwitch, int idEnt, int idSal, String turno) {
        Switch sw = findViewById(idSwitch);
        sw.setChecked(Config.turnoActivo(this, turno));
        sw.setOnCheckedChangeListener((s, v) -> {
            Config.setTurnoActivo(this, turno, v);
            reprogramar();
        });
        configurarHora(findViewById(idEnt), turno, "Entrada");
        configurarHora(findViewById(idSal), turno, "Salida");
    }

    private void configurarHora(Button b, String turno, String tipo) {
        b.setText(tipo + "  " + Config.hora(this, turno, tipo));
        b.setOnClickListener(v -> {
            String[] hm = Config.hora(this, turno, tipo).split(":");
            new TimePickerDialog(this, (tp, hh, mm) -> {
                Config.setHora(this, turno, tipo, hh, mm);
                b.setText(tipo + "  " + Config.hora(this, turno, tipo));
                reprogramar();
            }, Integer.parseInt(hm[0]), Integer.parseInt(hm[1]), true).show();
        });
    }

    private void reprogramar() {
        Programador.Evento ev = Programador.programar(this);
        Registro.log(this, "Horarios guardados." + (ev == null ? " No hay turnos activos."
                : " Próxima: " + ev.turno + " " + ev.tipo + " a las "
                + new SimpleDateFormat("HH:mm", Locale.US).format(new Date(ev.cuando))));
        actualizarProxima();
    }

    private void actualizarProxima() {
        Programador.Evento ev = Programador.proximo(this, System.currentTimeMillis());
        if (ev == null) {
            tvProx.setText("Sin programar");
            tvProxDet.setText("No hay turnos activos.");
        } else {
            Calendar hoy = Calendar.getInstance(), cal = Calendar.getInstance();
            cal.setTimeInMillis(ev.cuando);
            boolean esHoy = hoy.get(Calendar.DAY_OF_YEAR) == cal.get(Calendar.DAY_OF_YEAR);
            tvProx.setText((esHoy ? "Hoy, " : "Mañana, ")
                    + new SimpleDateFormat("HH:mm", Locale.US).format(new Date(ev.cuando)));
            tvProxDet.setText("Turno " + ev.turno + " · " + ev.tipo);
        }
        if (Config.soloEvidencia(this)) {
            chipModo.setText("Solo evidencia");
            chipModo.setTextColor(0xFF8A5A00);
            chipModo.getBackground().mutate().setTint(0xFFFFF4DB);
        } else {
            chipModo.setText("Marcación real");
            chipModo.setTextColor(0xFF166534);
            chipModo.getBackground().mutate().setTint(0xFFE7F6EC);
        }
    }

    // ---------------- Permisos ----------------
    private boolean tiene(String permiso) {
        return checkSelfPermission(permiso) == PackageManager.PERMISSION_GRANTED;
    }

    private boolean notifOk() {
        return Build.VERSION.SDK_INT < 33 || tiene(Manifest.permission.POST_NOTIFICATIONS);
    }

    private boolean ubicOk() {
        return tiene(Manifest.permission.ACCESS_FINE_LOCATION);
    }

    private boolean ubicFondoOk() {
        return Build.VERSION.SDK_INT < 29 || tiene(Manifest.permission.ACCESS_BACKGROUND_LOCATION);
    }

    private boolean bateriaOk() {
        return getSystemService(PowerManager.class).isIgnoringBatteryOptimizations(getPackageName());
    }

    private void actualizarPermisos() {
        String s = marca(notifOk()) + "Notificaciones\n"
                + marca(Programador.puedeAlarmasExactas(this)) + "Alarmas exactas\n"
                + marca(ubicOk()) + "Ubicación\n"
                + marca(ubicFondoOk()) + "Ubicación en segundo plano («Permitir todo el tiempo»)\n"
                + marca(bateriaOk()) + "Sin restricción de batería";
        tvPermisos.setText(s);
    }

    private static String marca(boolean ok) {
        return ok ? "✔  " : "✖  ";
    }

    private void pedirSiguientePermiso() {
        if (!notifOk()) {
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, REQ_NOTIF);
        } else if (!Programador.puedeAlarmasExactas(this)) {
            startActivity(new Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM,
                    Uri.parse("package:" + getPackageName())));
        } else if (!ubicOk()) {
            requestPermissions(new String[]{Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION}, REQ_UBIC);
        } else if (!ubicFondoOk()) {
            new AlertDialog.Builder(this)
                    .setTitle("Ubicación en segundo plano")
                    .setMessage("UCV Talent exige la ubicación activa para marcar. Para que la marcación "
                            + "automática funcione con el teléfono bloqueado, elige «Permitir todo el tiempo».")
                    .setPositiveButton("Continuar", (d, w) -> requestPermissions(
                            new String[]{Manifest.permission.ACCESS_BACKGROUND_LOCATION}, REQ_UBIC_FONDO))
                    .setNegativeButton("Ahora no", null)
                    .show();
        } else if (!bateriaOk()) {
            startActivity(new Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                    Uri.parse("package:" + getPackageName())));
        } else {
            Toast.makeText(this, "Todos los permisos están concedidos.", Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        actualizarPermisos();
    }
}
