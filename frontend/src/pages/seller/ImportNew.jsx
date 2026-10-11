import { useRef, useState } from 'react';
import { useNavigate, useParams } from 'react-router-dom';
import { api } from '../../api.js';
import { ErrorBox, Field } from '../../components/ui.jsx';
import { resizeForUpload } from '../../imageResize.js';

const MAX_POSTS = 20;           // 서버 ImportDtos.MAX_ITEMS 와 같다(2차 지표 "이미지 20장 기준")
const MAX_PHOTOS_PER_POST = 3;  // 단건 스캔과 같은 규칙(인스타 캐러셀)
const LOCALES = { ko: '한국어', ru: '러시아어', tg: '타직어', en: '영어' };

/**
 * SNS 사진 일괄 등록(2차 회의 추가 기능). 저장해 둔 SNS 게시물 사진을 한꺼번에 올리면 게시물마다 AI가 초안을 만든다.
 * 사진을 처음 고르는 순간 서버에 묶음을 만든다 → 그 시각부터 "상점 온보딩 소요시간"을 잰다.
 * 사진 한 장 = 게시물 하나로 시작하고, 여러 장짜리 게시물(캐러셀)은 "앞 게시물과 합치기"로 묶는다.
 */
export default function ImportNew() {
  const { storeId } = useParams();
  const navigate = useNavigate();
  const input = useRef(null);
  const [batchId, setBatchId] = useState(null);
  const [posts, setPosts] = useState([]);   // [{ key, photos: [{ id, thumbnail_url }], caption }]
  const [uploading, setUploading] = useState(null);   // { done, total }
  const [notice, setNotice] = useState(null);
  const [locale, setLocale] = useState('ko');
  const [error, setError] = useState(null);
  const [busy, setBusy] = useState(false);

  async function onFiles(e) {
    const picked = Array.from(e.target.files || []);
    e.target.value = '';
    if (picked.length === 0) return;
    const room = MAX_POSTS - posts.length;
    const files = picked.slice(0, room);
    const messages = [];
    if (picked.length > room) messages.push(`게시물은 ${MAX_POSTS}개까지예요. ${picked.length - room}장은 빠졌어요.`);
    setError(null);
    setNotice(null);
    let batch = batchId;
    try {
      if (batch === null) {
        batch = (await api.post(`/seller/stores/${storeId}/import-batches`)).id;
        setBatchId(batch);
      }
    } catch (err) {
      setError(err);
      return;
    }
    let failed = 0;
    let duplicates = 0;
    const known = new Set(posts.flatMap((p) => p.photos.map((x) => x.id)));
    setUploading({ done: 0, total: files.length });
    for (const [i, file] of files.entries()) {
      try {
        const img = await api.upload(await resizeForUpload(file));
        if (known.has(img.id)) {
          duplicates += 1;   // 같은 사진을 두 번 고른 경우(서버가 같은 사진 id를 돌려준다)
        } else {
          known.add(img.id);
          setPosts((list) => [...list,
            { key: `p${img.id}`, photos: [{ id: img.id, thumbnail_url: img.thumbnail_url }], caption: '' }]);
        }
      } catch {
        failed += 1;
      }
      setUploading({ done: i + 1, total: files.length });
    }
    setUploading(null);
    if (duplicates > 0) messages.push(`이미 고른 사진 ${duplicates}장은 한 번만 넣었어요.`);
    if (failed > 0) messages.push(`${failed}장은 올리지 못했어요. 다시 골라 주세요.`);
    setNotice(messages.length ? messages.join(' ') : null);
  }

  const update = (key, changes) => setPosts((list) => list.map((p) => (p.key === key ? { ...p, ...changes } : p)));

  function mergeUp(index) {
    setPosts((list) => {
      const prev = list[index - 1];
      const cur = list[index];
      const caption = [prev.caption, cur.caption].filter((c) => c.trim()).join('\n');
      const merged = { ...prev, photos: [...prev.photos, ...cur.photos], caption };
      return [...list.slice(0, index - 1), merged, ...list.slice(index + 1)];
    });
  }

  function split(index) {
    setPosts((list) => {
      const [first, ...rest] = list[index].photos;
      const parts = [{ ...list[index], photos: [first] },
        ...rest.map((x) => ({ key: `p${x.id}`, photos: [x], caption: '' }))];
      return [...list.slice(0, index), ...parts, ...list.slice(index + 1)];
    });
  }

  async function submit() {
    setBusy(true);
    setError(null);
    try {
      await api.post(`/seller/import-batches/${batchId}/jobs`, {
        items: posts.map((p) => ({ image_ids: p.photos.map((x) => x.id), caption_text: p.caption.trim() || null })),
        output_locale: locale,
      });
      navigate(`/seller/import-batches/${batchId}`, { replace: true });
    } catch (err) {
      if (err.code === 'IMPORT_BATCH_ALREADY_SUBMITTED') {
        navigate(`/seller/import-batches/${batchId}`, { replace: true });
        return;
      }
      setError(err);
      setBusy(false);
    }
  }

  const photoCount = posts.reduce((n, p) => n + p.photos.length, 0);

  return (
    <div className="stack">
      <h1>SNS 사진 한꺼번에 등록</h1>
      <p className="muted">
        인스타그램·텔레그램에 올렸던 상품 사진을 한 번에 골라 주세요. 게시물마다 AI가 상품 초안을 만들고,
        분석이 끝난 것부터 차례로 검수하면 됩니다. 한 번에 게시물 {MAX_POSTS}개까지.
      </p>

      <input ref={input} type="file" accept="image/jpeg,image/png,image/webp" multiple hidden onChange={onFiles} />
      <button type="button" className="block" disabled={uploading !== null || posts.length >= MAX_POSTS}
              onClick={() => input.current.click()}>
        {uploading ? `올리는 중… ${uploading.done} / ${uploading.total}`
          : posts.length === 0 ? '사진 고르기 (여러 장)' : `사진 더 고르기 (${MAX_POSTS - posts.length}개 남음)`}
      </button>
      {notice && <p className="alert info">{notice}</p>}

      {posts.length > 0 && (
        <p className="muted small">사진 한 장이 게시물 하나예요. 여러 장짜리 게시물이면 "앞 게시물과 합치기"를 누르세요(최대 {MAX_PHOTOS_PER_POST}장).</p>
      )}
      <div className="list">
        {posts.map((p, i) => {
          const canMerge = i > 0 && posts[i - 1].photos.length + p.photos.length <= MAX_PHOTOS_PER_POST;
          return (
            <div key={p.key} className="card stack">
              <div className="row between">
                <strong>게시물 {i + 1}</strong>
                <div className="row">
                  {canMerge && <button type="button" onClick={() => mergeUp(i)}>↑ 앞 게시물과 합치기</button>}
                  {p.photos.length > 1 && <button type="button" onClick={() => split(i)}>따로 나누기</button>}
                  <button type="button" className="ghost danger" onClick={() => setPosts(posts.filter((x) => x.key !== p.key))}>빼기</button>
                </div>
              </div>
              <div className="photos">{p.photos.map((x) => <img key={x.id} src={x.thumbnail_url} alt="" />)}</div>
              <Field label="게시물 글 (선택, 가격이 적혀 있으면 AI가 가격 칸에 채워 줍니다)">
                <textarea value={p.caption} maxLength={2000} rows={2} placeholder="Курага 45 сомони/кг"
                          onChange={(e) => update(p.key, { caption: e.target.value })} />
              </Field>
            </div>
          );
        })}
      </div>

      {posts.length > 0 && (
        <div className="card">
          <Field label="AI가 쓸 언어">
            <select value={locale} onChange={(e) => setLocale(e.target.value)}>
              {Object.entries(LOCALES).map(([v, l]) => <option key={v} value={v}>{l}</option>)}
            </select>
          </Field>
        </div>
      )}
      <ErrorBox error={error} />
      <button className="primary block" disabled={posts.length === 0 || uploading !== null || busy} onClick={submit}>
        {busy ? '요청 중…' : `AI 분석 시작 (게시물 ${posts.length}개 · 사진 ${photoCount}장)`}
      </button>
    </div>
  );
}
