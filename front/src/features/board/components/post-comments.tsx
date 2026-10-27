"use client";

import Link from "next/link";
import { useCallback, useEffect, useId, useRef, useState } from "react";
import ImageWithFallback from "@/components/ui/image-with-fallback";
import { boardRequest, commentListPath, type BoardComment, type BoardCommentPage } from "../api";
import type { Authorized } from "../use-authorized";
import styles from "../comments.module.css";

type Shared = { postId: number; authorized: Authorized; onChanged: () => void };

type Props = { postId: number; authorized: Authorized; open: boolean; onOpen: () => void; onCount: (count: number) => void };

// 입력창은 항상 보이고, 댓글 목록은 펼쳤을 때만 불러온다
export default function PostComments({ postId, authorized, open, onOpen, onCount }: Props) {
  const [version, setVersion] = useState(0);
  const changed = useCallback(() => setVersion(value => value + 1), []);
  // 부모가 다시 렌더링되어도 댓글 목록을 다시 불러오지 않도록 콜백을 고정한다
  const countRef = useRef(onCount);
  useEffect(() => { countRef.current = onCount; }, [onCount]);
  const reportCount = useCallback((count: number) => countRef.current(count), []);
  return <section className={styles.thread} aria-label="댓글">
    {open && <CommentList key={version} postId={postId} parentId={null} authorized={authorized} onChanged={changed} onCount={reportCount} />}
    <CommentEditor postId={postId} parentId={null} authorized={authorized} onDone={() => { onOpen(); changed(); }} />
  </section>;
}

function CommentList({ postId, parentId, authorized, onChanged, onCount }: Shared & { parentId: number | null; onCount?: (count: number) => void }) {
  const [rows, setRows] = useState<BoardComment[]>([]);
  const [cursor, setCursor] = useState<number | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");
  const [attempt, setAttempt] = useState(0);
  const lifetime = useRef<AbortController | null>(null);
  const lock = useRef(false);

  useEffect(() => {
    const controller = new AbortController();
    lifetime.current = controller;
    authorized(token => boardRequest<BoardCommentPage>(commentListPath(postId, parentId), token, "GET", undefined, controller.signal))
      .then(page => {
        if (controller.signal.aborted) return;
        setRows(page.comments);
        setCursor(page.nextCursor);
        onCount?.(page.totalCount);
      })
      .catch(reason => { if (!controller.signal.aborted) setError(reason instanceof Error ? reason.message : "댓글을 불러오지 못했습니다."); })
      .finally(() => { if (!controller.signal.aborted) setLoading(false); });
    return () => controller.abort();
  }, [postId, parentId, attempt, authorized, onCount]);

  async function more() {
    const signal = lifetime.current?.signal;
    if (!signal || lock.current) return;
    lock.current = true;
    setLoading(true);
    setError("");
    try {
      const page = await authorized(token => boardRequest<BoardCommentPage>(commentListPath(postId, parentId, cursor), token, "GET", undefined, signal));
      if (signal.aborted) return;
      setRows(previous => [...previous, ...page.comments.filter(comment => !previous.some(row => row.id === comment.id))]);
      setCursor(page.nextCursor);
    } catch (reason) {
      if (!signal.aborted) setError(reason instanceof Error ? reason.message : "댓글을 불러오지 못했습니다.");
    } finally {
      lock.current = false;
      if (!signal.aborted) setLoading(false);
    }
  }

  return <div className={parentId ? styles.replies : undefined}>
    {rows.map(comment => <CommentRow key={comment.id} comment={comment} postId={postId} authorized={authorized} onChanged={onChanged} />)}
    {loading && <p className={styles.removed} role="status">불러오는 중…</p>}
    {error && <p className={styles.error} role="alert">{error} <button type="button" disabled={loading} onClick={() => {
      if (rows.length) void more(); else { setLoading(true); setError(""); setAttempt(value => value + 1); }
    }}>다시 시도</button></p>}
    {cursor && !error && <button type="button" className={styles.more} disabled={loading} onClick={() => void more()}>
      {parentId ? "답글 더 보기" : "이전 댓글 더 보기"}
    </button>}
  </div>;
}

function CommentRow({ comment, postId, authorized, onChanged }: Shared & { comment: BoardComment }) {
  const [revealed, setRevealed] = useState(false);
  const [mode, setMode] = useState<"view" | "edit" | "reply">("view");
  const [showReplies, setShowReplies] = useState(false);
  const [replyVersion, setReplyVersion] = useState(0);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState("");

  async function run(work: () => Promise<unknown>): Promise<boolean> {
    if (busy) return false;
    setBusy(true);
    setError("");
    try { await work(); return true; }
    catch (reason) { setError(reason instanceof Error ? reason.message : "요청에 실패했습니다."); return false; }
    finally { setBusy(false); }
  }

  async function remove() {
    if (!window.confirm("댓글을 삭제할까요?")) return;
    if (await run(() => authorized(token => boardRequest(`/${postId}/comments/${comment.id}`, token, "DELETE")))) onChanged();
  }

  async function report() {
    const reason = window.prompt("신고 사유를 입력해 주세요. (최대 255자)")?.trim();
    if (!reason) return;
    const sent = await run(() => authorized(token =>
      boardRequest(`/${postId}/comments/${comment.id}/reports`, token, "POST", { reason: reason.slice(0, 255) })));
    if (sent) window.alert("신고를 접수했습니다.");
  }

  const author = comment.removed ? null : comment.author;
  const isRoot = !comment.parentId;
  return <div className={styles.row}>
    {author ? <Link href={`/profile/${author.id}`} className={styles.avatar} aria-label={`${author.nickname}님의 프로필`}>
      <ImageWithFallback src={author.profileImageUrl} alt="" fallback={<span>{author.nickname.slice(0, 1)}</span>} />
    </Link> : <span className={styles.avatar} aria-hidden="true" />}
    <div>
      {!author ? <p className={styles.removed}>삭제된 댓글입니다.</p> : mode === "edit" ? <CommentEditor postId={postId} parentId={comment.parentId}
        authorized={authorized} editing={comment} onDone={() => { setMode("view"); onChanged(); }} onCancel={() => setMode("view")} />
        : <>
          <div className={styles.bubble}>
            <Link href={`/profile/${author.id}`}>{author.nickname}</Link>
            {comment.spoiler && !revealed
              ? <button type="button" className={styles.spoiler} onClick={() => setRevealed(true)}>스포일러 댓글 · 내용 보기</button>
              : <p className={styles.text}>{comment.content}</p>}
          </div>
          <div className={styles.meta}>
            <time dateTime={comment.createdDate}>{relativeTime(comment.createdDate)}</time>
            {comment.edited && <span>수정됨</span>}
            {isRoot && <button type="button" disabled={busy} onClick={() => setMode(mode === "reply" ? "view" : "reply")}>답글 달기</button>}
            {comment.mine ? <>
              <button type="button" disabled={busy} onClick={() => setMode("edit")}>수정</button>
              <button type="button" disabled={busy} onClick={() => void remove()}>삭제</button>
            </> : <button type="button" disabled={busy} onClick={() => void report()}>신고</button>}
          </div>
          {error && <p className={styles.error} role="alert">{error}</p>}
        </>}
      {isRoot && comment.replyCount > 0 && <button type="button" className={styles.toggle} aria-expanded={showReplies}
        onClick={() => setShowReplies(value => !value)}>
        {showReplies ? "답글 숨기기" : `답글 ${comment.replyCount}개 보기`}
      </button>}
      {isRoot && showReplies && <CommentList key={replyVersion} postId={postId} parentId={comment.id} authorized={authorized} onChanged={onChanged} />}
      {mode === "reply" && <CommentEditor postId={postId} parentId={comment.id} authorized={authorized} target={author?.nickname}
        onDone={() => { setMode("view"); setShowReplies(true); setReplyVersion(value => value + 1); }} onCancel={() => setMode("view")} />}
    </div>
  </div>;
}

type EditorProps = {
  postId: number; parentId: number | null; authorized: Authorized; editing?: BoardComment; target?: string;
  onDone: () => void; onCancel?: () => void;
};

function CommentEditor({ postId, parentId, authorized, editing, target, onDone, onCancel }: EditorProps) {
  const inputId = useId();
  const [content, setContent] = useState(editing?.content ?? "");
  const [spoiler, setSpoiler] = useState(editing?.spoiler ?? false);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState("");

  async function submit() {
    if (busy || !content.trim()) return;
    setBusy(true);
    setError("");
    try {
      await authorized(token => editing
        ? boardRequest(`/${postId}/comments/${editing.id}`, token, "PUT", { content: content.trim(), spoiler })
        : boardRequest(`/${postId}/comments`, token, "POST", { content: content.trim(), spoiler, parentId }));
      setContent("");
      setSpoiler(false);
      onDone();
    } catch (reason) {
      setError(reason instanceof Error ? reason.message : "댓글을 저장하지 못했습니다.");
    } finally {
      setBusy(false);
    }
  }

  const label = editing ? "댓글을 수정해주세요" : parentId ? `${target ?? ""}님에게 답글을 작성해주세요` : "댓글을 작성해주세요";
  return <form className={styles.editor} onSubmit={event => { event.preventDefault(); void submit(); }}>
    <div className={styles.inputBox}>
      <textarea id={inputId} aria-label={label} placeholder={label} rows={1} maxLength={1000} value={content} disabled={busy} required
        onChange={event => setContent(event.target.value)}
        onKeyDown={event => { if (event.key === "Enter" && !event.shiftKey && !event.nativeEvent.isComposing) { event.preventDefault(); void submit(); } }} />
      <button type="button" className={styles.spoilerToggle} aria-pressed={spoiler} disabled={busy}
        title="켜면 다른 사람에게는 '내용 보기'를 누르기 전까지 댓글이 가려집니다." onClick={() => setSpoiler(value => !value)}>스포방지</button>
      <button className={styles.post} disabled={busy || !content.trim()}>{busy ? "…" : editing ? "저장" : "게시"}</button>
    </div>
    {(onCancel || content.length >= 900) && <div className={styles.editorOptions}>
      {onCancel && <button type="button" disabled={busy} onClick={onCancel}>취소</button>}
      {content.length >= 900 && <small>{content.length} / 1000자</small>}
    </div>}
    {error && <p className={styles.error} role="alert">{error}</p>}
  </form>;
}

function relativeTime(value: string): string {
  const minutes = Math.floor((Date.now() - new Date(value).getTime()) / 60000);
  if (minutes < 1) return "방금";
  if (minutes < 60) return `${minutes}분`;
  if (minutes < 60 * 24) return `${Math.floor(minutes / 60)}시간`;
  if (minutes < 60 * 24 * 7) return `${Math.floor(minutes / 1440)}일`;
  return value.slice(0, 10).replaceAll("-", ".");
}
