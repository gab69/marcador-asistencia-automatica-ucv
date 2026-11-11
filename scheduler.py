from datetime import datetime, timedelta

TURNOS = [("turno_manana", "Mañana"), ("turno_tarde", "Tarde")]
TIPOS = [("Entrada", "entrada"), ("Salida", "salida")]


def proximas_ejecuciones(cfg, ahora=None):
    """Lista ordenada de (datetime, tipo, turno) para hoy y mañana.
    La marcación se ejecuta exactamente a la hora configurada."""
    ahora = ahora or datetime.now()
    eventos = []
    for turno_key, turno_nombre in TURNOS:
        t = cfg.get(turno_key, {})
        if not t.get("activo"):
            continue
        for tipo, campo in TIPOS:
            hora_str = t.get(campo)
            if not hora_str:
                continue
            h, m = map(int, hora_str.split(":"))
            for delta_dia in (0, 1):
                objetivo = (ahora + timedelta(days=delta_dia)).replace(
                    hour=h, minute=m, second=0, microsecond=0)
                if objetivo > ahora:
                    eventos.append((objetivo, tipo, turno_nombre))
    eventos.sort(key=lambda x: x[0])
    return eventos
