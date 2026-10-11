import { useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { api } from '../api.js';
import { useAuth } from '../auth.jsx';
import { useCart } from '../cart.js';
import QuantityInput from '../components/QuantityInput.jsx';
import { ErrorBox, Field, PriceTag, Thumb, fieldError } from '../components/ui.jsx';
import { FULFILLMENT_LABEL, PAYMENT_LABEL, money } from '../format.js';

/** 장바구니: 상점별로 묶어서 보여주고, 주문도 상점마다 따로 보낸다(한 주문 = 한 상점). */
export default function Cart() {
  const { items, setQuantity, remove } = useCart();
  const groups = items.reduce((acc, item) => {
    (acc[item.store.id] ||= { store: item.store, items: [] }).items.push(item);
    return acc;
  }, {});

  if (items.length === 0) {
    return <div className="stack"><h1>장바구니</h1><p className="muted">담은 상품이 없습니다. <Link to="/">둘러보기</Link></p></div>;
  }
  return (
    <div className="stack">
      <h1>장바구니</h1>
      {Object.values(groups).map((g) => (
        <div key={g.store.id} className="card stack">
          <h2 style={{ marginTop: 0 }}><Link to={`/s/${g.store.slug}`}>{g.store.name}</Link></h2>
          {g.items.map((item) => (
            <div key={item.product_id} className="row" style={{ alignItems: 'flex-start', flexWrap: 'nowrap' }}>
              <Thumb src={item.thumbnail_url} className="thumb-sm" />
              <div className="grow">
                <div>{item.name}</div>
                <div className="small"><PriceTag price={item.price} unit={item.unit} priceType={item.price_type} /></div>
                <div style={{ marginTop: 6 }}>
                  <QuantityInput value={item.quantity} unit={item.unit} onChange={(q) => setQuantity(item.product_id, q)} />
                </div>
              </div>
              <button className="ghost danger" onClick={() => remove(item.product_id)} aria-label="빼기">✕</button>
            </div>
          ))}
          <Checkout group={g} />
        </div>
      ))}
    </div>
  );
}

function Checkout({ group }) {
  const { me } = useAuth();
  const { removeStore } = useCart();
  const navigate = useNavigate();
  const [form, setForm] = useState({ fulfillment_type: 'PICKUP', payment_method: 'CASH', contact_phone: '',
    delivery_address: '', landmark: '', buyer_note: '' });
  const [error, setError] = useState(null);
  const [busy, setBusy] = useState(false);
  const total = group.items.reduce((sum, i) => sum + (i.price || 0) * i.quantity, 0);
  const estimate = group.items.some((i) => i.price_type === 'NEGOTIABLE');
  const set = (key) => (e) => setForm({ ...form, [key]: e.target.value });

  async function submit(e) {
    e.preventDefault();
    if (!me) { navigate('/login?next=/cart'); return; }
    setBusy(true);
    setError(null);
    try {
      await api.post('/orders', {
        store_id: group.store.id,
        items: group.items.map((i) => ({ product_id: i.product_id, quantity: i.quantity })),
        ...form,
        contact_phone: form.contact_phone || null,
      });
      removeStore(group.store.id);
      navigate('/orders');
    } catch (err) {
      setError(err);
    } finally {
      setBusy(false);
    }
  }

  return (
    <form className="stack" onSubmit={submit}>
      <div className="row between">
        <strong>합계 {money(total)}</strong>
        {estimate && <span className="badge brand">흥정 상품 포함 · 예상 금액</span>}
      </div>
      <div className="grid-2">
        <Field label="받는 방법">
          <select value={form.fulfillment_type} onChange={set('fulfillment_type')}>
            {Object.entries(FULFILLMENT_LABEL).map(([v, l]) => <option key={v} value={v}>{l}</option>)}
          </select>
        </Field>
        <Field label="결제">
          <select value={form.payment_method} onChange={set('payment_method')}>
            {Object.entries(PAYMENT_LABEL).map(([v, l]) => <option key={v} value={v}>{l}</option>)}
          </select>
        </Field>
      </div>
      {form.fulfillment_type === 'DELIVERY' && (
        <>
          <Field label="주소" error={fieldError(error, 'delivery_address')}>
            <input value={form.delivery_address} onChange={set('delivery_address')} required />
          </Field>
          <Field label="랜드마크 (예: 중앙백화점 맞은편)"><input value={form.landmark} onChange={set('landmark')} /></Field>
        </>
      )}
      <Field label="연락처 (비우면 내 번호)" error={fieldError(error, 'contact_phone')}>
        <input value={form.contact_phone} onChange={set('contact_phone')} placeholder="+992…" inputMode="tel" />
      </Field>
      <Field label="요청 사항"><input value={form.buyer_note} onChange={set('buyer_note')} maxLength={500} /></Field>
      <ErrorBox error={error} />
      <button className="primary" disabled={busy}>{me ? '이 상점에 주문하기' : '로그인하고 주문하기'}</button>
      <p className="muted small">결제는 판매자와 직접 합니다(현금 또는 송금). 앱에서 돈이 빠져나가지 않습니다.</p>
    </form>
  );
}
