import { createContext, useCallback, useContext, useEffect, useState } from 'react';
import { api, getToken, setToken } from './api.js';

// 로그인 상태. 지금은 목 로그인(/dev/login)만 있다(임시 결정 #1).
// 실제 로그인을 붙일 때도 "토큰을 받아 setToken" 하는 부분만 바뀌고, 나머지 화면은 그대로다.

const AuthContext = createContext(null);

export function AuthProvider({ children }) {
  const [me, setMe] = useState(null);
  const [loading, setLoading] = useState(Boolean(getToken()));

  const refresh = useCallback(async () => {
    if (!getToken()) { setMe(null); setLoading(false); return null; }
    try {
      const data = await api.get('/me');
      setMe(data);
      return data;
    } catch {
      setMe(null);
      return null;
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => { refresh(); }, [refresh]);

  const loginAs = useCallback(async (userId) => {
    const res = await api.post('/dev/login', { user_id: userId });
    setToken(res.access_token);
    return refresh();
  }, [refresh]);

  const logout = useCallback(() => {
    setToken(null);
    setMe(null);
  }, []);

  return (
    <AuthContext.Provider value={{ me, loading, loginAs, logout, refresh }}>
      {children}
    </AuthContext.Provider>
  );
}

export function useAuth() {
  return useContext(AuthContext);
}
