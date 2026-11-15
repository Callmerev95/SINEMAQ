# Triage Labels

The skills speak in terms of five canonical triage roles. This file maps those roles to the actual label strings used in this repo's issue tracker.

| Label in mattpocock/skills | Label in our tracker | Meaning                                  |
| -------------------------- | -------------------- | ---------------------------------------- |
| `needs-triage`             | `needs-triage`       | Maintainer needs to evaluate this issue  |
| `needs-info`               | `needs-info`         | Waiting on reporter for more information |
| `ready-for-agent`          | `ready-for-agent`    | Fully specified, ready for an AFK agent  |
| `ready-for-human`          | `ready-for-human`    | Requires human implementation            |
| `wontfix`                  | `wontfix`            | Will not be actioned                     |

When a skill mentions a role (e.g. "apply the AFK-ready triage label"), use the corresponding label string from this table.

Edit the right-hand column to match whatever vocabulary you actually use.

## Supporting labels

The `triage` skill only drives the five state roles and two category roles above. These extra labels exist for human ordering and closure but are **not** auto-applied by the skill — apply them manually:

| Group | Label | Notes |
| ----- | ----- | ----- |
| Status | `needs-repro` | Verification step; skill may suggest it during triage but does not set it. |
| Kategori | `documentation` | Alias "docs". |
| Kategori | `question` | Usage question, not a defect. |
| Prioritas | `priority: critical` | P0 / blocker. |
| Prioritas | `priority: high` | P1. |
| Prioritas | `priority: medium` | P2. |
| Prioritas | `priority: low` | P3 / backlog. |
| Resolusi | `duplicate` | Same as another issue. |
| Resolusi | `invalid` | Not a real problem / irrelevant. |

`enhancement` covers "feature-request"; `wontfix` covers "by-design".
