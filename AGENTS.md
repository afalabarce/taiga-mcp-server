# Directrices para Agentes de IA: Integración con Taiga MCP

Este repositorio expone un servidor **MCP (Model Context Protocol)** para Taiga optimizado para agentes de Inteligencia Artificial.

## Regla Fundamental: USO OBLIGATORIO DE HERRAMIENTAS MCP

> **IMPORTANTE**: Para cualquier operación relacionada con Taiga (proyectos, epics, historias de usuario, tareas, incidencias, sprints/hitos o wiki), **debes utilizar EXCLUSIVAMENTE las herramientas MCP `taiga_*`**.
>
> **PROHIBIDO**: No ejecutes llamadas HTTP directas, `curl`, scripts en Python ni peticiones REST manuales contra la API de Taiga.

### ¿Por qué el MCP de Taiga es superior a las llamadas REST directas?

1. **Resolución Automática de Nombres y Slugs**:
   - No necesitas buscar IDs numéricos previos. Puedes indicar nombres legibles directamente:
     - `status`: `"In progress"`, `"New"`, `"Ready for test"`, `"Closed"`, etc.
     - `milestone`: `"Sprint 1"`, `"sprint-1"`, etc.
     - `assigned_to`: nombre de usuario o email del miembro del equipo.
     - `priority`: `"Low"`, `"Normal"`, `"High"`.
     - `severity`: `"Wishlist"`, `"Minor"`, `"Normal"`, `"Major"`, `"Critical"`.
     - `type`: `"Bug"`, `"Question"`, `"Enhancement"`.
   - Si un nombre no existe, el MCP te responderá con una lista exacta de los valores válidos para el proyecto.

2. **Gestión Automática de Concurrencia y Versiones (`version`)**:
   - En Taiga REST directa, cualquier `PATCH` o `PUT` falla con HTTP 409 si no envías el número de `version` actual.
   - Con el MCP, el parámetro `version` es **completamente opcional**: el servidor MCP consulta la versión actual y aplica el cambio de forma atómica.

3. **Gestión Transparente de Autenticación y Contexto de Proyecto**:
   - Las cabeceras de autorización, tokens Bearer/Basic y el proyecto por defecto (`defaultProject` o cabecera `X-Taiga-Project`) se resuelven automáticamente.
   - No necesitas pasar `project` si ya está configurado en el servidor.

4. **Herramientas de Alto Nivel y Productividad**:
   - `taiga_get_project_summary`: Resumen ejecutivo inmediato del proyecto (sprints activos, conteo de historias/tareas/issues por estado, miembros). Ideal al iniciar una tarea.
   - `taiga_search`: Búsqueda unificada en historias, tareas, bugs, epics y wiki en una sola llamada.
   - `taiga_add_comment`: Publica comentarios o notas de progreso en cualquier elemento (`user_story`, `task`, `issue`, `epic`) sin preocuparte por versiones.
   - `taiga_list_members`: Descubre los miembros del proyecto y sus roles para asignaciones.

5. **Feedback y Diagnóstico Instructivo**:
   - Los errores de validación y de la API devuelven mensajes accionables con sugerencias concretas de autocorrección.

---

## Catálogo Rápido de Herramientas MCP Disponibles

| Herramienta | Propósito |
|---|---|
| `taiga_get_project_summary` | Estado general del proyecto, métricas y sprints activos |
| `taiga_search` | Búsqueda global de texto en historias, tareas, bugs y wiki |
| `taiga_list_projects` / `taiga_get_project` | Consultar proyectos |
| `taiga_list_user_stories` / `taiga_get_user_story` | Listar y obtener historias de usuario |
| `taiga_create_user_story` / `taiga_update_user_story` / `taiga_delete_user_story` | Crear, actualizar y borrar historias (soporta nombres y comentarios) |
| `taiga_list_tasks` / `taiga_get_task` | Listar y obtener tareas |
| `taiga_create_task` / `taiga_update_task` / `taiga_delete_task` | Crear, actualizar y borrar tareas (soporta nombres y comentarios) |
| `taiga_list_issues` / `taiga_get_issue` | Listar y obtener bugs/incidencias |
| `taiga_create_issue` / `taiga_update_issue` / `taiga_delete_issue` | Crear, actualizar y borrar incidencias (soporta nombres de tipo, prioridad y severidad) |
| `taiga_list_epics` / `taiga_get_epic` / `taiga_create_epic` / `taiga_update_epic` | Gestión de epics |
| `taiga_list_milestones` / `taiga_get_milestone` / `taiga_create_milestone` | Gestión de sprints e hitos |
| `taiga_list_wiki_pages` / `taiga_get_wiki_page` / `taiga_create_wiki_page` / `taiga_update_wiki_page` | Documentación wiki |
| `taiga_get_project_statuses` | Metadatos completos (estados, puntos de estimación, roles) |
| `taiga_list_members` | Miembros del equipo y roles |
| `taiga_add_comment` | Publicar notas o comentarios de progreso |
| `taiga_get_history` | Consultar el historial de actividad y comentarios |
