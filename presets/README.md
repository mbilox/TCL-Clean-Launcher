# Presets

Presets de configuración de los dos modos (`tcl-normal.json`, `tcl-max.json`) y su documentación.
Los archivos se agregan en F3 (ver `../docs/project/PLAN.md`).

Reglas:

- `tcl-normal`: look del upstream (efectos, wallpapers, skins), con `uniform_tiles=false`.
- `tcl-max`: sin animaciones, sin cinematic, sin wallpaper; es el modo que pelea el presupuesto
  de `cold start <= 600 ms` y `PSS+swap <= 30 MB`.
- Nunca `uniform_tiles=true`: medido en la unidad, cuesta +3.972 KB de Native Heap.
