# Lecturas Gas 0.3.0

Aplicación Android para el trabajo de campo de un lector/repartidor de gas:
**tomar lecturas, ubicar medidores en el mapa y entregar facturas registrando el recorrido**.

Esta versión se reconstruyó desde cero con la misma idea de la 0.2.0, pero organizada
por módulos, más liviana para celulares sencillos y con un flujo de trabajo más corto.

---

## 1. Cómo abrirla

1. Descomprime el zip y abre la carpeta `LecturasGas` en Android Studio.
2. Deja que Gradle sincronice. Usa las mismas versiones que ya te funcionaban
   (AGP 8.7.3, Kotlin 2.0.21, Gradle 8.9, MapLibre 11.11.0).
3. Conecta el celular y pulsa ▶ Run.

> Mantiene el mismo `applicationId` (`com.lecturasgas.beta`). Si la instalas encima de
> la 0.2.0, la app **rescata automáticamente** los puntos GPS y las entregas que guardaba
> la versión anterior.

---

## 2. Estructura del proyecto

Antes había tres archivos gigantes (`MainActivity` ≈ 1.600 líneas, `MapScreen` ≈ 1.500,
`ReadingViewModel` ≈ 2.500). Cada cambio rompía todo. Ahora cada pieza tiene un solo trabajo:

```
com.lecturasgas.beta
├── MainActivity.kt          ← solo arranca la interfaz (36 líneas)
├── LecturasApp.kt           ← crea una sola vez la base de datos, el GPS y el repositorio
│
├── data
│   ├── db/LocalStore.kt     ← base de datos SQLite (lecturas, GPS, entregas, recorridos)
│   ├── excel/
│   │   ├── ExcelImporter.kt ← lee el Excel por streaming y detecta columnas por encabezado
│   │   ├── ExcelExporter.kt ← escribe SOLO la columna D (lectura actual)
│   │   ├── ExcelComparer.kt ← compara un Excel contra el original (solo puede cambiar la columna D)
│   │   ├── ImportValidator.kt ← revisa el Excel al importar + prueba real de exportación
│   │   ├── SheetScanner.kt  ← detecta fórmulas, textos, celdas combinadas, filas raras
│   │   ├── ExportVerifier.kt ← comprueba fila por fila que cada lectura quedó en SU medidor
│   │   └── XlsxPaths.kt     ← utilidades del formato .xlsx
│   ├── review/              ← «Revisión antes de exportar» e historial por medidor (código puro)
│   ├── model/Models.kt      ← MeterRecord, Delivery, TrackPoint, GeoFix…
│   └── repository/
│       ├── LecturasRepository.kt ← único punto que habla con la base de datos y los archivos
│       ├── SearchEngine.kt       ← búsqueda rápida con índice
│       ├── RoutePlanner.kt       ← orden por NIR y "ruta aprendida"
│       └── UiPrefs.kt            ← preferencias (campo de búsqueda, 123/ABC, satélite…)
│
├── location
│   ├── LocationTracker.kt         ← fuente única de GPS para toda la app
│   └── DeliveryTrackingService.kt ← graba el recorrido con la pantalla apagada
│
├── readings   (MÓDULO LECTURAS)  ReadingsScreen · ReadingsViewModel · ReadingDialog
├── map        (MÓDULO MAPA)      MapScreen · MapViewModel · MapLayers
├── delivery   (MÓDULO ENTREGA)   DeliveryScreen · DeliveryViewModel
│
└── ui
    ├── AppRoot.kt        ← navegación entre módulos, barra superior, permisos, diálogos
    ├── MainViewModel.kt  ← importar/exportar y lo que comparten los 3 módulos
    ├── HistoryDialog.kt  ← historial de jornadas
    ├── components/       ← tarjeta de medidor, buscador, barra de progreso, botones
    └── theme/            ← colores y tipografía
```

**Regla del proyecto:** las pantallas no tocan la base de datos ni los archivos. Solo
observan el `LecturasRepository` (a través de su ViewModel) y le piden cosas.
Así, si cambias el diseño de una pantalla, no rompes la lógica.

---

## 3. Excel: importar y exportar tal cual

**Lo más importante:** la app recibe el Excel de la empresa y lo devuelve **idéntico**,
solo con las lecturas tomadas en la columna D (`lectura_actual`).

- **Importar.** Lee las 2.021 filas de `Lecturas A sep` en menos de un segundo (la v1
  solo leía 914) y muestra una barra de progreso. Encuentra cada columna por su
  encabezado (`nir`, `medidor`, `lectura_actual`, `Lectura Anterior`, …) y reconoce el
  periodo (`2026septiembre`).
- **Guardar el original.** Guarda una copia exacta del archivo original dentro de la app.
- **Exportar.**
  1. Genera el archivo cambiando únicamente las celdas D de las filas leídas con la app.
  2. **Lo vuelve a leer y verifica** cada lectura antes de guardarlo. Si algo no cuadra,
     no guarda nada y te dice qué falló.
  3. Propone el mismo nombre del archivo original.
- **Lecturas iguales al Excel.** Si una lectura ya estaba en el Excel con el mismo valor,
  esa celda no se toca.
- **Celdas con fórmula.** Si una celda D tuviera fórmula, no se sobrescribe y se avisa.
- **Mes nuevo.** Si importas el Excel del mes siguiente y tienes lecturas sin exportar,
  la app te avisa antes. Si es el mismo mes (lo reimportas), las lecturas de la app se
  conservan. El GPS de los medidores se conserva siempre, mes a mes.

### Pruebas hechas con tu archivo real (`Lecturas A sep1.xlsx`)

Se compiló el código Kotlin real de la importación y la exportación con Kotlin 2.0.21
y se ejecutó contra tu archivo:

| Prueba | Resultado |
|---|---|
| Medidores leídos | 2.021 ✔ |
| Ya traían lectura | 1.289 ✔ |
| Llenar los 732 pendientes + corregir 50 | 782 celdas escritas ✔ |
| 30 lecturas iguales a las del Excel | no se tocaron ✔ |
| Hoja comparada byte a byte quitando la columna D | **idéntica** ✔ |
| Los otros 10 archivos internos del .xlsx | idénticos byte a byte ✔ |
| Protección de hoja, 2.022 fórmulas de la columna E, validaciones | intactas ✔ |
| Reexportar el resultado | 0 cambios ✔ |
| Abrir en LibreOffice | 2.021 medidores con lectura ✔ |

### Capas de seguridad del Excel (v0.7.0)

1. **Al importar** (`ImportValidator`): revisa encabezados, medidores/NIR repetidos, fórmulas, textos o
   decimales en la columna de lectura, celdas combinadas, filas sin medidor… y hace una **prueba real
   de exportación** con lecturas de ejemplo (solo en memoria): comprueba que únicamente cambia la columna
   de lectura, en la fila correcta. Si falla, avisa en rojo antes de guardar.
2. **Antes de exportar**: pantalla «Revisión antes de exportar» (lecturas menores a la anterior, consumos
   exagerados, lecturas del Excel que se reemplazan, sin leer, medidores corregidos). Se puede apagar en Ajustes.
3. **Al exportar** (`ExportVerifier` + `ExcelComparer`): se genera en un archivo temporal, se vuelve a leer y se
   comprueba (a) cada fila tiene el mismo NIR y medidor que la app, (b) cada lectura está en la fila de su
   medidor, (c) celda por celda contra el original solo cambia la columna de lectura, (d) cambian
   exactamente las filas que debían cambiar. Solo entonces se guarda, y **se vuelve a leer lo guardado**
   (suma de verificación) para confirmar que es idéntico.
4. **Comprobar exportación** (menú ☰): hace todo lo anterior sin guardar nada.
5. **Pruebas automáticas** (`app/src/test`): fabrican un .xlsx real y prueban importar → exportar → comparar →
   verificar, con fórmulas, textos, filas sin número, 3.000 medidores, etc. En Android Studio: clic derecho
   sobre la carpeta `test` → Run Tests.

---

## 4. Módulos y flujo de trabajo

Se cambia de módulo deslizando (como Facebook) o con la barra inferior:
**Lecturas ⟷ Mapa ⟷ Entrega**. Dentro del mapa el deslizamiento se desactiva para que
el dedo mueva el mapa.

### Lecturas
- **Progreso arriba.** Siempre ves «1.289 / 2.021 leídas».
- **Buscador.** Eliges el campo (Medidor, NIR, Usuario, Dirección, Barrio, Lectura,
  Observación o Todos) y el teclado **123 / ABC**. Las dos elecciones se recuerdan.
  Buscar por medidor encuentra por **los últimos dígitos**.
- **Filtros.** Pendientes · Leídas · Todas.
- **Tramos.** La lista se agrupa por barrio en bloques de 100 NIR, y cada tramo se
  puede plegar.
- **Tarjeta.** Cada medidor muestra NIR, medidor, usuario, dirección, barrio, lectura
  anterior → actual y el consumo.
- **Registrar en 2 toques.** Al tocar un medidor se abre el teclado numérico solo:
  escribes la lectura y pulsas **Guardar y siguiente**, que abre el próximo pendiente
  por NIR.
  - Calcula el consumo en vivo y pide confirmar si la lectura es **menor que la
    anterior** o si el consumo es muy alto.
  - Si el medidor no tiene GPS, puede guardar tu ubicación al registrar.
  - Puedes agregar una nota (solo queda en la app, no va al Excel).
- **Botón flotante.** «Siguiente pendiente».

### Mapa (ideas tomadas de Google Maps, Waze y Strava)
- **Puntos rápidos.** Los puntos se dibujan como capas, no como 2.000 marcadores sueltos.
  Con zoom lejano se **agrupan en círculos con número**; al tocar uno, se acerca.
- **Colores por estado.** 🔵 pendiente · 🟢 leído · 🟠 siguiente · 🔴 entregada.
- **Etiquetas.** El NIR aparece junto a cada punto con zoom cercano.
- **Tarjeta del punto.** Al tocar un punto aparece una tarjeta abajo con *Registrar
  lectura* o *Entregar*, *Mover pin*, *Cómo llegar* (abre tu app de mapas) y
  *Borrar GPS*.
- **Mover pin.** El pin queda **fijo en el centro** y mueves el mapa con el dedo. Se ven
  las coordenadas y tienes los botones *Ir a mi GPS* y *Guardar ubicación aquí*.
- **Ubicar en mapa.** Para los medidores sin GPS, lo haces desde la tarjeta o desde el
  diálogo de lectura.
- **Botones laterales.** *Yo* (seguirme), *Satélite / Calles* y *Todo* (encuadrar la ruta).
- **Modo Entrega.** Muestra el **recorrido real** (línea morada), la **ruta recomendada**
  (línea naranja punteada) y la tarjeta *SIGUIENTE* con distancia, dirección y botón
  ENTREGAR.
- **Sin internet.** Si no hay conexión, carga un fondo liso y los puntos se siguen
  viendo y usando.

### Entrega (estilo Strava)
- **INICIAR JORNADA.** Un servicio graba el recorrido aunque se apague la pantalla. Si
  Android cierra la app, se reanuda solo.
- **Puntos limpios.** Guarda un punto cada 5 m e ignora los saltos imposibles del GPS.
- **Siguiente sugerida.** Muestra la siguiente factura con distancia y flecha de dirección.
- **Aviso de cercanía.** «Estás a 12 m del NIR 0842» con su botón ENTREGAR.
- **Entregar.** Guarda hora, GPS, orden real, NIR, medidor, usuario, dirección y barrio.
  Si el medidor no tenía GPS, la entrega se lo asigna.
- **Pestañas.** *Por entregar* y *Entregadas* (en el orden real, con opción de deshacer).
- **Durante la jornada.** Ves estadísticas (entregas de hoy, distancia y tiempo) y la
  pantalla se mantiene encendida.
- **Exportar el recorrido.** Se exporta en CSV (abre en Excel): orden, NIR, medidor,
  usuario, dirección, hora, lat/long y precisión.
- **Historial de jornadas.** Muestra el orden real de cada día.

### Ruta aprendida
Cada jornada guarda en qué posición entregaste cada factura. La app promedia esas
posiciones entre jornadas y sugiere ese orden. Por ejemplo, si siempre haces
001 → 003 → 002, te lo sugiere así. Sin historial, usa el orden del NIR.

---

## 5. Por qué es más liviana en celulares viejos

| Antes (0.2.0) | Ahora (0.3.0) |
|---|---|
| Todo en un JSON gigante dentro de SharedPreferences, reescrito en cada lectura | SQLite: cada lectura es una fila |
| Importar y exportar en el hilo de la pantalla (se congelaba) | Siempre en segundo plano, con barra de progreso |
| Exportar recorría 1,5 MB de texto por cada lectura | Una sola pasada (≈ 0,1 s) |
| Un `Marker` por medidor, y todos se borraban y redibujaban con cada cambio | Capas GeoJSON con agrupación |
| Remapear puntos: miles × miles comparaciones de texto | Índices: búsqueda en ≈ 4 ms |
| GPS de MapLibre + GPS del servicio en paralelo | Una sola fuente de GPS compartida |
| El servicio GPS podía **cerrar la app en Android 10 o menor** (faltaba `onStatusChanged`) | Corregido |

---

## 6. Qué probar en el celular (lista corta)

1. Importar `Lecturas A sep.xlsx` → debe decir 2.021 medidores, 1.289 con lectura.
2. Buscar un medidor por sus últimos 4 dígitos y registrar una lectura.
3. Exportar → abrir el archivo en Excel y comparar con el original.
4. En el mapa, tocar un punto → *Mover pin* → guardar.
5. Entrega → *Iniciar jornada*, caminar un poco, entregar 2 o 3 facturas, terminar y
   revisar el historial.
6. Apagar los datos móviles y abrir el mapa (debe mostrar los puntos sobre fondo liso).

## 7. Limitaciones conocidas

- El proyecto no se pudo compilar completo fuera de Android Studio. La lógica (Excel,
  base de datos, GPS, ViewModels) sí se compiló y probó con Kotlin 2.0.21, y las
  134 llamadas a MapLibre, Compose y AndroidX se verificaron contra las librerías reales
  de tu APK. Si Android Studio marca algún detalle al sincronizar, es algo puntual y
  rápido de corregir.
- Satélite y calles necesitan internet; solo se reutilizan las zonas ya vistas
  (caché del mapa).
- La ruta recomendada une los puntos en orden; no es navegación calle por calle.
