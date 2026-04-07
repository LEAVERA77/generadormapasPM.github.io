# Soporte Técnico — HP Pavilion Gaming 16-a0061la
**Período:** 25 de marzo – 5 de abril de 2026  
**Equipo:** LAPTOP-GQP31NUQ  
**Usuario:** leave  

---

## Hardware del equipo

| Componente | Detalle |
|---|---|
| Modelo | HP Pavilion Gaming 16-a0061la |
| CPU | Intel Core i5-10300H (10ma gen, 8 hilos) |
| GPU | NVIDIA GeForce GTX 1650 |
| RAM | 16 GB DDR4 (2 slots SODIMM, máx. 64 GB) |
| Almacenamiento principal | Intel Optane H10 32 GB + SSD 512 GB (slot M.2, ambos en el mismo módulo) |
| Almacenamiento secundario | SSD 2.5" SATA 512 GB (bahía libre, disco G:) |
| SO | Windows 11 |
| BIOS | Versión F32 (actualizada) |

---

## Problema original

El sistema se congelaba (freeze total, requería reinicio forzado) durante uso intensivo. El controlador Intel RST no se podía actualizar por el mismo motivo.

---

## Diagnóstico

### Causa 1 — Térmica (congelamientos por temperatura)
- CPU llegaba al **100% de uso** al compilar en Android Studio
- Núcleo #2 alcanzaba **91°C** (límite del procesador: 100°C)
- TDP real: 31.6 W cuando el límite es 45 W (throttling activo)
- Logs de evento Windows ID 41 (Kernel-Power) con `BugcheckCode=0` y `Checkpoint=16` — característico de thermal shutdown

### Causa 2 — Conflicto de driver (crash de kernel)
- VirtualBox instalado con el driver `VBoxNetLwf` activo
- Al iniciar el emulador de Android Studio (que usa Hyper-V/WHPX), el driver de VirtualBox conflictuaba a nivel de kernel
- Log ID 41 con `Checkpoint=0` y `SystemSleepTransitionsToOn=0` — shutdown más abrupto, diferente al térmico

---

## Soluciones aplicadas

### 1. Limitar workers de Gradle (Android Studio)
Archivo: `C:\Users\leave\AndroidStudioProjects\<proyecto>\gradle.properties`

```properties
org.gradle.jvmargs=-Xmx1536m -Dfile.encoding=UTF-8
org.gradle.workers.max=4
org.gradle.daemon=true
org.gradle.parallel=true
org.gradle.caching=true
```

**Resultado:** CPU bajó de 100% a ~60% al compilar. Temperatura bajó de 91°C a ~65°C.

### 2. Desinstalar VirtualBox
- Eliminó el driver `VBoxNetLwf` que conflictuaba con Hyper-V
- Después del reinicio, el emulador dejó de causar crashes de kernel

### 3. Activar Hyper-V y WHPX para el emulador
```powershell
bcdedit /set hypervisorlaunchtype auto
DISM /Online /Enable-Feature /FeatureName:HypervisorPlatform /All
DISM /Online /Enable-Feature /FeatureName:VirtualMachinePlatform /All
```
- Se verificó que `Android Emulator Hypervisor Driver v2.2.0` e `Intel HAXM v7.6.5` estaban instalados en Android Studio SDK Tools
- El emulador pasó a usar WHPX (Windows Hypervisor Platform) con GPU: NVIDIA GeForce GTX 1650

### 4. ThrottleStop — Power Limits
| Parámetro | Valor configurado |
|---|---|
| PL1 | 35 W |
| PL2 | 45 W |
| PROCHOT Offset | 3 |
| BD PROCHOT | Activo |
| Speed Shift EPP | 64 (opcional, mejora respuesta) |

> Nota: El undervolting y el cambio de multiplicador están **bloqueados por la BIOS del fabricante**.

### 5. Plan de energía de Windows
- Plan cambiado de "Alto Rendimiento" a "Equilibrado"
- Estado máximo del procesador: **85%** (batería y enchufado)

### 6. Configuración del emulador de Android Studio
| Parámetro | Valor |
|---|---|
| Modelo recomendado | Pixel 2 / Pixel 3a (API 33) |
| CPU cores | 2 |
| RAM | 1536 MB |
| Graphics | Hardware |
| Default boot | Quick Boot (después del primer Cold Boot) |
| GPU preferida (Panel NVIDIA) | GeForce GTX 1650 (forzado para `qemu-system-x86_64.exe` y `emulator.exe`) |

---

## Resultados finales

| Métrica | Antes | Después |
|---|---|---|
| CPU máximo al compilar | 100% | ~60% |
| Temperatura máxima (núcleo) | 91°C | 65°C |
| Congelamientos | Frecuentes | **Cero** en 10 horas de trabajo intensivo |
| Throttling térmico activo | Sí | No |
| WHEA Errors | — | 0 |

---

## Intel Optane H10 — Estado y uso

- Modo activo: **Performance** (cacheo automático, sin anclado manual)
- Actividad promedio observada: lectura 4.8%, escritura 2%
- Mejora estimada para el uso actual: ~10-15% en apertura de aplicaciones frecuentes
- El Optane aprende el perfil de uso en 2-3 semanas de uso diario consistente
- **No es necesario configurarlo manualmente** en modo Performance

---

## SSD secundario (disco G:)

- Vida restante al 92% (24.500 GB escritos total)
- Estimación: 3-5 años de vida útil adicional
- Recomendación: monitorear con HWiNFO cada 6 meses

---

## Upgrades de hardware recomendados (pendientes)

### Número de serie del equipo
`5CD1308Y54`

### Slots de memoria confirmados
- **Slots SODIMM:** 2
- **Capacidad máxima soportada:** 64 GB
- **Condición importante:** Actualizar BIOS a versión F32 o superior **antes** de instalar los módulos (ya realizado — versión F32 instalada)
- Sin la actualización de BIOS, el sistema solo reconoce hasta 16 GB

### Slots M.2 confirmados
- **Cantidad:** 1 solo slot M.2 (ocupado por el Optane H10)
- No hay un segundo slot M.2 disponible
- Para agregar NVMe hay que **reemplazar** el Optane H10, no agregar uno nuevo

### Plan de upgrade recomendado

| Paso | Acción | Detalle |
|---|---|---|
| 1 | RAM | 2 × 16 GB DDR4 SODIMM 3200 MHz = 32 GB dual channel |
| 2 | Almacenamiento | Reemplazar Optane H10 por M.2 NVMe 2 TB |
| 3 | Resultado final | 32 GB RAM + 2 TB NVMe + SSD 2.5" 512 GB existente |

> Con 32 GB de RAM el Optane deja de ser necesario como buffer de memoria, por lo que reemplazarlo no implica pérdida funcional.

### RAM — Prioridad alta
- **Tipo exacto:** DDR4 SODIMM 3200 MHz
- **Configuración objetivo:** 2 × 16 GB = 32 GB (dual channel)
- **Marcas recomendadas:** Kingston, Crucial, Corsair
- BIOS F32 ya instalada — listo para instalar
- Con 32 GB: RAM pasaría de 92% a ~45% de uso con el mismo entorno de trabajo
- **Advertencia:** Mezclar módulos de distinto tamaño (ej. 8 GB + 16 GB = 24 GB) rompe el dual channel y reduce el rendimiento — conviene comprar los dos módulos juntos

### Almacenamiento — Prioridad media
- El único slot M.2 está ocupado por el Optane H10
- La bahía 2.5" ya tiene un SSD de 512 GB de fábrica (disco G:)
- **Opción recomendada:** Reemplazar el Optane H10 por M.2 NVMe PCIe Gen3 2280 de 2 TB
  - Recomendado: **Crucial P3 Plus** (PCIe Gen4, retrocompatible con Gen3) o **Kingston NV2**
  - Evitar: Patriot P300 (memoria QLC sin caché DRAM, bajo rendimiento sostenido en compilaciones)
  - Requiere clonar el disco antes con **Macrium Reflect Free** + adaptador M.2 a USB
  - Desactivar Optane en Intel RST **antes** de clonar
- **Opción B:** Mantener Optane intacto, reemplazar SSD 2.5" por uno más grande (SATA)

---

## Configuración ganadora (resumen operativo)

```
ThrottleStop:  PL1=35W / PL2=45W / PROCHOT Offset=3 / Speed Shift EPP=64
               Inicio automático con Windows (Programador de tareas, privilegios elevados)

gradle.properties:
  org.gradle.jvmargs=-Xmx1536m -Dfile.encoding=UTF-8
  org.gradle.workers.max=4
  org.gradle.daemon=true
  org.gradle.parallel=true
  org.gradle.caching=true

Emulador:      Pixel 2 API 33 / 2 cores / 1536 MB RAM / Quick Boot / Hardware GPU
Plan Windows:  Equilibrado / Estado máximo CPU: 85%
VirtualBox:    Desinstalado
Hyper-V:       Activo (bcdedit hypervisorlaunchtype=auto)
```

---

## Notas adicionales

- El driver Intel RST versión 18.37.6.1010 (2022) que originalmente se quería actualizar **no era la causa** de los congelamientos
- Los congelamientos eran causados por la combinación de Gradle sin límites + VirtualBox conflictuando con Hyper-V
- La pasta térmica podría necesitar reemplazo si los congelamientos reaparecen en el futuro (el núcleo #2 llegaba a 91°C mientras otros estaban en 73°C, indicando disipación despareja)
