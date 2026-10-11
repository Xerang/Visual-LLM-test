import { useState } from 'react';
import { Link, useParams } from 'react-router-dom';
import { api } from '../api.js';
import ProductCard from '../components/ProductCard.jsx';
import { ErrorBox, Loading, PriceTag, Thumb, useLoad } from '../components/ui.jsx';
import { whatsappLink } from '../format.js';

/**
 * 상점 화면. 템플릿(임시 결정 #3)은 데이터는 같고 배치만 다르다.
 * GRID = 진열대형, LIST = 가격표형, SHOWCASE = 공방형. theme_color 는 CSS 변수(--store)로 입힌다.
 */
export default function StorePage() {
  const { slug } = useParams();
  const [categoryId, setCategoryId] = useState('');
  const store = useLoad(() => api.get(`/stores/${slug}`), [slug]);
  const products = useLoad(
    () => api.get(`/stores/${slug}/products`, { category_id: categoryId, size: 50 }),
    [slug, categoryId],
  );

  if (store.error) return <ErrorBox error={store.error} />;
  if (!store.data) return <Loading />;
  const s = store.data;
  const items = products.data?.items || [];
  const wa = whatsappLink(s.contact.whatsapp_phone, `BOZOR에서 보고 연락드립니다: ${s.name}`);

  return (
    <div className="stack" style={s.theme_color ? { '--store': s.theme_color } : undefined}>
      <section className="store-hero">
        {(s.template === 'SHOWCASE' || s.banner_url) && (
          <div className="banner" style={s.banner_url ? { backgroundImage: `url(${s.banner_url})` } : undefined} />
        )}
        <div className={`body ${s.template === 'SHOWCASE' || s.banner_url ? '' : 'store-accent'}`}>
          <div className="row">
            {s.logo_url && <img src={s.logo_url} alt="" className="thumb thumb-sm" />}
            <div className="grow">
              <h1 style={{ marginBottom: 2 }}>{s.name}</h1>
              <div className="muted small">
                {s.market ? `${s.market.name}${s.location_detail ? ' · ' + s.location_detail : ''}` : s.address_text}
                {s.landmark && ` · ${s.landmark}`}
              </div>
            </div>
          </div>
          {s.description && <p style={{ marginTop: 10 }}>{s.description}</p>}
          <div className="row" style={{ marginTop: 8 }}>
            {wa && <a className="button primary" href={wa} target="_blank" rel="noreferrer">WhatsApp 문의</a>}
            {s.contact.phone && <a className="button" href={`tel:${s.contact.phone}`}>전화</a>}
            {s.contact.instagram_handle && (
              <a className="button" href={`https://instagram.com/${s.contact.instagram_handle}`} target="_blank" rel="noreferrer">Instagram</a>
            )}
          </div>
        </div>
      </section>

      {s.categories.length > 1 && (
        <div className="chips">
          <button className={`chip ${categoryId === '' ? 'on' : ''}`} onClick={() => setCategoryId('')}>전체 {s.product_count}</button>
          {s.categories.map((c) => (
            <button key={c.id} className={`chip ${String(c.id) === String(categoryId) ? 'on' : ''}`}
                    onClick={() => setCategoryId(c.id)}>{c.name} {c.product_count}</button>
          ))}
        </div>
      )}

      <ErrorBox error={products.error} />
      {products.loading && !products.data && <Loading />}
      {products.data && items.length === 0 && <p className="muted">아직 판매 중인 상품이 없습니다.</p>}
      {s.template === 'GRID' && <GridTemplate items={items} />}
      {s.template === 'LIST' && <ListTemplate items={items} />}
      {s.template === 'SHOWCASE' && <ShowcaseTemplate items={items} />}
    </div>
  );
}

/** 진열대형: 2열 사진 그리드 */
function GridTemplate({ items }) {
  return <div className="product-grid">{items.map((p) => <ProductCard key={p.id} product={p} showStore={false} />)}</div>;
}

/** 가격표형: 작은 썸네일 + 이름 + 단가, 카테고리별로 묶음 */
function ListTemplate({ items }) {
  const groups = items.reduce((acc, p) => {
    (acc[p.category.name] ||= []).push(p);
    return acc;
  }, {});
  return (
    <div className="price-list card">
      {Object.entries(groups).map(([name, list]) => (
        <div key={name}>
          <div className="group">{name}</div>
          {list.map((p) => (
            <Link key={p.id} to={`/p/${p.id}`} className="item">
              <Thumb src={p.thumbnail_url} className="thumb-sm" alt="" />
              <span className="grow">{p.name}</span>
              <span className="right small"><PriceTag price={p.price} unit={p.unit} priceType={p.price_type} currency={p.currency} /></span>
            </Link>
          ))}
        </div>
      ))}
    </div>
  );
}

/** 공방형: 큰 사진을 한 줄에 하나씩 */
function ShowcaseTemplate({ items }) {
  return (
    <div className="showcase">
      {items.map((p) => (
        <Link key={p.id} to={`/p/${p.id}`} className="item card">
          <Thumb src={p.thumbnail_url && p.thumbnail_url.replace('?size=thumb', '')} alt={p.name} />
          <h3 style={{ marginTop: 10 }}>{p.name}</h3>
          <PriceTag price={p.price} unit={p.unit} priceType={p.price_type} currency={p.currency} />
        </Link>
      ))}
    </div>
  );
}
