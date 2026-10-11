import { useState } from 'react';
import { useNavigate, useSearchParams } from 'react-router-dom';
import { api } from '../api.js';
import { useAuth } from '../auth.jsx';
import { ErrorBox, Loading, useLoad } from '../components/ui.jsx';

/**
 * 목 로그인(임시 결정 #1): 시드 사용자를 골라 바로 로그인한다. 비밀번호 없음.
 * 실제 로그인을 붙이면 이 화면만 전화번호 입력 화면으로 바뀐다.
 */
export default function Login() {
  const { loginAs } = useAuth();
  const navigate = useNavigate();
  const [params] = useSearchParams();
  const users = useLoad(() => api.get('/dev/users'), []);
  const [error, setError] = useState(null);

  async function choose(user) {
    setError(null);
    try {
      await loginAs(user.id);
      const next = params.get('next');
      navigate(next || (user.store_slugs.length > 0 ? '/seller' : '/'), { replace: true });
    } catch (e) {
      setError(e);
    }
  }

  return (
    <div className="stack">
      <h1>로그인</h1>
      <p className="alert info">개발용 목 로그인입니다. 사용자를 고르면 바로 로그인됩니다. 실증 전에 실제 로그인으로 바뀝니다.</p>
      <ErrorBox error={error || (users.error?.status === 404 ? { message: '목 로그인이 꺼져 있습니다(dev 프로필이 아님).' } : users.error)} />
      {users.loading && <Loading />}
      <div className="list">
        {users.data?.map((u) => (
          <button key={u.id} className="card block" style={{ justifyContent: 'space-between' }} onClick={() => choose(u)}>
            <span>{u.display_name}</span>
            <span className="muted small">
              {u.role === 'ADMIN' ? '관리자' : u.store_slugs.length > 0 ? `상점: ${u.store_slugs.join(', ')}` : '구매자'}
            </span>
          </button>
        ))}
      </div>
    </div>
  );
}
