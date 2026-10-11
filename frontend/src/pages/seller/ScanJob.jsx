import { useEffect, useRef, useState } from 'react';
import { Link, useParams } from 'react-router-dom';
import { api } from '../../api.js';
import { CategorySelect, useCategories } from '../../components/seller.jsx';
import { ErrorBox, Field, Loading, StatusBadge, fieldError, useLoad } from '../../components/ui.jsx';
import { PRICE_TYPE_LABEL, SCAN_STATUS_LABEL, UNIT_LABEL, dateTime, money } from '../../format.js';

const POLL_MS = 2000;
const POLL_LIMIT_MS = 2 * 60 * 1000;

/**
 * ② 상품이 된다: 스캔 결과를 기다렸다가(폴링) 검수하고 한 번에 확정한다.
 * 2분이 지나도 안 끝나면 폴링을 멈추고 "검수 대기함에서 나중에 확인"으로 안내한다(작업은 계속된다).
 */
export default function ScanJob() {
  const { jobId } = useParams();
  const [job, setJob] = useState(null);
  const [error, setError] = useState(null);
  const [gaveUp, setGaveUp] = useState(false);
  const [round, setRound] = useState(0);   // 재시도하면 1 올려서 폴링을 처음부터 다시 시작한다
  const startedAt = useRef(Date.now());

  useEffect(() => {
    let timer;
    let stopped = false;
    startedAt.current = Date.now();
    setGaveUp(false);
    async function poll() {
      try {
        const data = await api.get(`/seller/scan-jobs/${jobId}`);
        if (stopped) return;
        setJob(data);
        setError(null);
        if (data.status === 'QUEUED' || data.status === 'RUNNING') {
          if (Date.now() - startedAt.current > POLL_LIMIT_MS) setGaveUp(true);
          else timer = setTimeout(poll, POLL_MS);
        }
      } catch (e) {
        if (!stopped) setError(e);
      }
    }
    poll();
    return () => { stopped = true; clearTimeout(timer); };
  }, [jobId, round]);

  async function retry() {
    try {
      setJob(await api.post(`/seller/scan-jobs/${jobId}/retry`));
      setRound((r) => r + 1);
    } catch (e) {
      setError(e);
    }
  }

  if (error && !job) return <ErrorBox error={error} />;
  if (!job) return <Loading />;
  const inProgress = job.status === 'QUEUED' || job.status === 'RUNNING';

  return (
    <div className="stack">
      {job.import_batch_id && <Link className="small" to={`/seller/import-batches/${job.import_batch_id}`}>← SNS 일괄 등록 #{job.import_batch_id}</Link>}
      <div className="row between">
        <h1 style={{ margin: 0 }}>스캔 #{job.id}</h1>
        <StatusBadge status={job.status} labels={SCAN_STATUS_LABEL} />
      </div>
      <div className="photos">{job.images.map((img) => <img key={img.id} src={img.thumbnail_url} alt="" />)}</div>
      {job.caption_text && <p className="muted small">캡션: {job.caption_text}</p>}
      <ErrorBox error={error} />

      {inProgress && (
        <div className="alert info">
          {job.status === 'QUEUED' && job.error
            ? `잠시 후 다시 시도합니다 (${job.error.message}) · ${dateTime(job.available_at)}`
            : 'AI가 사진을 분석하고 있어요. 보통 몇 초~수십 초 걸립니다.'}
          {gaveUp && <div>오래 걸리고 있어요. 이 화면을 닫아도 분석은 계속되고, 상점 관리의 “AI 스캔 → 검수 대기”에서 이어서 할 수 있어요.</div>}
        </div>
      )}
      {job.status === 'FAILED' && (
        <div className="alert">
          분석에 실패했습니다: {job.error?.message}
          <div style={{ marginTop: 8 }}><button onClick={retry}>다시 시도</button></div>
        </div>
      )}
      {job.image_notes.length > 0 && (
        <div className="alert info"><strong>AI 메모</strong><ul>{job.image_notes.map((n, i) => <li key={i}>{n}</li>)}</ul></div>
      )}
      {job.status === 'SUCCEEDED' && !job.reviewed_at && <ReviewForm key={job.id} job={job} onDone={setJob} />}
      {job.reviewed_at && <ReviewedSummary job={job} />}
    </div>
  );
}

/** 검수 폼 한 줄. AI 값을 미리 채우고, 판매 단위는 카테고리 기본 단위로 제안한다. */
function newRow(candidate) {
  return {
    key: candidate ? `c${candidate.id}` : `n${Date.now()}`,
    candidate,
    decision: 'ACCEPTED',
    name: candidate?.name || '',
    description: candidate?.description || '',
    category_id: candidate?.category.id || null,
    price: candidate?.suggested_price ?? '',
    price_type: 'FIXED',
    unit: candidate?.category.default_unit || 'PIECE',
    stock: '',
  };
}

/** 검수 폼: AI 후보마다 수락/거절 + 수정, AI가 놓친 상품 추가, 바로 발행 여부. */
function ReviewForm({ job, onDone }) {
  const categories = useCategories();
  const [rows, setRows] = useState(() => job.candidates.map((c) => newRow(c)));
  const [publish, setPublish] = useState(true);
  const [error, setError] = useState(null);
  const [busy, setBusy] = useState(false);
  const update = (key, changes) => setRows((list) => list.map((r) => (r.key === key ? { ...r, ...changes } : r)));

  async function submit(e) {
    e.preventDefault();
    setBusy(true);
    setError(null);
    try {
      const items = rows.map((r) => (r.decision === 'REJECTED'
        ? { candidate_id: r.candidate.id, decision: 'REJECTED' }
        : {
          candidate_id: r.candidate?.id ?? null,
          decision: 'ACCEPTED',
          product: {
            category_id: r.category_id,
            translations: [{ locale: job.output_locale, name: r.name.trim(), description: r.description.trim() || null }],
            price: r.price === '' ? null : Number(r.price),
            price_type: r.price_type,
            unit: r.unit,
            stock_quantity: r.stock === '' ? null : Number(r.stock),
          },
        }));
      await api.post(`/seller/scan-jobs/${job.id}/review`, { publish, items });
      onDone(await api.get(`/seller/scan-jobs/${job.id}`));
    } catch (err) {
      setError(err);
    } finally {
      setBusy(false);
    }
  }

  return (
    <form className="stack" onSubmit={submit}>
      <h2>검수</h2>
      <p className="muted small">AI가 만든 초안입니다. 맞는 상품은 고쳐서 수락하고, 아닌 것은 거절하세요.</p>
      {rows.length === 0 && <p className="muted">AI가 상품을 찾지 못했어요. 아래에서 직접 추가하거나, 상품이 없는 사진(가게 전경 등)이면 “상품 없음으로 확정”을 누르세요.</p>}
      {rows.map((r, i) => (
        <div key={r.key} className={`card stack candidate ${r.decision === 'REJECTED' ? 'rejected' : 'accepted'}`}>
          <div className="row between">
            <strong>{r.candidate ? `AI 후보 ${r.candidate.candidate_index + 1}` : '직접 추가'}</strong>
            {r.candidate ? (
              <div className="row">
                <button type="button" className={r.decision === 'ACCEPTED' ? 'primary' : ''}
                        onClick={() => update(r.key, { decision: 'ACCEPTED' })}>수락</button>
                <button type="button" className={r.decision === 'REJECTED' ? 'danger' : ''}
                        onClick={() => update(r.key, { decision: 'REJECTED' })}>거절</button>
              </div>
            ) : <button type="button" className="ghost danger" onClick={() => setRows(rows.filter((x) => x.key !== r.key))}>빼기</button>}
          </div>
          {r.candidate?.review_notes.length > 0 && (
            <ul className="muted small" style={{ margin: 0 }}>{r.candidate.review_notes.map((n, j) => <li key={j}>{n}</li>)}</ul>
          )}
          {r.candidate?.visible_text.length > 0 && <div className="muted small">사진 속 글자: {r.candidate.visible_text.join(', ')}</div>}
          {r.decision === 'ACCEPTED' && (
            <>
              <Field label="상품명" error={fieldError(error, `items[${i}].product.translations[0].name`)}>
                <input value={r.name} onChange={(e) => update(r.key, { name: e.target.value })} required maxLength={120} />
              </Field>
              <Field label="설명"><textarea value={r.description} onChange={(e) => update(r.key, { description: e.target.value })} /></Field>
              <Field label="카테고리" error={fieldError(error, `items[${i}].product.category_id`)}>
                <CategorySelect value={r.category_id} categories={categories.data}
                                onChange={(id, leaf) => update(r.key, { category_id: id, unit: leaf?.default_unit || r.unit })} />
              </Field>
              <div className="grid-2">
                <Field label={`가격 (TJS / ${UNIT_LABEL[r.unit]})`} error={fieldError(error, `items[${i}].product.price`)}>
                  <input type="number" inputMode="decimal" min="0" step="0.01" value={r.price}
                         onChange={(e) => update(r.key, { price: e.target.value })} />
                </Field>
                <Field label="가격 방식">
                  <select value={r.price_type} onChange={(e) => update(r.key, { price_type: e.target.value })}>
                    {Object.entries(PRICE_TYPE_LABEL).map(([v, l]) => <option key={v} value={v}>{l}</option>)}
                  </select>
                </Field>
                <Field label="판매 단위">
                  <select value={r.unit} onChange={(e) => update(r.key, { unit: e.target.value })}>
                    {Object.entries(UNIT_LABEL).map(([v, l]) => <option key={v} value={v}>{l}</option>)}
                  </select>
                </Field>
                <Field label="재고 (비우면 관리 안 함)">
                  <input type="number" inputMode="decimal" min="0" step="any" value={r.stock}
                         onChange={(e) => update(r.key, { stock: e.target.value })} />
                </Field>
              </div>
              {r.candidate?.suggested_price != null && <p className="muted small">가격 {money(r.candidate.suggested_price)}은 캡션에서 가져왔어요.</p>}
            </>
          )}
        </div>
      ))}
      <button type="button" onClick={() => setRows([...rows, newRow(null)])}>+ AI가 놓친 상품 추가</button>
      <label className="check">
        <input type="checkbox" checked={publish} onChange={(e) => setPublish(e.target.checked)} />
        <span>바로 판매 시작 (가격이 모두 있어야 해요. 끄면 초안으로 저장)</span>
      </label>
      <ErrorBox error={error} />
      <button className="primary" disabled={busy}>{busy ? '저장 중…' : rows.length === 0 ? '상품 없음으로 확정' : '검수 확정'}</button>
    </form>
  );
}

function ReviewedSummary({ job }) {
  const accepted = job.candidates.filter((c) => c.decision === 'ACCEPTED');
  return (
    <div className="card stack">
      <p className="alert ok">검수를 마쳤어요 ({dateTime(job.reviewed_at)}). 상품 {accepted.length}개를 만들었습니다.</p>
      <ul>
        {job.candidates.map((c) => (
          <li key={c.id}>
            {c.name} — {c.decision === 'ACCEPTED' ? '수락' : '거절'}{c.origin === 'SELLER' ? ' (직접 추가)' : ''}
            {c.product_id && <> · <Link to={`/seller/products/${c.product_id}/edit`}>상품 보기</Link></>}
          </li>
        ))}
      </ul>
      {job.import_batch_id ? <NextInBatch job={job} />
        : <Link className="button" to={`/seller/stores/${job.store_id}`}>상점 관리로</Link>}
    </div>
  );
}

/** 일괄 등록 게시물이면 다음 검수할 게시물로 바로 넘어갈 수 있게 한다(온보딩 시간 단축). */
function NextInBatch({ job }) {
  const batch = useLoad(() => api.get(`/seller/import-batches/${job.import_batch_id}`), [job.import_batch_id]);
  const next = batch.data?.jobs.find((j) => j.id !== job.id && j.status === 'SUCCEEDED' && !j.reviewed_at);
  return (
    <div className="stack">
      {next && <Link className="button primary block" to={`/seller/scan-jobs/${next.id}`}>다음 게시물 검수하기</Link>}
      <Link className="button block" to={`/seller/import-batches/${job.import_batch_id}`}>
        {batch.data?.completed_at ? '일괄 등록 결과 보기' : '일괄 등록 진행 보기'}
      </Link>
    </div>
  );
}
