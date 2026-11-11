package pe.gabrec.ucvmarcador;

import android.app.Activity;
import android.app.AlertDialog;
import android.os.Bundle;
import android.webkit.WebView;
import android.widget.Button;
import android.widget.TextView;

/** Ejecuta la marcación con el navegador visible, para comprobar cada paso. */
public class PruebaActivity extends Activity {
    private WebView web;
    private Marcador marcador;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        String turno = getIntent().getStringExtra(Programador.EXTRA_TURNO);
        String tipo = getIntent().getStringExtra(Programador.EXTRA_TIPO);

        web = Marcador.crearWebView(this);
        TextView estado = new TextView(this);
        estado.setText(turno + " · " + tipo + (Config.soloEvidencia(this) ? " · solo evidencia" : ""));
        Button cerrar = new Button(this);
        cerrar.setText("Cerrar");
        cerrar.setOnClickListener(v -> finish());
        setContentView(Pantallas.pantalla(this, Pantallas.barra(this, "Prueba de marcación", estado, cerrar), web));

        marcador = new Marcador(this, web, turno, tipo, Config.soloEvidencia(this), (exito, resumen) -> {
            if (isFinishing()) return;
            estado.setText(exito ? "Completado" : "Con problemas");
            new AlertDialog.Builder(this)
                    .setTitle(exito ? "Resultado" : "No se completó")
                    .setMessage(resumen)
                    .setPositiveButton("Aceptar", null)
                    .show();
        });
        marcador.iniciar();
    }

    @Override
    protected void onDestroy() {
        if (marcador != null) marcador.cancelar();
        web.destroy();
        super.onDestroy();
    }
}
