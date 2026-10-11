import { Link, Navigate } from 'react-router-dom';
import { api } from '../../api.js';
import { ErrorBox, Loading, StatusBadge, useLoad } from '../../components/ui.jsx';
import { STORE_STATUS_LABEL, TEMPLATE_LABEL } from '../../format.js';

/** 판매자 첫 화면: 내 상점 목록. 상점이 하나뿐이면 바로 그 상점 관리 화면으로 간다. */
export default function SellerHome() {
  const stores = useLoad(() => api.get('/seller/stores'), []);
  if (stores.error) return <ErrorBox error={stores.error} />;
  if (!stores.data) return <Loading />;
  if (stores.data.length === 1) return <Navigate to={`/seller/stores/${stores.data[0].id}`} replace />;

  return (
    <div className="stack">
      <h1>판매자</h1>
      {stores.data.length === 0 && (
        <div className="card stack">
          <p>아직 상점이 없습니다. 상점을 만들면 사진만 찍어서 상품을 올릴 수 있어요.</p>
          <Link className="button primary" to="/seller/stores/new">상점 만들기</Link>
        </div>
      )}
      <div className="list">
        {stores.data.map((s) => (
          <Link key={s.id} to={`/seller/stores/${s.id}`} className="card row" style={{ textDecoration: 'none' }}>
            <div className="grow">
              <h3>{s.name}</h3>
              <div className="muted small">{TEMPLATE_LABEL[s.template]} · /s/{s.slug}</div>
            </div>
            <StatusBadge status={s.status} labels={STORE_STATUS_LABEL} />
          </Link>
        ))}
      </div>
      {stores.data.length > 0 && <Link className="button" to="/seller/stores/new">상점 하나 더 만들기</Link>}
    </div>
  );
}
