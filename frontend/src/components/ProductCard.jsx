import { Link } from 'react-router-dom';
import { PriceTag, Thumb } from './ui.jsx';

/** 구매자용 상품 카드(검색 결과·진열대형 템플릿). */
export default function ProductCard({ product, showStore = true }) {
  const soldOut = product.stock_quantity !== null && Number(product.stock_quantity) === 0;
  return (
    <Link to={`/p/${product.id}`} className="product-card">
      <Thumb src={product.thumbnail_url} alt={product.name} />
      <div className="name">{product.name}</div>
      <div className="small">
        <PriceTag price={product.price} unit={product.unit} priceType={product.price_type} currency={product.currency} />
      </div>
      {soldOut && <span className="badge danger">품절</span>}
      {showStore && (
        <div className="muted small">{product.store.name}{product.store.market_name ? ` · ${product.store.market_name}` : ''}</div>
      )}
    </Link>
  );
}
