package pe.gabrec.ucvmarcador;

import android.annotation.SuppressLint;
import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.webkit.CookieManager;
import android.webkit.GeolocationPermissions;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import org.json.JSONArray;
import org.json.JSONObject;

/**
 * Motor de marcación: carga UCV Talent en un WebView y ejecuta los mismos pasos que
 * la versión de escritorio (pestaña «Marcación manual», turno, botón «Marcar»).
 * Funciona con un WebView visible (prueba) o sin mostrar (servicio en segundo plano).
 */
public class Marcador {
    public interface AlTerminar {
        void terminado(boolean exito, String resumen);
    }

    private static final long LIMITE_MS = 90_000;

    /** Un paso por llamada; devuelve el estado como texto. */
    private static final String JS_PASO =
            "(function(turno,tipo,soloEv){"
            + "function n(s){return (s||'').replace(/\\s+/g,' ').trim();}"
            + "function vis(e){return e&&e.getClientRects().length>0;}"
            + "var card=document.querySelector('mark-card');"
            + "if(!card){"
            + "  var f=[].slice.call(document.querySelectorAll('input[type=password],input[type=email]')).filter(vis);"
            + "  return f.length?'login':'cargando';}"
            + "var st=window.__ucvSt||(window.__ucvSt={});"
            + "if(!st.tab){"
            + "  var t=[].slice.call(card.querySelectorAll('.mat-tab-label')).filter(function(x){return n(x.textContent).indexOf('Marcación manual')>=0;});"
            + "  if(!t.length)return 'esperando_pestana';"
            + "  t[0].click();st.tab=Date.now();return 'pestana';}"
            + "if(!st.turno){"
            + "  var b=[].slice.call(card.querySelectorAll('button')).filter(function(x){var d=x.querySelector('div');return d&&n(d.textContent)===turno;});"
            + "  if(!b.length)return 'esperando_turno';"
            + "  b[0].click();st.turno=Date.now();return 'turno';}"
            + "if(Date.now()-st.turno<2000)return 'esperando';"
            + "var bl=[].slice.call(card.querySelectorAll('div.my-1')).filter(function(d){var s=d.querySelector(':scope > span');return s&&n(s.textContent).indexOf(tipo)>=0;});"
            + "if(!bl.length)return 'esperando_bloque';"
            + "var et=n(bl[0].querySelector(':scope > span').textContent);"
            + "var bt=[].slice.call(bl[0].querySelectorAll('button')).filter(function(x){return n(x.textContent).indexOf('Marcar')>=0;})[0];"
            + "if(!bt)return 'esperando_boton';"
            + "bt.scrollIntoView({block:'center'});"
            + "if(bt.disabled)return 'deshabilitado|'+et;"
            + "if(soloEv)return 'habilitado|'+et;"
            + "bt.click();return 'clic|'+et;"
            + "})";

    private static final String JS_CONFIRMAR =
            "(function(){var c=document.querySelector('.cdk-overlay-container');if(!c)return 'sin_dialogo';"
            + "var b=[].slice.call(c.querySelectorAll('button')).filter(function(x){var t=x.textContent||'';"
            + "return t.indexOf('Aceptar')>=0||t.indexOf('Confirmar')>=0||t.indexOf('Sí')>=0;})[0];"
            + "if(!b)return 'sin_dialogo';b.click();return 'confirmado';})()";

    /** Lleva a la vista la tarjeta «Mis marcaciones online» y devuelve su rectángulo (px CSS). */
    private static final String JS_RECT =
            "(function(){var c=document.querySelector('mark-card');if(!c)return null;"
            + "var el=c,p=c.parentElement,ancho=window.innerWidth;"
            + "for(var i=0;i<6&&p&&p!==document.body;i++,p=p.parentElement){"
            + "  if(/marcaciones/i.test(el.textContent))break;"
            + "  if(p.getBoundingClientRect().width>ancho*0.99)break;"
            + "  el=p;}"
            + "if(el.getBoundingClientRect().height<50&&c.firstElementChild)el=c.firstElementChild;"
            + "el.scrollIntoView({block:'start'});window.scrollBy(0,-8);"
            + "var r=el.getBoundingClientRect();"
            + "return JSON.stringify({x:r.left,y:r.top,w:r.width,h:r.height,dpr:window.devicePixelRatio||1});})()";

    private final Context ctx;
    private final WebView web;
    private final String turno, tipo;
    private final boolean soloEvidencia;
    private final AlTerminar alTerminar;
    private final Handler h = new Handler(Looper.getMainLooper());
    private long inicio;
    private boolean terminado;
    private String ultimo = "";

    public Marcador(Context ctx, WebView web, String turno, String tipo, boolean soloEvidencia, AlTerminar fin) {
        this.ctx = ctx.getApplicationContext();
        this.web = web;
        this.turno = turno;
        this.tipo = tipo;
        this.soloEvidencia = soloEvidencia;
        this.alTerminar = fin;
    }

    /**
     * Crea y configura un WebView. Activa antes el dibujado del documento completo:
     * sin él, un WebView sin ventana (el del servicio) no dibuja nada al capturarlo
     * y las evidencias salen en blanco. Debe llamarse antes de crear cualquier WebView.
     */
    public static WebView crearWebView(Context c) {
        WebView.enableSlowWholeDocumentDraw();
        WebView w = new WebView(c);
        configurar(w);
        return w;
    }

    @SuppressLint("SetJavaScriptEnabled")
    public static void configurar(WebView w) {
        WebSettings s = w.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setDatabaseEnabled(true);
        s.setGeolocationEnabled(true);
        s.setLoadWithOverviewMode(true);
        s.setUseWideViewPort(true);
        CookieManager.getInstance().setAcceptCookie(true);
        CookieManager.getInstance().setAcceptThirdPartyCookies(w, true);
        w.setWebViewClient(new WebViewClient());
        w.setWebChromeClient(new WebChromeClient() {
            @Override
            public void onGeolocationPermissionsShowPrompt(String origin, GeolocationPermissions.Callback cb) {
                // Solo se comparte la ubicación con UCV Talent.
                cb.invoke(origin, origin != null && origin.contains("ucv.edu.pe"), false);
            }
        });
    }

    public void iniciar() {
        inicio = System.currentTimeMillis();
        Registro.log(ctx, "🚀 Iniciando " + turno + " " + tipo
                + (soloEvidencia ? " (modo solo evidencia)" : ""));
        web.onResume();
        web.resumeTimers();
        web.loadUrl(Config.URL);
        h.postDelayed(this::sondear, 3000);
    }

    public void cancelar() {
        terminado = true;
        h.removeCallbacksAndMessages(null);
    }

    private void sondear() {
        if (terminado) return;
        if (System.currentTimeMillis() - inicio > LIMITE_MS) {
            terminado = true; // detiene el sondeo mientras se captura
            capturar("tiempo_agotado", () -> {
                terminado = false;
                terminar(false, "Tiempo agotado. Último estado: " + ultimo);
            });
            return;
        }
        String js = JS_PASO + "(" + JSONObject.quote(turno) + "," + JSONObject.quote(tipo) + "," + soloEvidencia + ")";
        web.evaluateJavascript(js, r -> procesar(texto(r)));
    }

    private static String texto(String jsResultado) {
        try {
            Object o = new JSONArray("[" + jsResultado + "]").get(0);
            return o == null || o == JSONObject.NULL ? "" : o.toString();
        } catch (Exception e) {
            return "";
        }
    }

    private void procesar(String r) {
        if (terminado) return;
        ultimo = r;
        String estado = r.contains("|") ? r.substring(0, r.indexOf('|')) : r;
        String etiqueta = r.contains("|") ? r.substring(r.indexOf('|') + 1) : "";
        String base = turno + "_" + tipo;
        switch (estado) {
            case "login":
                capturar("sin_sesion", () ->
                        terminar(false, "No hay sesión iniciada. Abre la app y pulsa «Iniciar sesión»."));
                break;
            case "deshabilitado":
                capturar(base + "_deshabilitado", () ->
                        terminar(false, "Botón deshabilitado (" + etiqueta + "): fuera de la ventana permitida."));
                break;
            case "habilitado":
                capturar(base + "_habilitado", () ->
                        terminar(true, "Botón habilitado (" + etiqueta + "). Modo solo evidencia: no se pulsó «Marcar»."));
                break;
            case "clic":
                Registro.log(ctx, "✅ Clic en «Marcar» (" + etiqueta + ")");
                h.postDelayed(() -> capturar(base + "_despues", () ->
                        web.evaluateJavascript(JS_CONFIRMAR, r2 -> {
                            boolean conf = "confirmado".equals(texto(r2));
                            h.postDelayed(() -> capturar(base + "_final", () ->
                                    terminar(true, "Marcación realizada (" + etiqueta + ")"
                                            + (conf ? " y confirmada." : ". Revisa la captura."))), 2500);
                        })), 3000);
                break;
            default:
                h.postDelayed(this::sondear, 1000);
        }
    }

    /**
     * Captura solo la tarjeta de marcaciones (turno, Entrada/Salida y sus horas).
     * Si no la encuentra, captura lo visible. Luego ejecuta "despues".
     */
    @SuppressWarnings("deprecation")
    private void capturar(String etiqueta, Runnable despues) {
        web.evaluateJavascript(JS_RECT, res -> h.postDelayed(() -> {
            android.graphics.Rect recorte = null;
            try {
                JSONObject r = new JSONObject(texto(res));
                float esc = web.getScale();
                if (esc <= 0) esc = (float) r.getDouble("dpr");
                int m = Math.round(8 * esc);
                recorte = new android.graphics.Rect(
                        Math.round((float) r.getDouble("x") * esc) - m,
                        Math.round((float) r.getDouble("y") * esc) - m,
                        Math.round((float) (r.getDouble("x") + r.getDouble("w")) * esc) + m,
                        Math.round((float) (r.getDouble("y") + r.getDouble("h")) * esc) + m);
            } catch (Exception ignored) {
            }
            Evidencias.capturar(ctx, web, etiqueta, recorte);
            if (despues != null) despues.run();
        }, 700));
    }

    private void terminar(boolean exito, String resumen) {
        if (terminado) return;
        terminado = true;
        h.removeCallbacksAndMessages(null);
        CookieManager.getInstance().flush();
        Registro.log(ctx, (exito ? "🎉 " : "❌ ") + turno + " " + tipo + ": " + resumen);
        if (alTerminar != null) alTerminar.terminado(exito, resumen);
    }
}
