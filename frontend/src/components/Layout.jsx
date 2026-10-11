import { Link, NavLink, Outlet, useNavigate } from 'react-router-dom';
import { useAuth } from '../auth.jsx';
import { useCart } from '../cart.js';

export default function Layout() {
  const { me, logout } = useAuth();
  const { items } = useCart();
  const navigate = useNavigate();

  return (
    <>
      <header className="app-header">
        <Link to="/" className="brand">BOZOR</Link>
        <nav className="nav">
          <NavLink to="/" end>둘러보기</NavLink>
          <NavLink to="/cart">장바구니{items.length > 0 && <span className="count">{items.length}</span>}</NavLink>
          {me && <NavLink to="/orders">내 주문</NavLink>}
          {me && <NavLink to="/seller">판매자</NavLink>}
          {me?.role === 'ADMIN' && <NavLink to="/admin">지표</NavLink>}
          {me ? (
            // 홈으로 먼저 이동해야 로그인 필요 화면이 "다시 로그인" 화면으로 보내지 않는다
            <button className="link" onClick={() => { navigate('/', { replace: true }); logout(); }} title="로그아웃">
              {me.display_name} · 로그아웃
            </button>
          ) : (
            <NavLink to="/login">로그인</NavLink>
          )}
        </nav>
      </header>
      <main>
        <Outlet />
      </main>
    </>
  );
}
