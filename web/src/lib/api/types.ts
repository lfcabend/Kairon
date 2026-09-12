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

// --- Journal (M3) ------------------------------------------------------------

export interface JournalEntry {
  id: string;
  day: string;
  position: number;
  title: string | null;
  content: string;
  mood: number | null;
  createdAt: string;
  updatedAt: string;
  version: number;
}

export interface CreateJournalEntryBody {
  day: string;
  title?: string | null;
  content?: string;
  mood?: number | null;
}

export interface PatchJournalEntryBody {
  title?: string | null;
  content?: string;
  mood?: number | null;
  expectedVersion?: number;
}

export interface JournalSearchHit {
  id: string;
  day: string;
  title: string | null;
  snippet: string;
  mood: number | null;
  createdAt: string;
}

export interface JournalSearchPage {
  content: JournalSearchHit[];
  page: number;
  totalElements: number;
}

// --- Projects (M4) -----------------------------------------------------------

export type ProjectStatus = "PLANNING" | "ACTIVE" | "ON_HOLD" | "DONE" | "ARCHIVED";
export type ProjectSize = "XS" | "S" | "M" | "L" | "XL";
export type ProjectTaskStatus = "TODO" | "IN_PROGRESS" | "BLOCKED" | "DONE";

export interface ProjectCategory {
  id: string;
  name: string;
  color: string;
  position: number;
  createdAt: string;
  updatedAt: string;
  version: number;
}

export interface CreateProjectCategoryBody {
  name: string;
  color?: string;
}

export interface PatchProjectCategoryBody {
  name?: string;
  color?: string;
  expectedVersion?: number;
}

export interface Project {
  id: string;
  categoryId: string | null;
  name: string;
  description: string | null;
  status: ProjectStatus;
  size: ProjectSize | null;
  priorityRank: number;
  color: string;
  startDate: string | null;
  endDate: string | null;
  actualStart: string | null;
  actualEnd: string | null;
  createdAt: string;
  updatedAt: string;
  version: number;
}

export interface CreateProjectBody {
  categoryId?: string | null;
  name: string;
  description?: string | null;
  size?: ProjectSize | null;
  color?: string;
  startDate?: string | null;
  endDate?: string | null;
}

/** Whole-form overwrite (D15) — every field is always sent; `priorityRank` never appears (D18). */
export interface PatchProjectBody {
  categoryId: string | null;
  name: string;
  description: string | null;
  status: ProjectStatus;
  size: ProjectSize | null;
  color: string;
  startDate: string | null;
  endDate: string | null;
  actualStart: string | null;
  actualEnd: string | null;
  expectedVersion?: number;
}

export interface ProjectsPage {
  content: Project[];
  page: number;
  totalElements: number;
}

export interface ProjectTask {
  id: string;
  projectId: string;
  parentTaskId: string | null;
  name: string;
  description: string | null;
  status: ProjectTaskStatus;
  isMilestone: boolean;
  plannedStart: string | null;
  plannedEnd: string | null;
  estimateHours: number | null;
  actualHours: number | null;
  progressPercent: number;
  position: number;
  createdAt: string;
  updatedAt: string;
  version: number;
}

export interface CreateProjectTaskBody {
  name: string;
  description?: string | null;
  parentTaskId?: string | null;
  plannedStart?: string | null;
  plannedEnd?: string | null;
  estimateHours?: number | null;
  isMilestone?: boolean;
}

/** Whole-form overwrite (D15) — every field is always sent. */
export interface PatchProjectTaskBody {
  name: string;
  description: string | null;
  status: ProjectTaskStatus;
  parentTaskId: string | null;
  plannedStart: string | null;
  plannedEnd: string | null;
  estimateHours: number | null;
  actualHours: number | null;
  progressPercent: number;
  isMilestone: boolean;
  expectedVersion?: number;
}

export interface ProjectTasksPage {
  content: ProjectTask[];
  page: number;
  totalElements: number;
}

/** RFC 7807 problem detail — the shape of every error body from the API. */
export interface ProblemDetail {
  type?: string;
  title?: string;
  status: number;
  detail?: string;
  errors?: { field: string; message: string }[];
}
