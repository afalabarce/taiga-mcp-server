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

## Catálogo Rápido de Herramientas MCP Disponibles (121 Herramientas)

| Categoría | Herramientas MCP | Propósito |
|---|---|---|
| **Resumen y Búsqueda** | `taiga_get_project_summary`, `taiga_search` | Métricas generales del proyecto y búsqueda unificada en toda la plataforma |
| **Proyectos** | `taiga_list_projects`, `taiga_get_project`, `taiga_create_project`, `taiga_update_project`, `taiga_delete_project`, `taiga_duplicate_project`, `taiga_list_project_templates`, `taiga_export_project`, `taiga_export_csv`, `taiga_transfer_project_start`, `taiga_transfer_project_accept` | Ciclo de vida completo, duplicación, plantillas, exportación CSV/completa y transferencias |
| **Miembros y Roles** | `taiga_list_memberships`, `taiga_create_membership`, `taiga_delete_membership`, `taiga_list_members` | Gestión de miembros, invitaciones, roles y permisos |
| **Etiquetas** | `taiga_list_tags` | Consulta de etiquetas (tags) y sus códigos de color |
| **Swimlanes** | `taiga_list_swimlanes`, `taiga_get_swimlane`, `taiga_create_swimlane`, `taiga_update_swimlane`, `taiga_delete_swimlane`, `taiga_reorder_swimlanes`, `taiga_list_swimlane_userstory_statuses` | Gestión completa de carriles (swimlanes), ordenación y mapeo con estados |
| **Fechas Límite (Due Dates)** | `taiga_list_due_dates`, `taiga_get_due_date`, `taiga_create_due_date`, `taiga_update_due_date`, `taiga_delete_due_date` | Configuración de estados y alertas por color para vencimiento de historias, tareas e incidencias |
| **Operaciones Masivas (Bulk)** | `taiga_bulk_create_user_stories`, `taiga_bulk_create_tasks`, `taiga_bulk_create_issues`, `taiga_bulk_create_epics`, `taiga_bulk_create_memberships`, `taiga_move_to_sprint`, `taiga_bulk_update_backlog_order`, `taiga_bulk_update_kanban_order`, `taiga_bulk_update_taskboard_order` | Creación masiva rápida por líneas, asignación a sprints y reordenación en backlog/kanban/taskboard |
| **Historias de Usuario** | `taiga_list_user_stories`, `taiga_get_user_story`, `taiga_create_user_story`, `taiga_update_user_story`, `taiga_delete_user_story` | Gestión integral (soporta `id` o `ref`, nombres de estado/sprint/usuario, `due_date` y comentarios) |
| **Tareas** | `taiga_list_tasks`, `taiga_get_task`, `taiga_create_task`, `taiga_update_task`, `taiga_delete_task` | Tareas independientes o de historias (soporta `id` o `ref`, `due_date` y comentarios) |
| **Incidencias / Bugs** | `taiga_list_issues`, `taiga_get_issue`, `taiga_create_issue`, `taiga_update_issue`, `taiga_delete_issue`, `taiga_promote_issue_to_user_story` | Gestión de bugs (soporta nombres de severidad/prioridad/tipo, `due_date` y promoción a historia) |
| **Epics** | `taiga_list_epics`, `taiga_get_epic`, `taiga_create_epic`, `taiga_update_epic`, `taiga_delete_epic`, `taiga_list_epic_user_stories`, `taiga_link_user_story_to_epic`, `taiga_unlink_user_story_from_epic` | Gestión de epics (soporta `id` o `ref`) y vinculación bidireccional con historias |
| **Sprints / Hitos** | `taiga_list_milestones`, `taiga_get_milestone`, `taiga_create_milestone`, `taiga_update_milestone`, `taiga_delete_milestone`, `taiga_get_milestone_stats` | Gestión de sprints, fechas, estimaciones y métricas de avance/burndown |
| **Wiki** | `taiga_list_wiki_pages`, `taiga_get_wiki_page`, `taiga_create_wiki_page`, `taiga_update_wiki_page`, `taiga_delete_wiki_page`, `taiga_list_wiki_links`, `taiga_create_wiki_link`, `taiga_delete_wiki_link` | Documentación, páginas wiki, enlaces y navegación del proyecto |
| **Adjuntos y Archivos** | `taiga_list_attachments`, `taiga_upload_attachment`, `taiga_delete_attachment` | Gestión y subida de archivos adjuntos e imágenes (vía ruta local o Base64) |
| **Campos Personalizados** | `taiga_list_custom_attributes`, `taiga_create_custom_attribute`, `taiga_delete_custom_attribute`, `taiga_get_custom_attributes_values`, `taiga_update_custom_attributes_values` | Definición de atributos y lectura/actualización de sus valores |
| **Comentarios e Historial** | `taiga_add_comment`, `taiga_get_history`, `taiga_edit_comment`, `taiga_delete_comment`, `taiga_undelete_comment`, `taiga_get_comment_versions` | Publicación de notas, historial completo de cambios, edición, borrado, recuperación y versionado de comentarios |
| **Webhooks** | `taiga_list_webhooks`, `taiga_get_webhook`, `taiga_create_webhook`, `taiga_update_webhook`, `taiga_delete_webhook`, `taiga_test_webhook`, `taiga_list_webhook_logs`, `taiga_resend_webhook` | Configuración de webhooks, prueba de disparos, auditoría de logs y reenvío |
| **Notificaciones** | `taiga_list_web_notifications`, `taiga_mark_web_notifications_read`, `taiga_list_notify_policies`, `taiga_update_notify_policy` | Lectura y marcado de notificaciones web y políticas de alerta por proyecto |
| **Interacciones Sociales** | `taiga_upvote`, `taiga_downvote`, `taiga_watch`, `taiga_unwatch`, `taiga_list_voters`, `taiga_list_watchers` | Votaciones, suscripción/seguimiento de cambios y consulta de votantes y observadores |
| **Líneas de Tiempo (Timeline)** | `taiga_get_timeline` | Consulta de la línea de tiempo de eventos para proyectos, usuarios o perfiles |
| **Usuarios** | `taiga_get_me`, `taiga_get_user`, `taiga_get_user_contacts`, `taiga_get_user_liked`, `taiga_get_user_voted`, `taiga_get_user_watched` | Perfil del usuario conectado, datos públicos, contactos y elementos favoritos/votados |
| **Estados y Metadatos** | `taiga_get_project_statuses` | Esquema exhaustivo del proyecto: estados, tipos, severidades, prioridades y roles |
| **Servidor y Configuración** | `taiga_get_config`, `taiga_set_config`, `taiga_login`, `taiga_test_connection` | Control de configuración en caliente, autenticación y test de conectividad |
