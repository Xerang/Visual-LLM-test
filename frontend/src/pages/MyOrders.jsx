import { useState } from 'react';
import { api } from '../api.js';
import OrderCard from '../components/OrderCard.jsx';
import { ErrorBox, Loading, Pager, useLoad } from '../components/ui.jsx';
import { whatsappLink } from '../format.js';

export default function MyOrders() {
  const [page, setPage] = useState(0);
  const orders = useLoad(() => api.get('/orders', { page }), [page]);
  const [error, setError] = useState(null);

  async function cancel(id) {
    if (!window.confirm('주문을 취소할까요?')) return;
    setError(null);
    try {
      await api.post(`/orders/${id}/cancel`);
      orders.reload();
    } catch (e) {
      setError(e);
    }
  }

  return (
    <div className="stack">
      <h1>내 주문</h1>
      <ErrorBox error={error || orders.error} />
      {orders.loading && !orders.data && <Loading />}
      {orders.data?.items.length === 0 && <p className="muted">주문 내역이 없습니다.</p>}
      <div className="list">
        {orders.data?.items.map((o) => {
          const wa = whatsappLink(o.store.whatsapp_phone, `BOZOR 주문 ${o.order_number} 문의`);
          return (
            <OrderCard key={o.id} order={o}>
              <div className="row">
                {wa && <a className="button" href={wa} target="_blank" rel="noreferrer">판매자에게 WhatsApp</a>}
                {(o.status === 'REQUESTED' || o.status === 'CONFIRMED') && (
                  <button className="danger" onClick={() => cancel(o.id)}>주문 취소</button>
                )}
              </div>
            </OrderCard>
          );
        })}
      </div>
      <Pager page={orders.data} onChange={setPage} />
    </div>
  );
}
