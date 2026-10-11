// 백엔드 API 호출. 모든 요청이 여기를 거친다.
// - 로그인 토큰을 Authorization 헤더에 붙인다.
// - 오류 응답(ProblemDetail)을 ApiError로 바꾼다. 화면은 error.code 로 분기하고 error.message 를 보여준다.

const BASE = '/api/v1';
const TOKEN_KEY = 'bozor.token';

export class ApiError extends Error {
  constructor(status, body) {
    super(body?.detail || body?.title || `요청에 실패했습니다 (${status})`);
    this.status = status;
    this.code = body?.code || 'UNKNOWN';
    this.errors = body?.errors || [];
  }
}

export function getToken() {
  try { return localStorage.getItem(TOKEN_KEY); } catch { return null; }
}

export function setToken(token) {
  try {
    if (token) localStorage.setItem(TOKEN_KEY, token);
    else localStorage.removeItem(TOKEN_KEY);
  } catch { /* 저장소를 못 쓰는 환경(사생활 보호 모드 등)에서는 새로고침하면 로그아웃된다 */ }
}

async function request(method, path, { body, form, params } = {}) {
  const headers = {};
  const token = getToken();
  if (token) headers.Authorization = `Bearer ${token}`;
  let payload;
  if (form) {
    payload = form;   // multipart: Content-Type 은 브라우저가 경계값과 함께 붙인다
  } else if (body !== undefined) {
    headers['Content-Type'] = 'application/json';
    payload = JSON.stringify(body);
  }
  const query = params ? '?' + new URLSearchParams(
    Object.entries(params).filter(([, v]) => v !== undefined && v !== null && v !== ''),
  ) : '';
  const res = await fetch(BASE + path + query, { method, headers, body: payload });
  if (res.status === 204) return null;
  const data = await res.json().catch(() => null);
  if (!res.ok) {
    if (res.status === 401) setToken(null);   // 만료된 토큰은 버린다
    throw new ApiError(res.status, data);
  }
  return data;
}

export const api = {
  get: (path, params) => request('GET', path, { params }),
  post: (path, body) => request('POST', path, { body }),
  put: (path, body) => request('PUT', path, { body }),
  del: (path) => request('DELETE', path),
  upload: (file) => {
    const form = new FormData();
    form.append('file', file, file.name || 'photo.jpg');
    return request('POST', '/images', { form });
  },
};
