import { useState } from 'react';
import { Link, useParams } from 'react-router-dom';
import { api } from '../api.js';
import { useCart } from '../cart.js';
import { ErrorBox, Loading, PriceTag, Thumb, useLoad } from '../components/ui.jsx';
import { quantity as formatQuantity, whatsappLink } from '../format.js';
import QuantityInput from '../components/QuantityInput.jsx';

export default function ProductPage() {
  const { productId } = useParams();
  const product = useLoad(() => api.get(`/products/${productId}`), [productId]);
  const { add } = useCart();
  const [qty, setQty] = useState(null);
  const [added, setAdded] = useState(false);

  if (product.error) return <ErrorBox error={product.error} />;
  if (!product.data) return <Loading />;
  const p = product.data;
  const amount = qty ?? 1;
  const soldOut = p.stock_quantity !== null && Number(p.stock_quantity) === 0;
  const wa = whatsappLink(p.store.contact.whatsapp_phone, `BOZOR 상품 문의: ${p.name}`);

  function addToCart() {
    add({
      product_id: p.id, name: p.name, price: p.price, price_type: p.price_type, unit: p.unit,
      currency: p.currency, thumbnail_url: p.images[0]?.thumbnail_url || null,
      store: { id: p.store.id, slug: p.store.slug, name: p.store.name }, quantity: amount,
    });
    setAdded(true);
  }

  return (
    <div className="stack">
      {p.images.length > 0 ? (
        <div className="photos">{p.images.map((img) => <img key={img.id} src={img.url} alt={p.name} style={{ width: 260, height: 260 }} />)}</div>
      ) : <Thumb src={null} />}
      <div>
        <span className="badge">{p.category.name}</span>
        <h1 style={{ marginTop: 6 }}>{p.name}</h1>
        {p.is_auto_translated && <p className="muted small">자동 번역된 상품 정보예요. 판매자가 아직 확인하지 않았어요.</p>}
        <div style={{ fontSize: '1.15rem' }}><PriceTag price={p.price} unit={p.unit} priceType={p.price_type} currency={p.currency} /></div>
        {p.price_type === 'NEGOTIABLE' && <p className="muted small">기준 가격입니다. 최종 가격은 판매자와 협의합니다.</p>}
        {p.stock_quantity !== null && (
          <p className="muted small">{soldOut ? '품절' : `남은 수량 ${formatQuantity(p.stock_quantity, p.unit)}`}</p>
        )}
      </div>
      {p.description && <p>{p.description}</p>}

      <div className="card stack">
        <div className="row">
          <span className="muted">수량</span>
          <QuantityInput value={amount} unit={p.unit} max={p.stock_quantity} onChange={setQty} />
        </div>
        <button className="primary block" disabled={soldOut} onClick={addToCart}>장바구니에 담기</button>
        {added && <p className="alert ok">담았습니다. <Link to="/cart">장바구니 보기</Link></p>}
      </div>

      <div className="card">
        <h3>판매자</h3>
        <Link to={`/s/${p.store.slug}`}>{p.store.name}</Link>
        <div className="muted small">{p.store.market_name}{p.store.location_detail ? ` · ${p.store.location_detail}` : ''}</div>
        {wa && <a className="button" style={{ marginTop: 8 }} href={wa} target="_blank" rel="noreferrer">WhatsApp 문의</a>}
      </div>
    </div>
  );
}
