import { Link } from 'react-router-dom';
import { FULFILLMENT_LABEL, ORDER_STATUS_LABEL, PAYMENT_LABEL, dateTime, money, quantity, unitPrice } from '../format.js';
import { StatusBadge } from './ui.jsx';

/** 주문 한 건. 구매자·판매자 화면이 같이 쓰고, 버튼은 children 으로 받는다. */
export default function OrderCard({ order, showBuyer = false, children }) {
  return (
    <div className="card stack">
      <div className="row between">
        <strong>{order.order_number}</strong>
        <StatusBadge status={order.status} labels={ORDER_STATUS_LABEL} />
      </div>
      <div className="muted small">
        {showBuyer ? `주문자 ${order.buyer.display_name} · ${order.contact_phone}` : <Link to={`/s/${order.store.slug}`}>{order.store.name}</Link>}
        {' · '}{dateTime(order.created_at)}
      </div>
      <table className="table">
        <tbody>
          {order.items.map((i) => (
            <tr key={i.product_id}>
              <td>{i.product_name}<div className="muted small">{unitPrice(i.unit_price, i.unit)}{i.price_type === 'NEGOTIABLE' ? ' · 흥정 가능' : ''}</div></td>
              <td className="right">{quantity(i.quantity, i.unit)}</td>
              <td className="right">{money(i.line_total)}</td>
            </tr>
          ))}
        </tbody>
      </table>
      <div className="row between">
        <span className="muted small">
          {FULFILLMENT_LABEL[order.fulfillment_type]} · {PAYMENT_LABEL[order.payment_method]}
          {order.delivery_address && ` · ${order.delivery_address}`}{order.landmark && ` (${order.landmark})`}
        </span>
        <strong>{money(order.total_amount)}{order.is_estimate && <span className="muted small"> (예상)</span>}</strong>
      </div>
      {order.buyer_note && <p className="small">요청: {order.buyer_note}</p>}
      {children}
    </div>
  );
}
