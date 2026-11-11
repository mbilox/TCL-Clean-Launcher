# Presets

Presets de configuración de los dos modos (`tcl-normal.json`, `tcl-max.json`) y su documentación.
Los archivos se agregan en F3 (ver `../docs/project/PLAN.md`) y son la copia legible de los que
viajan dentro del APK:

| Archivo del repo | Recurso en el APK | Modo |
|---|---|---|
| `presets/tcl-normal.json` | `launcher/app/src/main/res/raw/tcl_normal.json` | Normal |
| `presets/tcl-max.json` | `launcher/app/src/main/res/raw/tcl_max.json` | Máxima optimización |

> Android no permite `-` en nombres de recursos, por eso los archivos de `res/raw` usan `_`. El
> contenido de cada par es idéntico; si se cambia uno, cambiar el otro.

La fila **Modo** de los ajustes del launcher escribe el recurso del modo elegido sobre
`config.json` del directorio externo de la app (`/sdcard/Android/data/ar.TCL.cleanLauncher/files/`),
que es el mismo overlay que usan los temas (`themes/README.md`), y el Home se recrea al volver. La
elección (`normal`/`max`) queda en `SharedPreferences` (`launcher_settings`, clave `mode`) y
sobrevive a un force-close.

Reglas:

- `tcl-normal`: look del upstream (efectos, wallpapers, skins). Es un archivo mínimo
  (`uniform_tiles: false`) para que manden los defaults de `config.xml`; no fija claves de banners.
- `tcl-max`: sin animaciones, sin efectos, sin cinematic, sin wallpaper, sin glow; nombres
  siempre visibles y banners en RGB_565 a 240 dpi. Es el modo que pelea el presupuesto de
  `cold start <= 600 ms` y `PSS+swap <= 30 MB`.
- **Nunca `uniform_tiles=true` como default**: medido en la unidad, cuesta +3.972 KB de Native
  Heap (`docs/project/AUDIT.md` §5). Por eso `tcl-max` no lo activa y `tcl-normal` lo deja en
  `false`.

## La fila Banners

Independiente del modo (decisión del dueño: "más allá del modo ahorro y el modo máximo"). Cicla
tres estados guardados en `launcher_settings` bajo `banner_mode`:

| Estado | Valor | Efecto |
|---|---|---|
| Completos | `full` (default) | Sin override: mandan el tema y `config.xml`. |
| Ahorro de memoria | `lean` | `banner_565=true` + `banner_density=240` sobre lo que diga el tema. |
| Solo iconos | `icons` | `uniform_tiles=true`: ícono sobre tarjeta tintada en vez de banner. |

Precedencia: cuando `banner_mode` no es `full`, gana sobre las claves de banners del tema/preset;
cuando es `full`, el preset decide. Cambiar de Modo no toca `banner_mode` (y escribir el preset
tampoco lo borra), así que la elección de Banners sobrevive a los cambios de modo y a un
force-close.
