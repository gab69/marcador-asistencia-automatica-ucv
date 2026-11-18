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
 * Siempre pulsa «Marcar» cuando el botón está habilitado.
 */
public class Marcador {
    public interface AlTerminar {
        /** @param horaMarcacion ms del clic en «Marcar», o 0 si no se pulsó. */
        void terminado(boolean exito, long horaMarcacion, String resumen);
    }

    private static final long LIMITE_MS = 90_000;

    /** Un paso por llamada; devuelve el estado como texto. */
    private static final String JS_PASO =
            "(function(turno,tipo){"
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
            + "bt.click();return 'clic|'+et;"
            + "})";

    private static final String JS_CONFIRMAR =
            "(function(){var c=document.querySelector('.cdk-overlay-container');if(!c)return 'sin_dialogo';"
            + "var b=[].slice.call(c.querySelectorAll('button')).filter(function(x){var t=x.textContent||'';"
            + "return t.indexOf('Aceptar')>=0||t.indexOf('Confirmar')>=0||t.indexOf('Sí')>=0;})[0];"
            + "if(!b)return 'sin_dialogo';b.click();return 'confirmado';})()";

    /**
     * Un paso del inicio de sesión con las credenciales guardadas: primero rellena los
     * campos visibles y, en la llamada siguiente, envía el formulario. Sirve para
     * formularios de un paso o de dos (usuario y luego contraseña).
     */
    private static final String JS_LOGIN =
            "(function(u,p){"
            + "function vis(e){return e&&e.getClientRects().length>0&&!e.disabled;}"
            + "function q(s,r){return [].slice.call((r||document).querySelectorAll(s)).filter(vis);}"
            + "function poner(el,v){"
            + "  if(el.__ucv===v&&el.value)return false;"
            + "  var d=Object.getOwnPropertyDescriptor(HTMLInputElement.prototype,'value');"
            + "  el.focus();d.set.call(el,v);el.__ucv=v;"
            + "  ['input','change','blur'].forEach(function(n){el.dispatchEvent(new Event(n,{bubbles:true}));});"
            + "  return true;}"
            + "function enviar(el){"
            + "  var f=el.form||el.closest('form'),r=f||document;"
            + "  var b=q('button[type=submit],input[type=submit]',r)[0];"
            + "  if(!b)b=q('button,input[type=button],[role=button]',r).filter(function(x){"
            + "    return /ingres|inici|entrar|siguiente|next|sign.?in|log.?in|acceder|continuar/i.test(x.textContent||x.value||'');})[0];"
            + "  if(b){b.click();return true;}"
            + "  if(f&&f.requestSubmit){f.requestSubmit();return true;}"
            + "  return false;}"
            + "var pw=q('input[type=password]');"
            + "var us=q('input[type=email],input[type=text],input[type=tel],input:not([type])');"
            + "if(pw.length){"
            + "  var c=false;"
            + "  if(us.length&&poner(us[0],u))c=true;"
            + "  if(poner(pw[0],p))c=true;"
            + "  if(c)return 'llenado';"
            + "  return enviar(pw[0])?'enviado_clave':'sin_boton';}"
            + "if(us.length){"
            + "  if(poner(us[0],u))return 'llenado';"
            + "  return enviar(us[0])?'enviado_usuario':'sin_boton';}"
            + "return 'nada';"
            + "})";

    /** Tope de envíos de la contraseña por ejecución, para no bloquear la cuenta. */
    private static final int MAX_ENVIOS_CLAVE = 2;
    private static final int MAX_PASOS_LOGIN = 12;

    private int enviosClave, pasosLogin;
    private boolean loginAvisado;

    private final Context ctx;
    private final WebView web;
    private final String turno, tipo;
    private final AlTerminar alTerminar;
    private final Handler h = new Handler(Looper.getMainLooper());
    private long inicio;
    private boolean terminado;
    private String ultimo = "";
    /** Momento exacto (ms) en que se pulsó «Marcar»; 0 si no se llegó a pulsar. */
    private long horaMarcacion;

    public Marcador(Context ctx, WebView web, String turno, String tipo, AlTerminar fin) {
        this.ctx = ctx.getApplicationContext();
        this.web = web;
        this.turno = turno;
        this.tipo = tipo;
        this.alTerminar = fin;
    }

    /** Crea y configura un WebView. */
    public static WebView crearWebView(Context c) {
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
        Registro.log(ctx, "🚀 Iniciando " + turno + " " + tipo);
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
            terminar(false, "Tiempo agotado. Último estado: " + ultimo);
            return;
        }
        String js = JS_PASO + "(" + JSONObject.quote(turno) + "," + JSONObject.quote(tipo) + ")";
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
        switch (estado) {
            case "login":
                iniciarSesion();
                break;
            case "deshabilitado":
                terminar(false, "Botón deshabilitado (" + etiqueta + "): fuera de la ventana permitida.");
                break;
            case "clic":
                horaMarcacion = System.currentTimeMillis();
                Registro.log(ctx, "✅ Clic en «Marcar» (" + etiqueta + ") a las " + Historial.hms(horaMarcacion));
                h.postDelayed(() -> web.evaluateJavascript(JS_CONFIRMAR, r2 -> {
                    boolean conf = "confirmado".equals(texto(r2));
                    h.postDelayed(() -> terminar(true, "Marcación realizada a las "
                            + Historial.hms(horaMarcacion) + " (" + etiqueta + ")"
                            + (conf ? " y confirmada." : ".")), 2500);
                }), 3000);
                break;
            default:
                h.postDelayed(this::sondear, 1000);
        }
    }

    /** La página pide iniciar sesión: entra con las credenciales guardadas, si las hay. */
    private void iniciarSesion() {
        String usuario = Credenciales.usuario(ctx);
        String clave = Credenciales.hay(ctx) ? Credenciales.clave(ctx) : "";
        if (usuario.isEmpty() || clave.isEmpty()) {
            terminar(false, "No hay sesión iniciada. Abre la app y guarda tu usuario y contraseña.");
            return;
        }
        String url = web.getUrl();
        if (url == null || !url.startsWith("https://")) {
            terminar(false, "La página de inicio de sesión no es segura; no se enviaron las credenciales.");
            return;
        }
        if (enviosClave >= MAX_ENVIOS_CLAVE || pasosLogin >= MAX_PASOS_LOGIN) {
            terminar(false, "No se pudo iniciar sesión con el usuario y la contraseña guardados. Revísalos en la app.");
            return;
        }
        if (!loginAvisado) {
            loginAvisado = true;
            Registro.log(ctx, "🔐 Sesión no iniciada; entrando con las credenciales guardadas...");
        }
        pasosLogin++;
        String js = JS_LOGIN + "(" + JSONObject.quote(usuario) + "," + JSONObject.quote(clave) + ")";
        web.evaluateJavascript(js, r -> {
            if (terminado) return;
            String res = texto(r);
            long espera = 1000;
            if ("enviado_clave".equals(res)) {
                enviosClave++;
                espera = 8000; // tiempo para que el servidor responda antes de reintentar
            } else if ("enviado_usuario".equals(res)) {
                espera = 4000;
            }
            h.postDelayed(this::sondear, espera);
        });
    }

    private void terminar(boolean exito, String resumen) {
        if (terminado) return;
        terminado = true;
        h.removeCallbacksAndMessages(null);
        CookieManager.getInstance().flush();
        Registro.log(ctx, (exito ? "🎉 " : "❌ ") + turno + " " + tipo + ": " + resumen);
        Historial.agregar(ctx, turno, tipo, exito, horaMarcacion, resumen);
        if (alTerminar != null) alTerminar.terminado(exito, horaMarcacion, resumen);
    }
}
