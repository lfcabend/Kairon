/**
 * Hand-written types mirroring the backend's `/api/v1/assistant/mcp/**` DTOs
 * (docs/milestones/M10.5-mcp-server.md §5) — this server has no generated
 * client and no coupling to the backend's OpenAPI spec (same posture as
 * `web/`'s own hand-written `src/lib/api/*.ts`), so there's nothing to
 * regenerate or diff against a drift check.
 */

export interface McpContextResponse {
  systemPrompt: string;
  userContent: string;
}

export interface AssistantRunView {
  id: string;
  kind: string;
  status: string;
  model: string;
  periodStart?: string | null;
  periodEnd?: string | null;
  inputTokens?: number | null;
  outputTokens?: number | null;
  error?: string | null;
  createdAt: string;
  suggestions: unknown[];
  suggestedProject?: unknown;
  suggestedProjectEdit?: unknown;
  outputMarkdown?: string | null;
  source: "API" | "MCP";
}

export interface SuggestedTaskPayload {
  title: string;
  notes?: string;
  rationale: string;
  suggestedForDay: string;
  estimateMinutes?: number;
  sourceProjectTaskId?: string;
}

export interface PlannedTaskPayload {
  key: string;
  parentKey?: string | null;
  name: string;
  description?: string;
  isMilestone: boolean;
  plannedStart?: string;
  plannedEnd?: string;
  estimateHours?: number;
}

export interface PlannedDependencyPayload {
  predecessorKey: string;
  successorKey: string;
  type?: string;
  lagDays?: number;
}

export interface ProjectPlanPayload {
  name: string;
  description?: string;
  size?: string;
  categoryName?: string | null;
  tasks: PlannedTaskPayload[];
  dependencies: PlannedDependencyPayload[];
}

export interface ProjectFieldChangesPayload {
  name?: string;
  description?: string;
  size?: string;
  startDate?: string;
  endDate?: string;
  categoryName?: string | null;
}

export interface TaskOperationPayload {
  op: "ADD" | "UPDATE" | "REMOVE";
  existingTaskId?: string;
  key?: string;
  parentRef?: string;
  name?: string;
  description?: string;
  isMilestone: boolean;
  plannedStart?: string;
  plannedEnd?: string;
  estimateHours?: number;
}

export interface DependencyOperationPayload {
  op: "ADD" | "REMOVE";
  existingDependencyId?: string;
  predecessorRef?: string;
  successorRef?: string;
  type?: string;
  lagDays?: number;
}

export interface ReorderOperationPayload {
  parentRef?: string;
  orderedRefs: string[];
}

export interface ProjectEditPayload {
  projectChanges?: ProjectFieldChangesPayload | null;
  taskOperations: TaskOperationPayload[];
  dependencyOperations: DependencyOperationPayload[];
  reorderOperations: ReorderOperationPayload[];
}
