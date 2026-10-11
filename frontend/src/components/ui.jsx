import { useCallback, useEffect, useState } from 'react';
import { PRODUCT_STATUS_LABEL, PRICE_TYPE_LABEL, unitPrice } from '../format.js';

/** 데이터를 불러오는 공통 훅. deps 가 바뀌면 다시 부른다. */
export function useLoad(load, deps) {
  const [state, setState] = useState({ data: null, error: null, loading: true });
  const run = useCallback(load, deps);   // load 함수는 deps 가 바뀔 때만 새로 만든다
  const reload = useCallback(async () => {
    setState((s) => ({ ...s, loading: true }));
    try {
      setState({ data: await run(), error: null, loading: false });
    } catch (error) {
      setState({ data: null, error, loading: false });
    }
  }, [run]);
  useEffect(() => { reload(); }, [reload]);
  return { ...state, reload, setData: (data) => setState((s) => ({ ...s, data })) };
}

/** API 오류(ApiError)를 사람이 읽을 수 있게 보여준다. 필드별 오류가 있으면 목록으로. */
export function ErrorBox({ error }) {
  if (!error) return null;
  return (
    <div className="alert" role="alert">
      {error.message}
      {error.errors?.length > 0 && (
        <ul>{error.errors.map((e, i) => <li key={i}>{e.field}: {e.message}</li>)}</ul>
      )}
    </div>
  );
}

export function Loading({ text = '불러오는 중…' }) {
  return <p className="muted">{text}</p>;
}

export function Thumb({ src, alt = '', className = '' }) {
  if (!src) return <div className={`thumb placeholder ${className}`}>사진 없음</div>;
  return <img className={`thumb ${className}`} src={src} alt={alt} loading="lazy" />;
}

export function PriceTag({ price, unit, priceType, currency }) {
  return (
    <span>
      <span className="price">{unitPrice(price, unit, currency)}</span>
      {priceType === 'NEGOTIABLE' && <> <span className="badge brand">{PRICE_TYPE_LABEL.NEGOTIABLE}</span></>}
    </span>
  );
}

const STATUS_TONE = {
  PUBLISHED: 'ok', ACTIVE: 'ok', SUCCEEDED: 'ok', COMPLETED: 'ok', CONFIRMED: 'ok', READY: 'ok',
  DRAFT: '', HIDDEN: 'warn', QUEUED: 'warn', RUNNING: 'warn', REQUESTED: 'warn',
  FAILED: 'danger', REJECTED: 'danger', CANCELLED: 'danger', ARCHIVED: '',
};

export function StatusBadge({ status, labels = PRODUCT_STATUS_LABEL }) {
  return <span className={`badge ${STATUS_TONE[status] || ''}`}>{labels[status] || status}</span>;
}

export function Field({ label, error, children }) {
  return (
    <label className="field">
      <span>{label}</span>
      {children}
      {error && <div className="field-error">{error}</div>}
    </label>
  );
}

/** ApiError.errors 에서 특정 필드의 오류 문장을 찾는다. */
export function fieldError(error, field) {
  return error?.errors?.find((e) => e.field === field || e.field.endsWith('.' + field))?.message;
}

export function Pager({ page, onChange }) {
  if (!page || page.total_pages <= 1) return null;
  return (
    <div className="row" style={{ justifyContent: 'center', marginTop: 12 }}>
      <button disabled={page.page === 0} onClick={() => onChange(page.page - 1)}>이전</button>
      <span className="muted">{page.page + 1} / {page.total_pages}</span>
      <button disabled={page.page + 1 >= page.total_pages} onClick={() => onChange(page.page + 1)}>다음</button>
    </div>
  );
}
