import { useState } from 'react';
import { Link, NavLink, useParams, useSearchParams } from 'react-router-dom';
import { api } from '../../api.js';
import OrderCard from '../../components/OrderCard.jsx';
import { ErrorBox, Loading, Pager, PriceTag, StatusBadge, Thumb, useLoad } from '../../components/ui.jsx';
import {
  ORDER_STATUS_LABEL, PRODUCT_STATUS_LABEL, SCAN_STATUS_LABEL, STORE_STATUS_LABEL, UNIT_LABEL, dateTime, quantityStep,
} from '../../format.js';
import { StoreSettingsForm } from './StoreForm.jsx';

const TABS = { products: '상품', scans: 'AI 스캔', orders: '주문', settings: '상점 설정' };

/** 판매자 상점 관리. 탭은 주소(?tab=)에 남겨 새로고침해도 유지한다. */
export default function StoreDashboard() {
  const { storeId } = useParams();
  const [params] = useSearchParams();
  const tab = TABS[params.get('tab')] ? params.get('tab') : 'products';
  const store = useLoad(() => api.get(`/seller/stores/${storeId}`), [storeId]);
  const [error, setError] = useState(null);

  if (store.error) return <ErrorBox error={store.error} />;
  if (!store.data) return <Loading />;
  const s = store.data;

  async function toggleStatus() {
    setError(null);
    try {
      store.setData(await api.post(`/seller/stores/${storeId}/status`, { status: s.status === 'ACTIVE' ? 'HIDDEN' : 'ACTIVE' }));
    } catch (e) {
      setError(e);
    }
  }

  return (
    <div className="stack">
      <div className="row between">
        <div>
          <h1 style={{ marginBottom: 2 }}>{s.name}</h1>
          <div className="row small">
            <StatusBadge status={s.status} labels={STORE_STATUS_LABEL} />
            {s.status === 'ACTIVE' && <Link to={`/s/${s.slug}`}>공개 화면 보기</Link>}
          </div>
        </div>
        <button onClick={toggleStatus}>{s.status === 'ACTIVE' ? '숨기기' : '공개하기'}</button>
      </div>
      <ErrorBox error={error} />
      <Link className="button primary block" to={`/seller/stores/${storeId}/scan`}>📷 사진으로 상품 등록</Link>
      <Link className="button block" to={`/seller/stores/${storeId}/import`}>SNS 사진 한꺼번에 등록 (게시물 20개까지)</Link>

      <nav className="tabs">
        {Object.entries(TABS).map(([key, label]) => (
          <NavLink key={key} to={`?tab=${key}`} className={() => (tab === key ? 'active' : '')}>{label}</NavLink>
        ))}
      </nav>
      {tab === 'products' && <ProductsTab storeId={storeId} />}
      {tab === 'scans' && <ScansTab storeId={storeId} />}
      {tab === 'orders' && <OrdersTab storeId={storeId} />}
      {tab === 'settings' && (
        <StoreSettingsForm store={s} onSubmit={async (body) => store.setData(await api.put(`/seller/stores/${storeId}`, body))} />
      )}
    </div>
  );
}

// ---- 상품 ----------------------------------------------------------------

function ProductsTab({ storeId }) {
  const [status, setStatus] = useState('');
  const [page, setPage] = useState(0);
  const products = useLoad(() => api.get(`/seller/stores/${storeId}/products`, { status, page }), [storeId, status, page]);

  return (
    <div className="stack">
      <div className="row between">
        <div className="chips">
          {['', 'DRAFT', 'PUBLISHED', 'HIDDEN', 'ARCHIVED'].map((s) => (
            <button key={s} className={`chip ${status === s ? 'on' : ''}`} onClick={() => { setStatus(s); setPage(0); }}>
              {s ? PRODUCT_STATUS_LABEL[s] : '전체'}
            </button>
          ))}
        </div>
        <Link className="button" to={`/seller/stores/${storeId}/products/new`}>직접 등록</Link>
      </div>
      <ErrorBox error={products.error} />
      {products.loading && !products.data && <Loading />}
      {products.data?.items.length === 0 && <p className="muted">상품이 없습니다. 사진으로 상품을 등록해 보세요.</p>}
      <div className="list">
        {products.data?.items.map((p) => <ProductRow key={p.id} product={p} onChange={products.reload} />)}
      </div>
      <Pager page={products.data} onChange={setPage} />
    </div>
  );
}

/** 상품 한 줄: 상태 버튼과 재고 [−][+]. 버튼마다 한 가지 일만 하는 API를 부른다. */
function ProductRow({ product: p, onChange }) {
  const [error, setError] = useState(null);
  const name = p.translations[0]?.name || '(이름 없음)';

  async function run(action) {
    setError(null);
    try { await action(); onChange(); } catch (e) { setError(e); }
  }
  const setStatus = (status) => run(() => api.post(`/seller/products/${p.id}/status`, { status }));

  return (
    <div className="card stack">
      <div className="row">
        <Thumb src={p.images[0]?.thumbnail_url} className="thumb-sm" />
        <div className="grow">
          <div className="row"><strong>{name}</strong><StatusBadge status={p.status} /></div>
          <div className="small"><PriceTag price={p.price} unit={p.unit} priceType={p.price_type} /> · {p.category.name}</div>
          {p.source && <div className="muted small">AI 스캔 #{p.source.scan_job_id}에서 등록</div>}
        </div>
        <Link className="button ghost" to={`/seller/products/${p.id}/edit`}>수정</Link>
      </div>
      <StockControl product={p} onSave={(stock) => run(() => api.post(`/seller/products/${p.id}/stock`, { stock_quantity: stock }))} />
      <div className="row">
        {p.status === 'DRAFT' && <button className="primary" onClick={() => setStatus('PUBLISHED')}>발행</button>}
        {p.status === 'PUBLISHED' && <button onClick={() => setStatus('HIDDEN')}>숨기기</button>}
        {p.status === 'HIDDEN' && <button onClick={() => setStatus('PUBLISHED')}>다시 판매</button>}
        {p.status !== 'ARCHIVED' && (p.status !== 'DRAFT' || p.source) && (
          <button className="ghost" onClick={() => window.confirm('보관하면 다시 판매할 수 없습니다. 보관할까요?') && setStatus('ARCHIVED')}>보관</button>
        )}
        {p.status === 'DRAFT' && !p.source && (
          <button className="ghost danger" onClick={() => window.confirm('초안을 지울까요?') && run(() => api.del(`/seller/products/${p.id}`))}>삭제</button>
        )}
      </div>
      <ErrorBox error={error} />
    </div>
  );
}

/** 재고: 판매자가 직접 관리한다. "관리 안 함"(null)이면 항상 구매 가능, 0이면 품절. */
function StockControl({ product, onSave }) {
  const tracked = product.stock_quantity !== null;
  const value = tracked ? Number(product.stock_quantity) : 0;
  const step = quantityStep(product.unit);
  return (
    <div className="row small">
      <span className="muted">재고</span>
      {tracked ? (
        <>
          <span className="stepper">
            <button type="button" onClick={() => onSave(Math.max(0, value - step))}>−</button>
            <strong style={{ minWidth: 60, textAlign: 'center' }}>{value} {UNIT_LABEL[product.unit]}</strong>
            <button type="button" onClick={() => onSave(value + step)}>+</button>
          </span>
          {value === 0 && <span className="badge danger">품절</span>}
          <button className="link" onClick={() => onSave(null)}>재고 관리 안 함</button>
        </>
      ) : (
        <>
          <span>관리 안 함 (항상 구매 가능)</span>
          <button className="link" onClick={() => onSave(0)}>재고 관리하기</button>
        </>
      )}
    </div>
  );
}

// ---- AI 스캔 ---------------------------------------------------------------

function ScansTab({ storeId }) {
  const [onlyPending, setOnlyPending] = useState(true);
  const [page, setPage] = useState(0);
  const jobs = useLoad(() => api.get(`/seller/stores/${storeId}/scan-jobs`,
    onlyPending ? { status: 'SUCCEEDED', reviewed: false, page } : { page }), [storeId, onlyPending, page]);

  return (
    <div className="stack">
      <div className="chips">
        <button className={`chip ${onlyPending ? 'on' : ''}`} onClick={() => { setOnlyPending(true); setPage(0); }}>검수 대기</button>
        <button className={`chip ${!onlyPending ? 'on' : ''}`} onClick={() => { setOnlyPending(false); setPage(0); }}>전체</button>
      </div>
      <ErrorBox error={jobs.error} />
      {jobs.data?.items.length === 0 && <p className="muted">{onlyPending ? '검수할 스캔이 없습니다.' : '스캔 기록이 없습니다.'}</p>}
      <div className="list">
        {jobs.data?.items.map((j) => (
          <Link key={j.id} to={`/seller/scan-jobs/${j.id}`} className="card row" style={{ textDecoration: 'none' }}>
            <Thumb src={j.images[0]?.thumbnail_url} className="thumb-sm" />
            <div className="grow">
              <div className="row"><strong>스캔 #{j.id}</strong><StatusBadge status={j.status} labels={SCAN_STATUS_LABEL} />
                {j.reviewed_at && <span className="badge ok">검수 완료</span>}
                {j.import_batch_id && <span className="badge">일괄 #{j.import_batch_id}</span>}</div>
              <div className="muted small">후보 {j.candidate_count}개 · {dateTime(j.created_at)}</div>
            </div>
          </Link>
        ))}
      </div>
      <Pager page={jobs.data} onChange={setPage} />
    </div>
  );
}

// ---- 주문 ----------------------------------------------------------------

const NEXT_ACTIONS = {
  REQUESTED: [['CONFIRMED', '접수', 'primary'], ['REJECTED', '거절', 'danger']],
  CONFIRMED: [['READY', '준비 완료', 'primary']],
  READY: [['COMPLETED', '거래 완료', 'primary']],
};

function OrdersTab({ storeId }) {
  const [status, setStatus] = useState('REQUESTED');
  const [page, setPage] = useState(0);
  const orders = useLoad(() => api.get(`/seller/stores/${storeId}/orders`, { status, page }), [storeId, status, page]);
  const [error, setError] = useState(null);

  async function change(order, next) {
    let deductStock = false;
    if (next === 'CONFIRMED') {
      // 재고는 자동으로 빼지 않는다. 접수할 때 판매자에게 한 번 묻는다.
      deductStock = window.confirm('주문 수량만큼 재고에서 뺄까요?\n(재고를 관리하지 않는 상품은 그대로입니다)');
    }
    if (next === 'REJECTED' && !window.confirm('주문을 거절할까요?')) return;
    setError(null);
    try {
      await api.post(`/seller/orders/${order.id}/status`, { status: next, deduct_stock: deductStock });
      orders.reload();
    } catch (e) {
      setError(e);
    }
  }

  return (
    <div className="stack">
      <div className="chips">
        {['REQUESTED', 'CONFIRMED', 'READY', 'COMPLETED', ''].map((s) => (
          <button key={s} className={`chip ${status === s ? 'on' : ''}`} onClick={() => { setStatus(s); setPage(0); }}>
            {s ? ORDER_STATUS_LABEL[s] : '전체'}
          </button>
        ))}
      </div>
      <ErrorBox error={error || orders.error} />
      {orders.data?.items.length === 0 && <p className="muted">주문이 없습니다.</p>}
      <div className="list">
        {orders.data?.items.map((o) => (
          <OrderCard key={o.id} order={o} showBuyer>
            <div className="row">
              {(NEXT_ACTIONS[o.status] || []).map(([next, label, tone]) => (
                <button key={next} className={tone} onClick={() => change(o, next)}>{label}</button>
              ))}
              {o.stock_deducted && <span className="muted small">재고에서 뺐음</span>}
            </div>
          </OrderCard>
        ))}
      </div>
      <Pager page={orders.data} onChange={setPage} />
    </div>
  );
}
