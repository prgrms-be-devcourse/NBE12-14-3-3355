import { acceptRefreshedToken } from "@/features/auth/api";

export type Notification = {
  id: number;
  kind: "REVIEW_LIKE" | "FOLLOW" | "REVIEW_HIDDEN" | "REVIEW_COMMENT" | "REVIEW_REPLY" | "REVIEW_COMMENT_LIKE";
  actorName: string | null;
  gameTitle: string | null;
  createdAt: string;
  read: boolean;
  url: string | null;
  unavailable: boolean;
};
export type NotificationPage = { items: Notification[]; nextCursor: number | null };
export async function notificationRequest<T>(path: string, token: string, method = "GET", signal?: AbortSignal): Promise<T> {
  const response = await fetch(`/api/review-api/notifications${path}`, {
    method, headers: { Authorization: `Bearer ${token}` }, cache: "no-store", signal,
  });
  acceptRefreshedToken(response);
  const body = await response.json();
  if (!response.ok) throw new Error(body.msg || "알림을 불러오지 못했습니다.");
  return body.data;
}
export function notificationMessage(n: Notification): string {
  const name = n.actorName || "사용자";
  switch (n.kind) {
    case "FOLLOW": return `${name}님이 회원님을 팔로우했습니다.`;
    case "REVIEW_LIKE": return `${name}님이 회원님의 리뷰를 좋아합니다.`;
    case "REVIEW_COMMENT": return `${name}님이 회원님의 리뷰에 댓글을 남겼습니다.`;
    case "REVIEW_REPLY": return `${name}님이 회원님의 리뷰 또는 댓글에 답글을 남겼습니다.`;
    case "REVIEW_COMMENT_LIKE": return `${name}님이 회원님의 댓글을 좋아합니다.`;
    case "REVIEW_HIDDEN": return "작성하신 리뷰가 신고 검토 후 숨김 처리되었습니다.";
  }
}

export function notificationListPath(unreadOnly: boolean, before?: number | null): string {
  const query = new URLSearchParams();
  if (unreadOnly) query.set("unreadOnly", "true");
  if (before != null) query.set("before", String(before));
  return query.size ? `?${query}` : "";
}
