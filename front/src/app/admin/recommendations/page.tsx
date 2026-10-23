"use client";

import Link from "next/link";
import { useEffect, useRef, useState } from "react";
import { useAuth } from "@/features/auth/auth-context";
import {
  isActive, normalizeGameIds, request, statusLabels, targetLabels,
  type ExecutionStatus, type Failure, type Page, type Run, type StartRequest, type TargetType,
} from "@/features/admin-recommendations/api";
import styles from "./page.module.css";

function time(value: string | null) {
  return value ? new Date(value).toLocaleString("ko-KR") : "—";
}
function message(error: unknown) {
  return error instanceof Error ? error.message : "조회에 실패했습니다.";
}

function Pagination({ page, totalPages, onChange, label }: {
  page: number; totalPages: number; onChange: (page: number) => void; label: string;
}) {
  return <nav className={styles.pagination} aria-label={label}>
    <button type="button" onClick={() => onChange(page - 1)} disabled={page === 0}>이전</button>
    <span>{page + 1} / {Math.max(1, totalPages)} 페이지</span>
    <button type="button" onClick={() => onChange(page + 1)} disabled={page + 1 >= totalPages}>다음</button>
  </nav>;
}

function RunSummary({ run }: { run: Run }) {
  return <>
    <div className={styles.heading}><h3>{targetLabels[run.type]}</h3><span className={styles.badge}>{statusLabels[run.status]}</span></div>
    {run.status === "ACCEPTED" && <p>요청을 접수했습니다. 계산 시작 전이며, 실행 경쟁이 발생하면 거절될 수 있습니다.</p>}
    {run.status === "PREPARING" && <p>계산 대상과 공통 데이터를 준비하고 있습니다.</p>}
    {run.status === "SUCCEEDED" && run.processed === 0 && <p>처리 대상이 없어 정상 완료했습니다.</p>}
    {run.status === "REJECTED" && <p>계산을 시작하지 못했습니다. 실행 상태를 확인한 뒤 수동으로 다시 요청하세요.</p>}
    {run.status === "INTERRUPTED" && <p>자동으로 이어서 실행하지 않습니다. 처리 건수는 마지막 저장 기준이며, 이미 저장된 게임별 결과는 유지됩니다.</p>}
    {run.heartbeatExpired && <p className={styles.warning}>생존 신호 확인이 지연되고 있습니다. 이 정보만으로 작업 중단을 확정하지 않습니다.</p>}
    <dl className={styles.metrics}>
      <div><dt>예상 대상</dt><dd>{run.estimatedTotal === null ? "준비 중" : `${run.estimatedTotal.toLocaleString()}개`}</dd></div>
      <div><dt>처리</dt><dd>{run.processed.toLocaleString()}개</dd></div>
      <div><dt>성공</dt><dd>{run.succeeded.toLocaleString()}개</dd></div>
      <div><dt>실패</dt><dd>{run.failed.toLocaleString()}개</dd></div>
      <div><dt>경과 시간</dt><dd>{run.elapsedSeconds.toLocaleString()}초</dd></div>
    </dl>
    <p>예상 대상 수와 최종 처리 수는 실행 중 게임 변경으로 달라질 수 있습니다. 후보가 없는 정상 계산도 성공에 포함됩니다.</p>
    <dl className={styles.details}>
      <dt>실행 ID</dt><dd>{run.runId}</dd>
      <dt>요청 관리자</dt><dd>{run.requestedBy}</dd>
      <dt>접수 시각</dt><dd>{time(run.requestedAt)}</dd>
      <dt>시작 / 종료</dt><dd>{time(run.startedAt)} / {time(run.finishedAt)}</dd>
      <dt>마지막 집계</dt><dd>{time(run.progressUpdatedAt)}</dd>
      <dt>생존 신호</dt><dd>{time(run.heartbeatAt)}</dd>
      {run.sourceRunId && <><dt>원본 실행</dt><dd>{run.sourceRunId}</dd></>}
      {run.type === "IDS" && <><dt>지정 게임 ID</dt><dd>{run.gameIds.join(", ")}</dd></>}
    </dl>
    {run.error && <p className={styles.error}>{run.error}</p>}
  </>;
}

export default function RecommendationsAdminPage() {
  const auth = useAuth();
  const token = auth.accessToken;
  const isAdmin = auth.status === "authenticated" && auth.user?.role === "ADMIN";
  const [scope, setScope] = useState<"ALL" | "PARTIAL">("PARTIAL");
  const [partialType, setPartialType] = useState<Exclude<TargetType, "ALL">>("IDS");
  const [ids, setIds] = useState("");
  const [status, setStatus] = useState<ExecutionStatus | null>(null);
  const [statusError, setStatusError] = useState("");
  const [history, setHistory] = useState<Page<Run> | null>(null);
  const [historyError, setHistoryError] = useState("");
  const [historyPage, setHistoryPage] = useState(0);
  const [selectedId, setSelectedId] = useState<string | null>(null);
  const [detail, setDetail] = useState<Run | null>(null);
  const [detailError, setDetailError] = useState("");
  const [failures, setFailures] = useState<Page<Failure> | null>(null);
  const [failureError, setFailureError] = useState("");
  const [failurePage, setFailurePage] = useState(0);
  const [sending, setSending] = useState(false);
  const submitting = useRef(false);
  const [notice, setNotice] = useState("");
  const [submitError, setSubmitError] = useState("");
  const [refresh, setRefresh] = useState(0);

  // 조회가 끝난 뒤 다음 조회를 예약해 응답이 느려도 폴링을 중복하지 않는다.
  // 페이지·선택·인증 변경 시 이전 요청을 취소해 오래된 결과의 덮어쓰기를 막는다.
  useEffect(() => {
    if (!isAdmin || !token) return;
    const controller = new AbortController();
    let timer: ReturnType<typeof setTimeout> | undefined;
    async function poll() {
      const signal = AbortSignal.any([controller.signal, AbortSignal.timeout(15000)]);
      const results = await Promise.allSettled([
        request<ExecutionStatus>(token!, "status", { signal }),
        request<Page<Run>>(token!, `runs?page=${historyPage}&size=10`, { signal }),
        selectedId ? request<Run>(token!, `runs/${selectedId}`, { signal }) : Promise.resolve(null),
        selectedId ? request<Page<Failure>>(token!, `runs/${selectedId}/failures?page=${failurePage}&size=10`, { signal }) : Promise.resolve(null),
      ] as const);
      if (controller.signal.aborted) return;
      const [current, records, selected, failed] = results;
      if (current.status === "fulfilled") { setStatus(current.value); setStatusError(""); }
      else setStatusError(message(current.reason));
      if (records.status === "fulfilled") { setHistory(records.value); setHistoryError(""); }
      else setHistoryError(message(records.reason));
      if (selected?.status === "fulfilled" && selected.value) { setDetail(selected.value); setDetailError(""); }
      else if (selected?.status === "rejected") setDetailError(message(selected.reason));
      if (failed?.status === "fulfilled" && failed.value) { setFailures(failed.value); setFailureError(""); }
      else if (failed?.status === "rejected") setFailureError(message(failed.reason));
      timer = setTimeout(poll, 5000);
    }
    void poll();
    return () => { controller.abort(); clearTimeout(timer); };
  }, [isAdmin, token, historyPage, selectedId, failurePage, refresh]);

  const busy = Boolean(status && (status.databaseLocked || status.localRequestActive || status.activeRuns.length));
  const blocked = sending || !status || Boolean(statusError) || busy;
  const selectedRun = detail?.runId === selectedId ? detail : null;

  function selectRun(runId: string) {
    setSelectedId(runId);
    setFailurePage(0);
    setFailures(null);
    setDetailError("");
    setFailureError("");
    setRefresh((value) => value + 1);
  }

  function closeDetail() {
    setSelectedId(null);
    setDetail(null);
    setFailures(null);
    setFailurePage(0);
    setDetailError("");
    setFailureError("");
  }

  async function start(source?: Run) {
    if (!token || blocked || submitting.current) return;
    setSubmitError("");
    setNotice("");
    let body: StartRequest;
    try {
      const type = source?.type ?? (scope === "ALL" ? "ALL" : partialType);
      if (source?.gameIds.some((id) => !Number.isSafeInteger(id))) {
        throw new Error("지정 ID를 정확히 복원할 수 없습니다. 부분 갱신 입력창에 ID를 직접 입력하세요.");
      }
      body = {
        type,
        ...(type === "IDS" ? { gameIds: normalizeGameIds(source ? source.gameIds.join(",") : ids) } : {}),
        ...(source ? { sourceRunId: source.runId } : {}),
      };
    } catch (error) { setSubmitError(message(error)); return; }
    const description = body.type === "IDS"
      ? `지정한 기준 게임 ${body.gameIds!.split(",").length}개의 추천 후보를 다시 계산합니다.`
      : body.type === "ALL" ? "모든 기준 게임의 추천 후보를 다시 계산합니다. 실행 규모에 따라 오래 걸릴 수 있습니다."
      : body.type === "FAILED" ? "현재 마지막 계산 시도가 실패한 게임의 추천 후보를 다시 계산합니다."
      : "아직 최초 계산에 성공하지 않은 게임의 추천 후보를 계산합니다.";
    if (!window.confirm(`${description}\n${source ? "원본 실행과 같은 범위의 새 실행을 만듭니다. 이미 성공한 게임도 다시 처리될 수 있습니다.\n" : ""}갱신을 요청할까요?`)) return;
    submitting.current = true;
    setSending(true);
    try {
      const run = await request<Run>(token, "runs", { body });
      setDetail(run);
      selectRun(run.runId);
      setHistoryPage(0);
      setNotice("요청을 접수했습니다. 아래 실행 상세에서 실제 시작과 처리 결과를 확인하세요.");
    } catch (error) {
      setSubmitError(`${message(error)} 실행 결과를 확인하지 못했다면 상태와 최신 실행 이력을 확인한 뒤 다시 요청하세요.`);
    } finally {
      // 연결이 끊겨도 POST를 자동 재전송하지 않고, 최신 상태 조회 전에는 다음 전송을 막는다.
      setStatus(null);
      setRefresh((value) => value + 1);
      submitting.current = false;
      setSending(false);
    }
  }

  if (!isAdmin) return <main className={styles.main}>
    <h1>추천 갱신</h1><p>{auth.status === "loading" ? "로그인 확인 중…" : "관리자만 접근할 수 있습니다."}</p>
    {auth.status !== "loading" && <Link href="/login?next=/admin/recommendations">로그인</Link>}
  </main>;

  return <main className={styles.main}>
    <header><span className={styles.eyebrow}>ADMIN CONSOLE</span><h1>연관 추천 후보 갱신</h1>
      <p>선택한 기준 게임의 공통 연관 후보를 다시 계산해 최대 20개까지 저장합니다. 게임 데이터 수집은 <Link href="/admin/igdb">게임 데이터 관리</Link>에서 실행하세요.</p>
    </header>

    <section className={styles.panel} aria-labelledby="request-title">
      <h2 id="request-title">갱신 요청</h2>
      <div className={styles.scope} role="group" aria-label="갱신 범위">
        <button type="button" aria-pressed={scope === "ALL"} onClick={() => setScope("ALL")}>전체 갱신</button>
        <button type="button" aria-pressed={scope === "PARTIAL"} onClick={() => setScope("PARTIAL")}>부분 갱신</button>
      </div>
      {scope === "ALL" ? <p>실행 시점의 전체 기준 게임을 대상으로 갱신합니다. 대상 예상 수는 계산 준비가 끝난 뒤 표시됩니다.</p> : <>
        <label className={styles.field}>부분 갱신 대상
          <select value={partialType} onChange={(event) => setPartialType(event.target.value as Exclude<TargetType, "ALL">)}>
            <option value="IDS">지정 ID</option><option value="FAILED">실패 게임</option><option value="UNREADY">최초 계산 미완료 게임</option>
          </select>
        </label>
        {partialType === "IDS" ? <label className={styles.field}>기준 게임 ID
          <textarea value={ids} onChange={(event) => setIds(event.target.value)} maxLength={30000} rows={4} placeholder="예: 1, 2, 3" aria-describedby="ids-help" />
          <span id="ids-help">쉼표·공백·줄바꿈으로 구분하며 중복 제외 최대 1,000개입니다. 잘못된 ID나 없는 게임이 있으면 요청 전체가 거절됩니다.</span>
        </label> : <p>{partialType === "FAILED" ? "마지막 계산 시도가 실패한 게임을 대상으로 합니다. 이전 성공 이력이 있는 게임도 포함됩니다." : "최초 성공 이력이 없는 게임을 대상으로 합니다. 아직 계산 상태가 없는 게임도 포함됩니다."} 실제 대상은 실행 시점에 선정합니다.</p>}
      </>}
      <p>게임별로 후보를 교체하며, 갱신 실패 시 이전 정상 후보와 마지막 성공 이력을 보존합니다. 창을 닫아도 서버에서 계속 실행됩니다.</p>
      <button className={styles.primary} type="button" onClick={() => void start()} disabled={blocked}>{sending ? "요청 중…" : busy ? "다른 추천 작업 진행 중" : `${scope === "ALL" ? "전체" : "부분"} 갱신 요청`}</button>
      <p role="status">{notice}</p>
      {submitError && <p role="alert" className={styles.error}>{submitError}</p>}
    </section>

    <section className={styles.panel} aria-labelledby="status-title">
      <div className={styles.heading}><h2 id="status-title">현재 실행 상태</h2><button type="button" onClick={() => setRefresh((value) => value + 1)}>상태 다시 조회</button></div>
      <p>5초 간격으로 조회합니다. 진행 건수는 마지막 저장 기준이며 실제 처리보다 늦게 표시될 수 있습니다.</p>
      {statusError && <p role="alert" className={styles.error}>상태 조회 실패: {statusError} 최신 상태 확인 전에는 실행 요청이 제한됩니다.</p>}
      {!status && !statusError && <p>실행 상태 확인 중…</p>}
      {status && <>
        <p>{busy ? "추천 갱신 요청 또는 계산 작업이 진행 중입니다." : "현재 확인된 추천 작업이 없습니다."}</p>
        {status.databaseLocked && <p>공통 DB 실행 잠금이 사용 중입니다. 잠금 정보만으로 CLI·신규 게임 처리·관리자 작업 중 어떤 실행인지 구분할 수 없습니다.</p>}
        {status.recoveryDeferred && <p className={styles.warning}>오래된 실행 이력의 중단 확인을 보류하고 있습니다. 공통 잠금이 해제된 뒤 다시 확인합니다.</p>}
        <ul className={styles.activeList}>{status.activeRuns.map((run) => <li key={run.runId}>
          <button type="button" onClick={() => selectRun(run.runId)}>{targetLabels[run.type]} · {statusLabels[run.status]} · 처리 {run.processed.toLocaleString()}개{run.heartbeatExpired ? " · 생존 신호 지연" : ""}</button>
        </li>)}</ul>
      </>}
    </section>

    <section className={styles.panel} aria-labelledby="history-title">
      <h2 id="history-title">관리자 실행 이력</h2><p>CLI와 신규 게임 처리는 이 목록에 실행 이력을 남기지 않습니다.</p>
      {historyError && <p role="alert" className={styles.error}>이력 조회 실패: {historyError}</p>}
      {history && history.page === historyPage ? <>
        <div className={styles.tableWrap}><table><caption className={styles.srOnly}>관리자 추천 갱신 실행 이력</caption>
          <thead><tr><th scope="col">접수 시각</th><th scope="col">범위</th><th scope="col">상태</th><th scope="col">처리 / 성공 / 실패</th><th scope="col">상세</th></tr></thead>
          <tbody>{history.content.map((run) => <tr key={run.runId} aria-selected={selectedId === run.runId}>
            <td>{time(run.requestedAt)}</td><td>{targetLabels[run.type]}</td><td>{statusLabels[run.status]}</td>
            <td>{run.processed.toLocaleString()} / {run.succeeded.toLocaleString()} / {run.failed.toLocaleString()}</td>
            <td><button type="button" onClick={() => selectRun(run.runId)} aria-label={`${time(run.requestedAt)} ${targetLabels[run.type]} 상세 보기`}>보기</button></td>
          </tr>)}</tbody>
        </table></div>
        {!history.content.length && <p>실행 이력이 없습니다.</p>}
        <Pagination page={historyPage} totalPages={history.totalPages} onChange={setHistoryPage} label="실행 이력 페이지" />
      </> : !historyError && <p>실행 이력 조회 중…</p>}
    </section>

    {selectedId && <section className={styles.panel} aria-labelledby="detail-title">
      <div className={styles.heading}>
        <h2 id="detail-title">선택한 실행 상세</h2>
        <button type="button" onClick={closeDetail} aria-label="실행 상세 닫기">닫기</button>
      </div>
      {detailError && <p role="alert" className={styles.error}>상세 조회 실패: {detailError}</p>}
      {selectedRun ? <>
        <RunSummary run={selectedRun} />
        {!isActive(selectedRun) && <>
          <button type="button" disabled={blocked || Boolean(detailError)} onClick={() => void start(selectedRun)}>같은 범위로 새 실행 요청</button>
          <p>전체·실패·미완료 범위는 새 실행 시점에 다시 선정합니다. 지정 ID는 원본 ID를 사용합니다. 과거 실행의 실패 게임만 그대로 재실행하는 기능은 아닙니다.</p>
        </>}
        <h3>실패한 게임</h3>
        {failureError && <p role="alert" className={styles.error}>실패 목록 조회 실패: {failureError}</p>}
        {failures && failures.page === failurePage ? <>
          <div className={styles.tableWrap}><table><caption className={styles.srOnly}>선택한 실행의 실패 게임 목록</caption>
            <thead><tr><th scope="col">게임 ID</th><th scope="col">오류 요약</th><th scope="col">발생 시각</th></tr></thead>
            <tbody>{failures.content.map((failure) => <tr key={failure.gameId}><td>{failure.gameId}</td><td className={styles.failure}>{failure.error}</td><td>{time(failure.occurredAt)}</td></tr>)}</tbody>
          </table></div>
          {!failures.content.length && <p>저장된 게임별 실패 기록이 없습니다. 작업 전체의 오류는 위 실행 상태와 오류 요약을 확인하세요.</p>}
          <Pagination page={failurePage} totalPages={failures.totalPages} onChange={setFailurePage} label="실패 게임 페이지" />
        </> : !failureError && <p>실패 목록 조회 중…</p>}
      </> : !detailError && <p>실행 상세 조회 중…</p>}
    </section>}
  </main>;
}
