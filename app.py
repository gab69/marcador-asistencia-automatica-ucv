import glob
import os
import subprocess
import sys
import threading
from datetime import datetime

from PySide6.QtCore import QObject, Qt, QTime, QTimer, QUrl, Signal
from PySide6.QtGui import QAction, QColor, QDesktopServices, QFont, QIcon, QPainter, QPixmap
from PySide6.QtNetwork import QLocalServer, QLocalSocket
from PySide6.QtWidgets import (
    QApplication, QCheckBox, QComboBox, QFormLayout, QFrame, QGridLayout,
    QHBoxLayout, QLabel, QLineEdit, QListWidget, QListWidgetItem, QMenu,
    QMessageBox, QPushButton, QStackedWidget, QSystemTrayIcon, QTextEdit,
    QTimeEdit, QVBoxLayout, QWidget,
)

import config as cfgmod
import grabador
from scheduler import proximas_ejecuciones
from worker import abrir_login_manual

APP_NOMBRE = "UCV Marcador"
VERSION = "1.2.1"
AUTOR = "@GabRec"
NOMBRE_INSTANCIA = "UCVMarcador_instancia_unica"
# Margen interno: si la PC estuvo suspendida/apagada y se pasó la hora por
# más de estos minutos, la marcación se omite (no se marca tarde).
TOLERANCIA_MIN = 2

# Paleta corporativa
AZUL_OSCURO = "#14213d"
AZUL = "#1c5fa8"
GRIS_FONDO = "#f3f5f8"
BORDE = "#dde3ea"
TEXTO = "#1f2937"
TEXTO_SUAVE = "#6b7280"

ESTILO = f"""
* {{ font-family: 'Segoe UI'; font-size: 9.5pt; color: {TEXTO}; }}
QWidget#raiz, QWidget#contenido {{ background: {GRIS_FONDO}; }}

/* Barra lateral */
QWidget#lateral {{ background: {AZUL_OSCURO}; }}
QLabel#marca {{ color: white; font-size: 12.5pt; font-weight: 700; }}
QLabel#submarca {{ color: #9aa8c4; font-size: 8.5pt; }}
QLabel#seccion {{ color: #6f7f9f; font-size: 7.5pt; font-weight: 700; letter-spacing: 1px; }}
QListWidget#menu {{ background: transparent; border: none; outline: 0; }}
QListWidget#menu::item {{ color: #c7d0e0; padding: 10px 18px; border-left: 3px solid transparent; }}
QListWidget#menu::item:selected {{ background: #1f3157; color: white; border-left: 3px solid #4f9cf0; font-weight: 600; }}
QListWidget#menu::item:hover:!selected {{ background: #1a2a4b; }}
QLabel#pie {{ color: #6f7f9f; font-size: 8pt; }}

/* Encabezado */
QWidget#encabezado {{ background: white; border-bottom: 1px solid {BORDE}; }}
QLabel#titulo {{ font-size: 14pt; font-weight: 600; color: {AZUL_OSCURO}; }}
QLabel#subtitulo, QLabel#ayuda {{ color: {TEXTO_SUAVE}; }}

/* Tarjetas */
QFrame#tarjeta {{ background: white; border: 1px solid {BORDE}; border-radius: 8px; }}
QLabel#tarjetaTitulo {{ color: {TEXTO_SUAVE}; font-size: 8pt; font-weight: 700; letter-spacing: 0.5px; }}
QLabel#nota {{ background: #fff8e6; border: 1px solid #f3d999; border-radius: 6px; padding: 8px 10px; color: #7a5b00; }}
QLabel#valorGrande {{ font-size: 13pt; font-weight: 600; color: {AZUL_OSCURO}; }}

/* Controles */
QPushButton {{ background: white; border: 1px solid #c9d2dd; border-radius: 6px; padding: 6px 14px; color: {TEXTO}; }}
QPushButton:hover {{ background: #f0f4f9; border-color: #aab6c5; }}
QPushButton#primario {{ background: {AZUL}; border: 1px solid {AZUL}; color: white; font-weight: 600; }}
QPushButton#primario:hover {{ background: #174f8c; }}
QPushButton#peligro {{ background: white; border: 1px solid #dc2626; color: #b91c1c; font-weight: 600; }}
QPushButton#peligro:hover {{ background: #fef2f2; }}
QLineEdit, QTimeEdit, QComboBox {{ background: white; border: 1px solid #c9d2dd; border-radius: 6px; padding: 5px 8px; min-height: 18px; }}
QLineEdit:focus, QTimeEdit:focus, QComboBox:focus {{ border: 1px solid {AZUL}; }}
QTextEdit#log {{ background: #fbfcfd; border: 1px solid {BORDE}; border-radius: 6px; font-family: Consolas; font-size: 8.5pt; color: #374151; }}
QCheckBox {{ spacing: 8px; }}
"""

CHIP = "border-radius: 10px; padding: 3px 10px; font-size: 8.5pt; font-weight: 600;"
CHIP_VERDE = f"background:#e7f6ec; color:#166534; {CHIP}"
CHIP_AMBAR = f"background:#fff4db; color:#8a5a00; {CHIP}"
CHIP_GRIS = f"background:#eef1f5; color:#4b5563; {CHIP}"
CHIP_AZUL = f"background:#e6f0fb; color:#174f8c; {CHIP}"


class Senales(QObject):
    mensaje = Signal(str)
    refrescar = Signal()


# ---------------- helpers de UI ----------------
def tarjeta(titulo=None):
    f = QFrame()
    f.setObjectName("tarjeta")
    v = QVBoxLayout(f)
    v.setContentsMargins(16, 14, 16, 14)
    v.setSpacing(10)
    if titulo:
        t = QLabel(titulo.upper())
        t.setObjectName("tarjetaTitulo")
        v.addWidget(t)
    return f, v


def boton(texto, accion, tipo=None):
    b = QPushButton(texto)
    if tipo:
        b.setObjectName(tipo)
    b.setCursor(Qt.PointingHandCursor)
    b.clicked.connect(accion)
    return b


def etiqueta(texto, nombre="ayuda", wrap=True):
    l = QLabel(texto)
    l.setObjectName(nombre)
    l.setWordWrap(wrap)
    return l


def chip(texto, estilo):
    l = QLabel(texto)
    l.setStyleSheet(estilo)
    l.setAlignment(Qt.AlignCenter)
    return l


class VentanaPrincipal(QWidget):
    def __init__(self):
        super().__init__()
        self.setObjectName("raiz")
        self.setWindowTitle(APP_NOMBRE)
        self.resize(1100, 600)
        self.setMinimumSize(960, 540)
        self.cfg = cfgmod.cargar()
        self.sen = Senales()
        self.sen.mensaje.connect(self.append_log)
        self.sen.refrescar.connect(lambda: self.actualizar_grabaciones())
        self.tray = None

        raiz = QHBoxLayout(self)
        raiz.setContentsMargins(0, 0, 0, 0)
        raiz.setSpacing(0)
        raiz.addWidget(self._barra_lateral())

        # Zona derecha: encabezado + (contenido | estado)
        zona = QWidget()
        zona.setObjectName("contenido")
        zv = QVBoxLayout(zona)
        zv.setContentsMargins(0, 0, 0, 0)
        zv.setSpacing(0)
        zv.addWidget(self._encabezado())

        cuerpo = QHBoxLayout()
        cuerpo.setContentsMargins(24, 20, 24, 20)
        cuerpo.setSpacing(20)
        col = QVBoxLayout()
        self.paginas = QStackedWidget()
        col.addWidget(self.paginas, 1)
        pie = QHBoxLayout()
        pie.addStretch()
        pie.addWidget(boton("Guardar cambios", lambda: self.guardar(avisar=True), "primario"))
        col.addLayout(pie)
        cuerpo.addLayout(col, 3)
        cuerpo.addWidget(self._panel_estado(), 2)
        zv.addLayout(cuerpo, 1)
        raiz.addWidget(zona, 1)

        self.secciones = [
            ("Sesión", "Sesión UCV Talent", "Acceso a la plataforma usado por las marcaciones automáticas.", self._pagina_sesion()),
            ("Horarios", "Horarios de marcación", "La marcación se ejecuta exactamente a la hora indicada.", self._pagina_horarios()),
            ("Grabaciones", "Grabaciones de acciones", "Clics grabados una vez y repetidos en segundo plano.", self._pagina_grabaciones()),
            ("Opciones", "Opciones", "Comportamiento de la marcación y del navegador.", self._pagina_opciones()),
            ("Acerca de", "Acerca de", f"{APP_NOMBRE} · versión {VERSION} · desarrollado por {AUTOR}", self._pagina_acerca()),
        ]
        for nombre, _, _, pagina in self.secciones:
            QListWidgetItem(nombre, self.menu)
            self.paginas.addWidget(pagina)
        self.menu.currentRowChanged.connect(self._cambiar_pagina)
        self.menu.setCurrentRow(0)

        self.timer_ui = QTimer(self)
        self.timer_ui.timeout.connect(self.actualizar_proxima)
        self.timer_ui.start(15_000)
        self.actualizar_grabaciones()

        self.detener = threading.Event()
        self.ejecutados = set()
        threading.Thread(target=self._loop_scheduler, daemon=True).start()

    # ====================== estructura ======================
    def _barra_lateral(self):
        w = QWidget()
        w.setObjectName("lateral")
        w.setFixedWidth(210)
        v = QVBoxLayout(w)
        v.setContentsMargins(0, 22, 0, 16)
        v.setSpacing(0)
        cab = QVBoxLayout()
        cab.setContentsMargins(20, 0, 20, 22)
        cab.setSpacing(2)
        cab.addWidget(etiqueta(APP_NOMBRE, "marca", False))
        cab.addWidget(etiqueta("Gestión de asistencia", "submarca", False))
        v.addLayout(cab)
        sec = etiqueta("MENÚ", "seccion", False)
        sec.setContentsMargins(20, 0, 0, 6)
        v.addWidget(sec)
        self.menu = QListWidget()
        self.menu.setObjectName("menu")
        v.addWidget(self.menu, 1)
        pie = etiqueta(f"Versión {VERSION}\nDesarrollado por {AUTOR}", "pie", False)
        pie.setContentsMargins(20, 0, 0, 0)
        v.addWidget(pie)
        return w

    def _encabezado(self):
        w = QWidget()
        w.setObjectName("encabezado")
        w.setFixedHeight(72)
        h = QHBoxLayout(w)
        h.setContentsMargins(24, 10, 24, 10)
        t = QVBoxLayout()
        t.setSpacing(0)
        self.lbl_titulo = etiqueta("", "titulo", False)
        self.lbl_subtitulo = etiqueta("", "subtitulo", False)
        t.addWidget(self.lbl_titulo)
        t.addWidget(self.lbl_subtitulo)
        h.addLayout(t)
        h.addStretch()
        self.chip_modo = chip("", CHIP_GRIS)
        self.chip_modo.setFixedHeight(26)
        h.addWidget(self.chip_modo, alignment=Qt.AlignVCenter)
        return w

    def _cambiar_pagina(self, i):
        _, titulo, sub, _ = self.secciones[i]
        self.paginas.setCurrentIndex(i)
        self.lbl_titulo.setText(titulo)
        self.lbl_subtitulo.setText(sub)

    # ====================== páginas ======================
    def _pagina_sesion(self):
        w = QWidget()
        v = QVBoxLayout(w)
        v.setContentsMargins(0, 0, 0, 0)
        fila = QHBoxLayout()
        fila.setSpacing(16)

        c1, l1 = tarjeta("Inicio de sesión manual · recomendado")
        l1.addWidget(etiqueta("Se abrirá Chrome. Inicia sesión normalmente y luego cierra la "
                              "ventana. La sesión queda guardada y tu contraseña no se almacena."))
        l1.addStretch()
        l1.addWidget(boton("Iniciar sesión", lambda: abrir_login_manual(self._log), "primario"),
                     alignment=Qt.AlignLeft)
        fila.addWidget(c1, 1)

        c2, l2 = tarjeta("Login automático · opcional")
        f = QFormLayout()
        f.setSpacing(10)
        self.in_user = QLineEdit(self.cfg.get("usuario", ""))
        self.in_pass = QLineEdit(self.cfg.get("contrasena", ""))
        self.in_pass.setEchoMode(QLineEdit.Password)
        f.addRow("Usuario", self.in_user)
        f.addRow("Contraseña", self.in_pass)
        l2.addLayout(f)
        l2.addWidget(etiqueta("Solo se usa si la sesión expira. Se guarda sin cifrar en config.json."))
        fila.addWidget(c2, 1)

        v.addLayout(fila)
        v.addStretch()
        return w

    def _pagina_horarios(self):
        w = QWidget()
        v = QVBoxLayout(w)
        v.setContentsMargins(0, 0, 0, 0)
        v.setSpacing(16)
        fila = QHBoxLayout()
        fila.setSpacing(16)

        def caja_turno(nombre, datos):
            c, l = tarjeta(f"Turno {nombre}")
            chk = QCheckBox(f"Marcar turno {nombre}")
            chk.setChecked(datos["activo"])
            ent = self._time_edit(datos["entrada"])
            sal = self._time_edit(datos["salida"])
            l.addWidget(chk)
            g = QGridLayout()
            g.setHorizontalSpacing(12)
            g.setVerticalSpacing(8)
            g.addWidget(etiqueta("Hora de marcación"), 0, 1)
            g.addWidget(QLabel("Entrada"), 1, 0)
            g.addWidget(ent, 1, 1)
            g.addWidget(QLabel("Salida"), 2, 0)
            g.addWidget(sal, 2, 1)
            g.setColumnStretch(1, 1)
            l.addLayout(g)
            fila.addWidget(c, 1)
            return chk, ent, sal

        self.chk_manana, self.tm_ent, self.tm_sal = caja_turno("Mañana", self.cfg["turno_manana"])
        self.chk_tarde, self.tt_ent, self.tt_sal = caja_turno("Tarde", self.cfg["turno_tarde"])
        v.addLayout(fila)
        v.addWidget(etiqueta(
            "Importante: UCV Talent solo permite marcar la salida entre 20 y 1 minuto antes de la hora "
            "de salida programada. Si tu salida es 22:00, configura por ejemplo 21:55. "
            "La entrada se puede marcar hasta 20 minutos después de la hora programada.", "nota"))
        v.addStretch()
        return w

    def _pagina_grabaciones(self):
        w = QWidget()
        v = QVBoxLayout(w)
        v.setContentsMargins(0, 0, 0, 0)
        c, l = tarjeta("Estado por turno")
        l.addWidget(etiqueta("Graba una vez tus clics (pestaña «Marcación manual», turno y «Marcar»). "
                             "Solo se graban clics, nunca lo que escribes. Sin grabación se usa el "
                             "flujo automático integrado."))
        grid = QGridLayout()
        grid.setHorizontalSpacing(12)
        grid.setVerticalSpacing(10)
        for col, tipo in enumerate(("Entrada", "Salida"), start=1):
            grid.addWidget(etiqueta(tipo.upper(), "tarjetaTitulo", False), 0, col, alignment=Qt.AlignCenter)
        self.celdas_rec = {}
        for fila_i, turno in enumerate(("Mañana", "Tarde"), start=1):
            grid.addWidget(etiqueta(turno, "valorGrande", False), fila_i, 0)
            for col, tipo in enumerate(("Entrada", "Salida"), start=1):
                celda = QFrame()
                celda.setStyleSheet(f"QFrame {{ background:{GRIS_FONDO}; border-radius:8px; }}")
                cv = QVBoxLayout(celda)
                cv.setContentsMargins(12, 10, 12, 10)
                est = QLabel()
                cv.addWidget(est, alignment=Qt.AlignCenter)
                bh = QHBoxLayout()
                bh.addWidget(boton("Grabar", lambda _=False, t=turno, ti=tipo: self.grabar(t, ti), "primario"))
                bh.addWidget(boton("Borrar", lambda _=False, t=turno, ti=tipo: self.borrar_grabacion(t, ti)))
                cv.addLayout(bh)
                grid.addWidget(celda, fila_i, col)
                self.celdas_rec[(turno, tipo)] = est
        grid.setColumnStretch(1, 1)
        grid.setColumnStretch(2, 1)
        l.addLayout(grid)
        v.addWidget(c)
        v.addStretch()
        return w

    def _pagina_opciones(self):
        w = QWidget()
        v = QVBoxLayout(w)
        v.setContentsMargins(0, 0, 0, 0)
        fila = QHBoxLayout()
        fila.setSpacing(16)

        c1, l1 = tarjeta("Modo de marcación")
        self.chk_solo_ev = QCheckBox("Modo solo evidencia (no hace clic en «Marcar»)")
        self.chk_solo_ev.setChecked(self.cfg.get("modo_solo_evidencia", True))
        l1.addWidget(self.chk_solo_ev)
        l1.addWidget(etiqueta("Recomendado mientras compruebas que las capturas son correctas."))
        l1.addStretch()
        fila.addWidget(c1, 1)

        c2, l2 = tarjeta("Navegador")
        self.chk_visible = QCheckBox("Mostrar el navegador al marcar")
        self.chk_visible.setChecked(self.cfg.get("mostrar_navegador", False))
        l2.addWidget(self.chk_visible)
        l2.addWidget(etiqueta("Desactivado: Chrome trabaja en segundo plano sin ventana. "
                              "Actívalo para depurar o si la ubicación no funciona en segundo plano."))
        l2.addStretch()
        fila.addWidget(c2, 1)

        v.addLayout(fila)
        v.addStretch()
        return w

    def _pagina_acerca(self):
        w = QWidget()
        v = QVBoxLayout(w)
        v.setContentsMargins(0, 0, 0, 0)
        fila = QHBoxLayout()
        fila.setSpacing(16)

        c1, l1 = tarjeta("Datos de la aplicación")
        l1.addWidget(etiqueta("Configuración, sesión, grabaciones y evidencias se guardan en:"))
        ruta = etiqueta(cfgmod.base_dir())
        ruta.setTextInteractionFlags(Qt.TextSelectableByMouse)
        ruta.setStyleSheet(f"color:{TEXTO}; font-family:Consolas;")
        l1.addWidget(ruta)
        l1.addStretch()
        l1.addWidget(boton("Abrir carpeta", lambda: QDesktopServices.openUrl(
            QUrl.fromLocalFile(cfgmod.base_dir()))), alignment=Qt.AlignLeft)
        fila.addWidget(c1, 1)

        c2, l2 = tarjeta("Desinstalar")
        l2.addWidget(etiqueta("Elimina la aplicación junto con su configuración, la sesión guardada, "
                              "las grabaciones y las evidencias. Copia antes las capturas que quieras conservar."))
        l2.addStretch()
        l2.addWidget(boton("Desinstalar UCV Marcador", self.desinstalar, "peligro"), alignment=Qt.AlignLeft)
        fila.addWidget(c2, 1)

        v.addLayout(fila)
        v.addWidget(etiqueta("Requisitos: Google Chrome instalado y conexión a internet."))
        credito = etiqueta(f"Desarrollado por {AUTOR}", "valorGrande", False)
        v.addWidget(credito)
        v.addStretch()
        return w

    def _panel_estado(self):
        panel = QWidget()
        v = QVBoxLayout(panel)
        v.setContentsMargins(0, 0, 0, 0)
        v.setSpacing(16)

        c, l = tarjeta("Próxima marcación")
        self.lbl_prox = etiqueta("", "valorGrande")
        self.lbl_prox_det = etiqueta("")
        l.addWidget(self.lbl_prox)
        l.addWidget(self.lbl_prox_det)
        v.addWidget(c)

        c2, l2 = tarjeta("Prueba manual")
        h = QHBoxLayout()
        self.cb_turno = QComboBox()
        self.cb_turno.addItems(["Mañana", "Tarde"])
        self.cb_tipo = QComboBox()
        self.cb_tipo.addItems(["Entrada", "Salida"])
        h.addWidget(self.cb_turno, 1)
        h.addWidget(self.cb_tipo, 1)
        h.addWidget(boton("Ejecutar", self.probar, "primario"))
        l2.addLayout(h)
        l2.addWidget(boton("Ver evidencias", self.abrir_evidencias))
        v.addWidget(c2)

        c3, l3 = tarjeta("Registro de actividad")
        self.txt_log = QTextEdit()
        self.txt_log.setObjectName("log")
        self.txt_log.setReadOnly(True)
        l3.addWidget(self.txt_log, 1)
        v.addWidget(c3, 1)
        return panel

    # ====================== utilidades ======================
    def _time_edit(self, hhmm):
        t = QTimeEdit()
        h, m = map(int, hhmm.split(":"))
        t.setTime(QTime(h, m))
        t.setDisplayFormat("HH:mm")
        return t

    def append_log(self, msg):
        self.txt_log.append(msg)

    def _log(self, msg):
        self.sen.mensaje.emit(msg)

    def actualizar_proxima(self):
        ev = proximas_ejecuciones(self.cfg)
        if ev:
            prox, tipo, turno = ev[0]
            dia = "Hoy" if prox.date() == datetime.now().date() else "Mañana"
            origen = "grabación" if grabador.existe_grabacion(turno, tipo) else "flujo automático"
            self.lbl_prox.setText(f"{dia}, {prox:%H:%M}")
            self.lbl_prox_det.setText(f"Turno {turno} · {tipo} · {origen}")
        else:
            self.lbl_prox.setText("Sin programar")
            self.lbl_prox_det.setText("No hay turnos activos.")
        if self.cfg.get("modo_solo_evidencia", True):
            self.chip_modo.setText("Modo solo evidencia")
            self.chip_modo.setStyleSheet(CHIP_AMBAR)
        else:
            self.chip_modo.setText("Marcación real activa")
            self.chip_modo.setStyleSheet(CHIP_VERDE)

    def abrir_evidencias(self):
        QDesktopServices.openUrl(QUrl.fromLocalFile(cfgmod.ruta_evidencias(self.cfg)))

    # ====================== acciones ======================
    def guardar(self, avisar=False):
        nuevo = dict(self.cfg)
        nuevo["usuario"] = self.in_user.text().strip()
        nuevo["contrasena"] = self.in_pass.text()
        nuevo["modo_solo_evidencia"] = self.chk_solo_ev.isChecked()
        nuevo["mostrar_navegador"] = self.chk_visible.isChecked()
        nuevo["turno_manana"] = {
            "activo": self.chk_manana.isChecked(),
            "entrada": self.tm_ent.time().toString("HH:mm"),
            "salida": self.tm_sal.time().toString("HH:mm"),
        }
        nuevo["turno_tarde"] = {
            "activo": self.chk_tarde.isChecked(),
            "entrada": self.tt_ent.time().toString("HH:mm"),
            "salida": self.tt_sal.time().toString("HH:mm"),
        }
        for k in ("minutos_antes", "minutos_despues"):  # ya no se usan
            nuevo.pop(k, None)
        self.cfg = nuevo  # reemplazo atómico: el scheduler lee self.cfg
        cfgmod.guardar(self.cfg)
        self.actualizar_proxima()
        self._log("Configuración guardada.")
        if avisar:
            QMessageBox.information(self, "Guardado", "Configuración guardada correctamente.")

    def probar(self):
        self.guardar()
        tipo, turno = self.cb_tipo.currentText(), self.cb_turno.currentText()
        self._log(f"▶ Prueba manual: {turno} {tipo}")
        grabador.lanzar(self.cfg, tipo, turno, self._log)

    def actualizar_grabaciones(self):
        for (turno, tipo), lbl in self.celdas_rec.items():
            if grabador.existe_grabacion(turno, tipo):
                try:
                    n = len(grabador.cargar_grabacion(turno, tipo)["pasos"])
                except Exception:
                    n = "?"
                lbl.setText(f"Grabada · {n} pasos")
                lbl.setStyleSheet(CHIP_VERDE)
            else:
                lbl.setText("Flujo automático")
                lbl.setStyleSheet(CHIP_GRIS)
        self.actualizar_proxima()

    def grabar(self, turno, tipo):
        if grabador.existe_grabacion(turno, tipo):
            r = QMessageBox.question(self, "Reemplazar",
                                     f"Ya existe una grabación de {turno} {tipo}. ¿Reemplazarla?")
            if r != QMessageBox.Yes:
                return
        QMessageBox.information(
            self, "Grabar",
            "Se abrirá Chrome.\n\n1. Haz los clics como siempre: pestaña «Marcación manual», "
            f"turno «{turno}» y «Marcar» en {tipo}.\n2. Cuando termines, CIERRA la ventana de Chrome.\n\n"
            "Solo se graban clics; no se guarda nada de lo que escribes.")
        grabador.grabar(turno, tipo, self._log, al_terminar=self.sen.refrescar.emit)

    def borrar_grabacion(self, turno, tipo):
        if not grabador.existe_grabacion(turno, tipo):
            return
        if QMessageBox.question(self, "Borrar", f"¿Borrar la grabación de {turno} {tipo}?") != QMessageBox.Yes:
            return
        grabador.borrar_grabacion(turno, tipo)
        self._log(f"Grabación {turno} {tipo} borrada; se usará el flujo automático.")
        self.actualizar_grabaciones()

    def desinstalar(self):
        desinstaladores = sorted(glob.glob(os.path.join(cfgmod.base_dir(), "unins*.exe")))
        if not getattr(sys, "frozen", False) or not desinstaladores:
            QMessageBox.information(
                self, "Desinstalar",
                "La desinstalación solo está disponible en la versión instalada.\n"
                "También puedes desinstalar desde Configuración de Windows › Aplicaciones.")
            return
        r = QMessageBox.warning(
            self, "Desinstalar",
            "Se cerrará UCV Marcador y se eliminará junto con su configuración, sesión, "
            "grabaciones y evidencias.\n\n¿Continuar?",
            QMessageBox.Yes | QMessageBox.No, QMessageBox.No)
        if r != QMessageBox.Yes:
            return
        subprocess.Popen([desinstaladores[-1]], close_fds=True)
        self.salir()

    def salir(self):
        self.detener.set()
        if self.tray:
            self.tray.hide()
        QApplication.instance().quit()

    # ====================== programador ======================
    def _loop_scheduler(self):
        self._log("Programador iniciado.")
        ultimo_chequeo = datetime.now()
        while not self.detener.is_set():
            try:
                ahora = datetime.now()
                cfg = self.cfg
                for objetivo, tipo, turno in proximas_ejecuciones(cfg, ultimo_chequeo):
                    if objetivo > ahora:
                        break
                    clave = (objetivo.isoformat(), tipo, turno)
                    if clave in self.ejecutados:
                        continue
                    self.ejecutados.add(clave)
                    if (ahora - objetivo).total_seconds() > TOLERANCIA_MIN * 60:
                        self._log(f"⏭ Omitido {turno} {tipo} ({objetivo:%H:%M}): la PC estaba apagada o suspendida.")
                        continue
                    self._log(f"⏰ {objetivo:%H:%M} — ejecutando {turno} {tipo}")
                    grabador.lanzar(cfg, tipo, turno, self._log)
                ultimo_chequeo = ahora
            except Exception as e:
                self._log(f"❌ Programador: {e}")
            self.detener.wait(5)

    def mostrar(self):
        self.showNormal()
        self.raise_()
        self.activateWindow()

    def closeEvent(self, event):
        if self.tray and self.tray.isVisible():
            event.ignore()
            self.hide()
            self.tray.showMessage(APP_NOMBRE, "Sigue ejecutándose en la bandeja del sistema.",
                                  QSystemTrayIcon.Information, 3000)
        else:
            event.accept()


def crear_icono():
    pm = QPixmap(64, 64)
    pm.fill(Qt.transparent)
    p = QPainter(pm)
    p.setRenderHint(QPainter.Antialiasing)
    p.setBrush(QColor(AZUL_OSCURO))
    p.setPen(Qt.NoPen)
    p.drawRoundedRect(4, 4, 56, 56, 12, 12)
    p.setPen(QColor("white"))
    f = QFont("Segoe UI", 26, QFont.Bold)
    p.setFont(f)
    p.drawText(pm.rect(), Qt.AlignCenter, "M")
    p.end()
    return QIcon(pm)


def main():
    if "--autoprueba" in sys.argv:
        from worker import autoprueba
        with open(os.path.join(cfgmod.base_dir(), "autoprueba.txt"), "w", encoding="utf-8") as f:
            f.write(autoprueba())
        return

    app = QApplication(sys.argv)
    app.setQuitOnLastWindowClosed(False)

    # Instancia única: si ya está abierta, se le pide que se muestre y salimos
    sock = QLocalSocket()
    sock.connectToServer(NOMBRE_INSTANCIA)
    if sock.waitForConnected(300):
        sock.write(b"mostrar")
        sock.waitForBytesWritten(300)
        return
    QLocalServer.removeServer(NOMBRE_INSTANCIA)
    servidor = QLocalServer()
    servidor.listen(NOMBRE_INSTANCIA)

    app.setStyleSheet(ESTILO)
    icono = crear_icono()
    app.setWindowIcon(icono)
    ventana = VentanaPrincipal()
    servidor.newConnection.connect(lambda: (servidor.nextPendingConnection(), ventana.mostrar()))

    tray = QSystemTrayIcon(icono, app)
    ventana.tray = tray
    menu = QMenu()
    act_mostrar = QAction("Mostrar", menu)
    act_mostrar.triggered.connect(ventana.mostrar)
    act_salir = QAction("Salir", menu)
    act_salir.triggered.connect(ventana.salir)
    menu.addAction(act_mostrar)
    menu.addAction(act_salir)
    tray.setContextMenu(menu)
    tray.setToolTip(APP_NOMBRE)
    tray.activated.connect(lambda r: ventana.mostrar() if r == QSystemTrayIcon.DoubleClick else None)
    tray.show()

    if "--minimizado" not in sys.argv:
        ventana.show()
    sys.exit(app.exec())


if __name__ == "__main__":
    main()
