# ZaryvoxHeadGenerators 1.0.0

Addon para Purpur/Paper 1.20.4 + ItemGenerators 1.2. Intercepta los objetos que ItemGenerators suelta físicamente y los sustituye por cabezas de jugador con textura configurable. Cada tier admite variante `normal` y `special`, probabilidad global y precio de venta guardado en PersistentDataContainer.

## Compilar

- Java 17
- Gradle 8+
- Ejecutar `gradle build`
- JAR: `build/libs/ZaryvoxHeadGenerators-1.0.0.jar`

## Instalar

1. Mantén `ItemGenerators 1.2` y Vault instalados.
2. Coloca `ZaryvoxHeadGenerators-1.0.0.jar` en `plugins/`.
3. Inicia una vez y apaga el servidor.
4. Edita `plugins/ZaryvoxHeadGenerators/config.yml` y pega las texturas Base64 en `tiers.<nivel>.normal.texture` y `tiers.<nivel>.special.texture`.
5. Inicia el servidor.

## Comandos

- `/zhg sellhand` vende la pila de cabezas reconocidas en la mano.
- `/zhg sellall` vende todas las cabezas reconocidas del inventario.
- `/zhg reload` recarga la configuración.

Permisos: `zaryvoxheadgenerators.sell` (default true), `zaryvoxheadgenerators.admin` (default op).

## Texturas

El campo `texture` espera el valor Base64 de la propiedad `textures` de una cabeza/player profile, no el ID de HeadDatabase. Si queda vacío, se verá una cabeza de jugador sin textura asignada. Debes completar las 120 texturas (60 niveles x 2 variantes) para tener el resultado visual final.

## Integración técnica

La integración detecta la clase de ItemGenerators 1.2 y consulta por reflexión sus generadores activos. Solo reemplaza el drop cuando coinciden material y ubicación exacta de salida del generador. No modifica drops de mobs ni objetos de otros plugins.

Este proyecto es código fuente inicial y requiere compilación y prueba en una copia del servidor antes de producción. No se ha validado aún en un servidor Purpur real.
