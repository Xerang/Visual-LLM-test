import { useState } from 'react';
import { api } from '../../api.js';
import { ErrorBox, Field, Loading, useLoad } from '../../components/ui.jsx';
import { duration } from '../../format.js';

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
          <p className="muted small">정밀도 = 수락한 AI 후보 ÷ 결정한 AI 후보, 재현율 = 수락한 AI 후보 ÷ (수락한 AI 후보 + 판매자가 추가한 상품).
            등록 소요시간은 단건 스캔만 셉니다(일괄 등록은 아래 온보딩 지표).</p>
          <Onboarding o={m.onboarding} target={m.targets.onboarding_seconds_for_20_images} />
        </>
      )}
    </div>
  );
}

/** 상점 온보딩(SNS 일괄 등록): 사진 고르기 시작 → 모든 게시물 검수 완료 */
function Onboarding({ o, target }) {
  return (
    <div className="stack">
      <h2>상점 온보딩 (SNS 일괄 등록)</h2>
      <div className="metrics">
        <Metric label="온보딩 소요시간 (중앙값)" value={duration(o.median_seconds)} target={`목표: 사진 20장 ${duration(target)} 이내`} />
        <Metric label="준비 시간 (중앙값)" value={duration(o.median_prepare_seconds)} target="사진 고르기 → 분석 요청" />
        <Metric label="묶음 (완료 / 시작)" value={`${o.completed} / ${o.started}`} target={`분석 요청 ${o.submitted}`} />
      </div>
      {o.recent.length > 0 && (
        <table className="table">
          <thead><tr><th>묶음</th><th>사진</th><th>게시물</th><th>발행 상품</th><th>소요시간</th></tr></thead>
          <tbody>
            {o.recent.map((b) => (
              <tr key={b.id}>
                <td>#{b.id}</td><td>{b.images}장</td><td>{b.posts}개</td><td>{b.published}개</td>
                <td>{duration(b.seconds)}<div className="muted small">준비 {duration(b.prepare_seconds)}</div></td>
              </tr>
            ))}
          </tbody>
        </table>
      )}
      <p className="muted small">목표는 사진 20장 기준입니다. 사진 수가 다른 묶음은 20장으로 환산하지 않으니 표에서 사진 수를 함께 보세요. 모든 게시물을 검수한 묶음만 셉니다.</p>
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
