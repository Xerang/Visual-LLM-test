import { useEffect, useState } from 'react';
import { Link, useNavigate, useParams } from 'react-router-dom';
import { api } from '../../api.js';
import { useAuth } from '../../auth.jsx';
import { CategorySelect, ImageUploadButton, useCategories } from '../../components/seller.jsx';
import { ErrorBox, Field, Loading, StatusBadge, fieldError } from '../../components/ui.jsx';
import { PRICE_TYPE_LABEL, UNIT_LABEL } from '../../format.js';

/**
 * 상품 직접 등록(AI 없이)과 수정. 수정은 PUT(전체 교체)이라 폼 전체를 보낸다.
 * 상태(발행·숨김)와 재고는 상점 관리 화면의 버튼으로 따로 바꾼다.
 */
export default function ProductForm() {
  const { storeId, productId } = useParams();
  const editing = Boolean(productId);
  const navigate = useNavigate();
  const { me } = useAuth();
  const categories = useCategories();
  const [product, setProduct] = useState(null);
  const [form, setForm] = useState({ category_id: null, name: '', description: '', price: '', price_type: 'FIXED', unit: 'PIECE', stock: '' });
  const [images, setImages] = useState([]);   // [{ id, thumbnail_url }]
  const [error, setError] = useState(null);
  const [busy, setBusy] = useState(false);
  const locale = me?.locale || 'ko';

  useEffect(() => {
    if (!editing) return;
    api.get(`/seller/products/${productId}`).then((p) => {
      setProduct(p);
      const t = p.translations.find((x) => x.locale === locale) || p.translations[0];
      setForm({ category_id: p.category.id, name: t?.name || '', description: t?.description || '',
        price: p.price ?? '', price_type: p.price_type, unit: p.unit, stock: p.stock_quantity ?? '' });
      setImages(p.images);
    }).catch(setError);
  }, [editing, productId, locale]);

  const set = (key) => (e) => setForm({ ...form, [key]: e.target.value });

  async function submit(e) {
    e.preventDefault();
    setBusy(true);
    setError(null);
    // 다른 언어 번역은 그대로 두고, 지금 언어의 이름·설명만 바꾼다.
    const others = (product?.translations || []).filter((t) => t.locale !== locale)
      .map((t) => ({ locale: t.locale, name: t.name, description: t.description }));
    const body = {
      category_id: form.category_id,
      translations: [{ locale, name: form.name.trim(), description: form.description.trim() || null }, ...others],
      price: form.price === '' ? null : Number(form.price),
      price_type: form.price_type,
      unit: form.unit,
      stock_quantity: form.stock === '' ? null : Number(form.stock),
      image_ids: images.map((i) => i.id),
    };
    try {
      const saved = editing
        ? await api.put(`/seller/products/${productId}`, body)
        : await api.post(`/seller/stores/${storeId}/products`, body);
      navigate(`/seller/stores/${saved.store_id}?tab=products`);
    } catch (err) {
      setError(err);
      setBusy(false);
    }
  }

  if (editing && !product && !error) return <Loading />;
  return (
    <form className="stack" onSubmit={submit}>
      <div className="row between">
        <h1 style={{ margin: 0 }}>{editing ? '상품 수정' : '상품 직접 등록'}</h1>
        {product && <StatusBadge status={product.status} />}
      </div>
      {product?.source && (
        <p className="muted small">AI 스캔 <Link to={`/seller/scan-jobs/${product.source.scan_job_id}`}>#{product.source.scan_job_id}</Link>에서 만든 상품입니다.</p>
      )}
      <div className="card stack">
        <div className="photos">
          {images.map((img, i) => (
            <div key={img.id} style={{ position: 'relative' }}>
              <img src={img.thumbnail_url} alt="" />
              {i === 0 && <span className="badge brand" style={{ position: 'absolute', left: 4, top: 4 }}>대표</span>}
              <button type="button" className="ghost" style={{ position: 'absolute', top: 2, right: 2, background: '#fff', padding: '2px 8px' }}
                      onClick={() => setImages(images.filter((x) => x.id !== img.id))} aria-label="사진 빼기">✕</button>
            </div>
          ))}
        </div>
        <ImageUploadButton multiple label="사진 추가" onUploaded={(img) =>
          setImages((list) => (list.some((x) => x.id === img.id) ? list : [...list, img]))} />
      </div>
      <div className="card stack">
        <Field label="상품명" error={fieldError(error, 'translations[0].name')}>
          <input value={form.name} onChange={set('name')} required maxLength={120} />
        </Field>
        <Field label="설명"><textarea value={form.description} onChange={set('description')} maxLength={2000} /></Field>
        <Field label="카테고리" error={fieldError(error, 'category_id')}>
          <CategorySelect value={form.category_id} categories={categories.data}
                          onChange={(id, leaf) => setForm({ ...form, category_id: id, unit: leaf?.default_unit || form.unit })} />
        </Field>
        <div className="grid-2">
          <Field label={`가격 (TJS / ${UNIT_LABEL[form.unit]})`} error={fieldError(error, 'price')}>
            <input type="number" inputMode="decimal" min="0" step="0.01" value={form.price} onChange={set('price')} />
          </Field>
          <Field label="가격 방식">
            <select value={form.price_type} onChange={set('price_type')}>
              {Object.entries(PRICE_TYPE_LABEL).map(([v, l]) => <option key={v} value={v}>{l}</option>)}
            </select>
          </Field>
          <Field label="판매 단위">
            <select value={form.unit} onChange={set('unit')}>
              {Object.entries(UNIT_LABEL).map(([v, l]) => <option key={v} value={v}>{l}</option>)}
            </select>
          </Field>
          <Field label="재고 (비우면 관리 안 함)" error={fieldError(error, 'stock_quantity')}>
            <input type="number" inputMode="decimal" min="0" step="any" value={form.stock} onChange={set('stock')} />
          </Field>
        </div>
      </div>
      <ErrorBox error={error} />
      <button className="primary" disabled={busy}>{editing ? '저장' : '초안으로 등록'}</button>
    </form>
  );
}
