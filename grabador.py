"""Grabar los clics que haces una vez a mano y reproducirlos en segundo plano.

- Grabar: abre Chrome visible (con el perfil de la app). Cada clic que haces se
  guarda como un paso. NO se graban teclas (tu contraseña nunca se guarda).
  Al cerrar la ventana, los pasos se guardan en grabaciones/<Turno>_<Tipo>.json
- Reproducir: abre Chrome headless (sin ventana) y repite los clics en orden.
  Antes de hacer clic en un botón «Marcar» comprueba que esté habilitado y
  respeta el «Modo solo evidencia».
"""
import json
import os
import threading
import time
from datetime import datetime

from selenium.common.exceptions import WebDriverException
from selenium.webdriver.common.by import By
from selenium.webdriver.support.ui import WebDriverWait

import config as cfgmod
import worker
from worker import _dashboard_o_login, _lock, crear_driver

# Script que se inyecta en cada página mientras se graba.
JS_GRABADOR = r"""
(function () {
  if (window.__ucvRec) return;
  window.__ucvRec = true;
  var CLICKABLE = 'button, a, input, select, label, [role=button], [role=tab], ' +
                  '[role=menuitem], [role=option], .mat-tab-label, mat-option';

  function texto(el) { return (el.textContent || '').replace(/\s+/g, ' ').trim(); }

  function xpathAbs(el) {
    var parts = [];
    while (el && el.nodeType === 1 && el !== document.documentElement) {
      var i = 1, s = el.previousElementSibling;
      while (s) { if (s.tagName === el.tagName) i++; s = s.previousElementSibling; }
      parts.unshift(el.tagName.toLowerCase() + '[' + i + ']');
      el = el.parentElement;
    }
    return '/html/' + parts.join('/');
  }

  function xpathTexto(el, t) {
    if (!t || t.length > 80) return null;
    var lit;
    if (t.indexOf("'") < 0) lit = "'" + t + "'";
    else if (t.indexOf('"') < 0) lit = '"' + t + '"';
    else return null;
    return '//' + el.tagName.toLowerCase() + '[normalize-space(.)=' + lit + ']';
  }

  document.addEventListener('click', function (ev) {
    try {
      var el = ev.target;
      // Subir hasta un elemento HTML "clicable" (evita svg/path/iconos)
      var c = el.closest ? el.closest(CLICKABLE) : null;
      if (c) el = c;
      while (el && el.namespaceURI !== 'http://www.w3.org/1999/xhtml') el = el.parentElement;
      if (!el) return;
      var t = texto(el);
      var xt = xpathTexto(el, t), idx = 0;
      if (xt) {
        var snap = document.evaluate(xt, document, null, XPathResult.ORDERED_NODE_SNAPSHOT_TYPE, null);
        for (var i = 0; i < snap.snapshotLength; i++) if (snap.snapshotItem(i) === el) { idx = i; break; }
      }
      var paso = { xpath_texto: xt, indice: idx, xpath_abs: xpathAbs(el),
                   texto: t.slice(0, 80), tag: el.tagName.toLowerCase(),
                   url: location.href, t: Date.now() };
      var arr = JSON.parse(localStorage.getItem('__ucvRecPasos') || '[]');
      arr.push(paso);
      localStorage.setItem('__ucvRecPasos', JSON.stringify(arr));
    } catch (e) { /* nunca romper la página */ }
  }, true);
})();
"""

JS_VACIAR = """
var a = localStorage.getItem('__ucvRecPasos') || '[]';
localStorage.removeItem('__ucvRecPasos');
return a;
"""


def carpeta_grabaciones():
    c = os.path.join(cfgmod.base_dir(), "grabaciones")
    os.makedirs(c, exist_ok=True)
    return c


def ruta_grabacion(turno, tipo):
    return os.path.join(carpeta_grabaciones(), f"{turno}_{tipo}.json")


def existe_grabacion(turno, tipo):
    return os.path.exists(ruta_grabacion(turno, tipo))


def borrar_grabacion(turno, tipo):
    p = ruta_grabacion(turno, tipo)
    if os.path.exists(p):
        os.remove(p)
        return True
    return False


def cargar_grabacion(turno, tipo):
    with open(ruta_grabacion(turno, tipo), "r", encoding="utf-8") as f:
        return json.load(f)


def _es_marcar(paso):
    return "marcar" in (paso.get("texto") or "").lower()


# ---------------------------------------------------------------------------
# GRABAR
# ---------------------------------------------------------------------------
def grabar(turno, tipo, log_cb=print, al_terminar=None):
    def tarea():
        if not _lock.acquire(blocking=False):
            log_cb("⚠️ Ya hay un navegador abierto por la app; espera a que termine.")
            return
        driver, pasos = None, []

        def vaciar():
            try:
                pasos.extend(json.loads(driver.execute_script(JS_VACIAR)))
            except Exception:
                pass

        try:
            log_cb(f"⏺ Grabando {turno} {tipo}. Haz los clics para marcar y luego CIERRA la ventana.")
            driver = crear_driver(headless=False)
            driver.execute_cdp_cmd("Page.addScriptToEvaluateOnNewDocument", {"source": JS_GRABADOR})
            driver.get(worker.URL)
            limite = time.time() + 20 * 60
            while time.time() < limite:
                try:
                    if not driver.window_handles:
                        break
                    vaciar()
                except WebDriverException:
                    break  # ventana cerrada
                time.sleep(0.4)
        except Exception as e:
            log_cb(f"❌ Error durante la grabación: {e}")
        finally:
            if driver:
                try:
                    driver.quit()
                except Exception:
                    pass
            _lock.release()

        # Quitar clics repetidos seguidos sobre el mismo elemento
        limpios = []
        for p in pasos:
            if limpios and limpios[-1]["xpath_abs"] == p["xpath_abs"] and p["t"] - limpios[-1]["t"] < 600:
                continue
            limpios.append(p)

        if not limpios:
            log_cb("⚠️ No se grabó ningún clic; no se guardó nada.")
        else:
            datos = {"turno": turno, "tipo": tipo,
                     "creado": datetime.now().isoformat(timespec="seconds"),
                     "pasos": limpios}
            with open(ruta_grabacion(turno, tipo), "w", encoding="utf-8") as f:
                json.dump(datos, f, indent=2, ensure_ascii=False)
            log_cb(f"💾 Grabación guardada ({len(limpios)} pasos):")
            for i, p in enumerate(limpios, 1):
                log_cb(f"   {i}. <{p['tag']}> «{p['texto'] or '(sin texto)'}»")
            if not any(_es_marcar(p) for p in limpios):
                log_cb("⚠️ Ningún paso hace clic en «Marcar». Revisa que hayas marcado.")
        if al_terminar:
            al_terminar()

    threading.Thread(target=tarea, daemon=True).start()


# ---------------------------------------------------------------------------
# REPRODUCIR (en segundo plano)
# ---------------------------------------------------------------------------
class ReproductorWorker(worker.MarcadorWorker):
    def _buscar(self, driver, paso, espera=25):
        def encontrar(d):
            if paso.get("xpath_texto"):
                els = d.find_elements(By.XPATH, paso["xpath_texto"])
                i = paso.get("indice", 0)
                if i < len(els) and els[i].is_displayed():
                    return els[i]
            els = d.find_elements(By.XPATH, paso["xpath_abs"])
            if els and els[0].is_displayed():
                return els[0]
            return False
        return WebDriverWait(driver, espera).until(encontrar)

    @staticmethod
    def _deshabilitado(driver, el):
        return driver.execute_script(
            "var b = arguments[0].closest('button,input'); return !!(b && b.disabled);", el)

    def run(self):
        if not _lock.acquire(blocking=False):
            self.log("⚠️ Ya hay un navegador abierto por la app; se omite esta ejecución.")
            return
        driver = None
        try:
            datos = cargar_grabacion(self.turno, self.tipo)
            pasos = datos["pasos"]
            self.log(f"🎬 Reproduciendo grabación {self.turno} {self.tipo} ({len(pasos)} pasos)...")
            driver = crear_driver(headless=not self.cfg.get("mostrar_navegador", False))
            driver.get(worker.URL)

            if WebDriverWait(driver, 40).until(_dashboard_o_login) == "login" \
                    and not self._login_automatico(driver):
                self.log("❌ No hay sesión iniciada. Usa «Iniciar sesión» en la app.")
                self.capturar(driver, "sin_sesion")
                return
            time.sleep(2)

            for n, paso in enumerate(pasos, 1):
                desc = f"paso {n}/{len(pasos)} «{paso.get('texto') or paso['tag']}»"
                try:
                    el = self._buscar(driver, paso)
                except Exception:
                    self.log(f"❌ No se encontró el {desc}.")
                    self.capturar(driver, f"{self.turno}_{self.tipo}_paso{n}_no_encontrado")
                    return

                if _es_marcar(paso):
                    self.capturar(driver, f"{self.turno}_{self.tipo}_antes")
                    if self._deshabilitado(driver, el):
                        self.log(f"⏳ {desc}: botón deshabilitado (fuera de la ventana permitida).")
                        return
                    if self.cfg.get("modo_solo_evidencia", True):
                        self.log(f"🛡️ {desc}: habilitado. Modo solo evidencia: NO se hace clic.")
                        return

                driver.execute_script("arguments[0].scrollIntoView({block:'center'});", el)
                try:
                    el.click()
                except Exception:
                    driver.execute_script("arguments[0].click();", el)
                self.log(f"   ✔ {desc}")
                time.sleep(1.5)

            time.sleep(2)
            self.capturar(driver, f"{self.turno}_{self.tipo}_final")
            self.log("🎉 Grabación reproducida completa. Revisa la captura final.")
        except Exception as e:
            msg = str(e).splitlines()[0] if str(e) else ""
            self.log(f"❌ Error: {type(e).__name__}: {msg}")
            if driver:
                self.capturar(driver, "error")
        finally:
            if driver:
                try:
                    driver.quit()
                except Exception:
                    pass
            _lock.release()


def lanzar(cfg, tipo, turno, log_cb=None):
    """Usa la grabación si existe; si no, el flujo automático normal."""
    if existe_grabacion(turno, tipo):
        w = ReproductorWorker(cfg, tipo, turno, log_cb)
        w.start()
        return w
    return worker.lanzar_marcacion(cfg, tipo, turno, log_cb)
