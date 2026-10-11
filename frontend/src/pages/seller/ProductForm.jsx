import { useEffect, useState } from 'react';
import { Link, useNavigate, useParams } from 'react-router-dom';
import { api } from '../../api.js';
import { useAuth } from '../../auth.jsx';
import { CategorySelect, ImageUploadButton, useCategories } from '../../components/seller.jsx';
import { ErrorBox, Field, Loading, StatusBadge, fieldError } from '../../components/ui.jsx';
import { LOCALE_LABEL, PRICE_TYPE_LABEL, UNIT_LABEL } from '../../format.js';

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
  const [others, setOthers] = useState([]);   // 다른 언어 번역: [{ locale, name, description, is_auto_translated, saved, confirm }]
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
      setOthers(othersOf(p, locale));
    }).catch(setError);
  }, [editing, productId, locale]);

  const set = (key) => (e) => setForm({ ...form, [key]: e.target.value });

  async function submit(e) {
    e.preventDefault();
    setBusy(true);
    setError(null);
    // 다른 언어 번역도 함께 보낸다. 자동 번역 표시는 서버가 정한다(고치지 않으면 그대로, 고치면 판매자 확인).
    // "확인했어요"를 고른 번역만 is_auto_translated=false 로 보낸다.
    const translations = others.map((o) => ({
      locale: o.locale, name: o.name.trim(), description: o.description.trim() || null,
      ...(o.confirm ? { is_auto_translated: false } : {}),
    }));
    const body = {
      category_id: form.category_id,
      translations: [{ locale, name: form.name.trim(), description: form.description.trim() || null }, ...translations],
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
      {editing ? (
        <OtherLanguages product={product} locale={locale} others={others} setOthers={setOthers}
                        sourceDirty={product && isSourceDirty(product, locale, form)}
                        onTranslated={(p) => { setProduct(p); setOthers(othersOf(p, locale)); }} />
      ) : <p className="muted small">저장한 뒤 수정 화면에서 타직어·러시아어·영어 번역 초안을 만들 수 있어요.</p>}
      <ErrorBox error={error} />
      <button className="primary" disabled={busy}>{editing ? '저장' : '초안으로 등록'}</button>
    </form>
  );
}

function othersOf(product, locale) {
  return product.translations.filter((t) => t.locale !== locale).map((t) => ({
    locale: t.locale, name: t.name, description: t.description || '', is_auto_translated: t.is_auto_translated,
    saved: { name: t.name, description: t.description || '' }, confirm: false,
  }));
}

/** 저장된 원문과 폼이 다르면 번역 전에 먼저 저장해야 한다(서버는 저장된 원문을 번역한다). */
function isSourceDirty(product, locale, form) {
  const t = product.translations.find((x) => x.locale === locale);
  return !t || t.name !== form.name.trim() || (t.description || '') !== form.description.trim();
}

/**
 * 다른 언어(구매자용) 상품명·설명. 2차 추가 기능 "다국어 상품 정보(타직어·러시아어·영어)".
 * AI 번역은 초안이다. 고치면 판매자 확인으로 바뀌고, 고치지 않고 확인만 할 수도 있다.
 */
function OtherLanguages({ product, locale, others, setOthers, sourceDirty, onTranslated }) {
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState(null);
  const update = (loc, changes) => setOthers(others.map((o) => (o.locale === loc ? { ...o, ...changes } : o)));
  const edited = (o) => o.name.trim() !== o.saved.name.trim() || o.description.trim() !== o.saved.description.trim();
  // 저장 안 한 변경이 있으면 번역하지 않는다(번역 결과가 화면의 고친 내용을 덮어쓰지 않게)
  const savedCount = product.translations.filter((t) => t.locale !== locale).length;
  const unsaved = sourceDirty || others.length !== savedCount || others.some((o) => edited(o) || o.confirm);

  async function translate() {
    setBusy(true);
    setError(null);
    try {
      onTranslated(await api.post(`/seller/products/${product.id}/translations/auto`));
    } catch (e) {
      setError(e);
    } finally {
      setBusy(false);
    }
  }

  return (
    <div className="card stack">
      <strong>다른 언어 (구매자 화면용)</strong>
      <p className="muted small">
        타직어·러시아어·영어 구매자에게 보일 이름이에요. AI 번역은 틀릴 수 있으니 확인해 주세요(특히 타직어는 현지 검수가 필요해요).
        직접 고친 언어는 AI가 다시 덮어쓰지 않아요.
      </p>
      {others.length === 0 && <p className="muted small">아직 다른 언어가 없어요.</p>}
      {others.map((o) => {
        const pending = o.is_auto_translated && !edited(o) && !o.confirm;
        return (
          <div key={o.locale} className="stack" style={{ borderTop: '1px solid var(--border)', paddingTop: 10 }}>
            <div className="row between">
              <strong>{LOCALE_LABEL[o.locale]}</strong>
              {pending ? <span className="badge warn">AI 번역 · 확인 전</span> : <span className="badge ok">판매자 확인</span>}
            </div>
            <Field label="상품명">
              <input value={o.name} onChange={(e) => update(o.locale, { name: e.target.value })} required maxLength={120} />
            </Field>
            <Field label="설명">
              <textarea value={o.description} onChange={(e) => update(o.locale, { description: e.target.value })} maxLength={2000} rows={2} />
            </Field>
            <div className="row between">
              {o.is_auto_translated && !edited(o) ? (
                <label className="check">
                  <input type="checkbox" checked={o.confirm} onChange={(e) => update(o.locale, { confirm: e.target.checked })} />
                  <span>고칠 곳 없이 확인했어요</span>
                </label>
              ) : <span />}
              <button type="button" className="ghost danger" onClick={() => setOthers(others.filter((x) => x.locale !== o.locale))}>이 언어 빼기</button>
            </div>
          </div>
        );
      })}
      <ErrorBox error={error} />
      <button type="button" disabled={busy || unsaved} onClick={translate}>
        {busy ? 'AI가 번역하는 중… (10초쯤 걸려요)' : 'AI 번역 초안 만들기 (타직어·러시아어·영어)'}
      </button>
      {unsaved && <p className="muted small">저장하지 않은 변경이 있어요. 먼저 저장한 뒤 번역할 수 있어요(저장된 글을 번역해요).</p>}
      <p className="muted small">번역 결과는 아래 "저장"을 눌러야 확인 표시·수정 내용이 반영돼요. 번역 초안 자체는 바로 저장돼요.</p>
    </div>
  );
}
