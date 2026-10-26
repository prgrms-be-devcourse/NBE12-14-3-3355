"use client";

import { Suspense, useEffect, useState } from "react";
import { useSearchParams } from "next/navigation";
import { useAuth } from "@/features/auth/auth-context";
import { acceptRefreshedToken } from "@/features/auth/api";
import ReviewComments from "./review-comments";
import type { Review } from "../types";

// 원래 리뷰가 목록의 다음 페이지로 밀렸더라도 재로그인 후 초안에 접근할 수 있다.
export default function CommentReturn({ gameId }: { gameId?: string }) {
  return <Suspense fallback={null}><CommentReturnContent gameId={gameId} /></Suspense>;
}

function CommentReturnContent({ gameId }: { gameId?: string }) {
  const searchParams = useSearchParams();
  const target = searchParams.get("notificationComment");
  const targetCommentId = target && /^[1-9]\d*$/.test(target) ? Number(target) : undefined;
  const reviewId = searchParams.get("commentReview");
  return <CommentReturnReview key={`${gameId}:${reviewId}:${targetCommentId}`} gameId={gameId} reviewId={reviewId} targetCommentId={targetCommentId} />;
}

function CommentReturnReview({ gameId, reviewId, targetCommentId }: { gameId?: string; reviewId: string | null; targetCommentId?: number }) {
  const { accessToken } = useAuth();
  const [review, setReview] = useState<Review | null>(null);
  const [error, setError] = useState("");
  const [revealed, setRevealed] = useState(false);
  useEffect(() => {
    const id = reviewId;
    if (!accessToken || !id || !/^[1-9]\d*$/.test(id)) return;
    const controller = new AbortController();
    fetch(`/api/review-api/reviews/${id}`, { headers: { Authorization: `Bearer ${accessToken}` }, signal: controller.signal, cache: "no-store" })
      .then(async response => {
        acceptRefreshedToken(response);
        const body = await response.json();
        if (!response.ok) throw new Error(body.msg || "원래 리뷰를 불러오지 못했습니다.");
        if (gameId && String(body.data.gameId) !== gameId) throw new Error("이 게임의 리뷰가 아닙니다.");
        if (!controller.signal.aborted) setReview(body.data);
      }).catch(e => { if (!controller.signal.aborted) setError(e.message); });
    return () => controller.abort();
  }, [accessToken, gameId, reviewId]);
  if (error) return <p role="alert">{error} 삭제되거나 숨겨진 리뷰에는 댓글을 등록할 수 없습니다.</p>;
  if (!review) return null;
  return <section aria-label={targetCommentId ? "알림 받은 댓글" : "리뷰와 댓글"} style={{ padding: 20, marginBottom: 24, border: "1px solid #4a6340", borderRadius: 12 }}>
    <h2>{targetCommentId ? "알림 받은 댓글" : "리뷰와 댓글"}</h2><p>{review.nickname}님의 {review.gameTitle} 리뷰</p>
    {review.spoiler && !revealed ? <button onClick={() => setRevealed(true)}>스포일러 리뷰 · 내용 보기</button>
      : <p style={{ whiteSpace: "pre-wrap", overflowWrap: "anywhere" }}>{review.content}</p>}
    <ReviewComments key={`${review.reviewId}:${targetCommentId ?? ""}`} targetCommentId={targetCommentId} reviewId={review.reviewId} locked={review.spoiler && !revealed} autoOpen />
  </section>;
}
