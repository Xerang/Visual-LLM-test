import { useEffect, useRef, useState } from 'react';
import { Link, useParams } from 'react-router-dom';
import { api } from '../../api.js';
import { ErrorBox, Loading, StatusBadge, Thumb } from '../../components/ui.jsx';
import { SCAN_STATUS_LABEL, dateTime, duration } from '../../format.js';

const POLL_MS = 2000;
const POLL_LIMIT_MS = 15 * 60 * 1000;   // 게시물 20개는 AI만 몇 분 걸릴 수 있어서 단건(2분)보다 길게 기다린다

/** 일괄 등록 진행 화면: 게시물별 분석 상태를 보고, 끝난 것부터 차례로 검수한다. */
export default function ImportBatch() {
  const { batchId } = useParams();
  const [batch, setBatch] = useState(null);
  const [error, setError] = useState(null);
  const [gaveUp, setGaveUp] = useState(false);
  const [round, setRound] = useState(0);   // 재시도하면 폴링을 다시 시작한다
  const startedAt = useRef(Date.now());

  useEffect(() => {
    let timer;
    let stopped = false;
    startedAt.current = Date.now();
    setGaveUp(false);
    async function poll() {
      try {
        const data = await api.get(`/seller/import-batches/${batchId}`);
        if (stopped) return;
        setBatch(data);
        setError(null);
        if (data.progress.in_progress > 0) {
          if (Date.now() - startedAt.current > POLL_LIMIT_MS) setGaveUp(true);
          else timer = setTimeout(poll, POLL_MS);
        }
      } catch (e) {
        if (!stopped) setError(e);
      }
    }
    poll();
    return () => { stopped = true; clearTimeout(timer); };
  }, [batchId, round]);

  async function retry(jobId) {
    try {
      await api.post(`/seller/scan-jobs/${jobId}/retry`);
      setRound((r) => r + 1);
    } catch (e) {
      setError(e);
    }
  }

  if (error && !batch) return <ErrorBox error={error} />;
  if (!batch) return <Loading />;
  const { progress } = batch;
  const next = batch.jobs.find((j) => j.status === 'SUCCEEDED' && !j.reviewed_at);

  return (
    <div className="stack">
      <h1>SNS 일괄 등록 #{batch.id}</h1>
      <p className="muted small">게시물 {progress.posts}개 · 사진 {progress.images}장 · 시작 {dateTime(batch.created_at)}</p>
      <ErrorBox error={error} />

      {batch.completed_at ? (
        <div className="alert ok">
          모든 게시물을 검수했어요. 걸린 시간 {duration(batch.onboarding_seconds)} (사진 고르기부터 마지막 검수까지)
        </div>
      ) : (
        <div className="card stack">
          <strong>검수 완료 {progress.reviewed} / {progress.posts}</strong>
          <div className="muted small">
            분석 중 {progress.in_progress} · 검수 대기 {batch.jobs.filter((j) => j.status === 'SUCCEEDED' && !j.reviewed_at).length}
            {progress.failed > 0 && <> · <span style={{ color: 'var(--danger)' }}>실패 {progress.failed}</span></>}
          </div>
          {next
            ? <Link className="button primary block" to={`/seller/scan-jobs/${next.id}`}>다음 게시물 검수하기</Link>
            : progress.in_progress > 0 && <p className="muted small">AI가 분석하고 있어요. 끝나는 대로 여기에 검수 버튼이 생겨요.</p>}
          {gaveUp && <p className="muted small">오래 걸리고 있어요. 이 화면을 닫아도 분석은 계속되고, 상점 관리의 "AI 스캔"에서 이어서 할 수 있어요.</p>}
        </div>
      )}

      <div className="list">
        {batch.jobs.map((j, i) => (
          <div key={j.id} className="card row">
            <Thumb src={j.images[0]?.thumbnail_url} className="thumb-sm" />
            <div className="grow">
              <div className="row">
                <strong>게시물 {i + 1}</strong>
                {j.reviewed_at ? <span className="badge ok">검수 완료</span> : <StatusBadge status={j.status} labels={SCAN_STATUS_LABEL} />}
              </div>
              <div className="muted small">
                사진 {j.images.length}장{j.caption_text ? ` · ${j.caption_text.slice(0, 40)}` : ''}
                {j.status === 'QUEUED' && j.error ? ` · 잠시 후 다시 시도 (${j.error.message})` : ''}
              </div>
            </div>
            {j.status === 'FAILED' && <button onClick={() => retry(j.id)}>다시 시도</button>}
            {j.status === 'SUCCEEDED' && (
              <Link className="button" to={`/seller/scan-jobs/${j.id}`}>{j.reviewed_at ? '보기' : '검수'}</Link>
            )}
          </div>
        ))}
      </div>
      <Link className="button block" to={`/seller/stores/${batch.store_id}`}>상점 관리로</Link>
    </div>
  );
}
