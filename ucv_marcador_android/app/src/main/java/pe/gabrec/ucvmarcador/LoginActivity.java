package pe.gabrec.ucvmarcador;

import android.app.Activity;
import android.os.Bundle;
import android.webkit.CookieManager;
import android.webkit.WebView;
import android.widget.Button;
import android.widget.TextView;

/** Muestra UCV Talent para iniciar sesión una vez; la sesión queda guardada en la app. */
public class LoginActivity extends Activity {
    private WebView web;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        web = Marcador.crearWebView(this);
        TextView estado = new TextView(this);
        estado.setText("Inicia sesión y luego pulsa «Listo»");
        Button listo = new Button(this);
        listo.setText("Listo");
        listo.setOnClickListener(v -> {
            CookieManager.getInstance().flush();
            Registro.log(this, "🔑 Sesión guardada.");
            finish();
        });
        setContentView(Pantallas.pantalla(this, Pantallas.barra(this, "Iniciar sesión", estado, listo), web));
        web.loadUrl(Config.URL);
    }

    @Override
    public void onBackPressed() {
        if (web.canGoBack()) web.goBack();
        else super.onBackPressed();
    }

    @Override
    protected void onPause() {
        CookieManager.getInstance().flush();
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        web.destroy();
        super.onDestroy();
    }
}
