# Taiga MCP Server

High-performance **Model Context Protocol (MCP)** server developed in **Kotlin Multiplatform** (`:server` module), designed to seamlessly connect Artificial Intelligence assistants (Claude Desktop, Cursor, Continue, autonomous agents, etc.) with the **Taiga** project management platform (`https://docs.taiga.io/api.html`).

It enables full management of projects, user stories, tasks, epics, issues/bugs, sprints (milestones), and wiki pages, supporting real-time streaming via **Server-Sent Events (SSE)**, bidirectional transport via **Stdio**, an interactive **Swagger UI** dynamically generated at runtime, dynamic on-the-fly remote configuration, and secure authentication with **Bearer Tokens**.

---

## 📑 Table of Contents

- [Main Features](#-main-features)
- [Technologies Used](#-technologies-used)
- [Architecture & Implementation](#-architecture--implementation)
- [Compilation & Building](#-compilation--building)
- [Security & Authentication](#-security--authentication)
- [Dynamic Remote Parameterization](#-dynamic-remote-parameterization)
- [Swagger UI & OpenAPI Documentation](#-swagger-ui--openapi-documentation)
- [MCP Tools Catalog](#-mcp-tools-catalog)
- [Configuration in Agentic IDEs and MCP Clients (mcp.json)](#-configuration-in-agentic-ides-and-mcp-clients-mcpjson)
- [Deployment on Remote Linux Server](#-deployment-on-remote-linux-server)
  - [Option 1: Native systemd Service + Nginx](#option-1-native-systemd-service--nginx)
  - [Option 2: Docker and Docker Compose + Nginx](#option-2-docker-and-docker-compose--nginx)
  - [Critical Nginx Tweaks for SSE](#critical-nginx-tweaks-for-sse)
  - [SSL Configuration with Let's Encrypt / Certbot](#ssl-configuration-with-lets-encrypt--certbot)
- [Maintenance & Monitoring](#-maintenance--monitoring)
- [License](#-license)

---

## 🚀 Main Features

1. **Comprehensive Taiga API Management**:
   - **Projects**: Listing and retrieval by ID or slug.
   - **User Stories**: Query, creation, update, and deletion with sprint, status, and assignment support.
   - **Tasks**: Full CRUD associated with user stories or milestones.
   - **Epics**: Organization by color, status, and tags.
   - **Issues / Bugs**: Management with severity, priority, type, and status attributes.
   - **Sprints / Milestones**: Progress, delivery dates, total points, and closed points.
   - **Wiki**: Reading, creation, and edition of pages in Markdown format.
   - **Metadata & Dictionaries**: Automatic resolution of valid IDs for project statuses, priorities, and severities.

2. **Security & Tokenization**:
   - Native Ktor protection using `authenticate("auth-bearer")` across all MCP and administrative endpoints.
   - Public `/api/auth/login` endpoint for credential-based authentication against Taiga, avoiding storing plaintext passwords on disk.
   - Token masking in configuration responses (`maskedToken`) to safeguard privacy.
   - Multi-format token transmission support: `Authorization: Bearer <token>`, `X-Taiga-Token: <token>`, or URL query parameter `?token=<token>`.

3. **Dynamic Swagger UI with Ktor DSL**:
   - Implemented using Ktor's official extension function: `Route.describe { ... }`.
   - Runtime generation of the OpenAPI 3.0 specification (`OpenApiDocSource.Routing()`) without static YAML or XML files.
   - Interactive web console at `/swagger` allowing testing of both the MCP API and direct Taiga calls.

4. **Dual MCP Transport**:
   - **SSE (Server-Sent Events)**: For remote server deployments behind Nginx and consumption by distributed AI clients.
   - **Stdio**: For local embedded execution inside desktop tools (Claude Desktop, CLI scripts).

---

## 💻 Technologies Used

| Component | Technology | Version | Role in Project |
|---|---|---|---|
| **Language** | [Kotlin Multiplatform (KMP)](https://kotlinlang.org/) | `2.3.0` | Base multiplatform architecture targeting JVM 17 for the `:server` module. |
| **HTTP Server** | [Ktor Server (CIO)](https://ktor.io/) | `3.4.0` | Asynchronous, reactive server powered by high-performance coroutines. |
| **OpenAPI / Swagger** | `ktor-server-routing-openapi` | `3.4.0` | Declarative `.describe { ... }` DSL and interactive Swagger UI console. |
| **HTTP Client** | [Ktor Client (CIO)](https://ktor.io/) | `3.4.0` | Non-blocking client for consuming the Taiga REST API. |
| **Authentication** | `ktor-server-auth` | `3.4.0` | Bearer provider (`auth-bearer`) inspecting headers and query parameters. |
| **Serialization** | [kotlinx.serialization](https://github.com/Kotlin/kotlinx.serialization) | `1.8.0` | Strict, typed JSON serialization without runtime reflection. |
| **Concurrency** | [kotlinx.coroutines](https://github.com/Kotlin/kotlinx.coroutines) | `1.10.1` | Reactive event channels (`Channel`) and structured concurrency. |
| **CLI & Arguments** | [Clikt](https://ajalt.github.io/clikt/) | `5.0.3` | Command-line parameter and environment variable parsing. |
| **Logging** | [Logback](https://logback.qos.ch/) + SLF4J | `1.5.16` | Configurable and structured event logging. |
| **Containers** | Docker + Docker Compose | - | Lightweight container packaging with Eclipse Temurin 17 JRE Alpine. |
| **Reverse Proxy** | Nginx | - | SSL termination, buffer-off streaming for SSE, and request load balancing. |

---

## 📐 Architecture & Implementation

The project follows a decoupled, event-driven architecture:

```
taiga-mcpserver/
├── build.gradle.kts
├── settings.gradle.kts
├── gradle/libs.versions.toml
├── nginx/
│   └── taiga-mcp.conf          # Nginx configuration optimized for SSE and Swagger
├── systemd/
│   └── taiga-mcp.service       # systemd unit file with auto-restart and hardening
├── Dockerfile                  # Multi-stage build (Gradle build + JRE Alpine runtime)
├── docker-compose.yml          # Orchestration with healthchecks
└── server/                     # Kotlin Multiplatform Module (:server)
    ├── build.gradle.kts
    └── src/
        ├── commonMain/
        │   ├── kotlin/dev/afalabarce/mcp/
        │   │   ├── Main.kt                     # CLI entrypoint and Ktor CIO server
        │   │   ├── config/                     # Thread-safe config (StateFlow/Atomic)
        │   │   │   ├── TaigaConfig.kt          # Configuration models & masking
        │   │   │   └── ConfigManager.kt        # Reactive configuration manager
        │   │   ├── taiga/
        │   │   │   ├── model/TaigaModels.kt    # Strongly typed Taiga DTOs
        │   │   │   ├── client/TaigaClient.kt   # Async Taiga REST client
        │   │   │   └── client/TaigaClientFactory.kt
        │   │   ├── mcp/
        │   │   │   ├── protocol/McpProtocol.kt # JSON-RPC 2.0 & MCP protocol (2024-11-05)
        │   │   │   ├── tools/                  # Registered tools catalog
        │   │   │   │   ├── TaigaToolRegistry.kt# Central tool registry
        │   │   │   │   ├── ProjectTools.kt     # Project management tools
        │   │   │   │   ├── UserStoryTools.kt   # User story tools
        │   │   │   │   ├── TaskTools.kt        # Task management tools
        │   │   │   │   ├── EpicTools.kt        # Epic management tools
        │   │   │   │   ├── IssueTools.kt       # Issue and bug tracking tools
        │   │   │   │   ├── MilestoneTools.kt   # Sprint/milestone tools
        │   │   │   │   ├── WikiTools.kt        # Wiki management tools
        │   │   │   │   ├── MetadataTools.kt    # Dictionary and status tools
        │   │   │   │   └── ConfigTools.kt      # Configuration & diagnostic tools
        │   │   │   └── server/                 # SSE, Stdio, and MCP Engine sessions
        │   │   │       ├── McpServer.kt        # JSON-RPC method dispatcher
        │   │   │       ├── McpSseSession.kt    # Concurrent SSE sessions via Channels
        │   │   │       └── McpStdioHandler.kt  # Standard I/O transport handler
        │   │   └── web/                        # HTTP Ktor & OpenAPI server
        │   │       ├── ServerApp.kt            # Ktor setup (CORS, Auth, ContentNeg)
        │   │       ├── auth/TaigaAuth.kt       # Bearer auth provider for Ktor
        │   │       └── routes/
        │   │           ├── McpRoutes.kt        # /sse, /messages, /mcp
        │   │           ├── ConfigRoutes.kt     # /api/config, /api/auth/login
        │   │           ├── SwaggerRoutes.kt    # /swagger, /openapi.json, interactive tests
        │   │           └── HealthRoutes.kt     # /health, /
        │   └── resources/logback.xml
        └── commonTest/kotlin/dev/afalabarce/mcp/
            └── TaigaMcpServerTest.kt           # Unit and integration test suite
```

---

## 🛠️ Compilation & Building

### Prerequisites
- **JDK 17 or higher** installed (`java --version`).
- Internet connection for initial Gradle dependencies download.

### Build Commands

```bash
# 1. Clone the repository
git clone https://github.com/afalabarce/taiga-mcp-server.git
cd taiga-mcp-server

# 2. Grant execution permissions to Gradle wrapper
chmod +x gradlew

# 3. Run unit and integration tests
./gradlew jvmTest

# 4. Generate standalone Fat JAR
./gradlew :server:fatJar
```

The standalone executable artifact will be located at:
```
server/build/libs/server-standalone.jar
```
*This self-contained file embeds the CIO server, all dependencies, and resources, ready to run on any environment with JVM 17.*

### Configuration via `SERVER_OPTS` (`application.yaml` & `logback.xml`)

The server supports the `SERVER_OPTS` environment variable to dynamically customize the configuration and logging file locations:

```bash
export SERVER_OPTS="-Dconfig.file=/srv/taiga-mcp/resources/application.yaml -Dlogback.configurationFile=/srv/taiga-mcp/resources/logback.xml"

# Running via start script (passes SERVER_OPTS to the Java process)
bin/server

# Or running standalone JAR directly (Main.kt detects and parses SERVER_OPTS automatically)
java -jar server-standalone.jar
```

The server resolves files following this lookup hierarchy:
1. **Paths provided in `SERVER_OPTS`** (via flags `-Dconfig.file=...`, `-Dlogback.configurationFile=...`, or arguments `--config=...`, `--logback=...`).
2. **JVM System Properties** (`-Dconfig.file=...`, `-Dlogback.configurationFile=...`).
3. **Specific environment variables** (`CONFIG_FILE`, `LOGBACK_CONFIGURATION_FILE`).
4. **Default conventional locations** (`resources/application.yaml`, `resources/logback.xml`).

---

## 🔒 Security & Authentication

The server implements a secure-by-default model using **Ktor Authentication**:

### Endpoint Access Matrix

| Path | Method | Access Level | Description |
|---|---|---|---|
| `/api/auth/login` | `POST` | **Public** | Authenticates against Taiga with credentials and returns Bearer token. |
| `/health` | `GET` | **Public** | Health check and Taiga connection status. |
| `/` | `GET` | **Public** | Service endpoint discovery. |
| `/swagger` | `GET` | **Public** | Interactive Swagger UI web console. |
| `/openapi.json` | `GET` | **Public** | Runtime OpenAPI 3.0 specification. |
| `/sse` | `GET`, `POST` | **Protected** (`Bearer` / `Basic`) | MCP Server-Sent Events / Streamable HTTP streaming channel. |
| `/messages` | `POST` | **Protected** (`Bearer` / `Basic`) | Sends JSON-RPC messages for SSE sessions. |
| `/mcp` | `GET`, `POST` | **Protected** (`Bearer` / `Basic`) | Direct Streamable HTTP JSON-RPC endpoint and standby SSE stream. |
| `/api/config` | `GET`, `POST`| **Protected** (`Bearer` / `Basic`) | Read and hot-update runtime configuration. |
| `/api/mcp/tools` | `GET` | **Protected** (`Bearer` / `Basic`) | List available tools in JSON format. |
| `/api/mcp/tools/{tool}/call` | `POST` | **Protected** (`Bearer` / `Basic`) | Execute tools from Swagger / API. |
| `/api/taiga/*` | `GET`, `POST`| **Protected** (`Bearer` / `Basic`) | Direct Taiga REST proxy testing endpoints. |

### How to Authenticate Protected Requests

The server supports **two authentication mechanisms** to accommodate any client and avoid token expirations:

#### Method 1: HTTP Basic Authentication with Username and Password (Recommended against Token Expiration)
Ideal for **Antigravity**, **Cursor**, or long-lived assistant configurations, as it **permanently eliminates JWT expiration headaches**. The MCP server automatically authenticates against Taiga and caches/renews the active session token:

1. **Encode your credentials in Base64:**
   ```bash
   echo -n "your_username:your_password" | base64
   # Example output: eW91cl91c2VybmFtZTp5b3VyX3Bhc3N3b3Jk
   ```
2. **Send the standard HTTP Basic header:**
   ```http
   Authorization: Basic eW91cl91c2VybmFtZTp5b3VyX3Bhc3N3b3Jk
   ```
3. **Alternative Basic Auth Options:**
   - Header: `X-Taiga-Basic: eW91cl91c2VybmFtZTp5b3VyX3Bhc3N3b3Jk`
   - URL Query Parameter (for SSE / EventSource): `GET /sse?basic=eW91cl91c2VybmFtZTp5b3VyX3Bhc3N3b3Jk` or `/mcp?basic=...`

#### Method 2: Bearer Authentication with JWT Token
If you already possess a previously generated Taiga JWT token:

1. **Standard HTTP Header:**
   ```http
   Authorization: Bearer your_taiga_token_here
   ```
2. **Alternative Header:**
   ```http
   X-Taiga-Token: your_taiga_token_here
   ```
3. **URL Query Parameter:**
   ```
   GET /sse?token=your_taiga_token_here
   ```

---

## 🔄 Dynamic Remote Parameterization

The server can be configured at boot time via environment variables or CLI flags, and **modified dynamically on-the-fly** via its REST API without requiring restarts:

### 1. Log in and Retrieve Token (Tokenization)
Send user credentials to `/api/auth/login`. The server verifies them directly with Taiga API and sets the active token:

```bash
curl -X POST http://localhost:8080/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{
    "username": "user_or_email@company.com",
    "password": "SecretPassword123",
    "apiUrl": "https://api.taiga.io"
  }'
```

**Response:**
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

### 2. Inspect Active Configuration
```bash
curl -H "Authorization: Bearer your_token" http://localhost:8080/api/config
```

**Response with Masked Token:**
```json
{
  "apiUrl": "https://api.taiga.io",
  "hasAuthToken": true,
  "maskedToken": "eyJh...98aB",
  "defaultProject": "my-active-project"
}
```

### 3. Hot-Update Configuration
```bash
curl -X POST http://localhost:8080/api/config \
  -H "Authorization: Bearer your_token" \
  -H "Content-Type: application/json" \
  -d '{
    "apiUrl": "https://taiga.mycompany.com",
    "authToken": "new_secure_token",
    "defaultProject": "backend-project"
  }'
```

---

## 📖 Swagger UI & OpenAPI Documentation

Interactive documentation is dynamically generated using Ktor 3.4.0's official `io.ktor:ktor-server-routing-openapi` module via the `.describe { ... }` extension function.

### Accessing the Console
Open in your browser:
👉 **`http://localhost:8080/swagger`** (or `https://taiga-mcp.yourdomain.com/swagger`)

### Features
- **No static YAML or XML files**: The complete OpenAPI contract is derived at runtime from Ktor's routing tree.
- **Authentication Testing**: Enter your Bearer Token in the `Authorize` button of Swagger UI.
- **Interactive Tool Execution**: Test MCP tools directly (`POST /api/mcp/tools/{toolName}/call`) before connecting them to your LLM.
- **JSON Schema**: Directly accessible at `http://localhost:8080/openapi.json`.

---

## 🧰 MCP Tools Catalog

The server registers the following tools under the Model Context Protocol specification:

| Tool | Action | Notable Parameters |
|---|---|---|
| `taiga_list_projects` | Lists projects accessible to the user | `member_id` |
| `taiga_get_project` | Gets detailed information for a project | `project_id`, `slug`, `project` |
| `taiga_list_user_stories` | Lists user stories with optional filters | `project`, `milestone_id`, `status_id`, `epic_id` |
| `taiga_get_user_story` | Retrieves a user story by ID | `id` |
| `taiga_create_user_story` | Creates a new user story | `subject`, `description`, `project`, `status_id`, `milestone_id`, `tags` |
| `taiga_update_user_story` | Updates an existing user story | `id`, `subject`, `description`, `status_id`, `version`, `milestone_id` |
| `taiga_delete_user_story` | Deletes a user story | `id` |
| `taiga_list_tasks` | Lists tasks in a project or user story | `project`, `user_story_id`, `milestone_id`, `status_id`, `assigned_to` |
| `taiga_get_task` | Retrieves details for a specific task | `id` |
| `taiga_create_task` | Creates a new task | `subject`, `description`, `user_story_id`, `milestone_id`, `project` |
| `taiga_update_task` | Updates task status or details | `id`, `subject`, `description`, `status_id`, `assigned_to`, `version` |
| `taiga_delete_task` | Deletes a task | `id` |
| `taiga_list_epics` | Lists epics for a project | `project`, `status_id` |
| `taiga_get_epic` | Retrieves epic details | `id` |
| `taiga_create_epic` | Creates an epic with color and tags | `subject`, `description`, `color`, `project`, `tags` |
| `taiga_update_epic` | Modifies an existing epic | `id`, `subject`, `description`, `color`, `status_id`, `version` |
| `taiga_delete_epic` | Deletes an epic by ID | `id` |
| `taiga_list_issues` | Lists issues or bugs | `project`, `status_id`, `priority_id`, `severity_id`, `type_id` |
| `taiga_get_issue` | Retrieves issue details | `id` |
| `taiga_create_issue` | Logs a new bug or issue | `subject`, `project`, `type_id`, `priority_id`, `severity_id`, `status_id` |
| `taiga_update_issue` | Updates status or priority of an issue | `id`, `subject`, `status_id`, `priority_id`, `version` |
| `taiga_delete_issue` | Deletes an issue by ID | `id` |
| `taiga_list_milestones` | Lists sprints or milestones | `project` |
| `taiga_get_milestone` | Inspects sprint progress, stories, and points | `id` |
| `taiga_create_milestone` | Schedules a new sprint with start/end dates | `name`, `estimated_start`, `estimated_finish`, `project` |
| `taiga_list_wiki_pages` | Lists wiki articles | `project` |
| `taiga_get_wiki_page` | Retrieves Markdown content of a wiki page | `slug`, `project` |
| `taiga_create_wiki_page` | Creates a new wiki page | `slug`, `content`, `project` |
| `taiga_update_wiki_page` | Updates content of a wiki page | `id`, `content`, `version` |
| `taiga_get_project_statuses`| Retrieves all IDs for statuses, priorities, and types | `project` |
| `taiga_get_config` | Returns current active server configuration | *(none)* |
| `taiga_set_config` | Dynamically updates URL, Token, or Project | `api_url`, `auth_token`, `default_project` |
| `taiga_login` | Authenticates with username and password | `username`, `password`, `api_url` |
| `taiga_test_connection` | Tests connectivity with Taiga instance | *(none)* |

*Note: All tools support optional `api_url`, `auth_token`, and `project` arguments to perform ad-hoc operations against different instances or projects without changing global configuration.*

---

## 🤖 Configuration in Agentic IDEs and MCP Clients (`mcp.json`)

The server strictly complies with the **Model Context Protocol (MCP)** specification (`2024-11-05`), enabling immediate integration with AI-assisted IDEs and agentic tools like **Cursor**, **Windsurf**, **Claude Desktop**, **Continue**, **Cline / Roo Code**, or **Antigravity**.

---

### 📍 Configuration File Location (`mcp.json`)

Depending on your agentic IDE or environment:

| Agentic IDE / Tool | Scope | File Path |
|---|---|---|
| **Cursor** | Global (User) | `~/.cursor/mcp.json` |
| **Cursor** | Workspace Specific | `<workspace-root>/.cursor/mcp.json` |
| **Windsurf** | Global (User) | `~/.codeium/windsurf/mcp_config.json` |
| **Claude Desktop** | macOS | `~/Library/Application Support/Claude/claude_desktop_config.json` |
| **Claude Desktop** | Windows | `%APPDATA%\Claude\claude_desktop_config.json` |
| **Claude Desktop** | Linux | `~/.config/Claude/claude_desktop_config.json` |
| **VS Code (Cline / Roo)** | Extension | UI Settings or `cline_mcp_settings.json` |
| **Antigravity / Gemini CLI** | Global / Workspace | `~/.gemini/antigravity/mcp_config.json` or `.agents/mcp_config.json` |

---

### 🔑 Preliminary Step: Obtaining an Authentication Token

All requests to MCP endpoints are protected by Bearer authentication. If you don't know your Taiga token, retrieve it easily via the deployed MCP server:

```bash
curl -X POST https://taiga-mcp.afalabarce.dev/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{
    "username": "your_taiga_username",
    "password": "your_taiga_password",
    "apiUrl": "https://api.taiga.io"
  }'
```

The response will contain the `authToken` (Taiga JWT) to place in your `mcp.json`.

---

### 🌐 Option 1: Remote Connection (Streamable HTTP & SSE)

Connects to your production Linux server over secure HTTPS.

#### Format A: Streamable HTTP Transport with Basic Auth (Recommended for Antigravity)
Modern clients like **Antigravity** or **Gemini CLI** use the **Streamable HTTP transport** specification, sending direct HTTP `POST` requests to the `/mcp` endpoint. Using **Basic Auth** (`base64(username:password)`) prevents frequent token expiration issues:

```json
{
  "mcpServers": {
    "taiga": {
      "url": "https://taiga-mcp.afalabarce.dev/mcp",
      "headers": {
        "Authorization": "Basic eW91cl91c2VybmFtZTp5b3VyX3Bhc3N3b3Jk",
        "X-Taiga-Url": "https://taiga.afalabarce.dev",
        "X-Taiga-Project": "oposita2"
      }
    }
  }
}
```
*(To encode credentials in base64: `echo -n "your_username:your_password" | base64`)*

#### Format B: Streamable HTTP Transport with Bearer Token
If you prefer using a pre-generated Taiga JWT token:

```json
{
  "mcpServers": {
    "taiga": {
      "url": "https://taiga-mcp.afalabarce.dev/mcp",
      "headers": {
        "Authorization": "Bearer YOUR_TAIGA_BEARER_TOKEN",
        "X-Taiga-Url": "https://taiga.afalabarce.dev",
        "X-Taiga-Project": "oposita2"
      }
    }
  }
}
```

#### Format C: Traditional Server-Sent Events (Cursor, Windsurf, Continue)
If your agentic IDE uses classic SSE transport via `EventSource`:

```json
{
  "mcpServers": {
    "taiga": {
      "url": "https://taiga-mcp.afalabarce.dev/sse",
      "headers": {
        "Authorization": "Basic eW91cl91c2VybmFtZTp5b3VyX3Bhc3N3b3Jk",
        "X-Taiga-Url": "https://taiga.afalabarce.dev",
        "X-Taiga-Project": "oposita2"
      }
    }
  }
}
```

> **💡 Hybrid Compatibility**: The server natively supports both Basic Auth and Bearer Token, and both Streamable HTTP and SSE on both `/mcp` and `/sse` endpoints, preventing `405 Method Not Allowed` errors regardless of which endpoint or client format you configure.
>
> **💡 Note on SSE Authentication**: Some MCP clients do not transmit custom HTTP headers when opening the `EventSource` connection. The server accepts both `?basic=<base64>` and `?token=<jwt>` as URL query parameters.

#### Format D: Via `mcp-remote` Proxy (Claude Desktop & Stdio-to-SSE Clients)
For desktop clients that require a local process to bridge to a remote SSE server:

```json
{
  "mcpServers": {
    "taiga": {
      "command": "npx",
      "args": [
        "-y",
        "mcp-remote",
        "https://taiga-mcp.afalabarce.dev/sse?token=YOUR_TAIGA_BEARER_TOKEN"
      ],
      "env": {
        "NODE_TLS_REJECT_UNAUTHORIZED": "0"
      }
    }
  }
}
```

---

### 💻 Option 2: Local Stdio Connection (Without Remote Server)

If you prefer to run the binary locally on your machine as a child process of your agentic IDE, use `--stdio` mode.

#### Format A: Using the `server` / `bin/server` Script
```json
{
  "mcpServers": {
    "taiga": {
      "command": "/absolute/path/to/taiga-mcpserver/server/build/distributions/server-1.0.0/server",
      "args": [
        "--stdio"
      ],
      "env": {
        "TAIGA_API_URL": "https://api.taiga.io",
        "TAIGA_AUTH_TOKEN": "YOUR_TAIGA_BEARER_TOKEN",
        "TAIGA_DEFAULT_PROJECT": "my-project-slug"
      }
    }
  }
}
```

#### Format B: Using the Standalone Fat JAR with `java`
```json
{
  "mcpServers": {
    "taiga": {
      "command": "java",
      "args": [
        "-jar",
        "/absolute/path/to/taiga-mcpserver/server/build/libs/server-standalone.jar",
        "--stdio",
        "--taiga-url", "https://api.taiga.io",
        "--taiga-token", "YOUR_TAIGA_BEARER_TOKEN",
        "--taiga-project", "my-project-slug"
      ]
    }
  }
}
```

---

### 💡 Capabilities and Example AI Prompts

Once configured in `mcp.json`, your AI assistant will have full access to the Taiga tool catalog:

- *"What pending tasks are in the current sprint of the backend project?"*
- *"Create a user story titled 'Implement OAuth2 login' with technical details and tags 'security, backend'."*
- *"Check all open critical issues and generate a summary report."*
- *"Update task #45 status to 'Resolved' and assign it to my user."*
- *"Create a wiki page with deployment documentation in Markdown format."*

---

## 🐧 Deployment on Remote Linux Server

### 🚀 Automated Deployment with Gradle (`./gradlew :server:deploy`)

The project includes an automated deployment task `:server:deploy` (built with `JSch` in `buildSrc/src/main/kotlin/ServerDeployTask.kt`). It compiles the project, generates the `distZip` distribution bundle, uploads it via SFTP, unpacks libraries, manages resources, and restarts the systemd service automatically.

#### Required Environment Variables:
- `TAIGA_MCP_SERVER`: Hostname or IP of the destination server (e.g. `taiga-mcp.yourdomain.com`).
- `TAIGA_MCP_SHELL_USER`: SSH / SFTP username.
- `TAIGA_MCP_SHELL_PASS`: SSH / SFTP user password.
- `TAIGA_MCP_SHELL_PORT`: *(Optional)* SSH server port (defaults to `22`).
- `TAIGA_MCP_SERVICE`: *(Optional)* Name of the systemd service to restart (defaults to `taiga-mcp.service`).

#### Execution:
```bash
export TAIGA_MCP_SERVER="taiga-mcp.yourdomain.com"
export TAIGA_MCP_SHELL_USER="my_ssh_user"
export TAIGA_MCP_SHELL_PASS="my_secure_password"

# Run automated deployment
./gradlew :server:deploy
```

#### Automated Pipeline Steps:
1. **Compilation & Packaging**: Generates `server-standalone.jar` and `server-1.0.0.zip` via `:server:distZip`.
2. **SFTP Upload**: Uploads the zip archive and configuration templates (`application.yaml` and `logback.xml`).
3. **Remote Unpacking**: Runs `unzip -o -L <zip>` on the remote server via SSH `exec` channel.
4. **Binary Setup**: Deploys libraries to working root and sets execution permissions (`chmod +x bin/*`).
5. **Resource Management**: Creates `resources/` folder and copies `application.yaml` / `logback.xml` **only if they do not already exist**, preserving production configuration.
6. **Cleanup**: Removes temporary zip files and extraction folders.
7. **Service Restart**: Executes `systemctl --user daemon-reload && systemctl --user restart taiga-mcp.service`.

---

### Option 1: Native systemd Service + Nginx

Ideal for dedicated servers or VPS environments requiring maximum performance with minimal memory overhead.

#### Step 1: Install Server Dependencies
```bash
# On Ubuntu / Debian
sudo apt update && sudo apt install -y openjdk-17-jre-headless nginx certbot python3-certbot-nginx
```

#### Step 2: Create System User and Directories
```bash
sudo useradd -r -s /bin/false -d /opt/taiga-mcp taiga
sudo mkdir -p /opt/taiga-mcp /etc/taiga-mcp /var/log/taiga-mcp
```

#### Step 3: Copy Standalone JAR
From your workstation:
```bash
scp server/build/libs/server-standalone.jar user@your-server:/tmp/
```
On the server:
```bash
sudo mv /tmp/server-standalone.jar /opt/taiga-mcp/server-standalone.jar
sudo chown -R taiga:taiga /opt/taiga-mcp /var/log/taiga-mcp
sudo chmod 750 /opt/taiga-mcp
```

#### Step 4: Configure Environment File
Create `/etc/taiga-mcp/taiga-mcp.env`:
```bash
sudo tee /etc/taiga-mcp/taiga-mcp.env > /dev/null << 'EOF'
HOST=127.0.0.1
PORT=8080
TAIGA_API_URL=https://api.taiga.io
TAIGA_AUTH_TOKEN=your_optional_initial_token
TAIGA_DEFAULT_PROJECT=your_optional_project_slug
EOF

sudo chown taiga:taiga /etc/taiga-mcp/taiga-mcp.env
sudo chmod 600 /etc/taiga-mcp/taiga-mcp.env
```

#### Step 5: Install systemd Unit
Copy `systemd/taiga-mcp.service` to `/etc/systemd/system/`:
```bash
sudo cp systemd/taiga-mcp.service /etc/systemd/system/taiga-mcp.service
sudo systemctl daemon-reload
sudo systemctl enable --now taiga-mcp
```

Check service status:
```bash
sudo systemctl status taiga-mcp
```

---

### Option 2: Docker and Docker Compose + Nginx

Provides containerized isolation and reproducible environments across platforms.

#### Step 1: Configure `.env`
In the project root on your server:
```bash
cat << 'EOF' > .env
TAIGA_API_URL=https://api.taiga.io
TAIGA_AUTH_TOKEN=your_token_here
TAIGA_DEFAULT_PROJECT=my-project
PORT=8080
EOF
```

#### Step 2: Build and Start Container Stack
The project provides an optimized multi-stage `Dockerfile` compiling code and running the JAR on Alpine JRE under a non-root user:

```bash
docker compose up -d --build
```

#### Step 3: Inspect Logs and Status
```bash
docker compose ps
docker compose logs -f
```

---

### Critical Nginx Tweaks for SSE

To prevent connection drops or lag in real-time Server-Sent Events (SSE) streaming, Nginx **must** disable proxy buffering on streaming endpoints.

The repository includes the pre-configured [`nginx/taiga-mcp.conf`](file:///Users/afalabarce/Desarrollo/IA/taiga-mcpserver/nginx/taiga-mcp.conf):

```nginx
upstream taiga_mcp_backend {
    server 127.0.0.1:8080;
    keepalive 32;
}

server {
    listen 80;
    server_name taiga-mcp.yourdomain.com;

    client_max_body_size 20M;

    # 1. SSE Real-Time Streaming Location
    location /sse {
        proxy_pass http://taiga_mcp_backend/sse;
        proxy_http_version 1.1;

        # CRITICAL: BUFFERING DISABLED FOR SSE
        proxy_buffering off;
        proxy_cache off;
        chunked_transfer_encoding off;

        # Persistent connection headers
        proxy_set_header Connection '';
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;

        # Long timeouts to keep SSE stream alive
        proxy_read_timeout 3600s;
        proxy_send_timeout 3600s;
    }

    # 2. JSON-RPC Messages and REST HTTP Endpoints
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

#### Enable Nginx Configuration:
```bash
sudo cp nginx/taiga-mcp.conf /etc/nginx/sites-available/taiga-mcp.conf
sudo ln -sf /etc/nginx/sites-available/taiga-mcp.conf /etc/nginx/sites-enabled/
sudo nginx -t
sudo systemctl reload nginx
```

---

### SSL Configuration with Let's Encrypt / Certbot

Enable HTTPS encryption with automatic redirection and certificate renewals:

```bash
sudo certbot --nginx -d taiga-mcp.yourdomain.com
```

---

## 🔍 Maintenance & Monitoring

### Deployment Verification

```bash
# 1. Health check
curl https://taiga-mcp.yourdomain.com/health

# 2. Inspect available tools (with token)
curl -H "Authorization: Bearer your_token" https://taiga-mcp.yourdomain.com/api/mcp/tools

# 3. Verify Swagger UI
curl -I https://taiga-mcp.yourdomain.com/swagger
```

### Log Inspection

- **systemd**:
  ```bash
  # Follow logs in real time
  sudo journalctl -u taiga-mcp -f

  # Recent error logs
  sudo journalctl -u taiga-mcp -p err -n 50
  ```

- **Docker**:
  ```bash
  docker compose logs -f --tail=100
  ```

- **Nginx**:
  ```bash
  sudo tail -f /var/log/nginx/access.log
  sudo tail -f /var/log/nginx/error.log
  ```

---

## 📄 License

This project is licensed under the MIT License. See the `LICENSE` file for details.
