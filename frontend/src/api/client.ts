import axios from 'axios';
import { toApiError } from '../utils/errors';
import { getEditorId } from './editorId';

export const API_BASE_URL = '/api';
export const EDITOR_ID_HEADER = 'X-Editor-Id';
export const EDIT_LOCK_TOKEN_HEADER = 'X-Edit-Lock-Token';

/**
 * The single HTTP client of the app. The base URL is relative: the Vite dev server (development) and nginx
 * (Docker) forward /api to the backend, so the browser never needs CORS.
 */
export const apiClient = axios.create({
  baseURL: API_BASE_URL,
  headers: { Accept: 'application/json' },
  timeout: 30_000,
});

apiClient.interceptors.request.use((config) => {
  config.headers.set(EDITOR_ID_HEADER, getEditorId());
  // Authentication placeholder: when login is added, attach the user's token here, e.g.
  //   const token = authStore.accessToken();
  //   if (token) config.headers.set('Authorization', `Bearer ${token}`);
  return config;
});

// Every failure becomes an ApiError (RFC 7807 problem detail → code, message, field errors).
apiClient.interceptors.response.use(
  (response) => response,
  (error: unknown) => Promise.reject(toApiError(error)),
);
