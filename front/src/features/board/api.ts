import { acceptRefreshedToken } from "@/features/auth/api";
import { ReviewApiError } from "@/features/reviews/api";

export type BoardAuthor = { id: number; nickname: string; profileImageUrl: string | null };
export type BoardGame = { id: number; title: string | null; coverImageUrl: string | null; releaseDate: string | null; rating: number | null };
export type BoardPost = {
  id: number; title: string; content: string; spoiler: boolean; edited: boolean; createdDate: string;
  author: BoardAuthor; mine: boolean; followingAuthor: boolean; game: BoardGame | null;
  likeCount: number; liked: boolean; commentCount: number;
};
export type BoardPostPage = { posts: BoardPost[]; nextCursor: number | null };
export type BoardComment = {
  id: number; parentId: number | null; author: BoardAuthor | null; content: string; spoiler: boolean;
  removed: boolean; edited: boolean; mine: boolean; replyCount: number; createdDate: string;
};
export type BoardCommentPage = { comments: BoardComment[]; nextCursor: number | null; totalCount: number };
export type BoardPostInput = { title: string; content: string; spoiler: boolean; gameId: number | null };

export async function boardRequest<T>(path: string, token: string, method = "GET", body?: unknown, signal?: AbortSignal): Promise<T> {
  const response = await fetch(`/api/review-api/community/posts${path}`, {
    method, cache: "no-store", signal,
    headers: { Authorization: `Bearer ${token}`, ...(body ? { "Content-Type": "application/json" } : {}) },
    body: body ? JSON.stringify(body) : undefined,
  });
  acceptRefreshedToken(response);
  const result = await response.json();
  if (!response.ok) throw new ReviewApiError(result.msg || "게시판 요청에 실패했습니다.", response.status, result.resultCode);
  return result.data;
}

export function postListPath(keyword: string, after?: number | null): string {
  const query = new URLSearchParams();
  if (keyword) query.set("q", keyword);
  if (after) query.set("after", String(after));
  return query.size ? `?${query}` : "";
}

export function commentListPath(postId: number, parentId: number | null, after?: number | null): string {
  const query = new URLSearchParams();
  if (parentId) query.set("parentId", String(parentId));
  if (after) query.set("after", String(after));
  return `/${postId}/comments${query.size ? `?${query}` : ""}`;
}
