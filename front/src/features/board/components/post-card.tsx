"use client";

import Link from "next/link";
import { useEffect, useLayoutEffect, useRef, useState } from "react";
import GameCoverFallback from "@/components/ui/game-cover-fallback";
import ImageWithFallback from "@/components/ui/image-with-fallback";
import { coverUrl } from "@/features/games/model";
import feed from "@/features/community/community.module.css";
import { boardRequest, type BoardPost } from "../api";
import type { Authorized } from "../use-authorized";
import PostComments from "./post-comments";
import styles from "../board.module.css";

type Props = {
  post: BoardPost;
  authorized: Authorized;
  highlighted?: boolean;
  onChange: (post: BoardPost) => void;
  onEdit: (post: BoardPost) => void;
  onDelete: (post: BoardPost) => void;
};

// 카드 모양은 피드 리뷰 카드(community.module.css)를 그대로 쓴다
export default function PostCard({ post, authorized, highlighted = false, onChange, onEdit, onDelete }: Props) {
  const [revealed, setRevealed] = useState(false);
  const [expanded, setExpanded] = useState(false);
  const [overflowing, setOverflowing] = useState(false);
  const [pending, setPending] = useState(false);
  // SNS처럼 댓글은 접어 두고, 알림으로 들어온 글만 펼친다
  const [commentsOpen, setCommentsOpen] = useState(highlighted);
  const [error, setError] = useState("");
  const body = useRef<HTMLParagraphElement>(null);
  const hidden = post.spoiler && !revealed;

  useLayoutEffect(() => {
    const element = body.current;
    if (element && !expanded) setOverflowing(element.scrollHeight > element.clientHeight + 1);
  }, [post.content, hidden, expanded]);

  async function run(work: () => Promise<void>) {
    if (pending) return;
    setPending(true);
    setError("");
    try { await work(); }
    catch (reason) { setError(reason instanceof Error ? reason.message : "요청에 실패했습니다."); }
    finally { setPending(false); }
  }

  const like = () => run(async () => {
    onChange(await authorized(token => boardRequest<BoardPost>(`/${post.id}/likes`, token, post.liked ? "DELETE" : "PUT")));
  });

  const report = () => {
    const reason = window.prompt("신고 사유를 입력해 주세요. (최대 255자)")?.trim();
    if (!reason) return;
    void run(async () => {
      await authorized(token => boardRequest(`/${post.id}/reports`, token, "POST", { reason: reason.slice(0, 255) }));
      window.alert("신고를 접수했습니다.");
    });
  };

  const author = post.author;
  const gameTitle = post.game?.title ?? "게임";
  return <article className={`${feed.card} ${highlighted ? styles.highlighted : ""}`} id={`post-${post.id}`}>
    <header className={feed.cardHeader}>
      <div className={feed.identity}>
        <Link className={feed.avatar} href={`/profile/${author.id}`} aria-label={`${author.nickname}님의 프로필`}>
          <ImageWithFallback src={author.profileImageUrl} alt="" fallback={<span>{author.nickname.slice(0, 1).toUpperCase()}</span>} />
        </Link>
        <div className={feed.reviewerDetails}>
          <div className={feed.reviewerName}>
            <Link className={feed.author} href={`/profile/${author.id}`}>{author.nickname}</Link>
            {post.edited && <span>수정됨</span>}
          </div>
        </div>
      </div>
      <div className={styles.headerRight}>
        <time dateTime={post.createdDate}>{post.createdDate.slice(0, 10).replaceAll("-", ".")}</time>
        <PostMenu disabled={pending} items={post.mine
          ? [{ label: "수정", onSelect: () => onEdit(post) }, { label: "삭제", danger: true, onSelect: () => onDelete(post) }]
          : [{ label: "신고", danger: true, onSelect: report }]} />
      </div>
    </header>

    <h3 className={styles.title}>{post.title}</h3>
    {hidden ? <div className={feed.spoiler}>
      <p>스포일러가 포함된 글입니다.</p>
      <button type="button" onClick={() => setRevealed(true)}>내용 보기</button>
    </div> : <div className={`${feed.body} ${styles.body}`}>
      <p ref={body} className={`${styles.text} ${expanded ? "" : styles.clamped}`}>{post.content}</p>
      {(overflowing || expanded) && <button type="button" aria-expanded={expanded} onClick={() => setExpanded(value => !value)}>
        {expanded ? "접기" : "더 보기"}
      </button>}
    </div>}

    {post.game && <Link className={feed.game} href={`/games/${post.game.id}`}>
      <span className={feed.cover}>
        <ImageWithFallback src={coverUrl(post.game.coverImageUrl)} alt="" fallback={<GameCoverFallback title={gameTitle} />} />
      </span>
      <span className={styles.gameInfo}>
        <strong>{gameTitle}</strong>
        <small>
          {post.game.releaseDate?.slice(0, 4) ?? "출시일 미정"}
          {post.game.rating != null && ` · ★ ${post.game.rating.toFixed(1)}`}
        </small>
      </span>
      <span className={feed.arrow} aria-hidden="true">↗</span>
    </Link>}

    <div className={feed.actions}>
      <button type="button" aria-pressed={post.liked} disabled={pending} onClick={() => void like()}
        aria-label={`좋아요 ${post.likeCount}개${post.liked ? ", 내가 좋아요를 누름" : ""}`}>
        <span aria-hidden="true">{post.liked ? "♥" : "♡"}</span>{post.likeCount.toLocaleString("ko-KR")}
      </button>
      <button type="button" aria-expanded={commentsOpen} disabled={hidden} onClick={() => setCommentsOpen(value => !value)}
        aria-label={`댓글 ${post.commentCount}개${commentsOpen ? " 접기" : " 보기"}`}>
        <span aria-hidden="true">💬</span>{post.commentCount.toLocaleString("ko-KR")}
      </button>
    </div>
    {error && <p className={feed.error} role="alert">{error}</p>}
    {!hidden && !commentsOpen && post.commentCount > 0 && <button type="button" className={styles.viewComments}
      onClick={() => setCommentsOpen(true)}>댓글 {post.commentCount.toLocaleString("ko-KR")}개 모두 보기</button>}
    {!hidden && <PostComments postId={post.id} authorized={authorized} open={commentsOpen} onOpen={() => setCommentsOpen(true)}
      onCount={count => { if (count !== post.commentCount) onChange({ ...post, commentCount: count }); }} />}
  </article>;
}

type MenuItem = { label: string; danger?: boolean; onSelect: () => void };

// 글 오른쪽 위 ⋯ 메뉴: 무엇을 수정·삭제하는지 헷갈리지 않도록 글 머리에 둔다
function PostMenu({ items, disabled }: { items: MenuItem[]; disabled: boolean }) {
  const [open, setOpen] = useState(false);
  const root = useRef<HTMLDivElement>(null);

  useEffect(() => {
    if (!open) return;
    const close = (event: MouseEvent | KeyboardEvent) => {
      if (event instanceof KeyboardEvent ? event.key === "Escape" : !root.current?.contains(event.target as Node)) setOpen(false);
    };
    document.addEventListener("mousedown", close);
    document.addEventListener("keydown", close);
    return () => {
      document.removeEventListener("mousedown", close);
      document.removeEventListener("keydown", close);
    };
  }, [open]);

  return <div className={styles.menu} ref={root}>
    <button type="button" className={styles.menuButton} aria-label="게시글 메뉴" aria-haspopup="menu" aria-expanded={open}
      disabled={disabled} onClick={() => setOpen(value => !value)}>⋯</button>
    {open && <div className={styles.menuList} role="menu">
      {items.map(item => <button key={item.label} type="button" role="menuitem" className={item.danger ? styles.danger : undefined}
        onClick={() => { setOpen(false); item.onSelect(); }}>{item.label}</button>)}
    </div>}
  </div>;
}
