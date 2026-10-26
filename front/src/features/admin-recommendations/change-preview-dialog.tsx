"use client";

import { useEffect, useRef, useState } from "react";
import { request, type Page } from "./api";
import styles from "@/app/admin/recommendations/page.module.css";

type PreviewTarget = { gameId: string; title: string | null };

export default function ChangePreviewDialog({ token, onClose }: { token: string; onClose: () => void }) {
  const dialog = useRef<HTMLDialogElement>(null);
  const [page, setPage] = useState(0);
  const [revision, setRevision] = useState(0);
  const [result, setResult] = useState<Page<PreviewTarget> | null>(null);
  const [error, setError] = useState("");

  useEffect(() => { dialog.current?.showModal(); }, []);
  useEffect(() => {
    const controller = new AbortController();
    request<Page<PreviewTarget>>(token, `changes/preview?page=${page}&size=20`, {
      signal: AbortSignal.any([controller.signal, AbortSignal.timeout(15000)]),
    }).then(value => {
      if (!controller.signal.aborted) { setResult(value); setError(""); }
    }).catch(reason => {
      if (!controller.signal.aborted) setError(reason instanceof Error ? reason.message : "미리보기 조회에 실패했습니다.");
    });
    return () => controller.abort();
  }, [token, page, revision]);

  function reload(nextPage: number) {
    setResult(null);
    setError("");
    setPage(nextPage);
    setRevision(value => value + 1);
  }

  return <dialog ref={dialog} className={styles.previewDialog} aria-labelledby="preview-title"
    aria-describedby="preview-help" onCancel={event => { event.preventDefault(); onClose(); }}>
    <div className={styles.heading}>
      <h2 id="preview-title">변경 영향 게임 미리보기</h2>
      <button type="button" onClick={onClose} aria-label="미리보기 닫기">닫기</button>
    </div>
    <p id="preview-help">미처리 변경의 영향 게임과 이미 선정된 갱신 대기를 중복 없이 표시합니다. 조회만으로 갱신을 실행하지 않습니다. 조회·페이지 이동 후 데이터가 변경되면 실제 실행 대상과 달라질 수 있습니다.</p>
    <button type="button" onClick={() => reload(0)}>최신 목록 다시 조회</button>
    {error ? <p role="alert" className={styles.error}>{error}</p> : !result ? <p role="status">영향 게임 조회 중…</p> : <>
      <p role="status">현재 예상 대상 <strong>{result.totalElements.toLocaleString()}개</strong></p>
      <div className={styles.tableWrap}><table>
        <caption className={styles.srOnly}>변경 영향 게임 ID 및 이름</caption>
        <thead><tr><th scope="col">게임 ID</th><th scope="col">게임 이름</th></tr></thead>
        <tbody>{result.content.map(game => <tr key={game.gameId}><td>{game.gameId}</td><td className={styles.failure}>{game.title || "이름 없음"}</td></tr>)}</tbody>
      </table></div>
      {!result.content.length && <p>현재 페이지에 변경 영향 게임이 없습니다.</p>}
      <nav className={styles.pagination} aria-label="변경 영향 게임 페이지">
        <button type="button" disabled={page === 0} onClick={() => reload(page - 1)}>이전</button>
        <span>{page + 1} / {Math.max(1, result.totalPages)} 페이지</span>
        <button type="button" disabled={page + 1 >= result.totalPages} onClick={() => reload(page + 1)}>다음</button>
      </nav>
    </>}
  </dialog>;
}
