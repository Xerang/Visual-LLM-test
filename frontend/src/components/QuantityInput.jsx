import { UNIT_LABEL, quantityStep } from '../format.js';

/** 단위에 맞는 수량 입력. 개수 단위는 1씩, kg·m 등은 0.5씩 오르내리고 직접 입력도 된다. */
export default function QuantityInput({ value, unit, onChange, min, max }) {
  const step = quantityStep(unit);
  const lower = min ?? (step === 1 ? 1 : 0.1);
  const clamp = (n) => {
    let v = Math.round(n * 1000) / 1000;
    if (v < lower) v = lower;
    if (max !== null && max !== undefined && v > Number(max)) v = Number(max);
    return v;
  };
  return (
    <span className="stepper">
      <button type="button" aria-label="줄이기" onClick={() => onChange(clamp(value - step))}>−</button>
      <input type="number" inputMode="decimal" step={step === 1 ? 1 : 0.1} min={lower} value={value}
             onChange={(e) => onChange(e.target.value === '' ? lower : clamp(Number(e.target.value)))}
             aria-label="수량" />
      <button type="button" aria-label="늘리기" onClick={() => onChange(clamp(value + step))}>+</button>
      <span className="muted small">{UNIT_LABEL[unit]}</span>
    </span>
  );
}
