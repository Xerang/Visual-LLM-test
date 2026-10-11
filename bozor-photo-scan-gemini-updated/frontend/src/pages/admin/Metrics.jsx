import { useState } from 'react';
import { api } from '../../api.js';
import { ErrorBox, Field, Loading, useLoad } from '../../components/ui.jsx';

/**
 * 2차 회의 검증 지표. 주간 보고서에 숫자를 그대로 옮겨 쓸 수 있게 목표와 함께 보여준다.
 * 프롬프트 버전으로 걸러서 v2 ↔ v3 처럼 전후 비교를 할 수 있다.
 */
export default function Metrics() {
  const [filter, setFilter] = useState({ from: '', to: '', prompt_version: '' });
  const [applied, setApplied] = useState(filter);
  const metrics = useLoad(() => api.get('/admin/metrics/scan', applied), [applied]);
  const set = (key) => (e) => setFilter({ ...filter, [key]: e.target.value });
  const m = metrics.data;

  return (
    <div className="stack">
      <h1>AI 등록 지표</h1>
      <form className="card grid-2" onSubmit={(e) => { e.preventDefault(); setApplied(filter); }}>
        <Field label="시작일"><input type="date" value={filter.from} onChange={set('from')} /></Field>
        <Field label="종료일"><input type="date" value={filter.to} onChange={set('to')} /></Field>
        <Field label="프롬프트 버전 (비우면 전체)"><input value={filter.prompt_version} onChange={set('prompt_version')} placeholder="product_scan_v3" /></Field>
        <div style={{ alignSelf: 'end' }}><button className="primary block">조회</button></div>
      </form>
      <ErrorBox error={metrics.error} />
      {metrics.loading && !m && <Loading />}
      {m && (
        <>
          <p className="muted small">{m.from} ~ {m.to}{m.prompt_version ? ` · ${m.prompt_version}` : ''}</p>
          <div className="metrics">
            <Metric label="카테고리 정확도 (소분류)" value={pct(m.category_accuracy.leaf)} target={`목표 ${pct(m.targets.category_accuracy_leaf)}`}
                    good={m.category_accuracy.leaf !== null && m.category_accuracy.leaf >= m.targets.category_accuracy_leaf} />
            <Metric label="카테고리 정확도 (대분류)" value={pct(m.category_accuracy.top)} />
            <Metric label="등록 소요시간 (중앙값)" value={m.median_seconds_to_publish === null ? '–' : `${m.median_seconds_to_publish}초`}
                    target={`목표 ${m.targets.seconds_to_publish}초 이하`}
                    good={m.median_seconds_to_publish !== null && m.median_seconds_to_publish <= m.targets.seconds_to_publish} />
            <Metric label="AI 분석 시간 (중앙값)" value={m.scan_jobs.median_elapsed_ms === null ? '–' : `${(m.scan_jobs.median_elapsed_ms / 1000).toFixed(1)}초`} />
            <Metric label="정밀도 (맞는 후보 비율)" value={pct(m.precision)} />
            <Metric label="재현율 (놓치지 않은 비율)" value={pct(m.recall)} />
            <Metric label="스캔 (성공 / 전체)" value={`${m.scan_jobs.succeeded} / ${m.scan_jobs.total}`} target={`실패 ${m.scan_jobs.failed}`} />
            <Metric label="후보 (수락 / AI 제안)" value={`${m.candidates.accepted} / ${m.candidates.ai_total}`} target={`판매자 추가 ${m.candidates.seller_added}`} />
          </div>
          <p className="muted small">정밀도 = 수락한 AI 후보 ÷ 결정한 AI 후보, 재현율 = 수락한 AI 후보 ÷ (수락한 AI 후보 + 판매자가 추가한 상품).</p>
        </>
      )}
    </div>
  );
}

function Metric({ label, value, target, good }) {
  return (
    <div className="metric">
      <div className="label">{label}</div>
      <div className="value" style={good === undefined ? undefined : { color: good ? 'var(--ok)' : 'var(--danger)' }}>{value}</div>
      {target && <div className="label">{target}</div>}
    </div>
  );
}

function pct(v) {
  return v === null || v === undefined ? '–' : `${Math.round(v * 100)}%`;
}
