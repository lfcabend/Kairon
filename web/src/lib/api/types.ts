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

/** RFC 7807 problem detail — the shape of every error body from the API. */
export interface ProblemDetail {
  type?: string;
  title?: string;
  status: number;
  detail?: string;
  errors?: { field: string; message: string }[];
}
