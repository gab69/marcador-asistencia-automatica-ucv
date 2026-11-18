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
    private TextView tvProx, tvProxDet, tvPermisos, tvLog;
    private Spinner spTurno, spTipo;
    private final java.util.concurrent.ExecutorService fondo = java.util.concurrent.Executors.newSingleThreadExecutor();
    private android.widget.LinearLayout llHistorial;
    private TextView tvSinHistorial, tvDias, tvHoy;
    private String firmaHistorial = "";

    private final Runnable refrescoLog = new Runnable() {
        @Override
        public void run() {
            tvLog.setText(Registro.ultimas(MainActivity.this, 40));
            actualizarProxima();
            cargarHistorial();
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
        tvPermisos = findViewById(R.id.tvPermisos);
        tvLog = findViewById(R.id.tvLog);
        spTurno = findViewById(R.id.spTurno);
        spTipo = findViewById(R.id.spTipo);

        findViewById(R.id.btnLogin).setOnClickListener(v -> startActivity(new Intent(this, LoginActivity.class)));
        configurarCredenciales();

        llHistorial = findViewById(R.id.llHistorial);
        tvSinHistorial = findViewById(R.id.tvSinHistorial);
        tvHoy = findViewById(R.id.tvHoy);
        tvDias = findViewById(R.id.tvDias);
        findViewById(R.id.btnLimpiarHistorial).setOnClickListener(v -> {
            Historial.limpiar(this);
            firmaHistorial = "";
            cargarHistorial();
        });

        configurarDias();
        configurarTurno(R.id.swManana, R.id.btnMEnt, R.id.btnMSal, "Mañana");
        configurarTurno(R.id.swTarde, R.id.btnTEnt, R.id.btnTSal, "Tarde");

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
                .setMessage("Se eliminará UCV Marcador junto con su configuración y la sesión guardada.")
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

    // ---------------- Credenciales ----------------
    private void configurarCredenciales() {
        android.widget.EditText etUsuario = findViewById(R.id.etUsuario);
        android.widget.EditText etClave = findViewById(R.id.etClave);
        TextView tvCred = findViewById(R.id.tvCredenciales);
        Runnable mostrar = () -> {
            boolean hay = Credenciales.hay(this);
            etUsuario.setText(Credenciales.usuario(this));
            etClave.setText("");
            etClave.setHint(hay ? "Contraseña guardada (escribe para cambiarla)" : "Contraseña");
            tvCred.setText(hay ? "✔  Credenciales guardadas y cifradas en este teléfono."
                    : "✖  Aún no hay credenciales guardadas.");
        };
        mostrar.run();

        findViewById(R.id.btnGuardarCred).setOnClickListener(v -> {
            String usuario = etUsuario.getText().toString().trim();
            String clave = etClave.getText().toString();
            // Permite cambiar solo el usuario conservando la contraseña ya guardada.
            if (clave.isEmpty() && Credenciales.hay(this)) clave = Credenciales.clave(this);
            if (usuario.isEmpty() || clave.isEmpty()) {
                Toast.makeText(this, "Escribe el usuario y la contraseña.", Toast.LENGTH_SHORT).show();
                return;
            }
            if (Credenciales.guardar(this, usuario, clave)) {
                Registro.log(this, "🔐 Credenciales guardadas.");
                Toast.makeText(this, "Credenciales guardadas.", Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(this, "No se pudieron guardar las credenciales.", Toast.LENGTH_LONG).show();
            }
            mostrar.run();
        });
        findViewById(R.id.btnBorrarCred).setOnClickListener(v -> {
            Credenciales.borrar(this);
            Registro.log(this, "Credenciales borradas.");
            mostrar.run();
        });
    }

    // ---------------- Registro de marcaciones (solo del día actual) ----------------
    private void cargarHistorial() {
        if (fondo.isShutdown()) return;
        fondo.execute(() -> {
            java.util.List<Historial.Entrada> lista = Historial.deHoy(this);
            String hoy = new SimpleDateFormat("yyyyMMdd", Locale.US).format(new Date());
            String firma = hoy + ":" + lista.size() + ":" + (lista.isEmpty() ? 0 : lista.get(0).fecha);
            if (firma.equals(firmaHistorial)) return; // sin cambios
            h.post(() -> {
                if (isDestroyed()) return;
                firmaHistorial = firma;
                mostrarHistorial(lista);
            });
        });
    }

    private void mostrarHistorial(java.util.List<Historial.Entrada> lista) {
        final int envolver = android.view.ViewGroup.LayoutParams.WRAP_CONTENT;
        final int llenar = android.view.ViewGroup.LayoutParams.MATCH_PARENT;
        llHistorial.removeAllViews();
        String hoy = new SimpleDateFormat("EEEE dd/MM/yyyy", new Locale("es", "PE")).format(new Date());
        tvHoy.setText("Hoy, " + hoy);
        tvSinHistorial.setVisibility(lista.isEmpty() ? android.view.View.VISIBLE : android.view.View.GONE);
        findViewById(R.id.btnLimpiarHistorial).setVisibility(
                lista.isEmpty() ? android.view.View.GONE : android.view.View.VISIBLE);
        for (int i = 0; i < lista.size(); i++) {
            Historial.Entrada e = lista.get(i);
            if (i > 0) {
                android.view.View linea = new android.view.View(this);
                linea.setBackgroundColor(getColor(R.color.borde));
                llHistorial.addView(linea, new android.widget.LinearLayout.LayoutParams(llenar, Pantallas.dp(this, 1)));
            }
            android.widget.LinearLayout fila = new android.widget.LinearLayout(this);
            fila.setOrientation(android.widget.LinearLayout.VERTICAL);
            fila.setPadding(0, Pantallas.dp(this, i == 0 ? 2 : 12), 0, Pantallas.dp(this, 12));

            // Turno y tipo + estado
            android.widget.LinearLayout cab = new android.widget.LinearLayout(this);
            cab.setGravity(android.view.Gravity.CENTER_VERTICAL);
            TextView titulo = new TextView(this);
            titulo.setText(e.turno + " · " + e.tipo);
            titulo.setTextSize(15);
            titulo.setTextColor(getColor(R.color.azul_oscuro));
            titulo.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
            cab.addView(titulo, new android.widget.LinearLayout.LayoutParams(0, envolver, 1));
            TextView estado = new TextView(this);
            estado.setTextSize(11);
            estado.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
            estado.setBackgroundResource(R.drawable.chip);
            estado.setPadding(Pantallas.dp(this, 10), Pantallas.dp(this, 4), Pantallas.dp(this, 10), Pantallas.dp(this, 4));
            if (e.exito) {
                estado.setText("✔ Marcada");
                estado.setTextColor(0xFF166534);
                estado.getBackground().mutate().setTint(0xFFE7F6EC);
            } else {
                estado.setText("✖ No completada");
                estado.setTextColor(0xFFB91C1C);
                estado.getBackground().mutate().setTint(0xFFFDECEC);
            }
            cab.addView(estado);
            fila.addView(cab);

            // Hora real de la marcación (hora, minuto y segundo)
            TextView hora = new TextView(this);
            hora.setText((e.horaMarcacion > 0 ? "Marcó a las " : "Intento a las ") + Historial.hms(e.hora()));
            hora.setTextSize(14);
            hora.setTextColor(getColor(R.color.texto));
            fila.addView(hora);

            TextView resumen = new TextView(this);
            resumen.setText(e.resumen);
            resumen.setTextSize(12);
            resumen.setTextColor(getColor(R.color.texto_suave));
            fila.addView(resumen);

            llHistorial.addView(fila);
        }
    }

    // ---------------- Días de la semana ----------------
    private void configurarDias() {
        android.widget.LinearLayout ll = findViewById(R.id.llDias);
        ll.removeAllViews();
        for (int i = 0; i < Config.DIAS.length; i++) {
            final int dia = Config.DIAS[i];
            TextView t = new TextView(this);
            t.setText(Config.DIAS_LETRA[i]);
            t.setContentDescription(Config.DIAS_NOMBRE[i]);
            t.setGravity(android.view.Gravity.CENTER);
            t.setTextSize(15);
            t.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
            t.setBackgroundResource(R.drawable.dia);
            pintarDia(t, Config.diaActivo(this, dia));
            t.setOnClickListener(v -> {
                boolean activo = !Config.diaActivo(this, dia);
                Config.setDiaActivo(this, dia, activo);
                pintarDia(t, activo);
                actualizarResumenDias();
                reprogramar();
            });
            android.widget.LinearLayout.LayoutParams lp =
                    new android.widget.LinearLayout.LayoutParams(0, Pantallas.dp(this, 40), 1);
            if (i < Config.DIAS.length - 1) lp.setMarginEnd(Pantallas.dp(this, 6));
            ll.addView(t, lp);
        }
        actualizarResumenDias();
    }

    private void pintarDia(TextView t, boolean activo) {
        t.setSelected(activo);
        t.setTextColor(activo ? 0xFFFFFFFF : getColor(R.color.texto_suave));
    }

    private void actualizarResumenDias() {
        StringBuilder si = new StringBuilder();
        int n = 0;
        for (int i = 0; i < Config.DIAS.length; i++) {
            if (!Config.diaActivo(this, Config.DIAS[i])) continue;
            if (n++ > 0) si.append(", ");
            si.append(Config.DIAS_NOMBRE[i]);
        }
        if (n == 0) tvDias.setText("Ningún día seleccionado: no se marcará automáticamente.");
        else if (n == Config.DIAS.length) tvDias.setText("Se repite todos los días.");
        else tvDias.setText("Se repite: " + si + ". Los demás días no se marca.");
    }

    /** «Hoy», «Mañana» o «lunes 23/11» según el día del evento. */
    private static String diaTexto(long cuando) {
        Calendar cal = Calendar.getInstance(), ref = Calendar.getInstance();
        cal.setTimeInMillis(cuando);
        for (int i = 0; i <= 1; i++) {
            if (ref.get(Calendar.YEAR) == cal.get(Calendar.YEAR)
                    && ref.get(Calendar.DAY_OF_YEAR) == cal.get(Calendar.DAY_OF_YEAR)) {
                return i == 0 ? "Hoy" : "Mañana";
            }
            ref.add(Calendar.DAY_OF_YEAR, 1);
        }
        String s = new SimpleDateFormat("EEEE dd/MM", new Locale("es", "PE")).format(new Date(cuando));
        return s.substring(0, 1).toUpperCase() + s.substring(1);
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
        Registro.log(this, "Horarios guardados." + (ev == null ? " No hay turnos ni días activos."
                : " Próxima: " + ev.turno + " " + ev.tipo + ", " + diaTexto(ev.cuando).toLowerCase() + " a las "
                + new SimpleDateFormat("HH:mm", Locale.US).format(new Date(ev.cuando))));
        actualizarProxima();
    }

    private void actualizarProxima() {
        Programador.Evento ev = Programador.proximo(this, System.currentTimeMillis());
        if (ev == null) {
            tvProx.setText("Sin programar");
            tvProxDet.setText(Config.hayDiasActivos(this) ? "No hay turnos activos."
                    : "No hay días seleccionados.");
        } else {
            tvProx.setText(diaTexto(ev.cuando) + ", "
                    + new SimpleDateFormat("HH:mm", Locale.US).format(new Date(ev.cuando)));
            tvProxDet.setText("Turno " + ev.turno + " · " + ev.tipo);
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
