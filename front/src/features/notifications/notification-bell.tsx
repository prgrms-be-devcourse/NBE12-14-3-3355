"use client";

import { useEffect, useRef, useState } from "react";
import { usePathname, useRouter } from "next/navigation";
import { useAuth } from "@/features/auth/auth-context";
import { notificationListPath, notificationMessage, notificationRequest, type Notification, type NotificationPage } from "./api";
import "./notifications.css";

export default function NotificationBell() {
  const { accessToken } = useAuth();
  const pathname = usePathname();
  const router = useRouter();
  const [open, setOpen] = useState(false);
  const [count, setCount] = useState<number | null>(null);
  const [page, setPage] = useState<NotificationPage>({ items: [], nextCursor: null });
  const [loaded, setLoaded] = useState(false);
  const [loading, setLoading] = useState(false);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState("");
  const [unreadOnly, setUnreadOnly] = useState(false);
  const [detail, setDetail] = useState<Notification | null>(null);
  const panel = useRef<HTMLDivElement>(null);
  const bell = useRef<HTMLButtonElement>(null);

  useEffect(() => {
    if (!accessToken) return;
    const controller = new AbortController();
    notificationRequest<number>("/unread-count", accessToken, "GET", controller.signal)
      .then(setCount).catch(() => { if (!controller.signal.aborted) setCount(null); });
    return () => controller.abort();
  }, [accessToken, pathname, open]);

  useEffect(() => {
    if (!open || !accessToken) return;
    const controller = new AbortController();
    notificationRequest<NotificationPage>(notificationListPath(unreadOnly), accessToken, "GET", controller.signal)
      .then(data => { if (!controller.signal.aborted) { setPage(data); setLoaded(true); setError(""); } })
      .catch(e => { if (!controller.signal.aborted) setError(e.message); });
    return () => controller.abort();
  }, [open, accessToken, pathname, unreadOnly]);

  useEffect(() => {
    if (!open) return;
    const close = (event: PointerEvent) => { if (event.target instanceof Node && !panel.current?.contains(event.target)) setOpen(false); };
    const escape = (event: KeyboardEvent) => { if (event.key === "Escape") { setOpen(false); bell.current?.focus(); } };
    document.addEventListener("pointerdown", close);
    document.addEventListener("keydown", escape);
    return () => { document.removeEventListener("pointerdown", close); document.removeEventListener("keydown", escape); };
  }, [open]);

  async function refreshCount() {
    if (!accessToken) return;
    try { setCount(await notificationRequest<number>("/unread-count", accessToken)); }
    catch { setCount(null); }
  }
  async function read(n: Notification) {
    if (!accessToken || busy || loading) return;
    setBusy(true); setError("");
    try {
      const updated = await notificationRequest<Notification>(`/${n.id}/read`, accessToken, "PUT");
      setPage(p => ({ ...p, items: unreadOnly ? p.items.filter(item => item.id !== n.id) : p.items.map(item => item.id === n.id ? updated : item) }));
      await refreshCount();
      if (updated.url) {
        const destination = new URL(updated.url, window.location.origin);
        // A second click on the same notice must reopen comments on the current page.
        if (destination.searchParams.has("commentReview")) {
          const previousVisit = Number(new URL(window.location.href).searchParams.get("notificationVisit"));
          destination.searchParams.set("notificationVisit", String(Number.isSafeInteger(previousVisit) ? previousVisit + 1 : 1));
        }
        setOpen(false);
        router.push(destination.pathname + destination.search);
      }
      else setDetail(updated);
    } catch (e) { setError(e instanceof Error ? e.message : "읽음 처리에 실패했습니다."); }
    finally { setBusy(false); }
  }
  async function readAll() {
    if (!accessToken || busy || loading) return;
    setBusy(true); setError("");
    try {
      await notificationRequest("/read-all", accessToken, "PUT");
      // Refetch: another action may have created a notice during the request.
      setPage(await notificationRequest<NotificationPage>(notificationListPath(unreadOnly), accessToken));
      await refreshCount();
    } catch (e) { setError(e instanceof Error ? e.message : "읽음 처리에 실패했습니다."); }
    finally { setBusy(false); }
  }
  async function more() {
    if (!accessToken || page.nextCursor == null || loading || busy || !loaded) return;
    setLoading(true); setError("");
    try {
      const next = await notificationRequest<NotificationPage>(notificationListPath(unreadOnly, page.nextCursor), accessToken);
      setPage(p => ({ items: [...p.items, ...next.items.filter(n => !p.items.some(old => old.id === n.id))], nextCursor: next.nextCursor }));
    } catch (e) { setError(e instanceof Error ? e.message : "알림을 불러오지 못했습니다."); }
    finally { setLoading(false); }
  }
  return <div className="notification-root" ref={panel}>
    <button ref={bell} className="notification-bell" aria-label={`알림${count == null ? "" : `, 미확인 ${count}개`}`} aria-expanded={open} aria-controls="notification-panel"
      onClick={() => { setOpen(!open); setDetail(null); setLoaded(false); setError(""); }}>
      <svg width="22" height="22" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.7" aria-hidden="true"><path d="M18 8a6 6 0 0 0-12 0c0 7-3 7-3 9h18c0-2-3-2-3-9M10 21h4" /></svg>
      {count != null && count > 0 && <span className="notification-badge">{count > 99 ? "99+" : count}</span>}
    </button>
    {open && <section id="notification-panel" className="notification-panel" aria-label="알림 목록">
      <div className="notification-heading"><h2>알림</h2><button disabled={busy || loading || !loaded} onClick={readAll}>모두 읽음</button><button onClick={() => setOpen(false)} aria-label="알림 닫기">닫기</button></div>
      <label className="notification-filter"><input type="checkbox" checked={unreadOnly} disabled={busy || loading}
        onChange={event => { setUnreadOnly(event.target.checked); setLoaded(false); setDetail(null); setError(""); }} /> 미확인만 보기</label>
      {error && <p role="alert">{error}</p>}
      {detail && <div className="notification-detail" role="status"><p>{notificationMessage(detail)}</p>{detail.gameTitle && <p>{detail.gameTitle}</p>}<time>{detail.createdAt.replace("T", " ").slice(0, 16)}</time>{detail.unavailable && <p>삭제되거나 숨겨진 대상이므로 열 수 없습니다.</p>}</div>}
      {!loaded && !error && <p role="status">알림을 불러오는 중…</p>}
      {loaded && !page.items.length && !error && <p>{unreadOnly ? "미확인 알림이 없습니다." : "알림이 없습니다."}</p>}
      <ul>{(loaded ? page.items : []).map(n => <li key={n.id}><button className={`notification-item${n.read ? "" : " unread"}`} disabled={busy || loading} onClick={() => read(n)}>
        <span>{notificationMessage(n)}</span>{n.gameTitle && <small>{n.gameTitle}</small>}<time>{n.createdAt.replace("T", " ").slice(0, 16)}</time>{!n.read && <small>미확인</small>}
      </button></li>)}</ul>
      {loaded && page.nextCursor != null && <button disabled={loading || busy} onClick={more}>{loading ? "불러오는 중…" : "더 보기"}</button>}
    </section>}
  </div>;
}
