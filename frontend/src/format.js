// 화면에 보여줄 이름·형식을 한 곳에 모은다.
// 운영 언어를 tg/ru로 바꿀 때(임시 결정 #2) 이 파일의 라벨부터 번역하면 된다.

export const UNIT_LABEL = { PIECE: '개', KG: 'kg', LITER: 'L', METER: 'm', SET: '세트' };
export const PRICE_TYPE_LABEL = { FIXED: '정가', NEGOTIABLE: '흥정 가능' };
export const TEMPLATE_LABEL = { GRID: '진열대형', LIST: '가격표형', SHOWCASE: '공방형' };
export const TEMPLATE_HELP = {
  GRID: '사진이 중요한 식품·소품 (2열 사진 그리드)',
  LIST: '품목이 많은 상점, 느린 회선 (작은 썸네일 + 단가 목록)',
  SHOWCASE: '이야기가 있는 수공예 (큰 사진 + 긴 설명)',
};
export const STORE_STATUS_LABEL = { DRAFT: '준비 중', ACTIVE: '공개', HIDDEN: '숨김' };
export const PRODUCT_STATUS_LABEL = { DRAFT: '초안', PUBLISHED: '판매 중', HIDDEN: '숨김', ARCHIVED: '보관' };
export const SCAN_STATUS_LABEL = { QUEUED: '대기 중', RUNNING: 'AI 분석 중', SUCCEEDED: '분석 완료', FAILED: '실패' };
export const ORDER_STATUS_LABEL = {
  REQUESTED: '주문 요청', CONFIRMED: '접수됨', READY: '준비 완료',
  COMPLETED: '거래 완료', REJECTED: '거절됨', CANCELLED: '취소됨',
};
export const FULFILLMENT_LABEL = { PICKUP: '매장 픽업', DELIVERY: '판매자 배송' };
export const PAYMENT_LABEL = { CASH: '현금 (대면·착불)', TRANSFER: '송금 (지갑·QR)' };

const number = new Intl.NumberFormat('ko-KR', { maximumFractionDigits: 2 });
const quantityFormat = new Intl.NumberFormat('ko-KR', { maximumFractionDigits: 3 });

export function money(amount, currency = 'TJS') {
  if (amount === null || amount === undefined) return '가격 미정';
  return `${number.format(amount)} ${currency}`;
}

/** 45 TJS / kg */
export function unitPrice(price, unit, currency = 'TJS') {
  if (price === null || price === undefined) return '가격 미정';
  return unit === 'PIECE' ? money(price, currency) : `${money(price, currency)} / ${UNIT_LABEL[unit]}`;
}

export function quantity(q, unit) {
  return `${quantityFormat.format(q)}${UNIT_LABEL[unit] === '개' ? '개' : ' ' + UNIT_LABEL[unit]}`;
}

/** 개수 단위는 1씩, 무게·길이 단위는 0.5씩 */
export function quantityStep(unit) {
  return unit === 'PIECE' || unit === 'SET' ? 1 : 0.5;
}

export function dateTime(iso) {
  if (!iso) return '';
  return new Date(iso).toLocaleString('ko-KR', { dateStyle: 'short', timeStyle: 'short' });
}

/** 초 → "4분 12초" */
export function duration(seconds) {
  if (seconds === null || seconds === undefined) return '–';
  const s = Math.round(Number(seconds));
  const m = Math.floor(s / 60);
  if (m === 0) return `${s}초`;
  return s % 60 === 0 ? `${m}분` : `${m}분 ${s % 60}초`;
}

/** wa.me 링크: + 와 숫자만 남긴다 */
export function whatsappLink(phone, text) {
  if (!phone) return null;
  const digits = phone.replace(/[^0-9]/g, '');
  return `https://wa.me/${digits}${text ? `?text=${encodeURIComponent(text)}` : ''}`;
}
