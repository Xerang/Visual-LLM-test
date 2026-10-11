import { useRef, useState } from 'react';
import { api } from '../api.js';
import { resizeForUpload } from '../imageResize.js';
import { ErrorBox, useLoad } from './ui.jsx';

/** 사진 고르기 → 브라우저에서 줄이기 → 업로드. 업로드된 사진 정보(id, url…)를 onUploaded 로 넘긴다. */
export function ImageUploadButton({ onUploaded, multiple = false, capture = false, label = '사진 올리기', max = 10 }) {
  const input = useRef(null);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState(null);

  async function handle(e) {
    const files = Array.from(e.target.files || []).slice(0, max);
    e.target.value = '';
    if (files.length === 0) return;
    setBusy(true);
    setError(null);
    try {
      for (const file of files) {
        onUploaded(await api.upload(await resizeForUpload(file)), file);
      }
    } catch (err) {
      setError(err);
    } finally {
      setBusy(false);
    }
  }

  return (
    <span>
      <input ref={input} type="file" accept="image/jpeg,image/png,image/webp" hidden multiple={multiple}
             capture={capture ? 'environment' : undefined} onChange={handle} />
      <button type="button" disabled={busy} onClick={() => input.current.click()}>{busy ? '올리는 중…' : label}</button>
      <ErrorBox error={error} />
    </span>
  );
}

/** 카테고리 고르기. 상품에는 말단(소분류)만 쓸 수 있어서 대분류는 묶음 제목(optgroup)으로만 보여준다. */
export function CategorySelect({ value, onChange, categories }) {
  return (
    <select value={value || ''} onChange={(e) => {
      const id = Number(e.target.value);
      onChange(id, findLeaf(categories, id));
    }} required>
      <option value="" disabled>카테고리 선택</option>
      {categories?.map((root) => (root.children.length === 0
        ? <option key={root.id} value={root.id}>{root.name}</option>
        : (
          <optgroup key={root.id} label={root.name}>
            {root.children.map((c) => <option key={c.id} value={c.id}>{c.name}</option>)}
          </optgroup>
        )))}
    </select>
  );
}

export function findLeaf(categories, id) {
  for (const root of categories || []) {
    if (root.id === id && root.children.length === 0) return root;
    const child = root.children.find((c) => c.id === id);
    if (child) return child;
  }
  return null;
}

export function useCategories() {
  return useLoad(() => api.get('/categories'), []);
}
