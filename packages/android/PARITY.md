# Parity

One row per surface of the shared React UI, with what the native app does
today. "Ported" means the surface exists in the app and uses the same server
routes as the web UI.

## Chat

| Surface | Status |
| --- | --- |
| Session list | Ported |
| New session | Ported |
| Message timeline | Partly: text, reasoning and tool parts |
| Composer | Partly: send and stop |
| Permission prompt | Ported |
| Form prompt | Ported |
| Session goal | Missing |
| Queued messages | Missing |
| Markdown | Missing: plain text only |
| Syntax highlighting | Missing |
| Image attachments | Missing |

## Sessions

| Surface | Status |
| --- | --- |
| Session tree and folders | Missing |
| Bulk actions | Missing |
| Session search | Missing |
| Archive | Missing |

## Files and code

| Surface | Status |
| --- | --- |
| File tree | Missing |
| File viewer | Missing |
| Editor | Missing |
| Search in files | Missing |

## Git

| Surface | Status |
| --- | --- |
| Changes list | Missing |
| Diff view | Missing |
| Commit | Missing |
| Pull requests | Missing |

## Terminal

| Surface | Status |
| --- | --- |
| Terminal | Missing |

## Settings

| Surface | Status |
| --- | --- |
| Providers and keys | Missing |
| Agents, commands, skills, MCP | Missing |
| Themes and appearance | Partly: light, dark and follow the system |
| Server log | Partly: the log ring buffer, no screen yet |
| About | Missing |
| Scheduled tasks | Missing |
| Usage | Missing |

## Notes, plans and tracking

| Surface | Status |
| --- | --- |
| Notes and todos | Missing |
| Plans and trackers | Missing |
| Source board | Missing |
| Multi run | Missing |

## Not ported on purpose

| Surface | Reason |
| --- | --- |
| Isolated spaces | No container runtime on Android |
| Browser panel | The phone has no scriptable browser host |
| Tunnel management | The phone does not serve other machines |
| Desktop SSH hosts | Desktop only |
| Mermaid and LaTeX | Rendered as plain text |
