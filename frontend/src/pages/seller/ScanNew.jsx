import { useState } from 'react';
import { useNavigate, useParams } from 'react-router-dom';
import { api } from '../../api.js';
import { ImageUploadButton } from '../../components/seller.jsx';
import { ErrorBox, Field } from '../../components/ui.jsx';

const MAX_PHOTOS = 3;
const LOCALES = { ko: '한국어', ru: '러시아어', tg: '타직어', en: '영어' };

/**
 * ① 촬영한다: 사진 1~3장(인스타 캐러셀이면 여러 장) + SNS 캡션.
 * 사진은 고르는 즉시 줄여서 올리고, "분석 시작"을 누르면 스캔 작업을 만든 뒤 결과 화면으로 간다.
 */
export default function ScanNew() {
  const { storeId } = useParams();
  const navigate = useNavigate();
  const [photos, setPhotos] = useState([]);   // [{ id, thumbnail_url, captured }]
  const [fromSns, setFromSns] = useState(false);
  const [caption, setCaption] = useState('');
  const [locale, setLocale] = useState('ko');
  const [error, setError] = useState(null);
  const [busy, setBusy] = useState(false);
  const remaining = MAX_PHOTOS - photos.length;

  const addPhoto = (captured) => (img) => setPhotos((list) =>
    list.some((p) => p.id === img.id) || list.length >= MAX_PHOTOS ? list : [...list, { ...img, captured }]);

  async function start() {
    setBusy(true);
    setError(null);
    try {
      const job = await api.post(`/seller/stores/${storeId}/scan-jobs`, {
        image_ids: photos.map((p) => p.id),
        source_type: fromSns ? 'SNS_IMPORT' : photos.some((p) => p.captured) ? 'CAMERA' : 'UPLOAD',
        caption_text: fromSns && caption.trim() ? caption.trim() : null,
        output_locale: locale,
      });
      navigate(`/seller/scan-jobs/${job.id}`, { replace: true });
    } catch (e) {
      setError(e);
      setBusy(false);
    }
  }

  return (
    <div className="stack">
      <h1>사진으로 상품 등록</h1>
      <p className="muted">상품이 잘 보이게 찍어 주세요. 진열대처럼 여러 상품이 한 장에 있어도 AI가 하나씩 나눠 줍니다.</p>

      <div className="card stack">
        <div className="photos">
          {photos.map((p) => (
            <div key={p.id} style={{ position: 'relative' }}>
              <img src={p.thumbnail_url} alt="" />
              <button className="ghost" style={{ position: 'absolute', top: 2, right: 2, background: '#fff', padding: '2px 8px' }}
                      onClick={() => setPhotos(photos.filter((x) => x.id !== p.id))} aria-label="사진 빼기">✕</button>
            </div>
          ))}
        </div>
        {remaining > 0 ? (
          <div className="row">
            <ImageUploadButton label="📷 사진 찍기" capture onUploaded={addPhoto(true)} max={1} />
            <ImageUploadButton label="갤러리에서 고르기" multiple max={remaining} onUploaded={addPhoto(false)} />
          </div>
        ) : <p className="muted small">사진은 {MAX_PHOTOS}장까지입니다.</p>}
      </div>

      <div className="card stack">
        <label className="check">
          <input type="checkbox" checked={fromSns} onChange={(e) => setFromSns(e.target.checked)} />
          <span>인스타그램 등 SNS에 올렸던 게시물이에요 (캡션도 함께 분석)</span>
        </label>
        {fromSns && (
          <Field label="게시물 캡션 (가격이 적혀 있으면 AI가 가격 칸에 채워 줍니다)">
            <textarea value={caption} onChange={(e) => setCaption(e.target.value)} maxLength={2000}
                      placeholder="Курага 45 сомони/кг. Доставка по Душанбе." />
          </Field>
        )}
        <Field label="AI가 쓸 언어">
          <select value={locale} onChange={(e) => setLocale(e.target.value)}>
            {Object.entries(LOCALES).map(([v, l]) => <option key={v} value={v}>{l}</option>)}
          </select>
        </Field>
      </div>

      <ErrorBox error={error} />
      <button className="primary block" disabled={photos.length === 0 || busy} onClick={start}>
        {busy ? '요청 중…' : `AI 분석 시작 (사진 ${photos.length}장)`}
      </button>
    </div>
  );
}
