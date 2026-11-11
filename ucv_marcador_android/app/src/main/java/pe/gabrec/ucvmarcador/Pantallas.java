package pe.gabrec.ucvmarcador;

import android.app.Activity;
import android.graphics.Typeface;
import android.view.Gravity;
import android.view.ViewGroup;
import android.webkit.WebView;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

/** Utilidades para las pantallas con WebView (login y prueba visible). */
final class Pantallas {
    private Pantallas() {}

    static int dp(Activity a, int v) {
        return Math.round(v * a.getResources().getDisplayMetrics().density);
    }

    /** Barra superior azul con título, texto de estado y un botón. */
    static LinearLayout barra(Activity a, String titulo, TextView estado, Button boton) {
        LinearLayout barra = new LinearLayout(a);
        barra.setOrientation(LinearLayout.HORIZONTAL);
        barra.setGravity(Gravity.CENTER_VERTICAL);
        barra.setBackgroundColor(a.getColor(R.color.azul_oscuro));
        barra.setPadding(dp(a, 16), dp(a, 10), dp(a, 12), dp(a, 10));

        LinearLayout textos = new LinearLayout(a);
        textos.setOrientation(LinearLayout.VERTICAL);
        TextView t = new TextView(a);
        t.setText(titulo);
        t.setTextColor(0xFFFFFFFF);
        t.setTextSize(16);
        t.setTypeface(Typeface.DEFAULT_BOLD);
        estado.setTextColor(0xFF9AA8C4);
        estado.setTextSize(12);
        textos.addView(t);
        textos.addView(estado);
        barra.addView(textos, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));

        boton.setAllCaps(false);
        boton.setTextColor(0xFFFFFFFF);
        boton.setBackgroundResource(R.drawable.boton_primario);
        boton.setStateListAnimator(null);
        barra.addView(boton, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(a, 40)));
        return barra;
    }

    /**
     * Android 15+ dibuja la app bajo las barras del sistema (edge-to-edge): se deja un
     * margen igual a la barra de estado, la de navegación y el teclado.
     */
    static void respetarBarras(android.view.View raiz) {
        if (android.os.Build.VERSION.SDK_INT < 35) return;
        raiz.setOnApplyWindowInsetsListener((v, ins) -> {
            android.graphics.Insets i = ins.getInsets(android.view.WindowInsets.Type.systemBars()
                    | android.view.WindowInsets.Type.displayCutout()
                    | android.view.WindowInsets.Type.ime());
            v.setPadding(i.left, i.top, i.right, i.bottom);
            return android.view.WindowInsets.CONSUMED;
        });
    }

    static LinearLayout pantalla(Activity a, LinearLayout barra, WebView web) {
        LinearLayout raiz = new LinearLayout(a);
        raiz.setBackgroundColor(a.getColor(R.color.azul_oscuro));
        respetarBarras(raiz);
        raiz.setOrientation(LinearLayout.VERTICAL);
        raiz.addView(barra, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));
        raiz.addView(web, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));
        return raiz;
    }
}
