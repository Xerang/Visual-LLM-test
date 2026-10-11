import { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { api } from '../../api.js';
import { useAuth } from '../../auth.jsx';
import { ImageUploadButton } from '../../components/seller.jsx';
import { ErrorBox, Field, fieldError, useLoad } from '../../components/ui.jsx';
import { TEMPLATE_HELP, TEMPLATE_LABEL } from '../../format.js';

const EMPTY = {
  name: '', slug: '', description: '', template: 'GRID', theme_color: '#B45309',
  logo_image_id: null, banner_image_id: null, market_id: '', location_detail: '', address_text: '', landmark: '',
  contact_phone: '', whatsapp_phone: '', instagram_handle: '', telegram_username: '',
};

/** 새 상점 만들기 화면 */
export default function StoreForm() {
  const navigate = useNavigate();
  const { refresh } = useAuth();
  return (
    <div className="stack">
      <h1>상점 만들기</h1>
      <StoreSettingsForm submitLabel="만들기" onSubmit={async (body) => {
        const store = await api.post('/seller/stores', body);
        await refresh();
        navigate(`/seller/stores/${store.id}?tab=settings`);
      }} />
    </div>
  );
}

/**
 * 상점 설정 폼. 만들기(POST)와 설정 저장(PUT)에 같이 쓴다.
 * PUT 은 전체 교체라서 폼 전체를 보낸다. 빈 칸은 null 로 바꿔 보낸다(= 지우기).
 */
export function StoreSettingsForm({ store, onSubmit, submitLabel = '저장' }) {
  const [form, setForm] = useState(() => (store ? fromStore(store) : EMPTY));
  const [logoUrl, setLogoUrl] = useState(store?.logo?.thumbnail_url || null);
  const [bannerUrl, setBannerUrl] = useState(store?.banner?.thumbnail_url || null);
  const [error, setError] = useState(null);
  const [saved, setSaved] = useState(false);
  const [busy, setBusy] = useState(false);
  const markets = useLoad(() => api.get('/markets'), []);
  const set = (key) => (e) => { setForm({ ...form, [key]: e.target.value }); setSaved(false); };

  async function submit(e) {
    e.preventDefault();
    setBusy(true);
    setError(null);
    try {
      const body = Object.fromEntries(Object.entries(form).map(([k, v]) => [k, v === '' ? null : v]));
      body.market_id = body.market_id ? Number(body.market_id) : null;
      await onSubmit(body);
      setSaved(true);
    } catch (err) {
      setError(err);
    } finally {
      setBusy(false);
    }
  }

  return (
    <form className="stack" onSubmit={submit}>
      <div className="card stack">
        <h2 style={{ marginTop: 0 }}>기본 정보</h2>
        <Field label="상점 이름" error={fieldError(error, 'name')}>
          <input value={form.name} onChange={set('name')} required maxLength={80} />
        </Field>
        <Field label="상점 주소 (영문 소문자·숫자·하이픈, 비우면 자동)" error={fieldError(error, 'slug')}>
          <input value={form.slug} onChange={set('slug')} placeholder="farhod-dried-fruit" pattern="[a-z0-9][a-z0-9-]{1,58}[a-z0-9]" />
        </Field>
        <Field label="소개"><textarea value={form.description} onChange={set('description')} maxLength={2000} /></Field>
      </div>

      <div className="card stack">
        <h2 style={{ marginTop: 0 }}>화면 꾸미기</h2>
        {Object.keys(TEMPLATE_LABEL).map((t) => (
          <label key={t} className="check card">
            <input type="radio" name="template" value={t} checked={form.template === t} onChange={set('template')} />
            <span><strong>{TEMPLATE_LABEL[t]}</strong> <span className="muted small">— {TEMPLATE_HELP[t]}</span></span>
          </label>
        ))}
        <Field label="대표 색"><input type="color" value={form.theme_color || '#B45309'} onChange={set('theme_color')} /></Field>
        <div className="row">
          {logoUrl && <img src={logoUrl} alt="로고" className="thumb thumb-sm" />}
          <ImageUploadButton label={logoUrl ? '로고 바꾸기' : '로고 올리기'} onUploaded={(img) => {
            setForm((f) => ({ ...f, logo_image_id: img.id })); setLogoUrl(img.thumbnail_url);
          }} />
          {logoUrl && <button type="button" className="ghost" onClick={() => { setForm({ ...form, logo_image_id: null }); setLogoUrl(null); }}>로고 지우기</button>}
        </div>
        <div className="row">
          {bannerUrl && <img src={bannerUrl} alt="배너" className="thumb thumb-sm" />}
          <ImageUploadButton label={bannerUrl ? '배너 바꾸기' : '배너 올리기'} onUploaded={(img) => {
            setForm((f) => ({ ...f, banner_image_id: img.id })); setBannerUrl(img.thumbnail_url);
          }} />
          {bannerUrl && <button type="button" className="ghost" onClick={() => { setForm({ ...form, banner_image_id: null }); setBannerUrl(null); }}>배너 지우기</button>}
        </div>
      </div>

      <div className="card stack">
        <h2 style={{ marginTop: 0 }}>위치</h2>
        <Field label="시장">
          <select value={form.market_id || ''} onChange={set('market_id')}>
            <option value="">시장 밖 매장</option>
            {markets.data?.map((m) => <option key={m.id} value={m.id}>{m.name} ({m.city})</option>)}
          </select>
        </Field>
        {form.market_id
          ? <Field label="시장 안 위치 (줄·칸 번호)"><input value={form.location_detail} onChange={set('location_detail')} placeholder="3열 12번" /></Field>
          : <Field label="주소"><input value={form.address_text} onChange={set('address_text')} /></Field>}
        <Field label="랜드마크 (예: 중앙백화점 맞은편)"><input value={form.landmark} onChange={set('landmark')} /></Field>
      </div>

      <div className="card stack">
        <h2 style={{ marginTop: 0 }}>연락처 <span className="muted small">상점을 공개하려면 WhatsApp 또는 전화번호가 필요합니다</span></h2>
        <div className="grid-2">
          <Field label="WhatsApp" error={fieldError(error, 'whatsapp_phone')}>
            <input value={form.whatsapp_phone} onChange={set('whatsapp_phone')} placeholder="+992…" inputMode="tel" />
          </Field>
          <Field label="전화" error={fieldError(error, 'contact_phone')}>
            <input value={form.contact_phone} onChange={set('contact_phone')} placeholder="+992…" inputMode="tel" />
          </Field>
          <Field label="Instagram"><input value={form.instagram_handle} onChange={set('instagram_handle')} /></Field>
          <Field label="Telegram"><input value={form.telegram_username} onChange={set('telegram_username')} /></Field>
        </div>
      </div>

      <ErrorBox error={error} />
      {saved && <p className="alert ok">저장했습니다.</p>}
      <button className="primary" disabled={busy}>{submitLabel}</button>
    </form>
  );
}

function fromStore(s) {
  return {
    name: s.name, slug: s.slug, description: s.description || '', template: s.template,
    theme_color: s.theme_color || '#B45309', logo_image_id: s.logo?.id || null, banner_image_id: s.banner?.id || null,
    market_id: s.market?.id || '', location_detail: s.location_detail || '', address_text: s.address_text || '',
    landmark: s.landmark || '', contact_phone: s.contact_phone || '', whatsapp_phone: s.whatsapp_phone || '',
    instagram_handle: s.instagram_handle || '', telegram_username: s.telegram_username || '',
  };
}
