"use client";

import { useEffect, useId, useRef, useState } from "react";
import type { BoardPost, BoardPostInput } from "../api";
import styles from "../board.module.css";

type Props = {
  initial: BoardPost | null;
  onSubmit: (input: BoardPostInput) => Promise<void>;
  onClose: () => void;
};

// 팝업 틀은 게임 상세 팝업(전역 .detail)과 같은 디자인을 쓴다
export default function PostComposer({ initial, onSubmit, onClose }: Props) {
  const titleId = useId();
  const dialog = useRef<HTMLDialogElement>(null);
  const [title, setTitle] = useState(initial?.title ?? "");
  const [content, setContent] = useState(initial?.content ?? "");
  const [spoiler, setSpoiler] = useState(initial?.spoiler ?? false);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState("");

  useEffect(() => {
    const element = dialog.current;
    element?.showModal();
    return () => element?.close();
  }, []);

  async function submit() {
    if (busy) return;
    setBusy(true);
    setError("");
    try {
      await onSubmit({ title: title.trim(), content: content.trim(), spoiler, gameId: initial?.game?.id ?? null });
    } catch (reason) {
      setError(reason instanceof Error ? reason.message : "게시글을 저장하지 못했습니다.");
      setBusy(false);
    }
  }

  return <dialog ref={dialog} className={`detail ${styles.composer}`} aria-labelledby={titleId}
    onCancel={event => { event.preventDefault(); onClose(); }}>
    <button type="button" className="icon-button close-detail" aria-label="닫기" onClick={onClose}>✕</button>
    <form onSubmit={event => { event.preventDefault(); void submit(); }}>
      <span className="eyebrow"><span className="dot" />FREE BOARD</span>
      <h2 id={titleId}>{initial ? "글 수정" : "글쓰기"}</h2>
      <input aria-label="제목" placeholder="제목 (최대 100자)" maxLength={100} value={title} disabled={busy}
        onChange={event => setTitle(event.target.value)} required />
      <textarea aria-label="본문" placeholder="잡담, 질문, 정보를 자유롭게 나눠 보세요." maxLength={5000} rows={10}
        value={content} disabled={busy} onChange={event => setContent(event.target.value)} required />
      <div className={styles.composerFooter}>
        <label><input type="checkbox" checked={spoiler} disabled={busy} onChange={event => setSpoiler(event.target.checked)} /> 스포일러 포함</label>
        <small className="muted">{content.length.toLocaleString("ko-KR")} / 5,000</small>
      </div>
      {error && <p className={styles.error} role="alert">{error}</p>}
      <div className={styles.composerFooter}>
        <button type="button" className="outline-button" disabled={busy} onClick={onClose}>취소</button>
        <button className="apply-button" disabled={busy || !title.trim() || !content.trim()}>
          {busy ? "저장 중…" : initial ? "수정 저장" : "게시"}
        </button>
      </div>
    </form>
  </dialog>;
}
