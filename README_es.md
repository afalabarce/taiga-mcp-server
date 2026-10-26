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
| `/sse` | `GET` | **Protegido** (`auth-bearer`) | Canal de eventos Server-Sent Events MCP. |
| `/messages` | `POST` | **Protegido** (`auth-bearer`) | Envío de mensajes JSON-RPC para sesiones SSE. |
| `/mcp` | `POST` | **Protegido** (`auth-bearer`) | Protocolo MCP directo por HTTP. |
| `/api/config` | `GET`, `POST`| **Protegido** (`auth-bearer`) | Consulta y actualización de parámetros en caliente. |
| `/api/mcp/tools` | `GET` | **Protegido** (`auth-bearer`) | Listado de herramientas disponibles en formato JSON. |
| `/api/mcp/tools/{tool}/call` | `POST` | **Protegido** (`auth-bearer`) | Ejecución de herramientas desde Swagger / API. |
| `/api/taiga/*` | `GET`, `POST`| **Protegido** (`auth-bearer`) | Proxy REST de testeo directo contra Taiga. |

### Cómo Autenticar las Peticiones Protegidas

Los clientes pueden incluir el token mediante cualquiera de las siguientes opciones:

1. **Cabecera HTTP Estándar (Recomendada):**
   ```http
   Authorization: Bearer tu_token_taiga_aqui
   ```
2. **Cabecera Alternativa:**
   ```http
   X-Taiga-Token: tu_token_taiga_aqui
   ```
3. **Parámetro de Query en la URL (para conexiones SSE donde el cliente no soporte cabeceras):**
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

## 🧰 Catálogo de Herramientas MCP

El servidor registra las siguientes herramientas bajo la especificación Model Context Protocol:

| Herramienta | Acción | Parámetros Notables |
|---|---|---|
| `taiga_list_projects` | Lista los proyectos accesibles para el usuario | `member_id` |
| `taiga_get_project` | Obtiene el detalle de un proyecto | `project_id`, `slug`, `project` |
| `taiga_list_user_stories` | Lista historias de usuario con filtros | `project`, `milestone_id`, `status_id`, `epic_id` |
| `taiga_get_user_story` | Obtiene el detalle de una historia por ID | `id` |
| `taiga_create_user_story` | Crea una historia de usuario | `subject`, `description`, `project`, `status_id`, `milestone_id`, `tags` |
| `taiga_update_user_story` | Modifica una historia existente | `id`, `subject`, `description`, `status_id`, `version`, `milestone_id` |
| `taiga_delete_user_story` | Elimina una historia de usuario | `id` |
| `taiga_list_tasks` | Lista tareas de un proyecto o historia | `project`, `user_story_id`, `milestone_id`, `status_id`, `assigned_to` |
| `taiga_get_task` | Obtiene el detalle de una tarea | `id` |
| `taiga_create_task` | Crea una tarea | `subject`, `description`, `user_story_id`, `milestone_id`, `project` |
| `taiga_update_task` | Modifica el estado o datos de una tarea | `id`, `subject`, `description`, `status_id`, `assigned_to`, `version` |
| `taiga_delete_task` | Elimina una tarea | `id` |
| `taiga_list_epics` | Lista las épicas de un proyecto | `project`, `status_id` |
| `taiga_get_epic` | Obtiene el detalle de una épica | `id` |
| `taiga_create_epic` | Crea una épica con color y etiquetas | `subject`, `description`, `color`, `project`, `tags` |
| `taiga_update_epic` | Modifica una épica existente | `id`, `subject`, `description`, `color`, `status_id`, `version` |
| `taiga_delete_epic` | Elimina una épica por ID | `id` |
| `taiga_list_issues` | Lista incidencias o bugs | `project`, `status_id`, `priority_id`, `severity_id`, `type_id` |
| `taiga_get_issue` | Obtiene el detalle de una incidencia | `id` |
| `taiga_create_issue` | Registra un nuevo bug o incidencia | `subject`, `project`, `type_id`, `priority_id`, `severity_id`, `status_id` |
| `taiga_update_issue` | Actualiza el estado o prioridad de una incidencia | `id`, `subject`, `status_id`, `priority_id`, `version` |
| `taiga_delete_issue` | Elimina una incidencia por ID | `id` |
| `taiga_list_milestones` | Lista los sprints o hitos | `project` |
| `taiga_get_milestone` | Consulta avance, historias y puntos del sprint | `id` |
| `taiga_create_milestone` | Planifica un nuevo sprint con fechas | `name`, `estimated_start`, `estimated_finish`, `project` |
| `taiga_list_wiki_pages` | Lista los artículos de la wiki | `project` |
| `taiga_get_wiki_page` | Obtiene el contenido Markdown de un artículo | `slug`, `project` |
| `taiga_create_wiki_page` | Crea una nueva página wiki | `slug`, `content`, `project` |
| `taiga_update_wiki_page` | Actualiza el texto de una página wiki | `id`, `content`, `version` |
| `taiga_get_project_statuses`| Obtiene todos los IDs de estados, prioridades y tipos | `project` |
| `taiga_get_config` | Consulta la configuración activa del servidor | *(ninguno)* |
| `taiga_set_config` | Modifica dinámicamente URL, Token o Proyecto | `api_url`, `auth_token`, `default_project` |
| `taiga_login` | Autentica con usuario y contraseña | `username`, `password`, `api_url` |
| `taiga_test_connection` | Comprueba la conectividad con Taiga | *(ninguno)* |

*Nota: Todas las herramientas soportan los parámetros opcionales `api_url`, `auth_token` y `project` para realizar consultas puntuales sobre instancias o proyectos diferentes al configurado globalmente.*

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

#### Formato A: Streamable HTTP Transport (Recomendado para Antigravity)
Los clientes modernos como **Antigravity** o **Gemini CLI** utilizan la especificación **Streamable HTTP transport**, comunicándose mediante peticiones HTTP `POST` directas al endpoint `/mcp`:

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

#### Formato B: Server-Sent Events tradicional (Cursor, Windsurf, Continue)
Si tu IDE agéntico utiliza el transporte SSE clásico por `EventSource`:

```json
{
  "mcpServers": {
    "taiga": {
      "url": "https://taiga-mcp.afalabarce.dev/sse?token=TU_TOKEN_BEARER_TAIGA",
      "headers": {
        "Authorization": "Bearer TU_TOKEN_BEARER_TAIGA",
        "X-Taiga-Url": "https://taiga.afalabarce.dev",
        "X-Taiga-Project": "oposita2"
      }
    }
  }
}
```

> **💡 Compatibilidad Híbrida**: El servidor soporta tanto Streamable HTTP como SSE en ambos endpoints (`/mcp` y `/sse`), evitando errores `405 Method Not Allowed` independientemente del tipo de cliente utilizado.
>
> **💡 Nota sobre Autenticación en SSE**: Algunos clientes MCP no transmiten cabeceras HTTP personalizadas al abrir el canal `EventSource`. Por esta razón, el servidor acepta el token tanto en la cabecera `Authorization: Bearer <token>` como en el parámetro query `?token=<token>`. Se recomienda incluir ambos para máxima compatibilidad.

#### Formato C: Mediante Proxy `mcp-remote` (Claude Desktop y Clientes Stdio-to-SSE)
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

- *"¿Cuáles son las tareas pendientes del sprint actual en el proyecto backend?"*
- *"Crea una historia de usuario titulada 'Implementar login OAuth2' con descripción técnica y etiquetas 'seguridad, backend'."*
- *"Consulta las incidencias críticas abiertas y genera un informe de estado."*
- *"Actualiza el estado de la tarea #45 a 'Resuelta' y asígnala a mi usuario."*
- *"Crea una página wiki con la documentación de despliegue en formato Markdown."*

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
