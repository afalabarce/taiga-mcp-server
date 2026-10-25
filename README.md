# Taiga MCP Server

High-performance **Model Context Protocol (MCP)** server developed in **Kotlin Multiplatform** (`:server` module) for connecting Artificial Intelligence assistants and agentic IDEs (Cursor, Windsurf, Claude Desktop, Antigravity, etc.) with the **Taiga** project management platform.

Servidor de alto rendimiento para el **Model Context Protocol (MCP)** desarrollado en **Kotlin Multiplatform** (módulo `:server`), diseñado para conectar asistentes de Inteligencia Artificial e IDEs agénticos con el gestor de proyectos **Taiga**.

---

## 🌐 Documentation / Documentación

Please select your language to view the complete documentation:

Por favor, selecciona tu idioma para consultar la documentación completa:

* 🇪🇸 **[Documentación en español](README_es.md)**
* 🇬🇧 **[Documentation in english](README_en.md)**

---

## 🚀 Quick Highlights / Aspectos Destacados

- **Dual Transport**: SSE (Server-Sent Events) for remote production deployments and Stdio for local embedded IDE integration.
- **Strict OpenAPI 3.1 & Dynamic Swagger UI**: Real-time interactive documentation generated at runtime via Ktor routing DSL at `/swagger`.
- **Comprehensive Taiga Coverage**: User Stories, Tasks, Epics, Issues/Bugs, Milestones/Sprints, Wiki pages, and project metadata dictionaries.
- **Automated Deployment**: Ready-to-use Gradle `:server:deploy` task via SFTP/SSH, native Linux systemd service units, and Docker container support.

---

## 📄 License

This project is licensed under the MIT License - see the [LICENSE](LICENSE) file for details.
