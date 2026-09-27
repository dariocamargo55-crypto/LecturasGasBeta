# Lecturas Gas Beta 0.2

Esta versión agrega importación real del XLSX de rutas, búsqueda por medidor/últimos 4 dígitos, registro local de lecturas y exportación conservando el archivo XLSX original.

## Flujo de prueba
1. Abrir el proyecto en Android Studio.
2. Seleccionar el celular físico.
3. Ejecutar la app.
4. Pulsar Importar Excel y seleccionar `Lecturas A sep.xlsx`.
5. Buscar un medidor y registrar una lectura de prueba.
6. Exportar a un nuevo XLSX.

La app mantiene el Excel original dentro de su almacenamiento privado y, al exportar, actualiza únicamente las celdas de lectura actual de las filas modificadas.
