// Hand-written for M1. When the backend publishes its OpenAPI spec, these are
// replaced by generated types (see docs/DESIGN.md §5.2).

export interface UserSummary {
  id: string;
  email: string;
  displayName: string;
  timezone: string;
}

export interface AuthResponse {
  accessToken: string;
  tokenType: "Bearer";
  expiresInSeconds: number;
  user: UserSummary;
}

export interface Me {
  id: string;
  email: string;
  displayName: string;
  timezone: string;
  status: string;
  preferences: Record<string, unknown>;
}

export interface RegisterBody {
  email: string;
  password: string;
  displayName: string;
  timezone?: string;
}

export interface LoginBody {
  email: string;
  password: string;
}

export interface UpdateMeBody {
  displayName?: string;
  timezone?: string;
  preferences?: Record<string, unknown>;
}

// --- Todo (M2) -------------------------------------------------------------

export type TodoStatus = "OPEN" | "DONE" | "CANCELLED";

export interface TodoItem {
  id: string;
  day: string;
  title: string;
  notes: string | null;
  status: TodoStatus;
  priority: number;
  position: number;
  estimateMinutes: number | null;
  sourceProjectTaskId: string | null;
  rolledOverFromId: string | null;
  completedAt: string | null;
  createdAt: string;
  updatedAt: string;
  version: number;
}

export interface CreateTodoBody {
  day: string;
  title: string;
  notes?: string | null;
  priority?: number;
  estimateMinutes?: number | null;
  sourceProjectTaskId?: string | null;
}

export interface PatchTodoBody {
  title?: string;
  notes?: string | null;
  priority?: number;
  estimateMinutes?: number | null;
  status?: TodoStatus;
  expectedVersion?: number;
}

export interface ReorderBody {
  day: string;
  orderedIds: string[];
}

export interface RolloverBody {
  toDay: string;
  fromDay?: string;
  ids?: string[];
}

export interface RolloverUndoBody {
  createdIds: string[];
}

export interface RolloverPreview {
  sourceDays: { day: string; items: TodoItem[] }[];
  totalItems: number;
}

export type RolloverMode = "manual" | "pick" | "auto";

/** RFC 7807 problem detail — the shape of every error body from the API. */
export interface ProblemDetail {
  type?: string;
  title?: string;
  status: number;
  detail?: string;
  errors?: { field: string; message: string }[];
}
