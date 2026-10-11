import { useState } from 'react';
import { Link, useSearchParams } from 'react-router-dom';
import { api } from '../api.js';
import ProductCard from '../components/ProductCard.jsx';
import { ErrorBox, Loading, Pager, Thumb, useLoad } from '../components/ui.jsx';
import { TEMPLATE_LABEL } from '../format.js';

/** 구매자 첫 화면: 검색 + 카테고리 + 시장 필터, 상품 목록, 상점 목록. 필터는 주소(?q=...)에 남겨 공유할 수 있게 한다. */
export default function Home() {
  const [params, setParams] = useSearchParams();
  const q = params.get('q') || '';
  const categoryId = params.get('category_id') || '';
  const marketId = params.get('market_id') || '';
  const sort = params.get('sort') || '';
  const page = Number(params.get('page') || 0);
  const [input, setInput] = useState(q);

  const categories = useLoad(() => api.get('/categories'), []);
  const markets = useLoad(() => api.get('/markets'), []);
  const products = useLoad(
    () => api.get('/products', { q, category_id: categoryId, market_id: marketId, sort, page, size: 20 }),
    [q, categoryId, marketId, sort, page],
  );
  const stores = useLoad(() => api.get('/stores', { market_id: marketId, size: 10 }), [marketId]);

  function update(changes) {
    const next = new URLSearchParams(params);
    Object.entries(changes).forEach(([k, v]) => (v === '' || v === null ? next.delete(k) : next.set(k, v)));
    if (!('page' in changes)) next.delete('page');
    setParams(next);
  }

  return (
    <div className="stack">
      <form className="row" onSubmit={(e) => { e.preventDefault(); update({ q: input.trim() }); }}>
        <input className="grow" type="search" placeholder="말린 살구, 아틀라스 원단…" value={input}
               onChange={(e) => setInput(e.target.value)} aria-label="상품 검색" />
        <button className="primary">검색</button>
      </form>

      <div className="chips" role="list">
        <button className={`chip ${categoryId === '' ? 'on' : ''}`} onClick={() => update({ category_id: '' })}>전체</button>
        {categories.data?.map((c) => (
          <button key={c.id} className={`chip ${String(c.id) === categoryId ? 'on' : ''}`}
                  onClick={() => update({ category_id: c.id })}>{c.name}</button>
        ))}
      </div>

      <div className="grid-2">
        <select value={marketId} onChange={(e) => update({ market_id: e.target.value })} aria-label="시장">
          <option value="">모든 시장</option>
          {markets.data?.map((m) => <option key={m.id} value={m.id}>{m.name} ({m.city})</option>)}
        </select>
        <select value={sort} onChange={(e) => update({ sort: e.target.value })} aria-label="정렬">
          <option value="">{q ? '관련도순' : '최신순'}</option>
          <option value="price_asc">가격 낮은순</option>
          <option value="price_desc">가격 높은순</option>
        </select>
      </div>

      <ErrorBox error={products.error} />
      {products.loading && !products.data && <Loading />}
      {products.data && (
        <>
          <p className="muted small">상품 {products.data.total_elements}개</p>
          {products.data.items.length === 0
            ? <p className="muted">조건에 맞는 상품이 없습니다.</p>
            : <div className="product-grid">{products.data.items.map((p) => <ProductCard key={p.id} product={p} />)}</div>}
          <Pager page={products.data} onChange={(p) => update({ page: p })} />
        </>
      )}

      <h2>시장의 상점</h2>
      <div className="list">
        {stores.data?.items.map((s) => (
          <Link key={s.slug} to={`/s/${s.slug}`} className="card row" style={{ textDecoration: 'none' }}>
            <Thumb src={s.logo_url} className="thumb-sm" alt="" />
            <div className="grow">
              <h3>{s.name}</h3>
              <div className="muted small">
                {s.market ? `${s.market.name}${s.location_detail ? ' · ' + s.location_detail : ''}` : '시장 밖 매장'}
                {' · '}상품 {s.product_count}개
              </div>
            </div>
            <span className="badge">{TEMPLATE_LABEL[s.template]}</span>
          </Link>
        ))}
      </div>
    </div>
  );
}
