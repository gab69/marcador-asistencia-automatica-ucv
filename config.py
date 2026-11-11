import copy
import json
import os
import sys


def base_dir():
    """Carpeta de la app: junto al .exe si está empaquetada, si no junto a este archivo."""
    if getattr(sys, "frozen", False):
        return os.path.dirname(sys.executable)
    return os.path.dirname(os.path.abspath(__file__))


CONFIG_PATH = os.path.join(base_dir(), "config.json")

DEFAULT = {
    "usuario": "",
    "contrasena": "",
    "modo_solo_evidencia": True,   # True = nunca hace clic en "Marcar"
    "mostrar_navegador": False,    # False = Chrome headless
    "turno_manana": {"activo": True, "entrada": "08:00", "salida": "13:00"},
    "turno_tarde": {"activo": True, "entrada": "14:00", "salida": "18:00"},
    "carpeta_evidencias": "evidencias",
}


def cargar():
    cfg = copy.deepcopy(DEFAULT)
    if os.path.exists(CONFIG_PATH):
        try:
            with open(CONFIG_PATH, "r", encoding="utf-8") as f:
                data = json.load(f)
            for k, v in data.items():
                if isinstance(v, dict) and isinstance(cfg.get(k), dict):
                    cfg[k].update(v)
                else:
                    cfg[k] = v
        except (json.JSONDecodeError, OSError):
            pass
    return cfg


def guardar(cfg):
    with open(CONFIG_PATH, "w", encoding="utf-8") as f:
        json.dump(cfg, f, indent=2, ensure_ascii=False)


def ruta_evidencias(cfg):
    carpeta = cfg.get("carpeta_evidencias", "evidencias")
    if not os.path.isabs(carpeta):
        carpeta = os.path.join(base_dir(), carpeta)
    os.makedirs(carpeta, exist_ok=True)
    return carpeta
