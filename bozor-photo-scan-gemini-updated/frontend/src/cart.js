import { useCallback, useEffect, useState } from 'react';

// 장바구니는 브라우저에만 저장한다(서버 테이블 없음, docs/03-api.md 5.8).
// 한 주문 = 한 상점이므로 주문할 때 상점별로 나눠 보낸다.

const KEY = 'bozor.cart';
const listeners = new Set();

function read() {
  try { return JSON.parse(localStorage.getItem(KEY)) || []; } catch { return []; }
}

function write(items) {
  try { localStorage.setItem(KEY, JSON.stringify(items)); } catch { /* 저장 불가 환경 */ }
  listeners.forEach((fn) => fn(items));
}

/** item: { product_id, name, price, price_type, unit, thumbnail_url, store: { id, slug, name }, quantity } */
export function useCart() {
  const [items, setItems] = useState(read);

  useEffect(() => {
    listeners.add(setItems);
    return () => listeners.delete(setItems);
  }, []);

  const add = useCallback((item) => {
    const current = read();
    const found = current.find((i) => i.product_id === item.product_id);
    write(found
      ? current.map((i) => (i.product_id === item.product_id ? { ...i, quantity: round(i.quantity + item.quantity) } : i))
      : [...current, item]);
  }, []);

  const setQuantity = useCallback((productId, quantity) => {
    write(read().map((i) => (i.product_id === productId ? { ...i, quantity } : i)));
  }, []);

  const remove = useCallback((productId) => {
    write(read().filter((i) => i.product_id !== productId));
  }, []);

  const removeStore = useCallback((storeId) => {
    write(read().filter((i) => i.store.id !== storeId));
  }, []);

  return { items, add, setQuantity, remove, removeStore };
}

function round(n) {
  return Math.round(n * 1000) / 1000;
}
