import React, { useEffect, useRef, useState } from 'react';

const MAX_BYTES = 5 * 1024 * 1024;
const TYPES = ['image/jpeg', 'image/png', 'image/webp'];
const CATEGORY_LABELS = {
  clothing: '의류', accessories: '액세서리', handicrafts: '수공예품',
  food: '식품', household: '생활용품', other: '기타',
};

export default function App() {
  const videoRef = useRef(null);
  const streamRef = useRef(null);
  const fileInputRef = useRef(null);
  const manualIdRef = useRef(0);
  const [cameraOpen, setCameraOpen] = useState(false);
  const [photo, setPhoto] = useState(null);
  const [preview, setPreview] = useState('');
  const [result, setResult] = useState(null);
  const [selectedIndices, setSelectedIndices] = useState([]);
  const [drafts, setDrafts] = useState({});
  const [manualDrafts, setManualDrafts] = useState([]);
  const [recordLookup, setRecordLookup] = useState('');
  const [recordImageUrl, setRecordImageUrl] = useState('');
  const [legacySelection, setLegacySelection] = useState(null);
  const [busy, setBusy] = useState(false);
  const [reviewSaving, setReviewSaving] = useState(false);
  const [reviewSaved, setReviewSaved] = useState(false);
  const [error, setError] = useState('');

  useEffect(() => {
    if (!photo) { setPreview(''); return; }
    const url = URL.createObjectURL(photo);
    setPreview(url);
    return () => URL.revokeObjectURL(url);
  }, [photo]);

  useEffect(() => () => {
    streamRef.current?.getTracks().forEach((track) => track.stop());
  }, []);

  function stopCamera() {
    streamRef.current?.getTracks().forEach((track) => track.stop());
    streamRef.current = null;
    if (videoRef.current) videoRef.current.srcObject = null;
    setCameraOpen(false);
  }

  function selectPhoto(file) {
    if (!file) return;
    if (!TYPES.includes(file.type)) { setError('JPEG, PNG, WEBP 사진을 선택해 주세요.'); return; }
    if (file.size > MAX_BYTES) { setError('사진은 5MB 이하로 선택해 주세요.'); return; }
    stopCamera();
    setPhoto(file);
    setResult(null);
    setRecordImageUrl('');
    setLegacySelection(null);
    setSelectedIndices([]);
    setDrafts({});
    setManualDrafts([]);
    setReviewSaved(false);
    setError('');
  }

  async function openCamera() {
    setError('');
    if (!navigator.mediaDevices?.getUserMedia) {
      setError('카메라를 사용할 수 없습니다. localhost 또는 HTTPS에서 열고 권한을 허용해 주세요.');
      return;
    }
    try {
      stopCamera();
      const stream = await navigator.mediaDevices.getUserMedia({
        audio: false, video: { facingMode: { ideal: 'environment' } },
      });
      streamRef.current = stream;
      videoRef.current.srcObject = stream;
      setCameraOpen(true);
      await videoRef.current.play();
    } catch {
      stopCamera();
      setError('카메라를 열지 못했습니다. 브라우저 권한을 확인하거나 파일을 선택해 주세요.');
    }
  }

  function takePhoto() {
    const video = videoRef.current;
    if (!video?.videoWidth) { setError('카메라 영상이 준비될 때까지 기다려 주세요.'); return; }
    const scale = Math.min(1, 1600 / Math.max(video.videoWidth, video.videoHeight));
    const canvas = document.createElement('canvas');
    canvas.width = Math.round(video.videoWidth * scale);
    canvas.height = Math.round(video.videoHeight * scale);
    canvas.getContext('2d').drawImage(video, 0, 0, canvas.width, canvas.height);
    canvas.toBlob((blob) => {
      if (!blob) { setError('사진을 만들지 못했습니다.'); return; }
      selectPhoto(new File([blob], 'product.jpg', { type: 'image/jpeg' }));
    }, 'image/jpeg', 0.9);
  }

  async function analyze() {
    if (!photo || busy) return;
    setBusy(true);
    setError('');
    setResult(null);
    setRecordImageUrl('');
    setLegacySelection(null);
    setSelectedIndices([]);
    setDrafts({});
    setManualDrafts([]);
    setReviewSaved(false);
    try {
      const form = new FormData();
      form.append('file', photo, photo.name);
      const response = await fetch('/api/analyze', { method: 'POST', body: form });
      const body = await response.json();
      if (!response.ok) throw new Error(body.error || body.detail || '분석 중 오류가 발생했습니다.');
      setResult(body);
    } catch (cause) {
      setError(cause.message || 'Spring Boot 서버 연결을 확인해 주세요.');
    } finally {
      setBusy(false);
    }
  }

  async function loadRecord() {
    const id = Number(recordLookup);
    if (!Number.isSafeInteger(id) || id <= 0) { setError('양의 정수 기록 번호를 입력해 주세요.'); return; }
    setError('');
    try {
      const response = await fetch(`/api/scan-records/${id}`);
      const record = await response.json();
      if (!response.ok) throw new Error(record.error || '기록을 불러오지 못했습니다.');
      setPhoto(null);
      setResult({ ...record.rawScan, record_id: record.id });
      setRecordImageUrl(`/api/scan-records/${id}/image`);
      setLegacySelection(record.selectedIndex == null ? null : record.selectedIndex);
      const reviewed = Array.isArray(record.reviewed_products) ? record.reviewed_products : [];
      const matched = reviewed.filter((item) => Number.isInteger(item.candidate_index)
        && item.candidate_index >= 0 && item.candidate_index < record.rawScan.candidates.length);
      setSelectedIndices(matched.map((item) => item.candidate_index));
      setDrafts(Object.fromEntries(matched.map((item) => [item.candidate_index, {
        ...item.product,
        price_amount: item.product.price_amount ?? '',
        stock_quantity: item.product.stock_quantity ?? '',
      }])));
      setManualDrafts(reviewed.filter((item) => item.candidate_index === null).map((item) => ({
        key: ++manualIdRef.current,
        product: { ...item.product,
          price_amount: item.product.price_amount ?? '',
          stock_quantity: item.product.stock_quantity ?? '',
        },
      })));
      setReviewSaved(false);
    } catch (cause) {
      setError(cause.message || '저장된 기록을 확인해 주세요.');
    }
  }

  function toggleCandidate(index) {
    const candidate = result.candidates[index];
    setSelectedIndices((current) => current.includes(index)
      ? current.filter((value) => value !== index)
      : [...current, index].sort((a, b) => a - b));
    setDrafts((current) => current[index] ? current : {
      ...current,
      [index]: { ...candidate, price_amount: '', stock_quantity: '' },
    });
    setReviewSaved(false);
  }

  function updateDraft(index, field, value) {
    setDrafts((current) => ({
      ...current,
      [index]: { ...current[index], [field]: value },
    }));
    setReviewSaved(false);
  }

  function addManualProduct() {
    setManualDrafts((current) => [...current, {
      key: ++manualIdRef.current,
      product: { name: '', category: 'other', description: '', visible_text: [],
        review_notes: [], price_amount: '', stock_quantity: '' },
    }]);
    setReviewSaved(false);
  }

  function updateManualProduct(key, field, value) {
    setManualDrafts((current) => current.map((item) => item.key === key
      ? { ...item, product: { ...item.product, [field]: value } } : item));
    setReviewSaved(false);
  }

  function reviewedPayload() {
    if (!result) return null;
    const reviewed = [];
    const append = (index, draft) => {
      if (!draft?.name?.trim()) { setError('상품의 이름을 입력해 주세요.'); return false; }
      const price = draft.price_amount === '' || draft.price_amount == null ? null : Number(draft.price_amount);
      const stock = draft.stock_quantity === '' || draft.stock_quantity == null ? null : Number(draft.stock_quantity);
      if ((price !== null && (!Number.isFinite(price) || price < 0)) ||
          (stock !== null && (!Number.isSafeInteger(stock) || stock < 0 || stock > 2147483647))) {
        setError('가격은 0 이상의 숫자, 재고는 0 이상의 정수로 입력해 주세요.');
        return false;
      }
      reviewed.push({ candidate_index: index, product: {
        ...draft,
        price_amount: price,
        stock_quantity: stock,
      } });
      return true;
    };
    for (const index of selectedIndices) {
      if (!append(index, drafts[index])) return null;
    }
    for (const item of manualDrafts) if (!append(null, item.product)) return null;
    if (!reviewed.length) { setError('상품 후보를 선택하거나 직접 추가해 주세요.'); return null; }
    setError('');
    return { scan: result, reviewed_products: reviewed };
  }

  async function saveReview() {
    if (!result?.record_id || reviewSaving) return;
    const payload = reviewedPayload();
    if (!payload) return;
    setReviewSaving(true);
    setReviewSaved(false);
    try {
      const response = await fetch(`/api/scan-records/${result.record_id}/reviews`, {
        method: 'PUT', headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ reviewed_products: payload.reviewed_products }),
      });
      const body = await response.json();
      if (!response.ok) throw new Error(body.error || '검수 결과를 저장하지 못했습니다.');
      setReviewSaved(true);
      setLegacySelection(null);
    } catch (cause) {
      setError(cause.message || 'Spring Boot와 PostgreSQL 연결을 확인해 주세요.');
    } finally {
      setReviewSaving(false);
    }
  }

  function downloadJson() {
    const payload = reviewedPayload();
    if (!payload) return;
    const blob = new Blob([JSON.stringify(payload, null, 2)], { type: 'application/json' });
    const url = URL.createObjectURL(blob);
    const link = document.createElement('a');
    link.href = url;
    link.download = 'bozor-gemini-product-draft.json';
    link.click();
    setTimeout(() => URL.revokeObjectURL(url), 0);
  }

  return (
    <main className="layout">
      <header className="header">
        <span className="eyebrow">BOZOR · CAPSTONE EXPERIMENT</span>
        <h1>Gemini로 상품 사진 스캔</h1>
        <p>사진을 분석해 상품명·카테고리·설명과 읽히는 문자의 후보를 JSON으로 만듭니다.</p>
      </header>

      <section className="grid">
        <div className="panel">
          <h2>1. 사진 준비</h2>
          <p className="muted">상품이 선명하게 보이는 사진을 촬영하거나 선택해 주세요. 최대 5MB.</p>
          <div className="actions">
            <button type="button" className="button secondary" onClick={openCamera}>카메라 열기</button>
            <button type="button" className="button secondary" onClick={() => fileInputRef.current?.click()}>사진 파일 선택</button>
            <input ref={fileInputRef} type="file" accept="image/jpeg,image/png,image/webp" hidden
              onChange={(event) => { selectPhoto(event.target.files?.[0]); event.target.value = ''; }} />
          </div>
          <video ref={videoRef} className="photo" style={{ display: cameraOpen ? 'block' : 'none' }}
            autoPlay muted playsInline aria-label="카메라 미리보기" />
          {cameraOpen && <div className="actions">
            <button type="button" className="button" onClick={takePhoto}>촬영하기</button>
            <button type="button" className="button ghost" onClick={stopCamera}>카메라 닫기</button>
          </div>}
          {preview ? <img className="photo" src={preview} alt="분석할 상품 사진" />
            : !cameraOpen && <div className="empty">선택한 사진이 여기에 표시됩니다.</div>}
          {photo && <p className="muted">선택: {photo.name} · {(photo.size / 1024 / 1024).toFixed(2)} MB</p>}
          <button type="button" className="button primary" disabled={!photo || busy} onClick={analyze}>
            {busy ? 'Gemini가 사진을 분석하고 있습니다…' : '2. 상품 사진 스캔'}
          </button>
          <div className="record-lookup">
            <label>기존 스캔 다시 검수 (record_id)
              <input type="number" min="1" step="1" value={recordLookup}
                onChange={(e) => setRecordLookup(e.target.value)} />
            </label>
            <button type="button" className="button secondary" onClick={loadRecord}>기록 불러오기</button>
          </div>
          {!preview && recordImageUrl && <img className="photo" src={recordImageUrl} alt="저장된 스캔 원본" />}
          {error && <p className="error" role="alert">{error}</p>}
        </div>

        <div className="panel">
          <div className="result-head">
            <h2>3. 상품 후보 확인</h2>
            {result && <button type="button" className="button ghost" onClick={downloadJson}>JSON 저장</button>}
          </div>
          {result ? <>
            <div className="note">Gemini 분석 · {result.candidates.length}개 후보 · {result.elapsed_ms} ms
              {result.record_id && <> · PostgreSQL 기록 #{result.record_id}</>}</div>
            <p className="muted">독립적으로 판매할 상품을 모두 선택하세요. 반사된 촬영 장비 등은 선택하지 마세요.</p>
            {legacySelection !== null && <p className="error">이전 단일 선택 기록: 후보 #{legacySelection + 1}. 새 다중 검수에는 자동 포함하지 않았습니다.</p>}
            {result.review_notes?.map((note, index) => <p className="muted" key={index}>• {note}</p>)}
            {result.candidates.length === 0 ? <div className="empty">상품 후보를 찾지 못했습니다. 다른 사진을 시도해 주세요.</div>
              : <div className="candidates">
                {result.candidates.map((candidate, index) => <button type="button" key={index}
                  aria-pressed={selectedIndices.includes(index)}
                  className={`candidate ${selectedIndices.includes(index) ? 'selected' : ''}`}
                  onClick={() => toggleCandidate(index)}>
                  <strong>{selectedIndices.includes(index) ? '☑' : '□'} {candidate.name}</strong>
                  <span>{CATEGORY_LABELS[candidate.category] || candidate.category}</span>
                  <small>{candidate.description || '설명 확인 필요'}</small>
                </button>)}
              </div>}
            <button type="button" className="button secondary" onClick={addManualProduct}>누락 상품 직접 추가</button>
            {(selectedIndices.length > 0 || manualDrafts.length > 0) && <div className="review">
              <h2>4. 판매자 검수 초안 ({selectedIndices.length + manualDrafts.length}개 상품)</h2>
              <p className="muted">사진에 담긴 상품을 각각 수정하고 함께 확정하세요. 원본 스캔은 그대로 보관됩니다.</p>
              {selectedIndices.map((index) => {
                const draft = drafts[index];
                if (!draft) return null;
                return <div className="review-item" key={index}>
                  <h3>후보 #{index + 1} · {result.candidates[index].name}</h3>
                  <label>상품명<input value={draft.name} onChange={(e) => updateDraft(index, 'name', e.target.value)} /></label>
                  <label>카테고리<select value={draft.category} onChange={(e) => updateDraft(index, 'category', e.target.value)}>
                    {Object.entries(CATEGORY_LABELS).map(([key, value]) => <option key={key} value={key}>{value}</option>)}
                  </select></label>
                  <label>설명<textarea rows="3" value={draft.description} onChange={(e) => updateDraft(index, 'description', e.target.value)} /></label>
                  <div className="inputs-row">
                    <label>가격 (판매자 입력)<input type="number" min="0" step="0.01" value={draft.price_amount}
                      onChange={(e) => updateDraft(index, 'price_amount', e.target.value)} /></label>
                    <label>재고 (판매자 입력)<input type="number" min="0" step="1" value={draft.stock_quantity}
                      onChange={(e) => updateDraft(index, 'stock_quantity', e.target.value)} /></label>
                  </div>
                  <label>상품에 쓰인 글자 (한 줄에 하나씩)<textarea rows="3"
                    value={(draft.visible_text || []).join('\n')}
                    onChange={(e) => updateDraft(index, 'visible_text', e.target.value.split('\n').map((value) => value.trim()).filter(Boolean))} /></label>
                  {draft.review_notes?.map((note, noteIndex) => <p className="muted" key={noteIndex}>• {note}</p>)}
                </div>;
              })}
              {manualDrafts.map(({ key, product: draft }) => <div className="review-item" key={key}>
                <h3>직접 추가한 상품 <button type="button" className="button ghost"
                  onClick={() => { setManualDrafts((current) => current.filter((item) => item.key !== key)); setReviewSaved(false); }}>삭제</button></h3>
                <label>상품명<input value={draft.name} onChange={(e) => updateManualProduct(key, 'name', e.target.value)} /></label>
                <label>카테고리<select value={draft.category} onChange={(e) => updateManualProduct(key, 'category', e.target.value)}>
                  {Object.entries(CATEGORY_LABELS).map(([category, value]) => <option key={category} value={category}>{value}</option>)}
                </select></label>
                <label>설명<textarea rows="3" value={draft.description} onChange={(e) => updateManualProduct(key, 'description', e.target.value)} /></label>
                <div className="inputs-row">
                  <label>가격 (판매자 입력)<input type="number" min="0" step="0.01" value={draft.price_amount}
                    onChange={(e) => updateManualProduct(key, 'price_amount', e.target.value)} /></label>
                  <label>재고 (판매자 입력)<input type="number" min="0" step="1" value={draft.stock_quantity}
                    onChange={(e) => updateManualProduct(key, 'stock_quantity', e.target.value)} /></label>
                </div>
                <label>상품에 쓰인 글자 (한 줄에 하나씩)<textarea rows="3"
                  value={(draft.visible_text || []).join('\n')}
                  onChange={(e) => updateManualProduct(key, 'visible_text', e.target.value.split('\n').map((value) => value.trim()).filter(Boolean))} /></label>
              </div>)}
              <button type="button" className="button" disabled={reviewSaving || !result.record_id}
                onClick={saveReview}>{reviewSaving ? '저장 중…' : `${selectedIndices.length + manualDrafts.length}개 상품 검수 확정 저장`}</button>
              {reviewSaved && <p className="note">기록 #{result.record_id}에 {selectedIndices.length + manualDrafts.length}개 상품의 검수 결과를 저장했습니다.</p>}
            </div>}
            <details><summary>Gemini 원본 JSON 보기</summary><pre>{JSON.stringify(result, null, 2)}</pre></details>
          </> : <div className="empty">사진을 분석하면 상품 후보와 JSON이 여기에 표시됩니다.</div>}
          <p className="muted footnote">AI가 생성한 이름·설명·문자는 틀릴 수 있습니다. 가격·재고는 Gemini가 채우지 않습니다.</p>
        </div>
      </section>
    </main>
  );
}
