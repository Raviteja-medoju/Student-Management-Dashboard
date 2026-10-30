/** Mirrors the backend's ApiError: the single error shape every endpoint returns. */
export interface ApiError {
  timestamp?: string;
  /** HTTP status; 0 means the server could not be reached. */
  status: number;
  error?: string;
  message: string;
  path?: string;
  fieldErrors?: FieldError[];
}

export interface FieldError {
  field: string;
  message: string;
  rejectedValue?: unknown;
}

export function isApiError(value: unknown): value is ApiError {
  return (
    typeof value === 'object' &&
    value !== null &&
    typeof (value as ApiError).status === 'number' &&
    typeof (value as ApiError).message === 'string'
  );
}
