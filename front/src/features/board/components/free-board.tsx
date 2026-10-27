"use client";

import { useRouter, useSearchParams } from "next/navigation";
import { useEffect, useRef, useState } from "react";
import { boardRequest, postListPath, type BoardPost, type BoardPostInput, type BoardPostPage } from "../api";
import { useAuthorized } from "../use-authorized";
import PostCard from "./post-card";
import PostComposer from "./post-composer";
import feed from "@/features/community/community.module.css";
import styles from "../board.module.css";

type Composer = { editing: BoardPost | null } | null;

export default function FreeBoard({ loginPath }: { loginPath: string }) {
  const router = useRouter();
  const params = useSearchParams();
  const keyword = params.get("q")?.trim().slice(0, 50) ?? "";
  const linkedPostId = Number(params.get("post")) || null;
  const authorized = useAuthorized(loginPath);
  const [search, setSearch] = useState(keyword);
  const [composer, setComposer] = useState<Composer>(null);

  function applySearch(value: string) {
    const query = new URLSearchParams({ board: "free" });
    if (value.trim()) query.set("q", value.trim().slice(0, 50));
    router.replace(`/community?${query}`);
  }

  return <section aria-label="자유게시판">
    <div className={styles.toolbar}>
      <form role="search" className={`header-search ${styles.search}`} onSubmit={event => { event.preventDefault(); applySearch(search); }}>
        <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.6" aria-hidden="true"><circle cx="10.5" cy="10.5" r="6.5" /><path d="m16 16 5 5" /></svg>
        <input aria-label="제목·본문 검색" placeholder="제목·본문 검색" maxLength={50} value={search}
          onChange={event => setSearch(event.target.value)} />
        {keyword && <button type="button" aria-label="검색 초기화" onClick={() => { setSearch(""); applySearch(""); }}>✕</button>}
        <button type="submit">검색 <span>↵</span></button>
      </form>
      <button type="button" className="apply-button" onClick={() => setComposer({ editing: null })}>글쓰기</button>
    </div>
    {keyword && <p className="muted">‘{keyword}’ 검색 결과</p>}
    <PostList key={`${keyword}:${linkedPostId}`} keyword={keyword} linkedPostId={linkedPostId} authorized={authorized}
      composer={composer} onCompose={setComposer} />
  </section>;
}

type ListProps = {
  keyword: string;
  linkedPostId: number | null;
  authorized: ReturnType<typeof useAuthorized>;
  composer: Composer;
  onCompose: (composer: Composer) => void;
};

function PostList({ keyword, linkedPostId, authorized, composer, onCompose }: ListProps) {
  const [posts, setPosts] = useState<BoardPost[]>([]);
  const [linked, setLinked] = useState<BoardPost | null>(null);
  const [cursor, setCursor] = useState<number | null>(null);
  const [pending, setPending] = useState(true);
  const [error, setError] = useState("");
  const [linkedError, setLinkedError] = useState("");
  const [attempt, setAttempt] = useState(0);
  const lifetime = useRef<AbortController | null>(null);
  const loadingMore = useRef(false);
  const sentinel = useRef<HTMLDivElement>(null);

  useEffect(() => {
    const controller = new AbortController();
    lifetime.current = controller;
    authorized(token => boardRequest<BoardPostPage>(postListPath(keyword), token, "GET", undefined, controller.signal))
      .then(page => { if (!controller.signal.aborted) { setPosts(page.posts); setCursor(page.nextCursor); } })
      .catch(reason => { if (!controller.signal.aborted) setError(reason instanceof Error ? reason.message : "게시글을 불러오지 못했습니다."); })
      .finally(() => { if (!controller.signal.aborted) setPending(false); });
    return () => controller.abort();
  }, [keyword, attempt, authorized]);

  useEffect(() => {
    if (!linkedPostId) return;
    const controller = new AbortController();
    authorized(token => boardRequest<BoardPost>(`/${linkedPostId}`, token, "GET", undefined, controller.signal))
      .then(post => { if (!controller.signal.aborted) setLinked(post); })
      .catch(() => { if (!controller.signal.aborted) setLinkedError("알림의 게시글이 삭제되었거나 볼 수 없는 상태입니다."); });
    return () => controller.abort();
  }, [linkedPostId, authorized]);

  async function loadMore() {
    const signal = lifetime.current?.signal;
    if (!signal || !cursor || loadingMore.current) return;
    loadingMore.current = true;
    setPending(true);
    setError("");
    try {
      const page = await authorized(token => boardRequest<BoardPostPage>(postListPath(keyword, cursor), token, "GET", undefined, signal));
      if (signal.aborted) return;
      setPosts(current => [...current, ...page.posts.filter(post => !current.some(item => item.id === post.id))]);
      setCursor(page.nextCursor);
    } catch (reason) {
      // 실패해도 이미 불러온 카드는 그대로 두고 다시 시도할 수 있게 한다
      if (!signal.aborted) setError(reason instanceof Error ? reason.message : "게시글을 불러오지 못했습니다.");
    } finally {
      loadingMore.current = false;
      if (!signal.aborted) setPending(false);
    }
  }

  const loadMoreRef = useRef(loadMore);
  useEffect(() => { loadMoreRef.current = loadMore; });
  useEffect(() => {
    const element = sentinel.current;
    if (!element || !cursor || error) return;
    const observer = new IntersectionObserver(entries => {
      if (entries.some(entry => entry.isIntersecting)) void loadMoreRef.current();
    }, { rootMargin: "300px" });
    observer.observe(element);
    return () => observer.disconnect();
  }, [cursor, error]);

  function replace(post: BoardPost) {
    setPosts(current => current.map(item => item.id === post.id ? post : item));
    setLinked(current => current?.id === post.id ? post : current);
  }

  async function remove(post: BoardPost) {
    if (!window.confirm("게시글을 삭제할까요? 댓글도 함께 삭제됩니다.")) return;
    try {
      await authorized(token => boardRequest(`/${post.id}`, token, "DELETE"));
      setPosts(current => current.filter(item => item.id !== post.id));
      setLinked(current => current?.id === post.id ? null : current);
    } catch (reason) {
      window.alert(reason instanceof Error ? reason.message : "게시글을 삭제하지 못했습니다.");
    }
  }

  async function save(input: BoardPostInput) {
    const editing = composer?.editing;
    const saved = await authorized(token => editing
      ? boardRequest<BoardPost>(`/${editing.id}`, token, "PUT", input)
      : boardRequest<BoardPost>("", token, "POST", input));
    if (editing) replace(saved);
    else setPosts(current => [saved, ...current]);
    onCompose(null);
  }

  const cardProps = { authorized, onChange: replace, onEdit: (post: BoardPost) => onCompose({ editing: post }), onDelete: (post: BoardPost) => void remove(post) };
  const visible = linked ? posts.filter(post => post.id !== linked.id) : posts;
  return <>
    {linkedError && <p className={styles.error} role="alert">{linkedError}</p>}
    {linked && <PostCard {...cardProps} post={linked} highlighted />}
    {visible.map(post => <PostCard key={post.id} {...cardProps} post={post} />)}
    {!pending && !error && posts.length === 0 && <div className="empty-state">
      <h3>{keyword ? "검색 결과가 없습니다." : "아직 게시글이 없습니다."}</h3>
      <p>{keyword ? "다른 검색어로 찾아보세요." : "첫 글을 남겨 이야기를 시작해 보세요."}</p>
    </div>}
    {pending && <p className={feed.state} role="status">게시글을 불러오는 중…</p>}
    {error && <div className={feed.state} role="alert"><p>{error}</p>
      <button type="button" disabled={pending} onClick={() => {
        if (posts.length) void loadMore(); else { setPending(true); setError(""); setAttempt(value => value + 1); }
      }}>다시 시도</button>
    </div>}
    <div ref={sentinel} aria-hidden="true" />
    {composer && <PostComposer initial={composer.editing} onSubmit={save} onClose={() => onCompose(null)} />}
  </>;
}
