// 업로드 전에 브라우저에서 사진을 줄인다(긴 변 2048px, JPEG).
// 현지 모바일 회선이 느려서(조사 §1) 원본 그대로 올리면 시간이 오래 걸리고 5MB 제한에도 걸린다.
// 서버도 같은 정규화를 다시 하므로, 여기서 실패하면 원본을 그대로 보낸다.

const MAX_EDGE = 2048;

export async function resizeForUpload(file) {
  if (!file.type.startsWith('image/')) return file;
  try {
    // imageOrientation: 'from-image' — 휴대폰 사진의 EXIF 회전을 반영해서 그린다
    const bitmap = await createImageBitmap(file, { imageOrientation: 'from-image' });
    const scale = Math.min(1, MAX_EDGE / Math.max(bitmap.width, bitmap.height));
    if (scale === 1 && file.size < 1.5 * 1024 * 1024 && file.type === 'image/jpeg') {
      bitmap.close();
      return file;   // 이미 충분히 작은 JPEG
    }
    const canvas = document.createElement('canvas');
    canvas.width = Math.round(bitmap.width * scale);
    canvas.height = Math.round(bitmap.height * scale);
    const ctx = canvas.getContext('2d');
    ctx.fillStyle = '#fff';   // 투명 PNG 배경을 흰색으로
    ctx.fillRect(0, 0, canvas.width, canvas.height);
    ctx.drawImage(bitmap, 0, 0, canvas.width, canvas.height);
    bitmap.close();
    const blob = await new Promise((resolve) => canvas.toBlob(resolve, 'image/jpeg', 0.88));
    if (!blob) return file;
    const name = (file.name || 'photo').replace(/\.[^.]+$/, '') + '.jpg';
    return new File([blob], name, { type: 'image/jpeg' });
  } catch {
    return file;
  }
}
