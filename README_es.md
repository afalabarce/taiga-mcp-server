# Taiga MCP Server

Servidor de alto rendimiento para el **Model Context Protocol (MCP)** desarrollado en **Kotlin Multiplatform** (módulo `:server`), diseñado para conectar asistentes de Inteligencia Artificial (Claude Desktop, Cursor, Continue, agentes autónomos, etc.) con el gestor de proyectos **Taiga** (`https://docs.taiga.io/api.html`).

Permite la gestión completa de proyectos, historias de usuario, tareas, épicas, incidencias, sprints (hitos) y páginas wiki, soportando streaming en tiempo real vía **Server-Sent Events (SSE)**, transporte bidireccional por **Stdio**, consola interactiva **Swagger UI** generada dinámicamente en tiempo de ejecución, parametrización remota en caliente y autenticación segura con **Bearer Tokens**.

---

## 📑 Tabla de Contenidos

- [Características Principales](#-características-principales)
- [Tecnologías Utilizadas](#-tecnologías-utilizadas)
- [Arquitectura e Implementación](#-arquitectura-e-implementación)
- [Compilación y Construcción](#-compilación-y-construcción)
- [Seguridad y Autenticación](#-seguridad-y-autenticación)
- [Parametrización Remota Dinámica](#-parametrización-remota-dinámica)
- [Swagger UI y Documentación OpenAPI](#-swagger-ui-y-documentación-openapi)
- [Catálogo de Herramientas MCP](#-catálogo-de-herramientas-mcp)
- [Configuración en IDEs Agénticos y Clientes MCP (mcp.json)](#-configuración-en-ides-agénticos-y-clientes-mcp-mcpjson)
- [Despliegue en Servidor Remoto Linux](#-despliegue-en-servidor-remoto-linux)
  - [Opción 1: Servicio Nativo systemd + Nginx](#opción-1-servicio-nativo-systemd--nginx)
  - [Opción 2: Docker y Docker Compose + Nginx](#opción-2-docker-y-docker-compose--nginx)
  - [Ajustes Críticos de Nginx para SSE](#ajustes-críticos-de-nginx-para-sse)
  - [Configuración SSL con Let's Encrypt / Certbot](#configuración-ssl-con-lets-encrypt--certbot)
- [Mantenimiento y Monitorización](#-mantenimiento-y-monitorización)

---

## 🚀 Características Principales

1. **Gestión Integral de Taiga API**:
   - **Proyectos**: Listado y búsqueda por ID o slug.
   - **Historias de Usuario (User Stories)**: Consulta, creación, actualización y borrado con soporte de sprints, estados y asignaciones.
   - **Tareas (Tasks)**: CRUD completo asociado a historias de usuario o sprints.
   - **Épicas (Epics)**: Organización por colores, estados y etiquetas.
   - **Incidencias (Issues / Bugs)**: Gestión con severidades, prioridades, tipos y estados.
   - **Sprints / Hitos (Milestones)**: Progreso, fechas de entrega, puntos totales y puntos cerrados.
   - **Wiki**: Lectura, creación y edición de páginas en formato Markdown.
   - **Metadatos y Diccionarios**: Resolución automática de IDs válidos para estados, prioridades y severidades del proyecto.

2. **Seguridad y Tokenización**:
   - Protección nativa en Ktor mediante `authenticate("auth-bearer")` en todas las rutas MCP y administrativas.
   - Endpoint público `/api/auth/login` para autenticación con credenciales contra Taiga, evitando almacenar contraseñas en texto plano o disco.
   - Enmascaramiento de tokens en respuestas de configuración (`maskedToken`) para proteger la privacidad.
   - Soporte multiformato para envío de token: `Authorization: Bearer <token>`, `X-Taiga-Token: <token>` o query parameter `?token=<token>`.

3. **Swagger UI Dinámico con DSL de Ktor**:
   - Implementado utilizando la función de extensión oficial de Ktor: `Route.describe { ... }`.
   - Generación en tiempo de ejecución de la especificación OpenAPI 3.0 (`OpenApiDocSource.Routing()`) sin necesidad de archivos YAML ni XML estáticos.
   - Consola web interactiva en `/swagger` que permite probar tanto la API MCP como llamadas directas a Taiga.

4. **Doble Transporte MCP**:
   - **SSE (Server-Sent Events)**: Para despliegue remoto en servidores con Nginx y consumo por clientes distribuidos.
   - **Stdio**: Para uso local embebido en herramientas de escritorio (Claude Desktop, scripts CLI).

---

## 💻 Tecnologías Utilizadas

| Componente | Tecnología | Versión | Rol en el Proyecto |
|---|---|---|---|
| **Lenguaje** | [Kotlin Multiplatform (KMP)](https://kotlinlang.org/) | `2.3.0` | Arquitectura base multiplataforma orientada al módulo `:server` (JVM 17). |
| **Servidor HTTP** | [Ktor Server (CIO)](https://ktor.io/) | `3.4.0` | Servidor asíncrono y reactivo basado en corrutinas de alto rendimiento. |
| **OpenAPI / Swagger** | `ktor-server-routing-openapi` | `3.4.0` | DSL declarativo `.describe { ... }` y consola interactiva Swagger UI. |
| **Cliente HTTP** | [Ktor Client (CIO)](https://ktor.io/) | `3.4.0` | Cliente no bloqueante para consumir la API REST de Taiga. |
| **Autenticación** | `ktor-server-auth` | `3.4.0` | Proveedor Bearer (`auth-bearer`) con inspección de cabeceras y queries. |
| **Serialización** | [kotlinx.serialization](https://github.com/Kotlin/kotlinx.serialization) | `1.8.0` | Serialización JSON estricta y tipada sin reflexión en tiempo de ejecución. |
| **Concurrencia** | [kotlinx.coroutines](https://github.com/Kotlin/kotlinx.coroutines) | `1.10.1` | Gestión reactiva de canales de eventos (`Channel`) y concurrencia estructurada. |
| **CLI & Argumentos** | [Clikt](https://ajalt.github.io/clikt/) | `5.0.3` | Procesamiento de parámetros de línea de comandos y variables de entorno. |
| **Logging** | [Logback](https://logback.qos.ch/) + SLF4J | `1.5.16` | Registro de eventos configurable y estructurado. |
| **Contenedores** | Docker + Docker Compose | - | Empaquetado en contenedor ligero con Eclipse Temurin 17 JRE Alpine. |
| **Proxy Inverso** | Nginx | - | Terminación SSL, buffering off para SSE y balanceo de peticiones. |

---

## 📐 Arquitectura e Implementación

El proyecto sigue una arquitectura desacoplada y orientada a eventos:

```
taiga-mcpserver/
├── build.gradle.kts
├── settings.gradle.kts
├── gradle/libs.versions.toml
├── nginx/
│   └── taiga-mcp.conf          # Configuración Nginx optimizada para SSE y Swagger
├── systemd/
│   └── taiga-mcp.service       # Unidad de servicio systemd con auto-reinicio y hardening
├── Dockerfile                  # Multi-stage build (compilación Gradle + JRE Alpine)
├── docker-compose.yml          # Orquestación con healthchecks
└── server/                     # Módulo Kotlin Multiplatform (:server)
    ├── build.gradle.kts
    └── src/
        ├── commonMain/
        │   ├── kotlin/dev/afalabarce/mcp/
        │   │   ├── Main.kt                     # Entrypoint CLI y servidor Ktor CIO
        │   │   ├── config/                     # Configuración thread-safe (StateFlow/Atomic)
        │   │   │   ├── TaigaConfig.kt          # Modelos de configuración y enmascaramiento
        │   │   │   └── ConfigManager.kt        # Gestor reactivo de parámetros
        │   │   ├── taiga/
        │   │   │   ├── model/TaigaModels.kt    # DTOs fuertemente tipados de Taiga
        │   │   │   ├── client/TaigaClient.kt   # Cliente REST asíncrono Taiga
        │   │   │   └── client/TaigaClientFactory.kt
        │   │   ├── mcp/
        │   │   │   ├── protocol/McpProtocol.kt # Protocolo JSON-RPC 2.0 y MCP (2024-11-05)
        │   │   │   ├── tools/                  # Herramientas registradas en el catálogo
        │   │   │   │   ├── TaigaToolRegistry.kt# Registro centralizado de herramientas
        │   │   │   │   ├── ProjectTools.kt     # Herramientas de Proyectos
        │   │   │   │   ├── UserStoryTools.kt   # Herramientas de Historias de Usuario
        │   │   │   │   ├── TaskTools.kt        # Herramientas de Tareas
        │   │   │   │   ├── EpicTools.kt        # Herramientas de Épicas
        │   │   │   │   ├── IssueTools.kt       # Herramientas de Incidencias
        │   │   │   │   ├── MilestoneTools.kt   # Herramientas de Sprints
        │   │   │   │   ├── WikiTools.kt        # Herramientas de Wiki
        │   │   │   │   ├── MetadataTools.kt    # Herramientas de Diccionarios y Estados
        │   │   │   │   └── ConfigTools.kt      # Herramientas de Configuración y Diagnóstico
        │   │   │   └── server/                 # Sesiones SSE, Stdio y Engine MCP
        │   │   │       ├── McpServer.kt        # Despachador de métodos JSON-RPC
        │   │   │       ├── McpSseSession.kt    # Concurrencia de sesiones SSE por Channel
        │   │   │       └── McpStdioHandler.kt  # Transporte por entrada/salida estándar
        │   │   └── web/                        # Servidor HTTP Ktor & OpenAPI
        │   │       ├── ServerApp.kt            # Configuración de Ktor (CORS, Auth, ContentNeg)
        │   │       ├── auth/TaigaAuth.kt       # Proveedor Bearer auth para Ktor
        │   │       └── routes/
        │   │           ├── McpRoutes.kt        # /sse, /messages, /mcp
        │   │           ├── ConfigRoutes.kt     # /api/config, /api/auth/login
        │   │           ├── SwaggerRoutes.kt    # /swagger, /openapi.json, tests interactivos
        │   │           └── HealthRoutes.kt     # /health, /
        │   └── resources/logback.xml
        └── commonTest/kotlin/dev/afalabarce/mcp/
            └── TaigaMcpServerTest.kt           # Suite de pruebas unitarias y de integración
```

---

## 🛠️ Compilación y Construcción

### Requisitos Previos
- **JDK 17 o superior** instalado en el sistema (`java --version`).
- Conexión a internet para la descarga inicial de dependencias de Gradle.

### Comandos de Construcción

```bash
# 1. Clonar el repositorio
git clone <url-del-repositorio> taiga-mcpserver
cd taiga-mcpserver

# 2. Dar permisos de ejecución al wrapper de Gradle
chmod +x gradlew

# 3. Ejecutar las pruebas unitarias y de integración
./gradlew jvmTest

# 4. Generar el artefacto autónomo standalone (Fat JAR)
./gradlew :server:fatJar
```

El binario ejecutable independiente se generará en:
```
server/build/libs/server-standalone.jar
```
*Este archivo incluye el servidor embebido CIO, todas las dependencias y recursos, listo para ejecutarse en cualquier entorno con JVM 17.*

### Parametrización con `SERVER_OPTS` (`application.yaml` y `logback.xml`)

El servidor admite la variable de entorno `SERVER_OPTS` para parametrizar dinámicamente la ubicación de los archivos de configuración y logging:

```bash
export SERVER_OPTS="-Dconfig.file=/srv/taiga-mcp/resources/application.yaml -Dlogback.configurationFile=/srv/taiga-mcp/resources/logback.xml"

# Ejecución mediante el script de inicio (incluye SERVER_OPTS en el proceso Java)
bin/server

# O ejecución directa del JAR independiente (Main.kt detecta y procesa SERVER_OPTS automáticamente)
java -jar server-standalone.jar
```

El servidor resolverá los archivos con la siguiente jerarquía de búsqueda:
1. **Rutas pasadas en `SERVER_OPTS`** (mediante flags `-Dconfig.file=...`, `-Dlogback.configurationFile=...`, o argumentos `--config=...`, `--logback=...`).
2. **Propiedades del sistema JVM** (`-Dconfig.file=...`, `-Dlogback.configurationFile=...`).
3. **Variables de entorno específicas** (`CONFIG_FILE`, `LOGBACK_CONFIGURATION_FILE`).
4. **Ubicaciones por defecto convencionales** (`resources/application.yaml`, `resources/logback.xml`).

---

## 🔒 Seguridad y Autenticación

El servidor implementa un modelo de seguridad por defecto mediante **Ktor Authentication**:

### Matriz de Acceso a Endpoints

| Ruta | Método | Nivel de Acceso | Descripción |
|---|---|---|---|
| `/api/auth/login` | `POST` | **Público** | Autentica contra Taiga con credenciales y entrega el token Bearer. |
| `/health` | `GET` | **Público** | Comprobación de salud y estado de conexión con Taiga. |
| `/` | `GET` | **Público** | Descubrimiento de endpoints del servicio. |
| `/swagger` | `GET` | **Público** | Interfaz visual interactiva Swagger UI. |
| `/openapi.json` | `GET` | **Público** | Esquema OpenAPI 3.0 en tiempo de ejecución. |
| `/sse` | `GET`, `POST` | **Protegido** (`Bearer` / `Basic`) | Canal de streaming SSE / Streamable HTTP MCP. |
| `/messages` | `POST` | **Protegido** (`Bearer` / `Basic`) | Envío de mensajes JSON-RPC para sesiones SSE. |
| `/mcp` | `GET`, `POST` | **Protegido** (`Bearer` / `Basic`) | Protocolo Streamable HTTP directo y SSE standby stream. |
| `/api/config` | `GET`, `POST`| **Protegido** (`Bearer` / `Basic`) | Consulta y actualización de parámetros en caliente. |
| `/api/mcp/tools` | `GET` | **Protegido** (`Bearer` / `Basic`) | Listado de herramientas disponibles en formato JSON. |
| `/api/mcp/tools/{tool}/call` | `POST` | **Protegido** (`Bearer` / `Basic`) | Ejecución de herramientas desde Swagger / API. |
| `/api/taiga/*` | `GET`, `POST`| **Protegido** (`Bearer` / `Basic`) | Proxy REST de testeo directo contra Taiga. |

### Cómo Autenticar las Peticiones Protegidas

El servidor admite **dos métodos de autenticación** para adaptarse a cualquier cliente y evitar problemas de expiración:

#### Método 1: Autenticación HTTP Basic con Usuario y Contraseña (Recomendada anti-caducidad)
Ideal para entornos como **Antigravity**, **Cursor** o clientes de larga duración, ya que **evita tener que renovar periódicamente los tokens JWT**. El servidor se encarga de autenticar contra Taiga y renovar/cachear el token de sesión automáticamente:

1. **Codifica tus credenciales en Base64:**
   ```bash
   echo -n "tu_usuario:tu_password" | base64
   # Ejemplo de salida: dHVfdXN1YXJpbzp0dV9wYXNzd29yZA==
   ```
2. **Envía la cabecera estándar HTTP Basic:**
   ```http
   Authorization: Basic dHVfdXN1YXJpbzp0dV9wYXNzd29yZA==
   ```
3. **Opciones alternativas para Basic Auth:**
   - Cabecera: `X-Taiga-Basic: dHVfdXN1YXJpbzp0dV9wYXNzd29yZA==`
   - Parámetro de query (para EventSource / SSE): `GET /sse?basic=dHVfdXN1YXJpbzp0dV9wYXNzd29yZA==` o `/mcp?basic=...`

#### Método 2: Autenticación Bearer con Token JWT
Si dispones de un token JWT generado previamente:

1. **Cabecera HTTP Estándar:**
   ```http
   Authorization: Bearer tu_token_taiga_aqui
   ```
2. **Cabecera Alternativa:**
   ```http
   X-Taiga-Token: tu_token_taiga_aqui
   ```
3. **Parámetro de Query en la URL:**
   ```
   GET /sse?token=tu_token_taiga_aqui
   ```

---

## 🔄 Parametrización Remota Dinámica

El servidor puede configurarse en el arranque mediante variables de entorno o parámetros CLI, y **modificarse dinámicamente en caliente** a través de su API REST sin necesidad de reinicios:

### 1. Iniciar Sesión y Obtener Token (Tokenización)
Envía las credenciales del usuario a `/api/auth/login`. El servidor autenticará la solicitud directamente con la API de Taiga y actualizará el token activo:

```bash
curl -X POST http://localhost:8080/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{
    "username": "usuario_o_email@empresa.com",
    "password": "PasswordSecreto123",
    "apiUrl": "https://api.taiga.io"
  }'
```

**Respuesta:**
```json
{
  "success": true,
  "message": "Successfully authenticated against Taiga",
  "authToken": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...",
  "userId": 42,
  "username": "usuario_taiga",
  "email": "usuario@empresa.com"
}
```

### 2. Consultar la Configuración Activa
```bash
curl -H "Authorization: Bearer tu_token" http://localhost:8080/api/config
```

**Respuesta con Token Enmascarado:**
```json
{
  "apiUrl": "https://api.taiga.io",
  "hasAuthToken": true,
  "maskedToken": "eyJh...98aB",
  "defaultProject": "mi-proyecto-activo"
}
```

### 3. Actualizar la Configuración en Caliente
```bash
curl -X POST http://localhost:8080/api/config \
  -H "Authorization: Bearer tu_token" \
  -H "Content-Type: application/json" \
  -d '{
    "apiUrl": "https://taiga.miempresa.com",
    "authToken": "nuevo_token_seguro",
    "defaultProject": "proyecto-backend"
  }'
```

---

## 📖 Swagger UI y Documentación OpenAPI

La documentación interactiva se genera dinámicamente utilizando el módulo oficial `io.ktor:ktor-server-routing-openapi` de Ktor 3.4.0 mediante la función de extensión `.describe { ... }`.

### Acceso a la Consola
Abre en tu navegador web:
👉 **`http://localhost:8080/swagger`** (o `https://taiga-mcp.tudominio.com/swagger`)

### Características de la Consola
- **Sin archivos YAML o XML**: Todo el contrato OpenAPI se deduce en tiempo de ejecución del árbol de rutas de Ktor.
- **Pruebas de Autenticación**: Puedes ingresar tu Bearer Token en el botón `Authorize` de Swagger UI.
- **Invocación Interactiva**: Permite probar las herramientas MCP (`POST /api/mcp/tools/{toolName}/call`) con datos de prueba antes de conectarlas a tu LLM.
- **Esquema JSON**: Accesible directamente en `http://localhost:8080/openapi.json`.

---

## 🧰 Catálogo de Herramientas MCP (123 Herramientas Disponibles)

El servidor registra **123 herramientas** bajo la especificación Model Context Protocol que cubren el 100% de la funcionalidad de Taiga:

| Herramienta | Acción | Parámetros Notables |
|---|---|---|
| **Proyectos y Plantillas** | | |
| `taiga_list_projects` | Lista los proyectos accesibles para el usuario | `member_id` |
| `taiga_get_project` | Obtiene el detalle de un proyecto por ID o slug | `project_id`, `slug`, `project` |
| `taiga_create_project` | Crea un nuevo proyecto | `name`, `description`, `is_private`, `tags` |
| `taiga_update_project` | Modifica metadatos del proyecto | `project_id`, `name`, `description`, `is_private`, `tags` |
| `taiga_delete_project` | Elimina un proyecto por ID o slug | `project_id`, `project` |
| `taiga_duplicate_project` | Duplica un proyecto existente y su estructura | `project`, `name`, `description`, `is_private` |
| `taiga_list_project_templates` | Lista las plantillas de proyecto disponibles (Scrum, Kanban, etc.) | *(ninguno)* |
| `taiga_export_project` | Dispara o consulta la exportación completa del proyecto | `project` |
| `taiga_export_csv` | Exporta datos en formato CSV plano | `type` ('userstories', 'tasks', 'issues', 'epics'), `project` |
| `taiga_transfer_project_start` | Inicia la transferencia de propiedad a otro usuario | `user`, `project` |
| `taiga_transfer_project_accept`| Acepta la transferencia de un proyecto mediante token | `token`, `reason`, `project` |
| **Miembros y Permisos** | | |
| `taiga_list_memberships` | Lista membresías y roles del proyecto | `project` |
| `taiga_create_membership` | Invita o añade un miembro con rol | `email`, `role`, `project` |
| `taiga_delete_membership` | Elimina un miembro del proyecto | `id` |
| `taiga_list_members` | Lista los miembros del equipo y sus roles | `project` |
| `taiga_list_tags` | Lista etiquetas y códigos de color del proyecto | `project` |
| **Swimlanes (Carriles Kanban)** | | |
| `taiga_list_swimlanes` | Lista todos los carriles configurados en el proyecto | `project` |
| `taiga_get_swimlane` | Consulta un carril por su ID | `id` |
| `taiga_create_swimlane` | Crea un nuevo carril | `name`, `order`, `project` |
| `taiga_update_swimlane` | Modifica nombre u orden del carril | `id`, `name`, `order` |
| `taiga_delete_swimlane` | Elimina un carril por ID | `id` |
| `taiga_reorder_swimlanes` | Reordena en bloque los carriles de un proyecto | `bulk_swimlanes`, `project` |
| `taiga_list_swimlane_userstory_statuses` | Mapeo de estados de historias por carril | `project` |
| **Fechas Límite (Due Dates)** | | |
| `taiga_list_due_dates` | Lista estados y colores de vencimiento | `type` ('userstory', 'task', 'issue'), `project` |
| `taiga_get_due_date` | Consulta configuración de vencimiento por ID | `type`, `id` |
| `taiga_create_due_date` | Crea un nuevo estado y color de vencimiento | `type`, `name`, `color`, `days_to_due`, `by_default`, `order`, `project` |
| `taiga_update_due_date` | Modifica estado o color de vencimiento | `type`, `id`, `name`, `color`, `days_to_due`, `by_default`, `order` |
| `taiga_delete_due_date` | Elimina un estado de vencimiento | `type`, `id` |
| **Operaciones Masivas (Bulk)** | | |
| `taiga_bulk_create_user_stories` | Creación masiva rápida de historias por líneas de texto | `bulk_stories`, `status`, `swimlane_id`, `project` |
| `taiga_bulk_create_tasks` | Creación masiva rápida de tareas por líneas | `bulk_tasks`, `user_story_id`, `milestone`, `status`, `project` |
| `taiga_bulk_create_issues` | Creación masiva rápida de bugs por líneas | `bulk_issues`, `milestone`, `project` |
| `taiga_bulk_create_epics` | Creación masiva rápida de épicas por líneas | `bulk_epics`, `status_id`, `project` |
| `taiga_bulk_create_memberships`| Añade múltiples miembros con rol a la vez | `bulk_memberships`, `project` |
| `taiga_move_to_sprint` | Mueve múltiples historias, tareas o bugs a un sprint | `type`, `milestone`, `item_ids`, `project` |
| `taiga_bulk_update_backlog_order` | Reordena historias en el backlog | `bulk_stories`, `project` |
| `taiga_bulk_update_kanban_order` | Reordena historias en el tablero Kanban | `bulk_stories`, `project` |
| `taiga_bulk_update_taskboard_order` | Reordena tareas en el panel de tareas del sprint | `bulk_tasks`, `project` |
| **Historias de Usuario** | | |
| `taiga_list_user_stories` | Lista historias de usuario con filtros | `project`, `milestone`, `status`, `epic_id` |
| `taiga_get_user_story` | Obtiene el detalle de una historia por ID o ref | `id`, `ref`, `project` |
| `taiga_create_user_story` | Crea una historia (soporta nombres de estado, sprint, `due_date`, puntos) | `subject`, `description`, `status`, `milestone`, `assigned_to`, `due_date`, `tags` |
| `taiga_update_user_story` | Modifica una historia con auto-versión concurrente | `id`, `subject`, `status`, `milestone`, `assigned_to`, `due_date`, `comment`, `version` |
| `taiga_delete_user_story` | Elimina una historia de usuario | `id` |
| **Tareas** | | |
| `taiga_list_tasks` | Lista tareas de un proyecto o historia | `project`, `user_story_id`, `milestone`, `status`, `assigned_to` |
| `taiga_get_task` | Obtiene el detalle de una tarea por ID o ref | `id`, `ref`, `project` |
| `taiga_create_task` | Crea una tarea (soporta nombres, sprint, `due_date`) | `subject`, `description`, `user_story_id`, `milestone`, `assigned_to`, `due_date` |
| `taiga_update_task` | Modifica estado o datos con auto-versión concurrente | `id`, `subject`, `status`, `assigned_to`, `due_date`, `comment`, `version` |
| `taiga_delete_task` | Elimina una tarea | `id` |
| **Incidencias / Bugs** | | |
| `taiga_list_issues` | Lista incidencias o bugs con filtros avanzados | `project`, `status`, `priority`, `severity`, `type` |
| `taiga_get_issue` | Obtiene el detalle de una incidencia por ID o ref | `id`, `ref`, `project` |
| `taiga_create_issue` | Registra un nuevo bug (nombres de severidad, prioridad, tipo, `due_date`) | `subject`, `type`, `priority`, `severity`, `status`, `due_date`, `project` |
| `taiga_update_issue` | Actualiza estado, prioridad o severidad con auto-versión | `id`, `subject`, `status`, `priority`, `severity`, `due_date`, `comment`, `version` |
| `taiga_delete_issue` | Elimina una incidencia por ID | `id` |
| `taiga_promote_issue_to_user_story` | Promociona un bug a historia de usuario | `id`, `project` |
| **Épicas** | | |
| `taiga_list_epics` | Lista las épicas de un proyecto | `project`, `status` |
| `taiga_get_epic` | Obtiene el detalle de una épica por ID o ref | `id`, `ref`, `project` |
| `taiga_create_epic` | Crea una épica con color y etiquetas | `subject`, `description`, `color`, `project`, `tags` |
| `taiga_update_epic` | Modifica una épica existente | `id`, `subject`, `description`, `color`, `status`, `version` |
| `taiga_delete_epic` | Elimina una épica por ID | `id` |
| `taiga_list_epic_user_stories` | Lista historias vinculadas a una épica | `epic_id` |
| `taiga_link_user_story_to_epic` | Vincula una historia de usuario a una épica | `epic_id`, `user_story_id`, `order` |
| `taiga_unlink_user_story_from_epic` | Desvincula una historia de una épica | `epic_id`, `user_story_id` |
| **Sprints / Hitos** | | |
| `taiga_list_milestones` | Lista los sprints o hitos | `project` |
| `taiga_get_milestone` | Consulta avance, historias y puntos del sprint | `id`, `milestone`, `project` |
| `taiga_create_milestone` | Planifica un nuevo sprint con fechas | `name`, `estimated_start`, `estimated_finish`, `project` |
| `taiga_update_milestone` | Modifica datos o cierra un sprint | `id`, `milestone`, `name`, `estimated_start`, `closed`, `project` |
| `taiga_delete_milestone` | Elimina un sprint por ID o nombre | `id`, `milestone`, `project` |
| `taiga_get_milestone_stats` | Métricas de avance y burndown del sprint | `id`, `milestone`, `project` |
| **Wiki** | | |
| `taiga_list_wiki_pages` | Lista los artículos de la wiki | `project` |
| `taiga_get_wiki_page` | Obtiene el contenido Markdown de un artículo | `slug`, `id`, `project` |
| `taiga_create_wiki_page` | Crea una nueva página wiki | `slug`, `content`, `project` |
| `taiga_update_wiki_page` | Actualiza el texto de una página wiki | `id`, `content`, `version` |
| `taiga_delete_wiki_page` | Elimina una página wiki por ID o slug | `id`, `slug`, `project` |
| `taiga_list_wiki_links` | Lista enlaces del menú de navegación wiki | `project` |
| `taiga_create_wiki_link` | Añade un enlace al menú de navegación wiki | `title`, `href`, `order`, `project` |
| `taiga_delete_wiki_link` | Elimina un enlace del menú de navegación wiki | `id` |
| **Adjuntos y Archivos** | | |
| `taiga_list_attachments` | Lista archivos adjuntos de historias, tareas, bugs, epics o wiki | `type`, `object_id`, `project` |
| `taiga_upload_attachment` | Sube un archivo adjunto (desde ruta local o base64) | `type`, `object_id`, `file_path`, `file_content_base64`, `file_name` |
| `taiga_delete_attachment` | Elimina un archivo adjunto por ID | `type`, `attachment_id` |
| **Campos Personalizados** | | |
| `taiga_list_custom_attributes` | Lista campos personalizados de un tipo | `type`, `project` |
| `taiga_create_custom_attribute` | Define un nuevo campo personalizado | `type`, `name`, `attribute_type`, `project` |
| `taiga_delete_custom_attribute` | Elimina un campo personalizado | `type`, `id` |
| `taiga_get_custom_attributes_values` | Consulta valores de campos personalizados de un elemento | `type`, `object_id` |
| `taiga_update_custom_attributes_values` | Actualiza valores de campos personalizados con auto-versión | `type`, `object_id`, `values`, `version` |
| **Comentarios e Historial** | | |
| `taiga_add_comment` | Publica comentarios en historias, tareas, bugs o epics con auto-versión | `type`, `id`, `comment` |
| `taiga_get_history` | Consulta el historial cronológico y actividad de un elemento | `type`, `id` |
| `taiga_edit_comment` | Edita el texto de un comentario específico en el historial | `type`, `id`, `comment_id`, `comment` |
| `taiga_delete_comment` | Elimina un comentario del historial de actividad | `type`, `id`, `comment_id` |
| `taiga_undelete_comment` | Recupera un comentario previamente eliminado | `type`, `id`, `comment_id` |
| `taiga_get_comment_versions` | Consulta todas las versiones y ediciones de un comentario | `type`, `id`, `comment_id` |
| **Webhooks** | | |
| `taiga_list_webhooks` | Lista los webhooks configurados en el proyecto | `project` |
| `taiga_get_webhook` | Consulta el detalle de un webhook por ID | `id` |
| `taiga_create_webhook` | Crea un nuevo webhook para eventos del proyecto | `name`, `url`, `key`, `project` |
| `taiga_update_webhook` | Modifica la configuración de un webhook | `id`, `name`, `url`, `key` |
| `taiga_delete_webhook` | Elimina un webhook por ID | `id` |
| `taiga_test_webhook` | Ejecuta un envío de prueba para validar el webhook | `id` |
| `taiga_list_webhook_logs` | Lista los registros y estados de entrega de un webhook | `webhook_id` |
| `taiga_resend_webhook` | Reenvía una entrega fallida por su log ID | `log_id` |
| **Notificaciones y Políticas** | | |
| `taiga_list_web_notifications` | Lista las notificaciones web del usuario | `only_unread` |
| `taiga_mark_web_notifications_read` | Marca notificaciones como leídas (una o todas) | `id` |
| `taiga_list_notify_policies` | Lista las directivas de notificación por proyecto | `project` |
| `taiga_update_notify_policy` | Cambia el nivel de notificaciones (1: Nada, 2: Involucrado, 3: Todo) | `id`, `notify_level` |
| **Interacciones Sociales y Seguimiento** | | |
| `taiga_upvote` | Vota a favor de una historia, tarea, bug o épica | `type`, `id` |
| `taiga_downvote` | Retira el voto de una historia, tarea, bug o épica | `type`, `id` |
| `taiga_list_voters` | Lista los usuarios que han votado por un elemento | `type`, `id` |
| `taiga_watch` | Se suscribe a notificaciones de cualquier elemento | `type`, `id` |
| `taiga_unwatch` | Cancela suscripción de notificaciones | `type`, `id` |
| `taiga_list_watchers` | Lista los usuarios observadores de cualquier recurso | `type`, `id` |
| **Líneas de Tiempo (Timeline)** | | |
| `taiga_get_timeline` | Obtiene el timeline de actividad de usuario, proyecto o perfil | `type` ('user', 'project', 'profile'), `id` |
| **Usuarios** | | |
| `taiga_get_me` | Consulta perfil del usuario autenticado actual | *(ninguno)* |
| `taiga_get_user` | Consulta perfil de un usuario por ID | `id` |
| `taiga_get_user_contacts` | Lista contactos y conexiones del usuario | `id` |
| `taiga_get_user_liked` | Lista elementos a los que el usuario ha dado like | `id` |
| `taiga_get_user_voted` | Lista elementos votados por el usuario | `id` |
| `taiga_get_user_watched` | Lista elementos en seguimiento por el usuario | `id` |
| **Consultas en Lenguaje Natural, Búsqueda y Resumen** | | |
| `taiga_natural_query` | Consulta en lenguaje natural inteligente con resolución automática de intenciones, sprint activo, estados y miembros (tabla markdown o JSON) | `query`, `project`, `format`, `type`, `status`, `milestone`, `assigned_to`, `limit` |
| `taiga_query` | Alias de `taiga_natural_query` para consultas en lenguaje natural | `query`, `project`, `format` |
| `taiga_search` | Búsqueda global de texto en historias, tareas, bugs, epics y wiki (con fallback a lenguaje natural) | `query`, `project` |
| `taiga_get_project_summary` | Resumen ejecutivo con métricas de estados, sprints activos y miembros | `project` |
| `taiga_get_project_statuses` | Obtiene todos los IDs de estados, prioridades, severidades y roles | `project` |
| **Servidor y Configuración** | | |
| `taiga_get_config` | Consulta la configuración activa del servidor | *(ninguno)* |
| `taiga_set_config` | Modifica dinámicamente URL, Token o Proyecto | `api_url`, `auth_token`, `default_project` |
| `taiga_login` | Autentica con usuario y contraseña | `username`, `password`, `api_url` |
| `taiga_test_connection` | Comprueba la conectividad con Taiga | *(ninguno)* |

*Nota: Todas las herramientas soportan nombres legibles para estados (`status`), sprints (`milestone`), prioridades, severidades y asignaciones (`assigned_to`), además de resolución automática de versión concurrente (`version`) y parámetros opcionales `api_url`, `auth_token` y `project`.*

---

## 🤖 Configuración en IDEs Agénticos y Clientes MCP (`mcp.json`)

El servidor cumple íntegramente con la especificación **Model Context Protocol (MCP)** (`2024-11-05`), permitiendo su uso inmediato en entornos de desarrollo asistidos por IA y herramientas agénticas como **Cursor**, **Windsurf**, **Claude Desktop**, **Continue**, **Cline / Roo Code** o **Antigravity**.

---

### 📍 Ubicación del archivo de configuración `mcp.json`

Dependiendo del entorno o IDE agéntico utilizado, el archivo de configuración se encuentra en:

| Entorno / IDE Agéntico | Tipo de Ámbito | Ruta del archivo |
|---|---|---|
| **Cursor** | Global (Usuario) | `~/.cursor/mcp.json` |
| **Cursor** | Proyecto específico | `<directorio-del-proyecto>/.cursor/mcp.json` |
| **Windsurf** | Global (Usuario) | `~/.codeium/windsurf/mcp_config.json` |
| **Claude Desktop** | macOS | `~/Library/Application Support/Claude/claude_desktop_config.json` |
| **Claude Desktop** | Windows | `%APPDATA%\Claude\claude_desktop_config.json` |
| **Claude Desktop** | Linux | `~/.config/Claude/claude_desktop_config.json` |
| **VS Code (Cline / Roo)** | Extensión | Configuración accesible vía UI o `cline_mcp_settings.json` |
| **Antigravity / Gemini CLI** | Global / Proyecto | `~/.gemini/antigravity/mcp.json` o `.gemini/mcp.json` |

---

### 🔑 Paso Previo: Obtención del Token de Autenticación

Todas las peticiones al MCP están protegidas por autenticación Bearer. Si no conoces tu token de Taiga, puedes obtenerlo fácilmente autenticándote contra el servidor MCP desplegado:

```bash
curl -X POST https://taiga-mcp.afalabarce.dev/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{
    "username": "tu_usuario_taiga",
    "password": "tu_password_taiga",
    "apiUrl": "https://api.taiga.io"
  }'
```

La respuesta te entregará el `authToken` (JWT de Taiga) para usarlo en tu `mcp.json`.

---

### 🌐 Opción 1: Conexión Remota (Streamable HTTP & SSE)

Esta modalidad se conecta a tu servidor Linux en producción a través del túnel seguro HTTPS.

#### Formato A: Streamable HTTP Transport con Basic Auth (Recomendado para Antigravity)
Los clientes modernos como **Antigravity** o **Gemini CLI** utilizan la especificación **Streamable HTTP transport**, comunicándose mediante peticiones HTTP `POST` directas al endpoint `/mcp`. Utilizar **Basic Auth** (`base64(usuario:password)`) evita que tengas que renovar tokens JWT periódicamente:

```json
{
  "mcpServers": {
    "taiga": {
      "url": "https://taiga-mcp.afalabarce.dev/mcp",
      "headers": {
        "Authorization": "Basic dXN1YXJpb190YWlnYTpwYXNzd29yZF90YWlnYQ==",
        "X-Taiga-Url": "https://taiga.afalabarce.dev",
        "X-Taiga-Project": "oposita2"
      }
    }
  }
}
```
*(Para generar tu valor base64: `echo -n "tu_usuario:tu_password" | base64`)*

#### Formato B: Streamable HTTP Transport con Bearer Token
Si prefieres utilizar un token JWT previamente emitido:

```json
{
  "mcpServers": {
    "taiga": {
      "url": "https://taiga-mcp.afalabarce.dev/mcp",
      "headers": {
        "Authorization": "Bearer TU_TOKEN_BEARER_TAIGA",
        "X-Taiga-Url": "https://taiga.afalabarce.dev",
        "X-Taiga-Project": "oposita2"
      }
    }
  }
}
```

#### Formato C: Server-Sent Events tradicional (Cursor, Windsurf, Continue)
Si tu IDE agéntico utiliza el transporte SSE clásico por `EventSource`:

```json
{
  "mcpServers": {
    "taiga": {
      "url": "https://taiga-mcp.afalabarce.dev/sse",
      "headers": {
        "Authorization": "Basic dXN1YXJpb190YWlnYTpwYXNzd29yZF90YWlnYQ==",
        "X-Taiga-Url": "https://taiga.afalabarce.dev",
        "X-Taiga-Project": "oposita2"
      }
    }
  }
}
```

> **💡 Compatibilidad Híbrida**: El servidor soporta tanto Basic Auth como Bearer Token, y tanto Streamable HTTP como SSE en ambos endpoints (`/mcp` y `/sse`), evitando errores `405 Method Not Allowed` independientemente del tipo de cliente utilizado.
>
> **💡 Nota sobre Autenticación en SSE**: Algunos clientes MCP no transmiten cabeceras HTTP personalizadas al abrir el canal `EventSource`. Por esta razón, el servidor acepta tanto `?basic=<base64>` como `?token=<jwt>` como parámetros query en la URL.

#### Formato D: Mediante Proxy `mcp-remote` (Claude Desktop y Clientes Stdio-to-SSE)
Para clientes como Claude Desktop que requieren un proceso local para conectarse a un SSE remoto:

```json
{
  "mcpServers": {
    "taiga": {
      "command": "npx",
      "args": [
        "-y",
        "mcp-remote",
        "https://taiga-mcp.afalabarce.dev/sse?token=TU_TOKEN_BEARER_TAIGA"
      ],
      "env": {
        "NODE_TLS_REJECT_UNAUTHORIZED": "0"
      }
    }
  }
}
```

---

### 💻 Opción 2: Conexión Local por Stdio (Sin Servidor Remoto)

Si prefieres ejecutar el binario localmente en tu máquina como un proceso hijo del IDE agéntico, utiliza el modo `--stdio`.

#### Formato A: Utilizando el script ejecutable `server` / `bin/server`
```json
{
  "mcpServers": {
    "taiga": {
      "command": "/ruta/absoluta/a/taiga-mcpserver/server/build/distributions/server-1.0.0/server",
      "args": [
        "--stdio"
      ],
      "env": {
        "TAIGA_API_URL": "https://api.taiga.io",
        "TAIGA_AUTH_TOKEN": "TU_TOKEN_BEARER_TAIGA",
        "TAIGA_DEFAULT_PROJECT": "mi-proyecto-slug"
      }
    }
  }
}
```

#### Formato B: Utilizando el JAR independiente directamente con `java`
```json
{
  "mcpServers": {
    "taiga": {
      "command": "java",
      "args": [
        "-jar",
        "/ruta/absoluta/a/taiga-mcpserver/server/build/libs/server-standalone.jar",
        "--stdio",
        "--taiga-url", "https://api.taiga.io",
        "--taiga-token", "TU_TOKEN_BEARER_TAIGA",
        "--taiga-project", "mi-proyecto-slug"
      ]
    }
  }
}
```

---

### 💡 Capacidades y Ejemplos de Uso con el Asistente IA

Una vez configurado en `mcp.json`, el agente inteligente dispondrá del catálogo de herramientas de Taiga. Puedes pedirle en lenguaje natural cosas como:

- *"¿Cuáles son las tareas pendientes del sprint actual en el proyecto oposita2?"* (usará `taiga_natural_query` para resolver automáticamente el sprint activo y estados no cerrados en una tabla Markdown lista con un mínimo consumo de tokens).
- *"Crea una historia de usuario titulada 'Implementar login OAuth2' con descripción técnica y etiquetas 'seguridad, backend'."*
- *"Consulta las incidencias críticas abiertas y genera un informe de estado."*
- *"Actualiza el estado de la tarea #45 a 'Resuelta' y asígnala a mi usuario."*
- *"Crea una página wiki con la documentación de despliegue en formato Markdown."*

#### ⚡ Consultas en Lenguaje Natural desde Terminal CLI (Sin Scripts Python)
Si el agente o usuario opera desde una terminal bash sin integración nativa de function calling MCP, puede ejecutar directamente la consulta con el binario:

```bash
# Consulta rápida en lenguaje natural (salida en tabla Markdown)
./bin/server -q "¿Cuáles son las tareas pendientes del sprint actual?"

# Salida en formato JSON compacto
./bin/server -q "historias de usuario asignadas a Antonio" --format json
```
Esto elimina por completo la necesidad de que los agentes creen scripts Python intermedios o llamadas REST ad-hoc.

---

## 🐧 Despliegue en Servidor Remoto Linux

### 🚀 Despliegue Automatizado con Gradle (`./gradlew :server:deploy`)

El proyecto dispone de la tarea Gradle `:server:deploy` (desarrollada con `JSch` en `buildSrc/src/main/kotlin/ServerDeployTask.kt`), idéntica al flujo de publicación de `Aplicacion-KMP-Oposiciones-AGE`. Esta tarea compila el proyecto, genera el paquete de distribución `distZip`, lo sube vía SFTP al servidor remoto, descomprime las librerías, configura los recursos y reinicia el servicio automáticamente.

#### Variables de Entorno Requeridas:
- `TAIGA_MCP_SERVER`: Host o dirección IP del servidor remoto al que publicar (ej. `taiga-mcp.tudominio.com`).
- `TAIGA_MCP_SHELL_USER`: Usuario con el que iniciar sesión SSH/SFTP.
- `TAIGA_MCP_SHELL_PASS`: Contraseña del usuario para la sesión SSH/SFTP.
- `TAIGA_MCP_SHELL_PORT`: *(Opcional)* Puerto SSH del servidor (por defecto `22`).
- `TAIGA_MCP_SERVICE`: *(Opcional)* Nombre del servicio systemd a reiniciar (por defecto `taiga-mcp.service`).

#### Ejecución del Despliegue:
```bash
export TAIGA_MCP_SERVER="taiga-mcp.tudominio.com"
export TAIGA_MCP_SHELL_USER="mi_usuario_ssh"
export TAIGA_MCP_SHELL_PASS="mi_password_seguro"

# Ejecutar despliegue completo
./gradlew :server:deploy
```

#### Flujo Automatizado Paso a Paso:
1. **Compilación y Empaquetado**: Genera automáticamente `server-standalone.jar` y el bundle de distribución `server-1.0.0.zip` vía `:server:distZip`.
2. **Subida SFTP**: Sube el archivo zip y las plantillas de configuración (`application.yaml` y `logback.xml`).
3. **Descompresión Remota**: Ejecuta `unzip -o -L <zip>` en el servidor vía canal SSH `exec`.
4. **Despliegue de Binarios**: Mueve las librerías a la raíz del directorio de trabajo y otorga permisos de ejecución a los binarios (`chmod +x bin/*`).
5. **Configuración de Recursos**: Crea la carpeta `resources/` y copia `application.yaml` y `logback.xml` **únicamente si no existen previamente en el servidor**, preservando ajustes personalizados en producción.
6. **Limpieza**: Elimina los archivos temporales y carpetas residuales de la descompresión.
7. **Reinicio del Servicio**: Ejecuta `systemctl --user daemon-reload && systemctl --user restart taiga-mcp.service`.

---

### Opción 1: Servicio Nativo systemd + Nginx

Esta es la opción idónea para servidores dedicados o VPS donde se desea la máxima velocidad y el mínimo consumo de memoria.

#### Paso 1: Instalar dependencias en el servidor
```bash
# En Ubuntu / Debian
sudo apt update && sudo apt install -y openjdk-17-jre-headless nginx certbot python3-certbot-nginx
```

#### Paso 2: Crear el usuario de sistema y directorios
```bash
sudo useradd -r -s /bin/false -d /opt/taiga-mcp taiga
sudo mkdir -p /opt/taiga-mcp /etc/taiga-mcp /var/log/taiga-mcp
```

#### Paso 3: Copiar el archivo JAR ejecutable
Desde tu máquina de desarrollo:
```bash
scp server/build/libs/server-standalone.jar usuario@tu-servidor:/tmp/
```
En el servidor:
```bash
sudo mv /tmp/server-standalone.jar /opt/taiga-mcp/server-standalone.jar
sudo chown -R taiga:taiga /opt/taiga-mcp /var/log/taiga-mcp
sudo chmod 750 /opt/taiga-mcp
```

#### Paso 4: Crear el archivo de entorno seguro
Crea el archivo `/etc/taiga-mcp/taiga-mcp.env`:
```bash
sudo tee /etc/taiga-mcp/taiga-mcp.env > /dev/null << 'EOF'
HOST=127.0.0.1
PORT=8080
TAIGA_API_URL=https://api.taiga.io
TAIGA_AUTH_TOKEN=tu_token_inicial_opcional
TAIGA_DEFAULT_PROJECT=tu_slug_o_id_opcional
EOF

sudo chown taiga:taiga /etc/taiga-mcp/taiga-mcp.env
sudo chmod 600 /etc/taiga-mcp/taiga-mcp.env
```

#### Paso 5: Instalar la unidad systemd
Copia el archivo `systemd/taiga-mcp.service` a `/etc/systemd/system/`:
```bash
sudo cp systemd/taiga-mcp.service /etc/systemd/system/taiga-mcp.service
sudo systemctl daemon-reload
sudo systemctl enable --now taiga-mcp
```

Comprueba el estado del servicio:
```bash
sudo systemctl status taiga-mcp
```

---

### Opción 2: Docker y Docker Compose + Nginx

Esta opción permite un aislamiento completo del entorno y portabilidad total mediante contenedores.

#### Paso 1: Configurar el archivo `.env`
En la raíz del proyecto en tu servidor:
```bash
cat << 'EOF' > .env
TAIGA_API_URL=https://api.taiga.io
TAIGA_AUTH_TOKEN=tu_token_aqui
TAIGA_DEFAULT_PROJECT=mi-proyecto
PORT=8080
EOF
```

#### Paso 2: Construir e iniciar el stack
El proyecto incluye un `Dockerfile` multi-stage optimizado que compila el código y ejecuta el JAR en una imagen segura Alpine con usuario no privilegiado:

```bash
docker compose up -d --build
```

#### Paso 3: Verificar contenedor y logs
```bash
docker compose ps
docker compose logs -f
```

---

### Ajustes Críticos de Nginx para SSE

Para garantizar que el streaming en tiempo real de Server-Sent Events (SSE) funcione correctamente sin cortes ni retardos acumulativos en el buffer, es **imprescindible** que Nginx desactive el buffering en las rutas de streaming.

El repositorio incluye el archivo optimizado [`nginx/taiga-mcp.conf`](file:///Users/afalabarce/Desarrollo/IA/taiga-mcpserver/nginx/taiga-mcp.conf):

```nginx
upstream taiga_mcp_backend {
    server 127.0.0.1:8080;
    keepalive 32;
}

server {
    listen 80;
    server_name taiga-mcp.tudominio.com;

    client_max_body_size 20M;

    # 1. Configuración de streaming en tiempo real SSE
    location /sse {
        proxy_pass http://taiga_mcp_backend/sse;
        proxy_http_version 1.1;

        # DESACTIVACIÓN CRÍTICA DE BUFFERING PARA SSE
        proxy_buffering off;
        proxy_cache off;
        chunked_transfer_encoding off;

        # Cabeceras de conexión persistente
        proxy_set_header Connection '';
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;

        # Timeouts largos para mantener vivo el canal SSE
        proxy_read_timeout 3600s;
        proxy_send_timeout 3600s;
    }

    # 2. Recepción de mensajes JSON-RPC y endpoints HTTP
    location / {
        proxy_pass http://taiga_mcp_backend/;
        proxy_http_version 1.1;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;
    }
}
```

#### Aplicar la configuración en Nginx:
```bash
sudo cp nginx/taiga-mcp.conf /etc/nginx/sites-available/taiga-mcp.conf
sudo ln -sf /etc/nginx/sites-available/taiga-mcp.conf /etc/nginx/sites-enabled/
sudo nginx -t
sudo systemctl reload nginx
```

---

### Configuración SSL con Let's Encrypt / Certbot

Habilita cifrado HTTPS con un solo comando para asegurar el transporte de tokens y datos sensibles:

```bash
sudo certbot --nginx -d taiga-mcp.tudominio.com
```

Certbot actualizará automáticamente la configuración de Nginx para redirigir todo el tráfico HTTP a HTTPS y configurará la renovación automática del certificado.

---

## 🔍 Mantenimiento y Monitorización

### Verificación del Despliegue

```bash
# 1. Comprobar salud del servicio
curl https://taiga-mcp.tudominio.com/health

# 2. Consultar herramientas disponibles (con token)
curl -H "Authorization: Bearer tu_token" https://taiga-mcp.tudominio.com/api/mcp/tools

# 3. Comprobar Swagger UI
curl -I https://taiga-mcp.tudominio.com/swagger
```

### Gestión de Logs

- **Si usas systemd**:
  ```bash
  # Ver logs en tiempo real
  sudo journalctl -u taiga-mcp -f

  # Ver logs de errores recientes
  sudo journalctl -u taiga-mcp -p err -n 50
  ```

- **Si usas Docker**:
  ```bash
  docker compose logs -f --tail=100
  ```

- **Logs de Nginx**:
  ```bash
  sudo tail -f /var/log/nginx/access.log
  sudo tail -f /var/log/nginx/error.log
  ```

---

## 📄 Licencia

Este proyecto está distribuido bajo la licencia MIT. Consulta el archivo `LICENSE` para más información.
