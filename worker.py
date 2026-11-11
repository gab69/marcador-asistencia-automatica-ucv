import os
import threading
import time
from datetime import datetime

# Importaciones explícitas: Selenium carga sus módulos de forma perezosa y
# PyInstaller no los detecta si solo se usa "webdriver.ChromeOptions".
from selenium.common.exceptions import WebDriverException
from selenium.webdriver.chrome.options import Options as ChromeOptions
from selenium.webdriver.chrome.service import Service as ChromeService
from selenium.webdriver.chrome.webdriver import WebDriver as ChromeDriver
from selenium.webdriver.common.by import By
from selenium.webdriver.common.keys import Keys
from selenium.webdriver.support import expected_conditions as EC
from selenium.webdriver.support.ui import WebDriverWait

import config as cfgmod

URL = "https://ucvtalent.ucv.edu.pe/dashboards/main-page"

# Evita que dos navegadores usen el mismo perfil a la vez
# (scheduler, botón "Probar" e "Iniciar sesión").
_lock = threading.Lock()


def ruta_perfil():
    """Perfil de Chrome propio de la app: guarda la sesión de UCV Talent."""
    return os.path.join(cfgmod.base_dir(), "chrome_perfil")


def crear_driver(headless):
    options = ChromeOptions()
    options.add_argument(f"--user-data-dir={ruta_perfil()}")
    if headless:
        options.add_argument("--headless=new")
    options.add_argument("--window-size=1366,900")
    options.add_argument("--log-level=3")
    # Selenium Manager descarga el ChromeDriver correcto automáticamente.
    # CREATE_NO_WINDOW evita que aparezca una consola negra del chromedriver.
    servicio = ChromeService()
    if os.name == "nt":
        servicio.creation_flags = 0x08000000
    return ChromeDriver(options=options, service=servicio)


def autoprueba():
    """Verifica que Chrome se pueda abrir (útil en la versión empaquetada)."""
    driver = None
    try:
        opts = ChromeOptions()
        opts.add_argument("--headless=new")
        servicio = ChromeService()
        if os.name == "nt":
            servicio.creation_flags = 0x08000000
        driver = ChromeDriver(options=opts, service=servicio)
        driver.get("about:blank")
        return f"OK Chrome {driver.capabilities.get('browserVersion')}"
    except Exception as e:
        return f"ERROR {type(e).__name__}: {e}"
    finally:
        if driver:
            driver.quit()


def _visibles(driver, css):
    return [e for e in driver.find_elements(By.CSS_SELECTOR, css) if e.is_displayed()]


# Ubica la tarjeta «Mis marcaciones online» (contiene <mark-card>), la lleva a la
# vista y devuelve su posición en la página.
JS_RECT_MARCACIONES = r"""
var c = document.querySelector('mark-card');
if (!c) return null;
var el = c, p = c.parentElement, ancho = window.innerWidth;
// Subir solo hasta la tarjeta que incluye el título «Mis marcaciones online»
for (var i = 0; i < 6 && p && p !== document.body; i++, p = p.parentElement) {
  if (/marcaciones/i.test(el.textContent)) break;
  if (p.getBoundingClientRect().width > ancho * 0.6) break;
  el = p;
}
if (el.getBoundingClientRect().height < 50 && c.firstElementChild) el = c.firstElementChild;
el.scrollIntoView({block: 'start'});
var r = el.getBoundingClientRect();
return {x: r.left + window.scrollX, y: r.top + window.scrollY, w: r.width, h: r.height};
"""


def capturar_marcaciones(driver, ruta):
    """Guarda solo la tarjeta de marcaciones (turno, Entrada/Salida y sus horas).
    Devuelve False si no la encuentra (entonces se captura la pantalla completa)."""
    import base64
    try:
        r = driver.execute_script(JS_RECT_MARCACIONES)
        if not r or r["w"] < 50 or r["h"] < 50:
            return False
        time.sleep(0.6)
        m = 10
        res = driver.execute_cdp_cmd("Page.captureScreenshot", {
            "format": "png",
            "captureBeyondViewport": True,
            "clip": {"x": max(0, r["x"] - m), "y": max(0, r["y"] - m),
                     "width": r["w"] + 2 * m, "height": r["h"] + 2 * m, "scale": 1},
        })
        with open(ruta, "wb") as f:
            f.write(base64.b64decode(res["data"]))
        return True
    except Exception:
        return False


def _dashboard_o_login(driver):
    """Condición de espera: devuelve 'dashboard', 'login' o False."""
    if driver.find_elements(By.TAG_NAME, "mark-card"):
        return "dashboard"
    if _visibles(driver, "input[type='password'], input[type='email']"):
        return "login"
    return False


# ---------------------------------------------------------------------------
# Login manual (una sola vez): abre Chrome visible con el perfil de la app
# ---------------------------------------------------------------------------
def abrir_login_manual(log_cb=print):
    def tarea():
        if not _lock.acquire(blocking=False):
            log_cb("⚠️ Hay una marcación en curso; intenta de nuevo en un momento.")
            return
        driver = None
        try:
            log_cb("🔑 Abriendo Chrome. Inicia sesión y luego CIERRA la ventana.")
            driver = crear_driver(headless=False)
            driver.get(URL)
            limite = time.time() + 15 * 60
            while time.time() < limite:
                try:
                    _ = driver.window_handles
                    if not _:
                        break
                except WebDriverException:
                    break  # el usuario cerró la ventana
                time.sleep(1)
            log_cb("✅ Ventana cerrada. La sesión quedó guardada en el perfil de la app.")
        except Exception as e:
            log_cb(f"❌ Error abriendo Chrome: {e}")
        finally:
            if driver:
                try:
                    driver.quit()
                except Exception:
                    pass
            _lock.release()

    threading.Thread(target=tarea, daemon=True).start()


# ---------------------------------------------------------------------------
# Worker de marcación
# ---------------------------------------------------------------------------
class MarcadorWorker(threading.Thread):
    def __init__(self, cfg, tipo, turno, log_cb=None):
        """tipo: "Entrada" | "Salida"; turno: "Mañana" | "Tarde"."""
        super().__init__(daemon=True)
        self.cfg = dict(cfg)
        self.tipo = tipo
        self.turno = turno
        self.log_cb = log_cb or print

    def log(self, msg):
        self.log_cb(f"[{datetime.now():%H:%M:%S}] {msg}")

    def capturar(self, driver, etiqueta):
        carpeta = cfgmod.ruta_evidencias(self.cfg)
        ts = datetime.now().strftime("%Y%m%d_%H%M%S")
        ruta = os.path.join(carpeta, f"{etiqueta}_{ts}.png")
        try:
            if not capturar_marcaciones(driver, ruta):
                driver.save_screenshot(ruta)
            self.log(f"📸 Evidencia: {ruta}")
        except Exception as e:
            self.log(f"⚠️ No se pudo capturar: {e}")

    def _login_automatico(self, driver):
        """Login genérico (opcional) usando usuario/contraseña de la config.
        Soporta formularios de un paso o de dos pasos (correo -> contraseña)."""
        usuario, clave = self.cfg.get("usuario"), self.cfg.get("contrasena")
        if not usuario or not clave:
            return False
        self.log("🔐 Sesión no iniciada; intentando login con las credenciales guardadas...")
        wait = WebDriverWait(driver, 20)

        pwd = _visibles(driver, "input[type='password']")
        if not pwd:
            # Paso 1: solo correo/usuario
            campo = _visibles(driver, "input[type='email'], input[type='text']")
            if not campo:
                return False
            campo[0].clear()
            campo[0].send_keys(usuario, Keys.ENTER)
            wait.until(lambda d: _visibles(d, "input[type='password']"))
            pwd = _visibles(driver, "input[type='password']")
        else:
            campo = _visibles(driver, "input[type='email'], input[type='text']")
            if campo:
                campo[0].clear()
                campo[0].send_keys(usuario)

        pwd[0].send_keys(clave, Keys.ENTER)
        try:
            WebDriverWait(driver, 40).until(
                EC.presence_of_element_located((By.TAG_NAME, "mark-card")))
            return True
        except Exception:
            return False

    def run(self):
        if not _lock.acquire(blocking=False):
            self.log("⚠️ Ya hay un navegador abierto por la app; se omite esta ejecución.")
            return
        driver = None
        try:
            self.log(f"🚀 Iniciando {self.turno} {self.tipo}...")
            driver = crear_driver(headless=not self.cfg.get("mostrar_navegador", False))
            driver.get(URL)

            # 1) ¿Sesión activa o pantalla de login?
            estado = WebDriverWait(driver, 40).until(_dashboard_o_login)
            if estado == "login" and not self._login_automatico(driver):
                self.log("❌ No hay sesión iniciada. Usa el botón «Iniciar sesión» de la app.")
                self.capturar(driver, "sin_sesion")
                return

            wait = WebDriverWait(driver, 25)

            # 2) Pestaña "Marcación manual"
            tab = wait.until(EC.element_to_be_clickable(
                (By.XPATH, "//mark-card//div[contains(@class,'mat-tab-label')]"
                           "[contains(.,'Marcación manual')]")))
            tab.click()

            # 3) Turno (botones "Mañana" / "Tarde")
            btn_turno = wait.until(EC.element_to_be_clickable(
                (By.XPATH, f"//mark-card//button[.//div[normalize-space()='{self.turno}']]")))
            btn_turno.click()
            time.sleep(2)

            # 4) Bloque Entrada/Salida: <div class="my-1"><span>Entrada 18:00</span> ... <button>Marcar</button>
            bloque = wait.until(EC.presence_of_element_located(
                (By.XPATH,
                 f"//mark-card//div[contains(concat(' ',normalize-space(@class),' '),' my-1 ')]"
                 f"[span[contains(normalize-space(),'{self.tipo}')]]")))
            etiqueta_hora = bloque.find_element(By.XPATH, "./span").text.strip()
            boton = bloque.find_element(By.XPATH, ".//button[contains(normalize-space(),'Marcar')]")
            self.log(f"🕒 Horario en la página: {self.turno} — {etiqueta_hora}")
            self.capturar(driver, f"{self.turno}_{self.tipo}_antes")

            if not boton.is_enabled():
                self.log(f"⏳ {self.turno} {self.tipo}: botón deshabilitado (fuera de la ventana permitida).")
                return

            if self.cfg.get("modo_solo_evidencia", True):
                self.log("🛡️ Botón habilitado. Modo solo evidencia: NO se hace clic.")
                return

            # 5) Clic real
            self.log(f"✅ Marcando {self.turno} {self.tipo}...")
            boton.click()
            time.sleep(3)
            self.capturar(driver, f"{self.turno}_{self.tipo}_despues")

            # 6) Diálogo de confirmación (si existe)
            try:
                confirm = WebDriverWait(driver, 5).until(EC.element_to_be_clickable(
                    (By.XPATH, "//div[contains(@class,'cdk-overlay-container')]//button"
                               "[contains(.,'Aceptar') or contains(.,'Confirmar') or contains(.,'Sí')]")))
                confirm.click()
                time.sleep(2)
                self.capturar(driver, f"{self.turno}_{self.tipo}_final")
                self.log("🎉 Marcación completada.")
            except Exception:
                self.log("ℹ️ No apareció diálogo de confirmación. Revisa la captura.")

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


def lanzar_marcacion(cfg, tipo, turno, log_cb=None):
    w = MarcadorWorker(cfg, tipo, turno, log_cb)
    w.start()
    return w
